# SPEC 14 — Dynamic Data Permission Engine

## V1.0 Frozen Baseline

MyBatis + JSqlParser AST 重写，禁止字符串拼接。
支持 SELECT/COUNT/Pagination/JOIN，简单 UPDATE/DELETE；不确定 SQL Fail Closed。
SHARED 通过业务本地 ACL Projection；大 Team 可用本地主体团队 Projection。

---

## Final Consistency Addendum - 本地主体团队 Projection 一致性边界

本地主体团队 Projection 仅为 TEAM / TEAM_AND_CHILDREN 数据范围求值的读优化，不产生任何新授权：

- 新鲜度以 Permission Version 为准：团队层级、成员或角色变更必须在成功返回前单调推进受影响 subject 的 permission version（SPEC 38 §3）。
- 投影落后于 subject 当前 permission version、或投影不可用时，不得以该投影为准放大数据可见范围，必须按 ADR-0008 Fail Closed。
- SHARED 分支不依赖该投影，始终执行 SPEC 16 Addendum 定义的 share epoch checkpoint 栅栏；两种机制不可互相替代。

本 Addendum 仅收敛既有冻结事实，不改变任何功能。
