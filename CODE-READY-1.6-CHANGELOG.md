# CODE-READY 1.6 Changelog

## Added

- Bounded rotating JWKS public-key resolver with synchronized global refresh
  budgeting and rotation-safe per-`kid` negative caching.
- Transport-neutral JWKS loader port and explicit key-resolution exception.
- Servlet delegation Spring Boot auto-configuration, internal path matcher and
  fail-closed startup behavior.
- Authorization-service exact issuer/audience/path configuration seed.
- Gateway route security registry with explicit protected or public policy.
- Gateway delegation filter that removes external bearer credentials and adds
  a new route-audience-bound ES256 token.
- Focused JWKS, auto-configuration, path and Gateway filter test sources.
- `validate_delegation_wiring.py` and SPEC 42.

## Changed

- Reactor version advanced to `1.6.0-SNAPSHOT` across all 31 POMs.
- Servlet/Spring Web dependencies moved out of `iam-common-security` into the
  Servlet starter, keeping the common crypto module reactive-safe.
- Unknown or omitted Gateway route security policy is now fail-closed instead
  of implicitly public.
- Delegation security parameters now freeze JWKS document/key/cache bounds.
- Unknown-key negative-cache memory is capped at 1,024 entries.
- Delegation validation now rejects multi-audience tokens and fractional
  numeric identity claims.

## Not claimed

- External access-token verification, HTTPS JWKS transport, KMS/HSM integration,
  deployed route policy and enabled downstream enforcement remain open.
- Maven/JDK 21/JUnit, Testcontainers and deployed runtime evidence were not
  executed on the packaging host.
- This artifact is not a complete or production-ready IAM backend.
