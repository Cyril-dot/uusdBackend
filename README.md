# USSD Data Bundle Backend

A Spring Boot backend that powers a USSD data-bundle-purchasing menu.
It receives USSD session requests from **Arkesel's USSD gateway** and, based
on the customer's menu selections, buys a bundle through the **Big Dreams
Data API**.

## Flow

```
Dial *xxx#
 └─ 1. MTN / 2. Telecel / 3. AirtelTigo (iShare)
     └─ Select bundle size (fetched live from Big Dreams Data, priced)
         └─ 1. My number / 2. Another number
             └─ (if "another number") Enter recipient number
                 └─ Confirm & Pay (1. Pay & Confirm / 2. Cancel)
                     └─ Paystack Mobile Money charge is initiated, USSD session ends
                         └─ Customer approves the payment prompt on their phone
                             └─ Paystack webhook (charge.success) fires
                                 └─ Bundle is bought via Big Dreams Data "place_order"
                                     └─ Confirmation SMS sent to the customer
```

Session state (which network/bundle/recipient the user picked) is kept
in-memory, keyed by Arkesel's `sessionID`, and expires automatically after
an idle timeout (default 5 minutes).

### Where the money goes

Two completely separate pots of money are involved, on purpose:

- **Customer → Paystack → your Paystack settlement account.** This is the
  actual sale price of the bundle, collected via Mobile Money charge, and
  it's yours - normal Paystack payout rules apply (settlement to your bank
  account on your usual schedule).
- **Your Big Dreams Data wallet → Big Dreams Data.** This funds the actual
  bundle purchase. It is **not** auto-topped-up from Paystack payments - you
  fund it yourself via the Big Dreams Data dashboard, exactly as you did
  before. This app only *spends* from that wallet (via `place_order`); it
  never deposits into it.

So in practice you're running two ends of a small margin business: collect
retail price via Paystack, keep your Big Dreams Data wallet topped up out of
that revenue (manually, at whatever cadence/markup you choose), and this app
wires the two together per order.

Because MoMo approval can take anywhere from a few seconds to a couple of
minutes, and USSD sessions typically expire well before that, the purchase
does **not** happen synchronously inside the USSD menu - it happens when the
Paystack webhook confirms the charge, which is why the SMS confirmation step
exists (the customer has already hung up the USSD session by the time their
bundle is actually bought).

## Requirements

- Java 17+
- Maven 3.8+
- A Big Dreams Data account + API key (`bh_live_...`)
- An Arkesel account with a USSD shortcode pointed at this app's `/ussd` endpoint

## Configuration

Set these environment variables before running (see `application.properties` for defaults):

| Variable | Description |
|---|---|
| `BIGDREAMS_API_KEY` | Your Big Dreams Data API key (this wallet is funded by you, manually) |
| `BIGDREAMS_BASE_URL` | Big Dreams Data API base URL (defaults to the production URL) |
| `PAYSTACK_SECRET_KEY` | Your Paystack secret key (`sk_test_...` while testing, `sk_live_...` in production) - used both to charge customers and to verify webhook signatures |
| `PAYSTACK_BASE_URL` | Paystack API base URL (defaults to `https://api.paystack.co`) |
| `ARKESEL_SMS_API_KEY` | Optional - Arkesel SMS v2 API key, for the "your bundle is on its way" confirmation text. Leave unset to skip SMS entirely |
| `ARKESEL_SMS_SENDER_ID` | Optional - SMS sender ID shown to customers, max 11 characters (default `DataBundle`) |
| `USSD_SESSION_TIMEOUT_MINUTES` | Idle session timeout, default `5` |
| `PORT` | HTTP port, default `8080` |

### Paystack webhook setup

1. In the Paystack Dashboard, go to **Settings → API Keys & Webhooks**.
2. Set the **Webhook URL** to `https://your-domain.com/paystack/webhook` (must be HTTPS and publicly reachable - use a tunnel like ngrok while testing).
3. That's it - no extra secret to copy, the app verifies the webhook using the same `PAYSTACK_SECRET_KEY` you already configured (Paystack signs every webhook body with HMAC-SHA512 of that key).
4. Use a `sk_test_...` key and Paystack's test Mobile Money numbers while developing, so no real money moves.

## Run locally

