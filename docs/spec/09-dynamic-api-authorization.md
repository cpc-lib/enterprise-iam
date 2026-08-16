# SPEC 09 — Dynamic API Authorization

## V1.0 Frozen Baseline

业务 API 通过 DB 配置 PUBLIC/AUTH_REQUIRED/INTERNAL_ONLY。
API Discovery 自动注册技术 Endpoint，管理员配置逻辑 Resource/Operation 映射。
未映射受保护 API 默认拒绝。
