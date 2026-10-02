package com.campusfind.service;

import com.campusfind.controller.ClaimController.*;
import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.verification.VerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ClaimService {
    public static final List<String> MEETING_POINTS = List.of("Security Office", "Administration Office",
            "Library Reception", "Department Office", "Campus Lost & Found Desk");
    private static final Set<String> TERMINAL = Set.of("REJECTED", "CLOSED", "RETURN_CONFIRMED");
    private static final Set<String> AVAILABLE_ITEMS = Set.of("AVAILABLE_FOR_CLAIM", "FOUND_ITEM_REPORTED", "CLAIM_PENDING");
    private static final Set<String> RETIRED_ITEMS = Set.of("RETURNED", "CLOSED", "REMOVED");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ClaimRepository claims;
    private final ItemRepository items;
    private final UserRepository users;
    private final UploadRepository uploads;
    private final Support support;
    private final CryptoService crypto;
    private final VerificationService verification;
    private final ItemService itemService;

    public ClaimService(ClaimRepository claims, ItemRepository items, UserRepository users,
                        UploadRepository uploads, Support support, CryptoService crypto,
                        VerificationService verification, ItemService itemService) {
        this.claims = claims; this.items = items; this.users = users; this.uploads = uploads;
        this.support = support; this.crypto = crypto; this.verification = verification; this.itemService = itemService;
    }

    public Map<String, Object> list(Principal principal) {
        UserAccount user = support.user(principal);
        List<Claim> all = claims.findAll();
        Comparator<Claim> newest = Comparator.comparing((Claim c) -> c.createdAt, Comparator.nullsLast(Comparator.reverseOrder()));
        return Map.of("mine", all.stream().filter(c -> same(c.claimant, user)).sorted(newest).map(c -> view(c, user, false)).toList(),
                "review", all.stream().filter(c -> isAdmin(user) || same(c.item.owner, user)).sorted(newest).map(c -> view(c, user, false)).toList(),
                "meetingPoints", MEETING_POINTS);
    }

    public Map<String, Object> detail(Long id, Principal principal) {
        UserAccount user = support.user(principal);
        Claim claim = requireClaim(id);
        authorizeView(claim, user);
        return view(claim, user, true);
    }

    @Transactional
    public Map<String, Object> submit(Submission input, Principal principal) {
        UserAccount sessionUser = support.user(principal);
        // Serialize the per-account limit before locking the item to prevent concurrent submissions.
        UserAccount user = users.findLockedById(sessionUser.id).orElseThrow(() -> missing("Account not found"));
        eligible(user);
        List<Claim> previous = claims.findByClaimantId(user.id);
        require(previous.stream().filter(c -> !TERMINAL.contains(c.status)).count() < 3,
                "You can have at most three active ownership claims.");
        require(previous.stream().noneMatch(c -> c.item.id.equals(input.itemId())), "You have already submitted a claim for this item.");
        require(rejections(previous) < 5, "Repeated unsuccessful claims require an administrator to review your account.");
        Item found = items.findLockedById(input.itemId()).orElseThrow(() -> missing("Found item not found"));
        require("FOUND".equals(found.type), "Ownership claims must reference a found item.");
        require(!same(found.owner, user), "You cannot claim an item you reported finding.");
        available(found);
        Item lost = null;
        if (input.lostItemId() != null) {
            lost = items.findById(input.lostItemId()).orElseThrow(() -> missing("Lost report not found"));
            require(same(lost.owner, user) && "LOST".equals(lost.type), "Link one of your own lost reports.");
            require(!RETIRED_ITEMS.contains(lost.status), "This lost report is already closed or unavailable.");
        }
        validateStatement(input.answers(), input.lossLocation());
        List<Upload> proof = validateEvidence(input.evidenceIds(), user, null);
        Claim claim = new Claim();
        claim.claimant = user; claim.item = found; claim.lostItem = lost;
        claim.answers = crypto.encrypt(input.answers().strip()); claim.serial = crypto.encrypt(clean(input.serial()));
        claim.lossLocation = input.lossLocation().strip(); claim.status = "FINDER_REVIEW";
        claim.createdAt = Instant.now(); claim.updatedAt = claim.createdAt;
        claim.score = verification.assess(user, found, input.answers(), input.serial(), input.lossLocation(), proof.size(), rejections(previous)).score();
        claims.saveAndFlush(claim);
        attach(proof, claim);
        found.status = "CLAIM_PENDING"; found.updatedAt = Instant.now();
        if (lost != null) { lost.status = "CLAIM_IN_PROGRESS"; lost.updatedAt = Instant.now(); }
        support.audit(user, "CLAIM_SUBMITTED", "claim:" + claim.id);
        support.notify(found.owner, "Ownership claim to review", "A campus member submitted evidence for " + found.title + ". Review claim #" + claim.id + ".");
        support.notify(user, "Claim submitted", "Claim #" + claim.id + " is awaiting the finder's review. Your proof remains private.");
        return view(claim, user, true);
    }

    @Transactional
    public Map<String, Object> finder(Long id, FinderDecision input, Principal principal) {
        UserAccount user = support.user(principal);
        Claim claim = locked(id);
        permit(same(claim.item.owner, user) && !same(claim.claimant, user), "Only this item's finder can review the evidence.");
        require("FINDER_REVIEW".equals(claim.status), "This claim is not awaiting finder review.");
        require(Set.of("YES", "NO", "UNSURE").contains(input.response()), "Choose yes, no, or unsure.");
        claim.finderResponse = input.response();
        claim.reviewNote = clean(input.note()).isBlank() ? "Finder response: " + input.response() : "Finder: " + input.note().strip();
        claim.status = "ADMIN_REVIEW"; claim.updatedAt = Instant.now();
        support.audit(user, "FINDER_REVIEW_" + input.response(), "claim:" + id);
        support.notify(claim.claimant, "Finder review complete", "Claim #" + id + " is now awaiting an independent administrator's review.");
        return view(claim, user, true);
    }

    @Transactional
    public Map<String, Object> review(Long id, ReviewDecision input, Principal principal) {
        UserAccount user = support.user(principal);
        support.admin(user);
        Claim claim = locked(id);
        permit(!same(claim.claimant, user) && !same(claim.item.owner, user), "A claim must be reviewed by an independent administrator.");
        require("ADMIN_REVIEW".equals(claim.status), "This claim is not ready for administrator review.");
        require(claim.finderResponse != null, "The finder must review the claim first.");
        require(clean(input.note()).length() >= 5 && clean(input.note()).length() <= 2000, "Add a review note of 5 to 2,000 characters.");
        switch (input.decision()) {
            case "APPROVE" -> {
                eligible(claim.claimant);
                require(!RETIRED_ITEMS.contains(claim.item.status), "This item is no longer available.");
                boolean reserved = claims.findByItemId(claim.item.id).stream()
                        .anyMatch(c -> !c.id.equals(claim.id) && Set.of("APPROVED", "ITEM_HANDOVER", "CLOSED", "RETURN_CONFIRMED").contains(c.status));
                require(!reserved, "Another claim already has an approved handover for this item.");
                List<Upload> proof = uploads.findByClaimId(claim.id);
                require(proof.stream().anyMatch(this::storedEvidence), "Stored ownership evidence is required before approval.");
                String location = clean(input.location()).isBlank() ? MEETING_POINTS.getFirst() : input.location().strip();
                require(MEETING_POINTS.contains(location), "Choose a staffed campus handover point.");
                claim.handoverLocation = location; claim.status = "APPROVED";
                issueCode(claim);
                claim.item.status = "CLAIM_VERIFIED";
                support.notify(claim.claimant, "Claim approved", "Claim #" + id + " is approved. Open your private claim page for a 24-hour handover code. Meet at " + location + ".");
                support.notify(claim.item.owner, "Handover approved", "Claim #" + id + " is ready for handover at " + location + ". Enter the claimant's code only when you return the item.");
            }
            case "REJECT", "FLAG" -> {
                claim.status = "REJECTED"; clearCode(claim);
                if ("FLAG".equals(input.decision()) || rejections(claims.findByClaimantId(claim.claimant.id)) >= 3) claim.claimant.flagged = true;
                restoreItemStatus(claim);
                support.notify(claim.claimant, "Claim not approved", "Claim #" + id + " was rejected. The administrator's reason is on your claim page.");
            }
            case "MORE_INFORMATION" -> {
                claim.status = "MORE_INFORMATION_REQUIRED"; clearCode(claim);
                refreshItemStatus(claim);
                support.notify(claim.claimant, "More evidence required", "Please add evidence to claim #" + id + ". Review the administrator's note on your claim page.");
            }
            default -> throw bad("Choose approve, reject, more information, or flag.");
        }
        claim.reviewNote = input.note().strip(); claim.updatedAt = Instant.now(); claim.item.updatedAt = Instant.now();
        support.audit(user, "CLAIM_" + input.decision(), "claim:" + id);
        return view(claim, user, true);
    }

    @Transactional
    public Map<String, Object> evidence(Long id, AdditionalEvidence input, Principal principal) {
        UserAccount user = support.user(principal);
        Claim claim = locked(id);
        permit(same(claim.claimant, user), "Only the claimant can add ownership evidence.");
        eligible(user);
        require("MORE_INFORMATION_REQUIRED".equals(claim.status), "Additional evidence has not been requested for this claim.");
        available(claim.item);
        validateStatement(input.answers(), claim.lossLocation);
        List<Upload> proof = validateEvidence(input.evidenceIds(), user, claim.id);
        long total = uploads.findByClaimId(claim.id).stream().map(u -> u.id).distinct().count()
                + proof.stream().filter(u -> u.claimId == null).count();
        require(total <= 10, "A claim can contain at most ten evidence images.");
        claim.answers = crypto.encrypt(input.answers().strip());
        attach(proof, claim);
        claim.score = verification.assess(user, claim.item, input.answers(), crypto.decrypt(claim.serial), claim.lossLocation,
                (int) total, rejections(claims.findByClaimantId(user.id))).score();
        claim.finderResponse = null; claim.status = "FINDER_REVIEW"; claim.updatedAt = Instant.now();
        support.audit(user, "CLAIM_EVIDENCE_ADDED", "claim:" + id);
        support.notify(claim.item.owner, "New ownership evidence", "The claimant added information to claim #" + id + ". Review the updated evidence.");
        return view(claim, user, true);
    }

    // Incorrect attempts must commit even though the HTTP response is an error.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> handover(Long id, String suppliedCode, Principal principal) {
        UserAccount user = support.user(principal);
        Claim claim = locked(id);
        permit(same(claim.item.owner, user) && !same(claim.claimant, user), "Only the finder can enter the claimant's handover code.");
        require("APPROVED".equals(claim.status), "This claim is not awaiting handover.");
        require(claim.codeExpiresAt != null && claim.codeExpiresAt.isAfter(Instant.now()), "This handover code expired. Ask the claimant to renew it from their claim page.");
        require(claim.codeAttempts < 5, "The handover is locked and needs administrator review.");
        if (!crypto.matches(clean(suppliedCode).toUpperCase(Locale.ROOT), claim.handoverHash)) {
            claim.codeAttempts++; claim.updatedAt = Instant.now();
            support.audit(user, "HANDOVER_CODE_FAILED", "claim:" + id + ":attempt:" + claim.codeAttempts);
            if (claim.codeAttempts >= 5) {
                claim.status = "ADMIN_REVIEW"; clearCode(claim);
                claim.reviewNote = "Handover locked after five incorrect code attempts. An independent administrator must review and approve a new code.";
                support.notify(claim.claimant, "Handover temporarily locked", "Claim #" + id + " needs administrator review after repeated incorrect handover codes.");
                claims.saveAndFlush(claim);
                throw bad("Five incorrect attempts. The handover is locked for administrator review.");
            }
            claims.saveAndFlush(claim);
            throw bad("Incorrect handover code. " + (5 - claim.codeAttempts) + " attempts remain.");
        }
        claim.finderConfirmed = true; claim.status = "ITEM_HANDOVER"; claim.updatedAt = Instant.now();
        clearCode(claim);
        support.audit(user, "FINDER_HANDOVER_CONFIRMED", "claim:" + id + ":location:" + claim.handoverLocation);
        support.notify(claim.claimant, "Confirm your receipt", "The finder confirmed handover for claim #" + id + ". Confirm receipt only after you have your item.");
        return view(claim, user, true);
    }

    @Transactional
    public Map<String, Object> renewCode(Long id, Principal principal) {
        UserAccount user = support.user(principal);
        Claim claim = locked(id);
        permit(same(claim.claimant, user), "Only the claimant can renew their code.");
        require("APPROVED".equals(claim.status) && !claim.finderConfirmed && claim.codeAttempts < 5, "This handover code cannot be renewed.");
        require(claim.codeExpiresAt != null && !claim.codeExpiresAt.isAfter(Instant.now()), "Your current code is still valid.");
        // Preserve failed attempts when renewing; expiry cannot reset the guessing limit.
        int attempts = claim.codeAttempts;
        issueCode(claim); claim.codeAttempts = attempts; claim.updatedAt = Instant.now();
        support.audit(user, "HANDOVER_CODE_RENEWED", "claim:" + id);
        return view(claim, user, true);
    }

    @Transactional
    public Map<String, Object> confirm(Long id, Principal principal) {
        UserAccount user = support.user(principal);
        Claim claim = locked(id);
        permit(same(claim.claimant, user), "Only the claimant can confirm receipt.");
        require("ITEM_HANDOVER".equals(claim.status) && claim.finderConfirmed && !claim.claimantConfirmed,
                "The finder must verify your code before you confirm receipt.");
        claim.claimantConfirmed = true; claim.status = "CLOSED"; claim.returnedAt = Instant.now(); claim.updatedAt = claim.returnedAt;
        claim.item.status = "RETURNED"; claim.item.updatedAt = claim.returnedAt;
        if (claim.lostItem != null) { claim.lostItem.status = "RETURNED"; claim.lostItem.updatedAt = claim.returnedAt; }
        claim.item.owner.reputation += 10;
        claim.claimant.reputation += 5;
        for (Claim competing : claims.findByItemId(claim.item.id)) {
            if (!competing.id.equals(claim.id) && !TERMINAL.contains(competing.status)) {
                competing.status = "REJECTED"; competing.reviewNote = "The item was returned to its verified owner.";
                competing.updatedAt = Instant.now(); clearCode(competing);
                restoreLostReport(competing);
                support.notify(competing.claimant, "Item no longer available", "Claim #" + competing.id + " was closed because the item was returned to its verified owner.");
            }
        }
        support.audit(user, "ITEM_RETURNED", "claim:" + id + ":item:" + claim.item.id + ":finder:" + claim.item.owner.id + ":claimant:" + user.id + ":location:" + claim.handoverLocation);
        support.notify(user, "Recovery complete", "Your receipt for " + claim.item.title + " is confirmed. Claim #" + id + " is complete.");
        support.notify(claim.item.owner, "Thank you for helping", "The owner confirmed receipt of " + claim.item.title + ". You earned 10 community points.");
        return view(claim, user, true);
    }

    private Map<String, Object> view(Claim claim, UserAccount user, boolean detail) {
        boolean own = same(claim.claimant, user), finder = same(claim.item.owner, user), admin = isAdmin(user);
        List<Upload> proof = uploads.findByClaimId(claim.id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", claim.id); result.put("item", itemService.publicView(claim.item));
        result.put("itemId", claim.item.id); result.put("itemTitle", claim.item.title);
        result.put("claimantName", claim.claimant.name); result.put("status", claim.status);
        result.put("createdAt", claim.createdAt); result.put("updatedAt", claim.updatedAt); result.put("returnedAt", claim.returnedAt);
        result.put("ownClaim", own); result.put("finderConfirmed", claim.finderConfirmed); result.put("claimantConfirmed", claim.claimantConfirmed);
        String answers = crypto.decrypt(claim.answers), serial = crypto.decrypt(claim.serial);
        // A claimant's numeric score never depends on the finder's hidden details.
        result.put("score", own ? verification.completeness(claim.claimant, answers, serial, claim.lossLocation, proof.size()) : claim.score);
        result.put("scoreLabel", own ? "Evidence completeness" : "Automated review estimate");
        result.put("nextAction", nextAction(claim));
        result.put("canFinderReview", finder && !own && "FINDER_REVIEW".equals(claim.status));
        result.put("canReview", admin && !own && !finder && "ADMIN_REVIEW".equals(claim.status));
        result.put("canHandover", finder && !own && "APPROVED".equals(claim.status));
        result.put("canConfirm", own && "ITEM_HANDOVER".equals(claim.status) && claim.finderConfirmed);
        result.put("canAddEvidence", own && "MORE_INFORMATION_REQUIRED".equals(claim.status));
        result.put("canRenewCode", own && "APPROVED".equals(claim.status) && claim.codeExpiresAt != null && !claim.codeExpiresAt.isAfter(Instant.now()));
        result.put("steps", steps(claim));
        if (detail) {
            result.put("answers", clean(answers)); result.put("serial", clean(serial)); result.put("lossLocation", claim.lossLocation);
            result.put("finderResponse", claim.finderResponse); result.put("reviewNote", claim.reviewNote);
            result.put("handoverLocation", claim.handoverLocation); result.put("codeExpiresAt", claim.codeExpiresAt);
            result.put("lostItemId", claim.lostItem == null ? null : claim.lostItem.id);
            result.put("meetingPoints", MEETING_POINTS);
            result.put("questions", List.of("What is the exact model?", "Describe unique marks, scratches, stickers, or engravings.",
                    "Describe the case or contents only the owner would know.", "When and where did you purchase it? Do not enter any password."));
            result.put("evidence", proof.stream().map(u -> Map.of("id", u.id, "name", clean(u.originalName), "url", "/api/uploads/" + u.id)).toList());
            result.put("evidenceNotice", "Images are stored privately. An attached photo or receipt has not been automatically authenticated.");
            if (own && "APPROVED".equals(claim.status) && claim.codeExpiresAt != null && claim.codeExpiresAt.isAfter(Instant.now())) {
                result.put("handoverCode", crypto.decrypt(claim.handoverCode));
            }
            if ((finder || admin) && !own) {
                result.put("foundPrivateDetails", clean(crypto.decrypt(claim.item.privateDetails)));
                result.put("foundSerial", clean(crypto.decrypt(claim.item.serial)));
                var assessment = verification.assess(claim.claimant, claim.item, answers, serial, claim.lossLocation,
                        proof.size(), rejections(claims.findByClaimantId(claim.claimant.id)));
                result.put("assessment", assessment);
            }
            if (admin) {
                Map<String, Object> identity = new LinkedHashMap<>();
                identity.put("name", claim.claimant.name); identity.put("collegeId", claim.claimant.collegeId);
                identity.put("verified", claim.claimant.verified); identity.put("active", claim.claimant.active);
                identity.put("flagged", claim.claimant.flagged); identity.put("memberSince", claim.claimant.createdAt);
                identity.put("rejectedClaims", rejections(claims.findByClaimantId(claim.claimant.id)));
                identity.put("totalClaims", claims.findByClaimantId(claim.claimant.id).size()); result.put("claimant", identity);
            }
        }
        return result;
    }

    private List<Map<String, Object>> steps(Claim claim) {
        List<String> labels = List.of("Claim submitted", "Automated checks", "Finder review", "Administrator review", "Safe handover", "Receipt confirmed");
        int stage = switch (claim.status) {
            case "FINDER_REVIEW" -> 2;
            case "ADMIN_REVIEW", "MORE_INFORMATION_REQUIRED", "REJECTED" -> 3;
            case "APPROVED" -> 4;
            case "ITEM_HANDOVER" -> 5;
            case "CLOSED", "RETURN_CONFIRMED" -> 6;
            default -> 1;
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) result.add(Map.of("label", labels.get(i), "complete", i < stage, "current", i == stage));
        return result;
    }

    private String nextAction(Claim claim) {
        return switch (claim.status) {
            case "FINDER_REVIEW" -> "The finder needs to review the ownership evidence.";
            case "ADMIN_REVIEW" -> "An independent administrator needs to review this claim.";
            case "MORE_INFORMATION_REQUIRED" -> "Read the administrator's note and submit additional evidence.";
            case "APPROVED" -> "Meet at " + claim.handoverLocation + ". The finder enters the claimant's private code at handover.";
            case "ITEM_HANDOVER" -> "The claimant needs to confirm they received the item.";
            case "REJECTED" -> "This claim was rejected. Review the administrator's explanation.";
            case "CLOSED", "RETURN_CONFIRMED" -> "Both parties confirmed the return. Recovery complete.";
            default -> "Your claim has been submitted.";
        };
    }

    private List<Upload> validateEvidence(List<String> ids, UserAccount user, Long claimId) {
        require(ids != null && !ids.isEmpty() && ids.size() <= 5, "Upload between one and five ownership evidence images.");
        require(new HashSet<>(ids).size() == ids.size(), "Each evidence image can be submitted only once.");
        List<Upload> result = new ArrayList<>();
        for (String id : ids) {
            Upload upload = uploads.findById(id).orElseThrow(() -> bad("An evidence upload could not be found."));
            permit(same(upload.owner, user), "You can attach only your own evidence uploads.");
            require(upload.itemId == null && (upload.claimId == null || upload.claimId.equals(claimId)), "This upload is already attached to another report or claim.");
            require(storedEvidence(upload), "Upload a valid proof image before submitting your claim.");
            result.add(upload);
        }
        return result;
    }

    private boolean storedEvidence(Upload upload) {
        if (!"EVIDENCE".equals(upload.purpose) || !Set.of("image/png", "image/jpeg").contains(clean(upload.mimeType)) || clean(upload.path).isBlank()) return false;
        try { return Files.isRegularFile(Path.of(upload.path)) && Files.size(Path.of(upload.path)) > 0; }
        catch (Exception exception) { return false; }
    }

    private void attach(List<Upload> proof, Claim claim) { proof.forEach(u -> u.claimId = claim.id); uploads.saveAll(proof); }
    private void issueCode(Claim claim) {
        String code = "CF-" + String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        claim.handoverHash = crypto.hash(code); claim.handoverCode = crypto.encrypt(code);
        claim.codeExpiresAt = Instant.now().plus(24, ChronoUnit.HOURS); claim.codeAttempts = 0;
        claim.finderConfirmed = false; claim.claimantConfirmed = false;
    }
    private void clearCode(Claim claim) { claim.handoverHash = null; claim.handoverCode = null; claim.codeExpiresAt = null; }

    private void restoreItemStatus(Claim claim) {
        refreshItemStatus(claim);
        restoreLostReport(claim);
    }
    private void refreshItemStatus(Claim claim) {
        if (!RETIRED_ITEMS.contains(claim.item.status)) {
            List<Claim> remaining = claims.findByItemId(claim.item.id).stream()
                    .filter(c -> !TERMINAL.contains(c.status)).toList();
            claim.item.status = remaining.stream().anyMatch(c -> Set.of("APPROVED", "ITEM_HANDOVER").contains(c.status)) ? "CLAIM_VERIFIED"
                    : remaining.isEmpty() ? "AVAILABLE_FOR_CLAIM" : "CLAIM_PENDING";
        }
    }
    private void restoreLostReport(Claim claim) {
        if (claim.lostItem == null || RETIRED_ITEMS.contains(claim.lostItem.status)) return;
        boolean anotherActive = claims.findByClaimantId(claim.claimant.id).stream()
                .anyMatch(c -> !c.id.equals(claim.id) && c.lostItem != null && c.lostItem.id.equals(claim.lostItem.id) && !TERMINAL.contains(c.status));
        if (!anotherActive) { claim.lostItem.status = "LOST"; claim.lostItem.updatedAt = Instant.now(); }
    }
    private Claim locked(Long id) {
        Long itemId = claims.findItemIdById(id).orElseThrow(() -> missing("Claim not found"));
        items.findLockedById(itemId).orElseThrow(() -> missing("Item not found"));
        return claims.findLockedById(id).orElseThrow(() -> missing("Claim not found"));
    }
    private Claim requireClaim(Long id) { return claims.findById(id).orElseThrow(() -> missing("Claim not found")); }
    private void authorizeView(Claim claim, UserAccount user) { permit(same(claim.claimant, user) || same(claim.item.owner, user) || isAdmin(user), "This ownership claim is private."); }
    private void eligible(UserAccount user) {
        permit(user.active && user.verified && user.collegeId != null && !user.collegeId.isBlank(), "Verify your college account before claiming an item.");
        permit(!user.flagged, "An administrator must review your flagged account before it can submit claims.");
    }
    private void available(Item item) { require(AVAILABLE_ITEMS.contains(item.status), "This item is no longer available for new claims."); }
    private void validateStatement(String answers, String location) {
        require(clean(answers).length() >= 20 && clean(answers).length() <= 4000, "Describe your ownership evidence in 20 to 4,000 characters.");
        require(clean(location).length() >= 3 && clean(location).length() <= 300, "Provide the location where you lost the item.");
    }
    private static long rejections(List<Claim> history) { return history.stream().filter(c -> "REJECTED".equals(c.status) && !"The item was returned to its verified owner.".equals(c.reviewNote)).count(); }
    private static boolean same(UserAccount left, UserAccount right) { return left != null && right != null && Objects.equals(left.id, right.id); }
    private static boolean isAdmin(UserAccount user) { return "ADMIN".equals(user.role) || "ROLE_ADMIN".equals(user.role); }
    private static String clean(String text) { return text == null ? "" : text.strip(); }
    private static void require(boolean condition, String message) { if (!condition) throw bad(message); }
    private static void permit(boolean condition, String message) { if (!condition) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
