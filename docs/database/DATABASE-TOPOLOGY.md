# Database Topology — Final Freeze

Development may use one MySQL 8 instance with separate logical databases:

- iam_auth
- iam_identity
- iam_organization
- iam_authorization
- iam_sharing
- iam_file
- iam_audit
- iam_job

Production preserves the same ownership boundaries even when databases are deployed on different clusters.

Rules:
- No cross-service database JOIN.
- No cross-service table writes.
- Each service owns and runs its own Flyway migrations.
- `sys_*` infrastructure tables are local to the owning service database.
