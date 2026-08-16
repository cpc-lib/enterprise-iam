# SPEC 05 — Resource & Operation Model

## V1.0 Frozen Baseline

Application → Service → Resource → Operation。
Operation 为第一等动态元数据，不固定 CRUD。
API 通过 service + method + normalized path 动态映射 Resource + Operation。
