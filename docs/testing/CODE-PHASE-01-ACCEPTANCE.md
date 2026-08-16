# CODE PHASE 01 Acceptance Catalog

Every case runs with real MySQL and Redis containers unless its purpose is to
simulate dependency loss. Fixtures use at least two tenants and two subjects.

| ID | Given / When | Required result |
|---|---|---|
| SEC-TEN-001 | External caller injects tenant/user forwarding headers | Gateway removes them; trusted context comes only from verified authentication. |
| IT-TEN-001 | Tenant A and Tenant B contain colliding business identifiers | Every repository result remains tenant-local. |
| SEC-IDOR-001 | Tenant A token requests Tenant B user/resource ID | `404` or deny without revealing existence; no cross-tenant audit data leaks. |
| SEC-AUTHN-001 | Compare unknown identity and wrong-password attempts | Same public code/status/body shape and comparable bounded processing path. |
| SEC-JWT-001 | Token uses `none`, wrong algorithm, unknown `kid`, wrong issuer/audience/type | Verification rejects every token before authorization. |
| SEC-JWT-002 | Token is expired, not-yet-valid or exceeds clock-skew bound | Request returns `401`; no downstream trusted context is created. |
| SEC-ACCESS-001 | Access token has wrong/multiple audience, unsafe JOSE header, fractional version or future issue time | Gateway returns generic `401`; key/session/downstream trust is not created. |
| SEC-SESSION-001 | Session is missing, revoked, expired, version-mismatched or Redis is unavailable | Invalid state returns generic `401`; dependency outage returns generic `503`; neither reaches downstream. |
| SEC-JWKS-TRANSPORT-001 | JWKS URI/host is unsafe; DNS is private/mixed; TLS/HTTP redirects; response type/encoding/length/body is invalid | Fetch rejects without following another destination or serving an expired stale key; protected request fails closed. |
| IT-SESSION-PROJECTION-001 | Duplicate, out-of-order and terminal session projections race in Redis | Lua update is atomic; versions never decrease; terminal session never becomes ACTIVE; absolute TTL is applied with the hash write. |
| IT-LOGIN-ISSUANCE-001 | Valid identity issues a login while signer success/failure and the concurrent-session boundary are exercised | Success returns credentials only after session, refresh HMAC and projection Outbox commit; any signing/DB/Outbox failure leaves all three absent; the configured session bound is serialized per user. |
| IT-REFRESH-001 | Valid refresh token is used once | Old token becomes `ROTATED`; exactly one new token becomes active atomically. |
| SEC-REFRESH-002 | Rotated token is used again | Token family and session are revoked; security event emitted once; subsequent access denies. |
| IT-DISABLE-001 | Administrator disables an active user with sessions | Command succeeds only after affected access/refresh path is invalidated. |
| SEC-FAILCLOSED-001 | API mapping is absent or authorization dependency is unavailable | Protected request denies; no fallback allow path executes. |
| PT-CACHEKEY-001 | Vary tenant, subject, instance, operation and policy context independently | No decision is reused across any changed policy-relevant dimension. |
| IT-VERSION-001 | Bind/unbind role or add/remove role permission | Permission version increases monotonically in the mutation transaction. |
| SEC-REVOKE-001 | Warm an ALLOW cache entry and revoke its grant concurrently | Requests after revoke success cannot receive ALLOW from the stale entry. |
| IT-IDEM-001 | Repeat same mutation with same key and fingerprint | Original status/body is replayed; one business effect and one event exist. |
| IT-IDEM-002 | Reuse key with a different canonical body | `409 IAM_IDEMPOTENCY_KEY_CONFLICT`; second body has no effect. |
| IT-EVENT-001 | Deliver identical event multiple times | Consumer commits one effect and one consume record. |
| IT-EVENT-002 | Deliver lower aggregate version after higher version | Lower version is ignored/quarantined and cannot overwrite current state. |
| DB-TENANT-001 | Attempt duplicate active tenant-scoped codes in same/different tenants | Same tenant conflicts; different tenants succeed where the schema permits. |
| IT-OUTBOX-001 | Crash between business mutation and relay publish | Mutation and outbox are both committed or both absent; relay eventually publishes once logically. |
| E2E-AUDIT-001 | Execute allow, deny and revoke flows | Trace, request, decision, permission version and sanitized audit entries correlate end to end. |
| SEC-LOG-001 | Exercise login, refresh and error paths while capturing logs | No password, token, cookie, auth header, raw identity or protected field appears. |
| CT-API-001 | Omit idempotency key or send stale optimistic version | Required error code/status is returned; state is unchanged. |
| CT-COMPAT-001 | Diff proposed OpenAPI/AsyncAPI/schema against released baseline | Breaking change blocks CI without approved migration evidence. |

## Property tests

- `PROP-AUTHZ-001`: identical normalized input and permission version produce
  the same decision.
- `PROP-AUTHZ-002`: removing an effective grant never increases rights.
- `PROP-AUTHZ-003`: cross-tenant subject/resource pairs never allow.
- `PROP-AUTHZ-004`: unknown policy states never produce allow.
- `PROP-IDEM-001`: any duplicate delivery count produces at most one business
  effect.

## Evidence format

CI publishes JUnit results with these IDs, immutable build ID, contract version,
database migration version and test-environment image digests. Skips for P0
security tests fail the release gate.
