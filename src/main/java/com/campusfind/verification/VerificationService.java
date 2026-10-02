package com.campusfind.verification;

import com.campusfind.entity.Item;
import com.campusfind.entity.UserAccount;
import com.campusfind.service.CryptoService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Explainable triage for a human reviewer; neither a probability nor image verification. */
@Service
public class VerificationService {
    private final CryptoService crypto;

    public VerificationService(CryptoService crypto) { this.crypto = crypto; }

    public Assessment assess(UserAccount user, Item found, String answers, String serial,
                             String lossLocation, int evidenceCount, long rejectedClaims) {
        Map<String, Integer> checks = new LinkedHashMap<>();
        checks.put("Verified campus identity", user.verified && user.collegeId != null && !user.collegeId.isBlank() ? 15 : 0);
        boolean established = user.createdAt != null && Duration.between(user.createdAt, Instant.now()).toDays() >= 7;
        checks.put("Account history", !user.flagged && rejectedClaims < 3 ? (established ? 5 : 2) : 0);
        checks.put("Detailed ownership statement", Math.min(15, text(answers).length() / 12));
        checks.put("Location consistency", similarity(lossLocation, found.location + " " + text(found.building)) >= .3 ? 15 : 0);
        String privateDetails = crypto.decrypt(found.privateDetails);
        double sharedDetails = similarity(answers, privateDetails);
        checks.put("Private description overlap", privateDetails == null || privateDetails.isBlank() ? 0 : (int) Math.round(25 * sharedDetails));
        // Presence is the only automated evidence check. Receipt authenticity requires a person.
        checks.put("Evidence attached (not authenticated)", evidenceCount > 0 ? 10 : 0);
        String expected = normalizeSerial(crypto.decrypt(found.serial));
        String supplied = normalizeSerial(serial);
        int serialScore = 0;
        if (!expected.isBlank() && supplied.length() >= 6) {
            if (expected.equals(supplied)) serialScore = 15;
            else if (expected.length() > supplied.length() && expected.endsWith(supplied)) serialScore = 8;
        }
        checks.put("Serial consistency", serialScore);
        return new Assessment(checks.values().stream().mapToInt(Integer::intValue).sum(), checks,
                "A rules-based triage estimate, not proof of ownership. Attached images are not authenticated. A finder and an independent administrator must review every claim.");
    }

    /** This score depends only on the claimant's input, so it cannot reveal hidden answers. */
    public int completeness(UserAccount user, String answers, String serial, String location, int evidenceCount) {
        return (user.verified ? 20 : 0) + Math.min(30, text(answers).length() / 5)
                + (text(location).length() >= 3 ? 20 : 0) + (evidenceCount > 0 ? 25 : 0)
                + (!text(serial).isBlank() ? 5 : 0);
    }

    static double similarity(String submitted, String expected) {
        Set<String> actual = tokens(submitted), target = tokens(expected);
        if (actual.isEmpty() || target.isEmpty()) return 0;
        return (double) target.stream().filter(actual::contains).count() / target.size();
    }

    private static Set<String> tokens(String value) {
        Set<String> stop = Set.of("the", "and", "with", "this", "that", "was", "have", "has", "near", "item", "its", "for", "from");
        return Arrays.stream(text(value).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 3 && !stop.contains(t)).collect(Collectors.toSet());
    }

    private static String normalizeSerial(String value) { return text(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""); }
    private static String text(String value) { return value == null ? "" : value.strip(); }

    public record Assessment(int score, Map<String, Integer> checks, String explanation) { }
}
