# iam-common-mybatis

Shared MyBatis-Plus runtime for IAM persistence adapters.

Business services keep repository ports in `application`/`domain` and put
mapper interfaces under `infrastructure.persistence.mapper`. SQL belongs in
`src/main/resources/mapper/**/*.xml`; annotation SQL and direct
`JdbcTemplate` access are intentionally excluded.

IAM Framework 技术模块。
