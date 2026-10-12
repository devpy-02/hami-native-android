# Hami Native Android migration status

## Latest increment: direct customer selection on invoices

- Invoice forms can search active customers and select a customer from the saved list.
- The selected customer's ID is saved on the invoice (`invoices.customerId`), instead of relying only on the display name.
- Sales and sales-return ledger entries prefer the explicit customer ID; legacy free-text invoices still fall back to exact-name matching.
- Editing an invoice carries the selected customer ID forward, and changing the selected customer reverses the prior invoice ledger effect before recording the revised invoice.
- Room database schema version is now 4, with migration `3 -> 4` adding nullable `customerId` to `invoices` without dropping existing records.

## Validation status

- Static source review performed.
- A local Android Gradle build has **not** been run in this environment because Gradle is not installed and dependencies were not resolved here.
- The GitHub Actions workflow is configured to run `gradle assembleDebug` and upload the debug APK artifact. The first GitHub Actions run is still required to confirm compilation.
- The app is still a partial native rebuild. Do not use it as the sole system of record until inventory, invoice editing/voiding, accounting, backup/restore, and migration paths have been tested end-to-end.

## Latest increment: customer debt report

- Added a native Compose debt report screen accessible from the home screen.
- Shows total positive receivables, total customer credit balances, debtor/creditor counts, and searchable/filterable customer balances.
- Report is derived from stored customer balances; it has not yet been validated against a full ledger rebuild or a real Android build.
