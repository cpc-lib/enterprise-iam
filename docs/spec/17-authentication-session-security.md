# SPEC 17 — Authentication & Session Security

## V1.0 Frozen Baseline

短生命周期 JWT Access Token + Redis Session + Rotating Refresh Token。
支持 Token Family、Reuse Detection、Token Version、强制下线、密码变更失效、多设备会话。
内部调用采用 Service Identity + Signed Delegation Token。
