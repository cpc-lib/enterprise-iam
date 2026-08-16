# SPEC 13 — Authorization Engine

## V1.0 Frozen Baseline

授权 Pipeline：Guards → Subject → Resource/Operation → Grants → Conditions → Merge → Data → Fields → Decision。
Hard Guard 不可覆盖；最高 priority tier 胜；同 priority DENY > ALLOW；无 Grant 默认 DENY。
支持 Explain、Batch、L1/L2 Cache、Permission Version。
