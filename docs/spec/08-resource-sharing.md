# SPEC 08 — Resource Sharing

## V1.0 Frozen Baseline

资源实例可以分享给 USER、TEAM、ROLE、TEAM_ROLE。
Share 的 Operation/Field/有效期必须是授予者有效权限的子集。
支持 Revoke、Expire、Reshare、maxDepth，禁止权限提升。
