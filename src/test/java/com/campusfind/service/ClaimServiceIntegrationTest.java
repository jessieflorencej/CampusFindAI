package com.campusfind.service;

import com.campusfind.controller.ClaimController.*;
import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.verification.VerificationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "campus.crypto-key-file=target/test-claims.key"})
@Import({ClaimService.class, VerificationService.class, CryptoService.class, Support.class, ClaimServiceIntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClaimServiceIntegrationTest {
    @TestConfiguration static class TestBeans {
        @Bean ItemService itemService() {
            ItemService mock = mock(ItemService.class);
            when(mock.publicView(any())).thenAnswer(call -> Map.of("id", ((Item) call.getArgument(0)).id, "title", ((Item) call.getArgument(0)).title));
            return mock;
        }
    }
    @Autowired ClaimService service;
    @Autowired ClaimRepository claims;
    @Autowired ItemRepository items;
    @Autowired UserRepository users;
    @Autowired UploadRepository uploads;
    @Autowired CryptoService crypto;
    Path directory;
    UserAccount claimant, finder, administrator, stranger;
    Item found;

    @BeforeEach void setup() throws Exception {
        directory = Files.createDirectories(Path.of("target", "claim-test-uploads", UUID.randomUUID().toString()).toAbsolutePath().normalize());
        claimant = account("Claimant", "USER"); finder = account("Finder", "USER");
        administrator = account("Administrator", "ADMIN"); stranger = account("Stranger", "USER");
        found = foundItem(finder);
    }

    @AfterEach void cleanupEvidence() throws Exception {
        Path root = Path.of("target", "claim-test-uploads").toAbsolutePath().normalize();
        if (directory == null || !directory.startsWith(root)) throw new IllegalStateException("Unexpected test evidence directory");
        try (var files = Files.list(directory)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
        Files.deleteIfExists(directory);
    }

    @Test void completeJourneyKeepsSecretsPrivateConsumesCodeAndReturnsBothReports() throws Exception {
        Item lost = foundItem(claimant); lost.type = "LOST"; lost.status = "LOST"; lost = items.saveAndFlush(lost);
        Upload evidence = evidence(claimant);
        long id = ((Number) service.submit(new Submission(found.id, lost.id, statement(), "CF2026A17", "Main Library", List.of(evidence.id)), principal(claimant)).get("id")).longValue();
        Map<String, Object> own = service.detail(id, principal(claimant));
        assertThat(own).doesNotContainKeys("foundPrivateDetails", "foundSerial", "assessment", "handoverCode");
        assertThat(own.get("scoreLabel")).isEqualTo("Evidence completeness");
        assertThat(claims.findById(id).orElseThrow().answers).doesNotContain("scratch");
        assertThatThrownBy(() -> service.detail(id, principal(stranger))).isInstanceOf(ResponseStatusException.class);
        service.finder(id, new FinderDecision("YES", "The physical mark is consistent."), principal(finder));
        Map<String, Object> review = service.detail(id, principal(administrator));
        assertThat(review.get("foundSerial")).isEqualTo("CF2026A17");
        service.review(id, new ReviewDecision("APPROVE", "Inspected the receipt and ownership marks.", "Library Reception"), principal(administrator));
        String code = (String) service.detail(id, principal(claimant)).get("handoverCode");
        assertThat(code).matches("CF-\\d{6}");
        assertThat(service.detail(id, principal(finder))).doesNotContainKey("handoverCode");
        assertThat(service.detail(id, principal(administrator))).doesNotContainKey("handoverCode");
        assertThatThrownBy(() -> service.confirm(id, principal(claimant))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.handover(id, code, principal(claimant))).isInstanceOf(ResponseStatusException.class);
        service.handover(id, code, principal(finder));
        assertThat(claims.findById(id).orElseThrow().handoverHash).isNull();
        assertThatThrownBy(() -> service.handover(id, code, principal(finder))).isInstanceOf(ResponseStatusException.class);
        service.confirm(id, principal(claimant));
        Claim completed = claims.findById(id).orElseThrow();
        assertThat(completed.status).isEqualTo("CLOSED");
        assertThat(completed.finderConfirmed && completed.claimantConfirmed).isTrue();
        assertThat(completed.returnedAt).isNotNull();
        assertThat(items.findById(found.id).orElseThrow().status).isEqualTo("RETURNED");
        assertThat(items.findById(lost.id).orElseThrow().status).isEqualTo("RETURNED");
        assertThat(users.findById(finder.id).orElseThrow().reputation).isEqualTo(10);
        assertThatThrownBy(() -> service.confirm(id, principal(claimant))).isInstanceOf(ResponseStatusException.class);
        assertThat(users.findById(finder.id).orElseThrow().reputation).isEqualTo(10);
    }

    @Test void invalidOtpAttemptsCommitAcrossFailingTransactionsAndLockTheHandover() throws Exception {
        long id = approvedClaim();
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> service.handover(id, "CF-INVALID", principal(finder))).isInstanceOf(ResponseStatusException.class);
            assertThat(claims.findById(id).orElseThrow().codeAttempts).isEqualTo(attempt);
        }
        Claim locked = claims.findById(id).orElseThrow();
        assertThat(locked.status).isEqualTo("ADMIN_REVIEW");
        assertThat(locked.handoverHash).isNull();
        assertThatThrownBy(() -> service.renewCode(id, principal(claimant))).isInstanceOf(ResponseStatusException.class);
        service.review(id, new ReviewDecision("APPROVE", "Checked both parties after the unsuccessful attempts.", null), principal(administrator));
        assertThat(claims.findById(id).orElseThrow().codeAttempts).isZero();
    }

    @Test void expiryRequiresClaimantRenewalAndPreservesPriorFailedAttempts() throws Exception {
        long id = approvedClaim();
        String oldCode = (String) service.detail(id, principal(claimant)).get("handoverCode");
        Claim expired = claims.findById(id).orElseThrow(); expired.codeExpiresAt = Instant.now().minusSeconds(10); expired.codeAttempts = 2; claims.saveAndFlush(expired);
        assertThatThrownBy(() -> service.handover(id, oldCode, principal(finder))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.renewCode(id, principal(finder))).isInstanceOf(ResponseStatusException.class);
        assertThat(service.detail(id, principal(claimant))).doesNotContainKey("handoverCode");
        service.renewCode(id, principal(claimant));
        assertThat(claims.findById(id).orElseThrow().codeAttempts).isEqualTo(2);
        assertThat(claims.findById(id).orElseThrow().codeExpiresAt).isAfter(Instant.now());
    }

    @Test void requireStoredOwnedEvidenceAndVerifiedIdentity() throws Exception {
        Upload otherPersonsEvidence = evidence(stranger);
        assertThatThrownBy(() -> submit(found, claimant, List.of(otherPersonsEvidence.id))).isInstanceOf(ResponseStatusException.class);
        Upload missingFile = evidence(claimant); Files.delete(Path.of(missingFile.path));
        assertThatThrownBy(() -> submit(found, claimant, List.of(missingFile.id))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> submit(found, claimant, List.of())).isInstanceOf(ResponseStatusException.class);
        claimant.verified = false; users.saveAndFlush(claimant);
        Upload valid = evidence(claimant);
        assertThatThrownBy(() -> submit(found, claimant, List.of(valid.id))).isInstanceOf(ResponseStatusException.class);
        assertThat(claims.findByClaimantId(claimant.id)).isEmpty();
    }

    @Test void prohibitSelfClaimsDuplicateClaimsPrematureAndConflictedApproval() throws Exception {
        Upload finderProof = evidence(finder);
        assertThatThrownBy(() -> submit(found, finder, List.of(finderProof.id))).isInstanceOf(ResponseStatusException.class);
        long id = submittedClaim();
        Upload duplicate = evidence(claimant);
        assertThatThrownBy(() -> submit(found, claimant, List.of(duplicate.id))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.review(id, new ReviewDecision("APPROVE", "Premature review must fail.", null), principal(administrator))).isInstanceOf(ResponseStatusException.class);
        finder.role = "ADMIN"; users.saveAndFlush(finder);
        service.finder(id, new FinderDecision("YES", "The description matches."), principal(finder));
        assertThatThrownBy(() -> service.review(id, new ReviewDecision("APPROVE", "Finder cannot self approve.", null), principal(finder))).isInstanceOf(ResponseStatusException.class);
        claimant.role = "ADMIN"; users.saveAndFlush(claimant);
        assertThatThrownBy(() -> service.review(id, new ReviewDecision("APPROVE", "Claimant cannot self approve.", null), principal(claimant))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void additionalEvidenceRestartsFinderReview() throws Exception {
        long id = submittedClaim();
        service.finder(id, new FinderDecision("UNSURE", "Need a clearer receipt."), principal(finder));
        service.review(id, new ReviewDecision("MORE_INFORMATION", "Please include a dated proof of purchase.", null), principal(administrator));
        Upload extra = evidence(claimant);
        service.evidence(id, new AdditionalEvidence(statement() + " Purchased last August; receipt attached.", List.of(extra.id)), principal(claimant));
        Claim revised = claims.findById(id).orElseThrow();
        assertThat(revised.status).isEqualTo("FINDER_REVIEW");
        assertThat(revised.finderResponse).isNull();
        assertThat(uploads.findByClaimId(id)).hasSize(2);
        assertThatThrownBy(() -> service.review(id, new ReviewDecision("APPROVE", "Cannot skip a second finder review.", null), principal(administrator))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void parallelSubmissionsCannotExceedThreeActiveClaims() throws Exception {
        List<Callable<Boolean>> attempts = new ArrayList<>();
        CountDownLatch gate = new CountDownLatch(1);
        for (int i = 0; i < 5; i++) {
            Item item = foundItem(finder); Upload proof = evidence(claimant);
            attempts.add(() -> { gate.await(); try { submit(item, claimant, List.of(proof.id)); return true; } catch (ResponseStatusException ex) { return false; } });
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
            List<Future<Boolean>> futures = attempts.stream().map(pool::submit).toList(); gate.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) if (future.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(3);
        }
        assertThat(claims.findByClaimantId(claimant.id)).hasSize(3);
    }

    @Test void successfulReturnRejectsCompetingClaimsAndRestoresTheirLostReport() throws Exception {
        long winner = submittedClaim();
        Item otherLost = foundItem(stranger); otherLost.type = "LOST"; otherLost.status = "LOST"; otherLost = items.saveAndFlush(otherLost);
        Upload proof = evidence(stranger);
        long other = ((Number) service.submit(new Submission(found.id, otherLost.id, statement(), "", "Main Library", List.of(proof.id)), principal(stranger)).get("id")).longValue();
        service.finder(winner, new FinderDecision("YES", "Ownership evidence is consistent."), principal(finder));
        service.review(winner, new ReviewDecision("APPROVE", "Evidence checked in person.", null), principal(administrator));
        String code = (String) service.detail(winner, principal(claimant)).get("handoverCode");
        service.handover(winner, code, principal(finder)); service.confirm(winner, principal(claimant));
        assertThat(claims.findById(other).orElseThrow().status).isEqualTo("REJECTED");
        assertThat(items.findById(otherLost.id).orElseThrow().status).isEqualTo("LOST");
        assertThat(users.findById(stranger.id).orElseThrow().flagged).isFalse();
    }

    private long submittedClaim() throws Exception { return submit(found, claimant, List.of(evidence(claimant).id)); }
    private long approvedClaim() throws Exception {
        long id = submittedClaim(); service.finder(id, new FinderDecision("YES", "Private markings inspected."), principal(finder));
        service.review(id, new ReviewDecision("APPROVE", "Ownership evidence reviewed.", "Security Office"), principal(administrator)); return id;
    }
    private long submit(Item item, UserAccount user, List<String> evidenceIds) {
        return ((Number) service.submit(new Submission(item.id, null, statement(), "CF2026A17", "Main Library", evidenceIds), principal(user)).get("id")).longValue();
    }
    private UserAccount account(String name, String role) {
        UserAccount user = new UserAccount(); user.name = name; user.email = UUID.randomUUID() + "@campus.edu";
        user.collegeId = UUID.randomUUID().toString(); user.passwordHash = "test-only"; user.role = role;
        user.active = true; user.verified = true; user.emailVerified = true; user.identityVerified = true;
        return users.saveAndFlush(user);
    }
    private Item foundItem(UserAccount owner) {
        Item item = new Item(); item.owner = owner; item.type = "FOUND"; item.title = "Scientific calculator";
        item.category = "Calculator"; item.location = "Main Library"; item.status = "AVAILABLE_FOR_CLAIM";
        item.privateDetails = crypto.encrypt("white scratch beside SHIFT key"); item.serial = crypto.encrypt("CF2026A17");
        return items.saveAndFlush(item);
    }
    private Upload evidence(UserAccount owner) throws Exception {
        Upload upload = new Upload(); upload.owner = owner; upload.purpose = "EVIDENCE"; upload.mimeType = "image/png";
        upload.path = Files.write(directory.resolve(upload.id + ".png"), new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}).toAbsolutePath().toString();
        upload.originalName = "purchase-proof.png"; return uploads.saveAndFlush(upload);
    }
    private String statement() { return "My Casio calculator has a white scratch beside the SHIFT key, with its original sliding case."; }
    private Principal principal(UserAccount user) { return () -> user.email; }
}
