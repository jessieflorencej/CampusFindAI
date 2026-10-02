# CampusFind AI — demonstration guide

## The Arun–Priya journey

1. Open `open-campusfind.cmd`, then sign in at `/login` as **arun@campus.edu** using your privately configured `CAMPUS_DEMO_STUDENT_PASSWORD`.
2. Open **Findy AI**, ask **“I lost my calculator”**, then **“Library”**. The assistant searches actual unclaimed records and remembers the category.
3. Open **Casio scientific calculator**, found near Main Library. Its public page contains no serial, contact information or hidden markings.
4. Click **This might be mine**. Link Arun's **Casio FX-991EX calculator** lost report. Enter: **“My calculator is a black Casio FX-991EX. There is a small white scratch beside the SHIFT key. I last used it in the Main Library reading hall during an afternoon study session.”** Demo serial: **CF2026A17**. Loss location: **Main Library reading hall**.
5. Upload a real JPG/PNG as demonstration proof, such as an old item photo. Explain that attaching a photo does not automatically authenticate it. Submit the claim and show its timeline.
6. Sign out, then sign in as **priya@campus.edu** using your `CAMPUS_DEMO_STUDENT_PASSWORD`. Open **My Claims → Claims on items you found**. Review the private statement and proof. Select **YES**, add a note and send to administration.
7. Sign out. Use **`/admin/login`** with **admin@campus.edu** and your `CAMPUS_DEMO_ADMIN_PASSWORD`. Open **Review claims**. Inspect identity, account history, finder response, private details and evidence. Choose **APPROVE**, write a reason and select **Security Office**.
8. Sign in as Arun. His private claim page shows a **CF-xxxxxx** handover code, valid for 24 hours. The finder and administrator cannot see it.
9. Simulate meeting at Security Office. Sign in as Priya and enter Arun's code only when handing over the item. The code is consumed and the claim enters **Item handover**.
10. Sign in as Arun and click **I have received my item**. Both parties have confirmed. The claim closes, the linked reports become **Returned**, notifications and reputation points are recorded, and Findy excludes the returned item.

Records persist across restarts. For repeated demonstrations, create fresh reports with distinct names rather than deleting a real database.

## Other demonstrations

- Register using any valid email address, including Gmail or Outlook. In local demo mode, follow the email link in `.local/mail`, then have the admin verify the physical college ID. Email and ID checks are independent.
- Report a found item with a public photo and separate private marks. New matches generate notifications.
- Search with category, campus location, color, brand, date and status filters.
- Request further evidence as administrator; the claimant updates proof and finder review restarts.
- Add a category/location, publish an announcement, flag/deactivate an account and inspect audit events.
- Update profile/photo, change password, mark notifications read and use mobile navigation/light-dark theme.

## Viva explanations

**AI:** explainable local Java language search and weighted recommendations; no external LLM or fabricated image model.

**Two scores:** item similarity suggests pairs. Admin ownership triage assists human review. Claimants see input completeness to avoid an oracle for hidden answers.

**Encryption:** AES-GCM protects details that authorized reviewers need to read. BCrypt hashes passwords. HMAC protects short OTPs and reset tokens. The key stays outside source control.

**Concurrency:** per-user locks enforce the three-active-claim limit; item locks serialize approvals and handovers. Integration tests exercise parallel submissions and failed-code persistence.

**OOP:** injected services, generic JPA interfaces, ImageMatchingService abstraction and implementation, PasswordEncoder polymorphism, typed request/assessment records, exceptions, streams/collections and transactional file/database workflows.

**Future work:** advanced visual matching, university SSO, mobile app, QR/NFC/RFID, voice/multilingual search, multi-campus isolation, distributed rate limiting, production retention and backups.
