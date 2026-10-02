# CampusFind AI

**Lost on Campus. Found with Intelligence.**

A working local Java application with Spring Boot, MySQL, Spring Security and a Thymeleaf frontend. Includes reporting, photos, search, Findy conversational search, matching, private ownership proof, finder review, independent administrator approval, notifications, audit trails and two-party handover.

## Run locally on Windows

Double-click **`open-campusfind.cmd`** to start the project and open **http://localhost:8080**. Double-click **`stop.cmd`** to stop the app. Alternatively, use `start.cmd` for a foreground terminal and Ctrl+C to stop it. Run one copy at a time.

Install Java 21 or later and MySQL Server 8 before starting. Clone or download this repository, extract it if needed, and run the launcher from the project folder. The setup scripts create a dedicated local database and download Maven on the first build. Internet access is required for the initial dependency download.

## Demonstration accounts

| Purpose | Email | Password | Portal |
|---|---|---|---|
| Claimant, Arun | `arun@campus.edu` | Your `CAMPUS_DEMO_STUDENT_PASSWORD` | `/login` |
| Finder, Priya | `priya@campus.edu` | Your `CAMPUS_DEMO_STUDENT_PASSWORD` | `/login` |
| Student, Mira | `mira@campus.edu` | Your `CAMPUS_DEMO_STUDENT_PASSWORD` | `/login` |
| Administrator | `admin@campus.edu` | Your `CAMPUS_DEMO_ADMIN_PASSWORD` | `/admin/login` |

Before the first launch, set `CAMPUS_DEMO_STUDENT_PASSWORD` and `CAMPUS_DEMO_ADMIN_PASSWORD` in your local environment to privately chosen passwords. Each must contain 12–72 characters with uppercase, lowercase, a number and a symbol. Start the application from that environment. No default account passwords are included in this repository. Existing databases retain their existing password hashes.

For the optional presentation-data scripts, also set `CAMPUS_PRESENTATION_PASSWORD`. The setup script uses it for `demo@example.com`, and the check script uses the same value. Keep these environment values private and never commit them.

Demo accounts and twelve sample reports are initialized once when the database is empty. Displayed counts come from the database. Category illustrations are labelled where a report has no uploaded photo. See [the demonstration guide](documentation/DEMO_GUIDE.md).

## Implemented features

- Registration with any valid email address, separate email and physical college ID verification, separate admin login and no public admin registration.
- BCrypt passwords, strength validation, five-attempt lockout, CSRF, rate limits, session expiry, remember-me and revocation after password changes.
- Lost/found reports, photo uploads, date/location/category/brand/color/status filters, edit/close rules and duplicate detection.
- Up to five JPG/PNG photos per report; actual-file validation, size/pixel limits, normalized PNG storage and private proof access.
- AES-256-GCM encryption of private characteristics, serials and claim answers; installation key outside source control.
- Weighted report matching and notifications when relevant reports appear.
- Findy searches actual unclaimed records, remembers conversational context and refuses private-information requests.
- Verified identity, one claim per user/item, three active claims maximum, evidence uploads and repeated-failure tracking.
- Finder review, independent admin review, more-evidence requests, rejection and account flagging.
- A 24-hour one-time handover code, persistent failed-code limits, finder confirmation then claimant receipt confirmation, returned status, audit events and reputation points.
- Profiles and photos, notifications, admin user/report/catalog/announcement management and live analytics.
- Responsive light/dark interface, local artwork and no frontend build or paid AI requirement.

## Build and test

Java source targets **Java 21**, running on this laptop's installed **Java 25**. MySQL 8 stores application records; H2 is used only for isolated integration tests.

```powershell
.\stop.cmd
.\mvnw.cmd clean verify
.\open-campusfind.cmd
```

Maven 3.9.11 and downloaded dependencies live under `.tools`. First builds need internet access; the built application and local Findy search do not. The executable is `target/CampusLostAndFound-1.0.jar`. Rebuild after changing source; `start.cmd -Rebuild` builds and runs in the foreground.

