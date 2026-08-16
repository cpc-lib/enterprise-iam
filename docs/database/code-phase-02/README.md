# CODE PHASE 02–05 Physical DDL

| Migration | Owner | Capability |
|---|---|---|
| `organization/V1__organization_team_baseline.sql` | iam-organization-service | organization/team/member/TeamRole |
| `authorization/V2__authorization_policy_baseline.sql` | iam-authorization-service | data/field policy metadata and bindings |
| `sharing/V1__sharing_security_baseline.sql` | iam-sharing-service | share graph, grant basis, history and epoch |
| `file/V1__file_security_baseline.sql` | iam-file-service | object/file/reference/upload/scan/quota |

All CODE PHASE 01 rules continue to apply. These migrations are implementation
inputs and become immutable after application to any shared environment.

Required database verification includes tenant isolation, active uniqueness,
optimistic conflicts, hierarchy cycle rejection at the domain/repository layer,
share epoch monotonicity, concurrent upload completion and purge/legal-hold
guards.
