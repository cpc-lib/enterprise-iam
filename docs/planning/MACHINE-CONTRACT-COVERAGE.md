# Machine Contract Coverage — 1.2

`READY` means the capability has normative API/event/schema inputs and named
acceptance evidence. It does not mean implementation exists.

| Capability | OpenAPI | Events | DDL | Acceptance | State |
|---|---|---|---|---|---|
| Tenant/User/Role/Auth/RBAC | Phase 01 | Phase 01 | Phase 01 | Phase 01 | READY |
| Organization/Team/TeamRole | Phase 02 | Phase 02 | Organization V1 + Authorization V2 | Phase 02–05 | READY |
| Data scope | Phase 02 | Phase 02 | Authorization V2 | Phase 02–05 | READY |
| Field permission/masking | Phase 02 | Phase 02 | Authorization V2 | Phase 02–05 | READY |
| Cross-team sharing/revoke fence | Phase 03 | Phase 02 | Sharing V1 | Phase 02–05 | READY |
| File multipart/instant/reference/access | Phase 03 | Phase 02 | File V1 | Phase 02–05 | READY |
| Dynamic API discovery/mapping administration | Prose SPEC 26 | Catalog only | Logical plan | Prose acceptance | CONTRACT NEXT |
| Direct and temporary grants | Prose SPEC 26/28 | Catalog only | Logical plan | Prose acceptance | CONTRACT NEXT |
| Condition DSL authoring | Disabled baseline | Catalog only | Logical plan | Fuzzing required | BLOCKED BY DEC-014 |
| Session/security administration queries | Partial prose | Phase 01 security event | Auth baseline | Prose acceptance | CONTRACT NEXT |
| Audit/security-center query APIs | Prose SPEC 26 | Consumer contracts pending | Audit logical plan | Prose acceptance | CONTRACT NEXT |
| File lifecycle/DR administration | Prose SPEC 35 | File event baseline | Extension plan | Prose acceptance | CONTRACT NEXT |
| Frontend administration console | Prose SPEC 20/26 | N/A | N/A | Prose acceptance | UI CONTRACT NEXT |
| Deployment/CI full build | Skeleton | N/A | N/A | Gate B | NOT BUILD READY |

## Next contract sequence

1. Dynamic API discovery/mapping and PUBLIC/INTERNAL policy lifecycle.
2. Audit/security-center query contracts and retention evidence.
3. Direct/temporary grant lifecycle if retained in V1 product scope.
4. File retention/legal-hold/purge/restore administration.
5. Frontend generated client and permission-aware UI interaction contracts.

No `CONTRACT NEXT` capability may be implemented from prose alone without a
Story-level machine-contract slice and traceability update.