```bash
export BIGDREAMS_API_KEY=bh_live_your_api_key_here
mvn spring-boot:run
```

The USSD endpoint is then available at:

```
POST http://localhost:8080/ussd
```

Register this URL (behind a public HTTPS domain, e.g. via ngrok while
testing, or your real deployment) as the **Endpoint URL** for your USSD
shortcode in the Arkesel dashboard.

## Testing without a real USSD dial

You can simulate Arkesel's requests directly with curl. A session starts
with `newSession: true` and empty `userData`; every subsequent request
carries the digit(s) the user just typed and `newSession: false`.

```bash
# 1. Dial in
curl -s -X POST http://localhost:8080/ussd \
  -H "Content-Type: application/json" \
  -d '{
    "sessionID": "test-session-1",
    "userID": "USSD_TEST",
    "newSession": true,
    "msisdn": "233544919953",
    "userData": "",
    "network": "MTN"
  }'

# 2. Choose network -> MTN
curl -s -X POST http://localhost:8080/ussd \
  -H "Content-Type: application/json" \
  -d '{
    "sessionID": "test-session-1",
    "userID": "USSD_TEST",
    "newSession": false,
    "msisdn": "233544919953",
    "userData": "1",
    "network": "MTN"
  }'

# 3. Choose bundle -> option 1
curl -s -X POST http://localhost:8080/ussd \
  -H "Content-Type: application/json" \
  -d '{
    "sessionID": "test-session-1",
    "userID": "USSD_TEST",
    "newSession": false,
    "msisdn": "233544919953",
    "userData": "1",
    "network": "MTN"
  }'

# 4. Send to my own number -> option 1
curl -s -X POST http://localhost:8080/ussd \
  -H "Content-Type: application/json" \
  -d '{
    "sessionID": "test-session-1",
    "userID": "USSD_TEST",
    "newSession": false,
    "msisdn": "233544919953",
    "userData": "1",
    "network": "MTN"
  }'

# 5. Pay & Confirm -> option 1 (this initiates a REAL Paystack charge, so use a sk_test_ key!)
curl -s -X POST http://localhost:8080/ussd \
  -H "Content-Type: application/json" \
  -d '{
    "sessionID": "test-session-1",
    "userID": "USSD_TEST",
    "newSession": false,
    "msisdn": "233544919953",
    "userData": "1",
    "network": "MTN"
  }'
```

Step 5 only *starts* the payment - the USSD reply says "approve the prompt on
your phone". The actual bundle purchase happens when Paystack calls your
webhook. You can simulate that webhook locally too (grab the `reference`
that was generated - check your app logs for it, since the curl session
above doesn't return it directly):

```bash
# Simulate a successful Paystack webhook (only works if signature
# verification is temporarily bypassed, or by using a real Paystack test
# transaction which will call your webhook for you automatically).
curl -s -X POST http://localhost:8080/paystack/webhook \
  -H "Content-Type: application/json" \
  -H "X-Paystack-Signature: <computed-hmac-sha512-of-body-with-your-secret-key>" \
  -d '{
    "event": "charge.success",
    "data": { "reference": "USSD_test-session-1_1234567890" }
  }'
```

In practice it's much easier to just use a real `sk_test_...` key with
Paystack's documented test Mobile Money numbers - Paystack will fire the
real webhook for you, correctly signed, once the test charge "succeeds".

## Build a deployable jar

```bash
mvn clean package
java -jar target/ussd-data-backend.jar
```

## Notes / production hardening ideas

- **Multi-instance deployments**: session state is stored in-memory
  (`ConcurrentHashMap`). If you scale this app horizontally, swap
  `UssdSessionService` for a shared store (Redis) or enable sticky sessions
  on your load balancer, otherwise a session's later steps can land on an
  instance that never saw the earlier steps.
- **Idempotency**: `placeOrder` always sends `order_id = "USSD_" + sessionID`,
  so a duplicated request for the same session won't double-charge the
  wallet (Big Dreams Data returns the original order on a repeat `order_id`).
- **Menu size**: only the first `ussd.session.max-menu-items` (default 9)
  bundles per network are shown, to keep menu choices single-digit. Add
  paging if a network ever has more than 9 active bundles.
- **Logging**: request/response bodies are logged at DEBUG. Turn this down
  in production if you don't want customer numbers in logs, or scrub them.
