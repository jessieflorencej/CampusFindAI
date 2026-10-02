# Verification record

## GitHub publication check, 2 October 2026

The publication copy reads demo account passwords from private environment variables. Integration tests generate isolated credentials. Maven `verify` completed successfully after these changes: 23 tests, zero failures, zero errors and zero skipped tests. Node syntax checks passed for the presentation setup/check scripts, smoke test and credential helper. This check did not repeat the live MySQL workflow described below.

Date: **10 September 2026**. Workspace: `D:\Workspace\CampusLostAndFound`.

## Automated Java verification

Command: `mvnw.cmd verify` — **BUILD SUCCESS** on the final Java and frontend build.

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| AuthenticationIntegrationTest | 8 | 0 | 0 |
| ItemAndAssistantIntegrationTest | 7 | 0 | 0 |
| ClaimServiceIntegrationTest | 8 | 0 | 0 |
| Total | **23** | **0** | **0** |

The test suites cover CSRF, registration across email domains, invalid email rejection and role injection, portal separation, persistent lockout, verification/reset token expiry and reuse, session revocation, encryption tampering, private item/evidence access, input/date/duplicate checks, conversational database search, admin boundaries, notification ownership, private claim statements, real stored proof, concurrent three-claim limits, independent reviews, more-evidence flow, expiring/replayed codes, durable incorrect attempts, competing claims and two-party returns.

JavaScript syntax checks passed for `app.js`, `workflows.js` and `account.js` using `node --check`.

## Live MySQL application

`node scripts/smoke-test.mjs` completed **14 passing checks** against the actual application on `127.0.0.1:8080`, backed by this project's MySQL at port 3307:

1. Student, finder and separate administrator logins.
2. Findy actual-record results and contextual follow-up.
3. Private-information refusal.
4. Lost and found report persistence.
5. Public ownership-field exclusion.
6. Valid PNG upload and private unattached evidence access.
7. Claim submission with stored proof and encrypted private answers.
8. Finder-to-administrator review transition.
9. Approval reservation and claimant-only code visibility.
10. Failed-code response, valid handover and both-party returned states.
11. Consumed code rejection.
12. Returned item exclusion from Findy results.
13. Administrator category/location/announcement creation and deletion.
14. Profile save, notification read state and dashboard updates.

One completed demonstration claim (#1) and its two explicitly named demo reports are retained in the local database for inspection. Its proof is a synthetic image fixture, not a claim of real ownership. Twelve original seed reports remain available; total reports after the live verification are fourteen. The raw test result is in `.local/smoke-result.json` on this laptop.

MySQL startup revealed a VARCHAR/utf8mb4 row-size issue that H2 did not detect. Encrypted long-form fields were changed to TEXT, schema errors now halt startup, the test suite passed again and the real MySQL application started successfully. The schema file was exported from this running installation.

## Browser verification

Verified in the local browser:

- Homepage, live statistics, local artwork and navigation render.
- Student login opens Arun's personal dashboard; sign-out returns to the public page.
- Findy answers **“I lost my calculator near the library”** with the actual unclaimed Casio report at **Main Library**.
- Item detail separates public metadata from hidden ownership details.
- “This might be mine” opens the complete proof/ownership form.
- The claim form at a **390 × 844** viewport has no horizontal overflow; mobile navigation opens and navigates correctly. The temporary viewport was reset afterward.
- Claim #1 displays the completed six-step return timeline and protected synthetic evidence.
- The separate administrator portal opens live management analytics; user management renders verification and account controls.
- An administrator can load and save the original found report with its private fields preserved.
- No browser console errors were recorded during the checked flows.
- `start-background.ps1 -NoBrowser` correctly detects or starts the application, reports readiness and leaves it running. `stop-app.ps1` stops only the exact project JAR process.

See [SCREENSHOTS.md](SCREENSHOTS.md) for captured pages.

## Scope of verification

The local delivery uses Java 25 with Java 21 bytecode, MySQL 8 and in-process Spring sessions. Real SMTP delivery, institutional SSO, a trained vision model, public HTTPS hosting and real-world identity/evidence authenticity were not exercised. Email defaults to the private local demonstration outbox. These operational integrations are described in README rather than represented as completed external services.
