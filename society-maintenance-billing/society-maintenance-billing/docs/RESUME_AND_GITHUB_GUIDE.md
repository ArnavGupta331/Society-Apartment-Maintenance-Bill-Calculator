# Resume & GitHub guide

## Resume entry (pick 3-4 bullets)

**Society Maintenance Bill Calculator** | Java 17, JDBC, SQLite, Maven, JUnit 5, OpenPDF | [GitHub link]

- Built a layered Java application (model / repository / service / CLI) that automates monthly maintenance billing, payment tracking and dues reporting for an apartment society, persisting data in SQLite through JDBC.
- Implemented a configurable billing engine (area-based charges, grace period, per-day late fee with cap) using `BigDecimal` for exact money arithmetic; kept it free of I/O so every rule is unit-testable.
- Recorded payments transactionally (payment insert + bill status/late-fee update in one transaction) with support for partial payments and automatic receipt numbering.
- Generated PDF payment receipts with OpenPDF and produced pending-dues and monthly collection reports.
- Wrote JUnit 5 tests covering billing rules, input validation and end-to-end workflows against a temporary database; designed a custom exception hierarchy and central input validation.

## Interview talking points

- **Why BigDecimal and text columns?** Floating point cannot represent money exactly; SQLite has no decimal type, so amounts are stored as text.
- **Why is late fee computed on demand for unpaid bills?** It changes every day; storing it daily would need a scheduler. It is frozen on the bill when a payment is made.
- **Why a transaction in PaymentService?** The payment row and the bill's status must change together.
- **What would you change at scale?** Move to PostgreSQL/Spring Data JPA, add optimistic locking on bills, add a scheduler for reminders.

## GitHub checklist

1. `git init`, add the `.gitignore` already included, commit in small meaningful steps (not one giant commit).
2. Replace `<your-username>` in the README clone URL.
3. Run `java -jar target/maintenance-billing.jar`, choose 9, and take screenshots into `docs/screenshots/`:
   - `pending-dues.png` (option 6), `record-payment.png` (option 5), `receipt.png` (open a PDF from `receipts/`).
4. Add repo topics: `java`, `jdbc`, `sqlite`, `maven`, `junit5`, `billing`.
5. Optional: add a GitHub Actions workflow running `mvn verify` and a build badge.
