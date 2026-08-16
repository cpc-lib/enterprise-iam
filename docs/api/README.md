# API Contracts

Detailed V1.0 prose contracts are in:

- `../spec/26-api-contract-business-state-machine-acceptance.md`

`openapi-code-phase-01.yaml` is the normative contract for the first vertical
slice. Remaining APIs must be added to the same contract before their Story
may move from `READY` to `IN_PROGRESS`.

V1.2 phase contracts:

- `openapi-code-phase-02-policy.yaml`: organization/TeamRole/data/field policy
- `openapi-code-phase-03-sharing-file.yaml`: sharing/upload/file access

Normative priority:

1. OpenAPI request/response/status/security declarations
2. SPEC 38 capability-specific rules
3. SPEC 37 cross-cutting rules
4. SPEC 26 prose examples

CI must validate the document and run backward-compatibility diff checks.
