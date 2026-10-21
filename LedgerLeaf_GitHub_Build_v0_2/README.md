# LedgerLeaf — Native Android Bookkeeping Prototype (0.1)

This is an **actual Kotlin/Jetpack Compose Android source project**, not a hosted web app. It is **not production-ready financial software**.

## Present features
- Local SQLite database, usable without internet.
- Add/edit/delete income and expense transactions, category and business-purpose explanations.
- Take full-size receipt photos through the Android camera using a FileProvider; images live in app-private storage.
- Import CSV (`date,merchant,amount` with optional `type,category,explanation`) and export entered transaction metadata.
- Basic income/expense summaries. No accounts, loan treatment, double-entry journal, reconciliation, or audited P&L yet.
- No internet permission and no telemetry/analytics in this version.

## Building and installing
1. Install **Android Studio** on Windows/Mac/Linux and its suggested Android SDK components.
2. Extract this ZIP; open the `ledgerleaf_android` folder in Android Studio.
3. Allow Android Studio to sync Gradle and download dependencies.
4. Connect your Android phone with USB debugging enabled (or use a trusted emulator).
5. Choose **Run ▶** and select your device. Android Studio will build and install a debug version.
6. In the phone app, tap `Transactions` then `+ Add` to add a sample expense or photograph a receipt.

No APK is included because the current build environment has no Android SDK or Gradle build tool available. An Android Studio build and on-device testing are required.

## Safe data note
**Do not use real bank transactions, sensitive payroll files, customer records or confidential receipts with this prototype yet.** The SQLite database is private to the application sandbox, but it does not have independent at-rest database encryption. It also lacks secure backup/recovery, access controls, complete audit trails, and accounting review. Uninstalling or clearing app data can destroy all local records. Receipt image files are NOT included in the CSV export; the CSV is NOT a full backup. CSV import does not deduplicate, so importing the same file twice creates duplicates.

## CSV sample
```csv
date,merchant,amount,type,category,explanation
2026-10-01,Hardware Store,42.50,expense,Workshop supplies,Materials for business workbench
2026-10-02,Customer payment,350.00,income,Sales,Invoice 102 paid
```

CSV import uses ISO 8601 dates (YYYY-MM-DD). When `type` is omitted, positive numbers are interpreted as income and negative numbers as expenses. Check the bank's sign convention before importing.

## Architecture roadmap
1. Proper double-entry accounting journal and reconciliation with automated validation tests.
2. SQLCipher/Keystore encryption, encrypted full backup including receipts, recovery workflows and audit log.
3. On-device OCR and offline categorization; optional voice dictation subject to model/device support.
4. Secure approved read-only external integrations using a small server-side token broker (bank, Amazon Business, Oracle POS, ADP) without storing bank credentials on the phone.
5. Commercial hardening: accountant review, penetration testing, privacy/compliance/legal review, access control, billing, and migration tools.

External integrations are **not** included in v0.1. This prototype is not QuickBooks compatible beyond generic CSV transaction data; it cannot yet replace QuickBooks.
