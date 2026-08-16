# CODE-READY 1.5 Changelog

## Added

- Real P-256 / ES256 internal delegation JWT signer and cryptographic decoder.
- Public-key resolver port, strict header/key/claim validation order and compact
  token size bound.
- Fail-closed downstream servlet filter with generic non-leaking 401 response.
- Frozen Argon2id PHC password adapter with Bouncy Castle and NFC normalization.
- Enumeration-resistant login use case with real/dummy database and password
  paths, internal-only failure reasons and password-buffer destruction.
- Focused unit-test sources and `validate_auth_crypto.py`, wired into both CI
  workflows.
- SPEC 41 authentication/delegation crypto implementation freeze.

## Changed

- Maven Reactor version advanced to `1.5.0-SNAPSHOT` across all 31 POMs.
- Nimbus JOSE + JWT 10.9.1 and Bouncy Castle 1.85.2 are centrally versioned in
  the parent and publishable BOM; unused direct crypto dependencies were removed.
- Delegation tokens are capped at 4096 compact bytes.
- Implementation evidence distinguishes implemented crypto components from
  production wiring and executed CI proof.

## Not claimed

- Gateway external-token verification, KMS/HSM integration, rotating JWKS,
  service filter registration, persistence adapters and login HTTP/session
  flows are not implemented.
- Maven/Java 21/JUnit, MySQL container and full runtime evidence remain
  unexecuted on the packaging host.
- The artifact is not a complete or production-ready IAM backend.