## Database and files

This laptop uses a dedicated MySQL process on **127.0.0.1:3307**, schema **campus_lost_found**, data directory `.local/mysql/data`. The original MySQL service on port 3306 is not reconfigured. Local credentials are in `config/local.properties` and excluded from submission archives. Setup/shutdown scripts verify the server's actual data directory.

`sql/schema.sql` is the schema exported from the running application. Hibernate can also create tables on a fresh installation. Demo initialization is in `src/main/java/com/campusfind/config/DemoData.java`.

Keep **`.local/encryption.key`** with database and upload backups: losing it makes encrypted details unreadable. Images live in `.local/uploads` and are served only through permission-checked endpoints.

To stop the project's database too:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-db.ps1
```

## Email verification and reset

This laptop uses **local demonstration outbox mode**. Verification and password-reset emails are saved in **`.local/mail`**. No email is sent externally. Open the newest matching text email on this laptop to follow its one-time link. This demonstrates the workflow; it does not prove control of a real inbox.

For actual email delivery, add private SMTP configuration to `config/local.properties`, then restart:

```properties
campus.mail-mode=smtp
campus.mail-from=campusfind@yourcollege.edu

campus.base-url=https://your-campus-host
spring.mail.host=your-smtp-host
spring.mail.port=587
spring.mail.username=your-account
spring.mail.password=your-secret
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

Administrators separately check new users' physical college IDs. Demo accounts are preverified. New accounts can sign in and browse while verification is pending.

## Matching and AI scope

Findy is an **explainable Java language-search assistant**, not a trained external LLM. It extracts supported item, brand, color, location and date entities, keeps search context in the session and returns actual database records. It never decides ownership.

Report score: category 20 + brand 15 + color 10 + location 15 + date 15 + name/model/description overlap 15. The remaining 10 image points are **not awarded** because advanced visual matching is optional and unavailable. Maximum current score: **90/100**. `ImageMatchingService` is an extension interface; no fabricated image percentage is displayed.

Administrators see a separate rules-based ownership estimate. Claimants see **evidence completeness**, based only on their own input, so the score cannot reveal hidden answers. A human must authenticate proof images and approve every claim.

## Structure

```text
src/main/java/com/campusfind/
  ai/             Intent/entity extraction, contextual search, image extension
  config/         Demonstration initialization
  controller/     REST APIs, Thymeleaf pages and error handling
  entity/         JPA persistence objects
  repository/     Database access and pessimistic locks
  security/       Login, sessions and rate limiting
  service/        Reports, claims, encryption, profiles, mail and audit support
  verification/   Ownership triage
src/main/resources/templates/  Thymeleaf document shell
src/main/resources/static/     CSS, JavaScript and local artwork
src/test/                      Integration/security/concurrency tests
sql/                           MySQL schema
documentation/                 Report, diagrams, demo, requirements, verification
scripts/                       Setup, build, start, stop and packaging
```

Accounts share a table with server-assigned roles; lost/found reports share a typed item table. This is an intentional equivalent to the prompt's illustrative separate entity names and avoids duplicate authentication and search logic.

## Submission and deployment notes

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\make-submission.ps1
```

Creates `submission/CampusFindAI-Submission.zip` with source, tests, SQL, documentation, scripts and executable JAR. Excludes real credentials, databases, uploads, keys, private outbox mail and cached dependencies. Extract before running.

For a real campus deployment, configure institutional SMTP or SSO, provision admin credentials privately, disable sample seeding on a fresh database (`campus.demo-data=false`), use HTTPS and `COOKIE_SECURE=true`, and establish backups and retention. Default binding is local-only, `127.0.0.1`. Advanced vision, QR/RFID/NFC, mobile/voice interfaces, multi-campus support and distributed rate limiting are future extensions.

References: [Spring Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Thymeleaf](https://www.thymeleaf.org/documentation.html), [MySQL 8](https://dev.mysql.com/doc/refman/8.0/en/).
