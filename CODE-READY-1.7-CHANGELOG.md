# CODE-READY 1.7 Changelog

## Added

- Strict P-256 ES256 `at+jwt` signer/decoder with exact issuer and singleton
  Gateway audience.
- Positive `tver` and `sver` access claims for immediate user/session
  invalidation checks.
- Shared bounded JWKS cache mechanics with profile-specific access and
  delegation trust-store types.
- Gateway Bearer authentication filter that establishes identity only after
  token and authoritative session validation.
- Exact authoritative session snapshot/version/ACTIVE/expiry verifier.
- Generated Gateway request correlation and fixed sanitizer → authentication →
  delegation ordering.
- Focused access-token, session-fence, Gateway and application-context tests.
- `validate_access_authentication.py` and SPEC 43.

## Hardened

- Multiple Authorization headers, malformed Bearer syntax, multiple audience,
  fractional versions, future `iat`, embedded/remote key headers and stale
  session versions deny.
- JWKS/session dependency outages return generic 503 and never fail open.
- Downstream errors after authentication are no longer in the authentication
  recovery boundary.
- Public routes never create identity from an optional bearer token.

## Changed

- Maven Reactor version advanced to `1.7.0-SNAPSHOT` across all 31 POMs.

## Not claimed

- HTTPS JWKS transport, Redis session projection adapter/writer, KMS/HSM,
  auth-service persistence/issuance transaction, real routes and deployment
  enablement remain open.
- Maven/JDK 21/JUnit/Testcontainers and deployed runtime evidence were not run
  on the packaging host.
- This artifact remains an implementation seed, not a production IAM backend.
