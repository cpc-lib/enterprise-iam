# CODE-READY SPEC 1.1 — Change Log

## Purpose

This release converts the architecture freeze into an implementation-entry
contract while preserving all original specifications.

## Added

- SPEC 37 as the authoritative implementation contract and conflict resolver.
- OpenAPI 3.1 contract for the first security-critical vertical slice.
- AsyncAPI 3.0 contract with a versioned event envelope and core events.
- Frozen development/test security parameters and production override rules.
- Executable MySQL/Flyway DDL for the CODE PHASE 01 identity, auth and
  authorization baseline.
- Requirement-to-contract-to-test traceability matrix.
- Open-decision register with owners, due gates and safe defaults.
- Two-level entry gate separating design readiness from build readiness.
- Acceptance catalog with concrete security, integration, contract and
  property-test IDs.
- Deterministic contract validator and a minimal contract-quality CI workflow.
- Official security standards baseline and explicit FAPI scope boundary.

## Corrected

- `docs/api/README.md` no longer says every OpenAPI contract is deferred.
- The project README now identifies SPEC 37 and machine-readable contracts as
  the highest implementation authority.
- CODE PHASE readiness no longer implies that the placeholder Maven reactor,
  Docker environment or tests already work.

## Not claimed

- This package is not a completed backend implementation.
- Only CODE PHASE 01 has machine-readable API, event and DDL contracts.
- Production capacity, SLOs and secrets still require environment approval.

## Compatibility

No V1 business capability was removed. Where prose differs from the new
machine-readable contract, SPEC 37 defines the conflict-resolution rule.
