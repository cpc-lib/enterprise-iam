# Contract Quality Gate — V1.10.1

Run from the repository root:

```bash
python -m pip install -r tools/requirements-contracts.txt
python tools/validate_code_ready_spec.py
```

The validator checks:

- parseability and frozen versions of OpenAPI, AsyncAPI and security YAML;
- all phased OpenAPI/AsyncAPI documents and cross-document operation-ID uniqueness;
- all local `$ref` targets;
- unique OpenAPI operation IDs;
- required idempotency headers on mutations;
- explicit, documented exclusions for non-replayable secret responses;
- signed-64-bit ID semantics across OpenAPI and AsyncAPI;
- response-direction correctness for access tokens;
- exact login refresh-cookie and session-limit response contracts;
- versioned event names;
- deny-by-default and JWT algorithm safety invariants;
- SPEC manifest authority and artifact existence;
- unique, test-linked requirement traceability rows.
- required DDL ownership sets for Organization Authorization Sharing and File.
- frozen-content SHA-256 verification for normative contracts.

Repository CI must additionally run an industry-standard OpenAPI/AsyncAPI
validator, contract compatibility diff, MySQL Flyway test and security tests.
The local script is a deterministic baseline, not a substitute for those tools.
