# Professor presentation — saved demonstration account

Open http://localhost:8080/login and sign in as **demo@example.com** using your privately configured `CAMPUS_PRESENTATION_PASSWORD`.

This is a fictional local account, not an external email inbox. It is already verified for this demonstration.

| Metric | Prepared value |
|---|---:|
| Active lost reports | 5 |
| Active found reports | 4 |
| Possible matches | 3 |
| Pending claims | 2 |
| Successful returns | 1 |
| Unread updates | 15 |

All ten reports owned by this account have locally stored illustrated pictures. Reports have varied categories, brands, values, dates, times, locations and private identification details. Pictures and ownership evidence are fictional demonstration material.

These are database records, not hardcoded dashboard numbers. Signing out, refreshing or restarting does not reset them. Counts change naturally when you create reports, resolve claims or read notifications. The homepage shows campus-wide totals; My Dashboard shows the current account's activity. A newly registered account starts with no reports.

## Five-minute demonstration

1. Sign in with the presentation account. Show the six different dashboard counts.
2. Open My Reports: show the iPad, backpack, phone, notebook, laptop, flask, wallet, keys, earbuds and returned calculator. Open a report to show its saved image and private fields available to its owner.
3. Open Findy and ask “I lost my phone”, then “Cafeteria”. Open the matching coral phone report. It has no claim yet, so it can demonstrate a fresh claim.
4. Open My Claims. The iPad is awaiting finder review. The backpack has already passed finder review and awaits administration. The calculator is a completed return.
5. Sign in as **priya@campus.edu** using your `CAMPUS_DEMO_STUDENT_PASSWORD` to demonstrate finder review of the iPad.
6. Sign in at `/admin/login` as **admin@campus.edu** using your `CAMPUS_DEMO_ADMIN_PASSWORD` to review the backpack claim. Approve it with a review note and a safe handover location.
7. Sign in with the presentation account to obtain its one-time handover code. Switch to Priya to enter the code at handover; return to the presentation account to confirm receipt.
8. Sign out and back in to show that changes remain saved.

The final handover demonstration changes the counts intentionally. The prepared calculator provides an already completed example if you prefer to keep the two pending claims available.

## Local setup and verification

- Start with `open-campusfind.cmd` from the project folder.
- Data is saved in the project's MySQL database on port 3307; pictures are in `.local/uploads`.
- `node scripts/prepare-presentation.mjs` prepares missing demo records through normal APIs while the app is running. It reuses existing records and does not reset them. Run only against local outbox mode.
- `node scripts/check-presentation.mjs` compares current records to `.local/presentation-result.json`. Use immediately after setup/restart; user activity can change expected counts.
- Submission includes setup scripts and image assets. Local database contents and private credentials remain on this laptop; run the setup script on a fresh configured copy to create its presentation records.
