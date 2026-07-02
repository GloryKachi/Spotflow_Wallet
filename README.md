# Spotflow Wallet — Pay-In & Payout Backend

A Spring Boot service that automates wallet funding (Pay-In) and cash withdrawals
(Payout) for a tournament app, integrated with the Spotflow API.

> **Quickstart:** this project runs fully mocked out of the box — no Spotflow
> API keys required. See "Running with mock Spotflow" below.

## Stack

- Java 17, Spring Boot 4.1
- PostgreSQL + Flyway migrations
- `RestClient` for outbound Spotflow calls (no external HTTP SDK)

## Running locally

1. Create a local Postgres database:
```bash
   createdb wallet_db
```
2. Set datasource credentials in `application.properties` if they differ from
   the defaults (`postgres` / no password / `localhost:5432`).
3. Run the app — Flyway will create the schema and seed one test user + wallet
   automatically:
```bash
   ./mvnw spring-boot:run
```
4. Import `postman/spotflow-wallet.postman_collection.json` (and the
   accompanying environment) into Postman and hit the endpoints. The seeded
   test user has `id=1` and a starting wallet balance of `5000.00 NGN` (for
   testing withdrawals without needing a webhook first).

## Running with mock Spotflow (no API key needed)

By default this project runs against an in-memory `MockSpotflowClient` —
you can clone and run it with zero Spotflow credentials. Make sure your
local config has:

```properties
spotflow.mock=true
```

Every endpoint (fund, withdraw, webhook) works fully in this mode, so the
entire flow can be exercised before sandbox credentials are ever needed.

## Using a real Spotflow account (optional)

To hit the real Spotflow sandbox instead of the mock, set `spotflow.mock=false`
and provide your own credentials as environment variables. **Never put the
real key in `application.properties` or commit it to git:**

```bash
export SPOTFLOW_SECRET_KEY=sk_test_your_real_key_here
export SPOTFLOW_WEBHOOK_SIGNING_SECRET=your_webhook_signing_secret
```

`application.properties` reads these automatically via:
```properties
spotflow.secret-key=${SPOTFLOW_SECRET_KEY:dummy-key}
```

## Architecture: where things live


com.glory.spotflow_wallet
├── spotflow/              <-- ALL Spotflow API integration, isolated from business logic
│   ├── SpotflowClient.java        interface - the only thing the rest of the app depends on
│   ├── HttpSpotflowClient.java    real implementation (RestClient + Bearer auth)
│   ├── MockSpotflowClient.java    fake implementation used when spotflow.mock=true
│   ├── SpotflowConfig.java        decides which implementation is wired up
│   ├── SpotflowProperties.java    spotflow.* config binding
│   ├── dto/                       request/response shapes matching Spotflow's OpenAPI spec
│   └── exception/                 SpotflowApiException
│
├── domain/
│   ├── user/                      User entity + repository
│   ├── wallet/                    Wallet entity, WalletService (core funding/withdrawal logic)
│   └── transaction/                Transaction entity, status/type enums, repository
│
├── webhook/                       WebhookController, WebhookService, idempotency gate (WebhookEvent)
├── worker/                        Scheduled fallback reconciliation worker
├── web/                           REST DTOs for the wallet controller
└── config/                        SchedulingConfig (enables @Scheduled)

The `spotflow` package is the only place that knows about Spotflow's URLs,
auth header format, or payload shapes. `WalletService` and `WebhookService`
depend only on the `SpotflowClient` interface and its DTOs — swapping the
mock for the real client (or pointing at a different provider entirely)
never touches business logic.

## Endpoints

### `POST /wallet/fund`
Creates a `PENDING` transaction, then calls Spotflow's Create Dynamic Account
API to generate a temporary virtual account number for the user to pay into.

```json
{ "userId": 1, "amount": 1000.00 }
```

### `POST /wallet/withdraw`
Debits the local wallet balance immediately (so a user can never withdraw
more than they have, even while the disbursement call is in flight), then
calls Spotflow's Single Disbursement API.

```json
{
  "userId": 1,
  "amount": 500.00,
  "bankAccountNumber": "0123456789",
  "bankCode": "058",
  "accountName": "Test Player"
}
```

### `POST /webhooks/spotflow`
Receives `account_credit_successful` events from Spotflow when a pay-in
lands. Implements the idempotency gate: the delivery's `webhook-id` header
is inserted into a `webhook_events` table with a unique constraint before
any wallet mutation happens. If the same event is delivered twice (network
retry), the second insert fails on the unique constraint and the handler
exits without crediting the wallet again. A second, independent safety net
— a row-level lock plus a "transaction must still be PENDING" check in
`WalletService` — guards against the rarer case of something racing on the
same transaction reference. See "Webhook security" below for signature
verification.

## Background worker: `PendingTransactionReconciliationWorker`

Runs every 10 minutes via `@Scheduled`. Finds transactions stuck `PENDING`
for over 60 minutes, and for withdrawals, calls Spotflow's
`GET /transfers/reference/{reference}` to get the true status:

- `successful` → marks the transaction `SUCCESS`
- `failed` → refunds the wallet (since the local debit happened up-front)
  and marks `FAILED`
- not found / still pending past the window → marks `ABANDONED`

**Note:** Spotflow's documented API doesn't expose a "check pay-in status
by our reference" endpoint for dynamic accounts (pay-ins are confirmed
exclusively via webhook) — so for stuck `FUNDING` transactions the worker
can only apply the timeout rule and mark them `ABANDONED`, which is
communicated honestly in code comments rather than silently guessed at.

## Webhook security

Spotflow's webhooks follow the Standard Webhooks spec: each delivery
includes a `webhook-id` header (the documented idempotency key) and an
`x-spotflow-signature` header — an HMAC-SHA256 signature computed over
`{webhook-id}.{webhook-timestamp}.{raw body}`.

`WebhookController` verifies this signature before doing anything else,
and rejects unverified requests with `401`. The signing secret is separate
from your API secret key — it's generated when you set your webhook URL in
the Spotflow dashboard under Settings > API Keys. Set it via:

```bash
export SPOTFLOW_WEBHOOK_SIGNING_SECRET=your_webhook_signing_secret
```

If it's left unset, verification is skipped with a loud warning in the
logs — fine for local/mock development, but this must be set before
pointing a real webhook at this service, otherwise anyone who finds the
URL could POST a fake "successful" payment event and get a wallet credited
for free.

Bank code validation via Spotflow's Resolve Bank Account endpoint is
recommended before disbursing in production.