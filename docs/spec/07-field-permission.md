# SPEC 07 — Field Permission

## V1.0 Frozen Baseline

字段能力：READ、WRITE、MASK、HIDDEN。
后端负责请求字段写校验、MyBatis SET 列保护、响应序列化过滤和脱敏。
前端字段隐藏只是 UX，不是安全边界。
