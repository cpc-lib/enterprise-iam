# CODE PHASE 01 Flyway DDL

These files are normative physical-schema inputs for the first vertical slice.
Copy each migration to the matching service only when implementation starts;
after a shared environment applies a migration, never edit it in place.

| File | Owner/database | Purpose |
|---|---|---|
| `identity/V1__identity_baseline.sql` | iam-identity-service / `iam_identity` | tenant, user, identity, role and binding |
| `auth/V1__auth_baseline.sql` | iam-auth-service / `iam_auth` | credentials, sessions, refresh families and security state |
| `auth/V2__session_projection_outbox.sql` | iam-auth-service / `iam_auth` | durable session projection delivery and leased relay state |
| `authorization/V1__authorization_rbac_baseline.sql` | iam-authorization-service / `iam_authorization` | resource/operation, permission grants, version and outbox |

Validation requirements:

1. MySQL 8.4 LTS, `utf8mb4`, strict SQL mode and UTC session timezone.
2. Flyway runs with one database account per owning service.
3. Apply on an empty schema, inspect all constraints/indexes, then run Flyway
   again and verify that no migration remains pending.
4. Verify every tenant-scoped repository query with cross-tenant fixtures.
5. Do not add a cross-service foreign key or runtime cross-database join.

The complete V1 migration sequence remains in `FLYWAY-MIGRATION-PLAN.csv`.
