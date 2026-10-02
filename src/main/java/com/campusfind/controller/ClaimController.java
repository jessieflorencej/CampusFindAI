package com.campusfind.controller;

import com.campusfind.service.ClaimService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimService claims;

    public ClaimController(ClaimService claims) { this.claims = claims; }

    @GetMapping
    public Map<String, Object> list(Principal principal) { return claims.list(principal); }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id, Principal principal) { return claims.detail(id, principal); }

    @PostMapping
    public Map<String, Object> submit(@Valid @RequestBody Submission input, Principal principal) { return claims.submit(input, principal); }

    @PostMapping("/{id}/finder")
    public Map<String, Object> finder(@PathVariable Long id, @Valid @RequestBody FinderDecision input, Principal principal) { return claims.finder(id, input, principal); }

    @PostMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable Long id, @Valid @RequestBody ReviewDecision input, Principal principal) { return claims.review(id, input, principal); }

    @PostMapping("/{id}/evidence")
    public Map<String, Object> evidence(@PathVariable Long id, @Valid @RequestBody AdditionalEvidence input, Principal principal) { return claims.evidence(id, input, principal); }

    @PostMapping("/{id}/handover")
    public Map<String, Object> handover(@PathVariable Long id, @Valid @RequestBody Handover input, Principal principal) { return claims.handover(id, input.code(), principal); }

    @PostMapping("/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable Long id, Principal principal) { return claims.confirm(id, principal); }

    @PostMapping("/{id}/renew-code")
    public Map<String, Object> renewCode(@PathVariable Long id, Principal principal) { return claims.renewCode(id, principal); }

    public record Submission(@NotNull @Positive Long itemId, @Positive Long lostItemId,
                             @NotBlank @Size(min = 20, max = 4000) String answers,
                             @Size(max = 200) String serial, @NotBlank @Size(min = 3, max = 300) String lossLocation,
                             @NotEmpty @Size(max = 5) List<@NotBlank String> evidenceIds) { }
    public record FinderDecision(@NotBlank @Pattern(regexp = "YES|NO|UNSURE") String response,
                                 @Size(max = 2000) String note) { }
    public record ReviewDecision(@NotBlank @Pattern(regexp = "APPROVE|REJECT|MORE_INFORMATION|FLAG") String decision,
                                 @NotBlank @Size(min = 5, max = 2000) String note, @Size(max = 200) String location) { }
    public record AdditionalEvidence(@NotBlank @Size(min = 20, max = 4000) String answers,
                                     @NotEmpty @Size(max = 5) List<@NotBlank String> evidenceIds) { }
    public record Handover(@NotBlank @Size(max = 30) String code) { }
}
