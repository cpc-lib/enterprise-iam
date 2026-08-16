# SPEC 03 — RBAC Model

## V1.0 Frozen Baseline

User N:N Role；Role N:N Permission。
Permission 绑定 Resource + Operation，并支持 Effect、Priority、Condition。
禁止在 Java 业务代码中硬编码 permission code。

---

## Final Consistency Addendum - Condition 语义与边界

Condition 采用受限 DSL，禁止 SpEL / Groovy / JavaScript / 任意 SQL（SPEC 25 §65）；
以 condition expression AST 形式存储于 `iam_condition_policy`（SPEC 28 §44），
由授权 Pipeline 的 Conditions 阶段经 ConditionEvaluator 求值（SPEC 30）。
安全威胁与缓解见 SPEC 31 Threat 80（Unsafe Condition DSL）。

生产环境的 Condition DSL 授权默认禁用（Disabled baseline）；
启用前必须通过解析器 fuzzing 与复杂度上限验收（OPEN-DECISIONS DEC-014、docs/planning/MACHINE-CONTRACT-COVERAGE.md）。

本 Addendum 仅收敛既有冻结事实，不新增能力、不改变任何功能。
