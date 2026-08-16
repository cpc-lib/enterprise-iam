# SPEC 06 — Data Permission

## V1.0 Frozen Baseline

Data Scope：ALL、SELF、TEAM、TEAM_AND_CHILDREN、SPECIFIED_TEAM、SHARED、CUSTOM_POLICY。
Operation ALLOW 只代表具备操作能力，Data Scope 决定具体数据行。
Tenant Predicate 永远存在，ALL 也不能跨租户。
