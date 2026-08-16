# SPEC 16 — Resource Sharing & Cross-Team ACL Engine

## V1.0 Frozen Baseline

ResourceShare Aggregate + 状态机 WAITING/ACTIVE/EXPIRED/REVOKED。
支持 USER/TEAM/ROLE/TEAM_ROLE、字段收敛、转分享深度、Parent Share、Owner Transfer Policy。
通过 Outbox/MQ 同步 ACL Projection。
---

## Final Consistency Addendum — Share Revocation Security Fence

Final V1.x rule:

```text
Share create / permission expansion:
Sharing local transaction + Outbox is sufficient.
Temporary propagation delay may cause under-grant, never pre-authorized over-grant.

Share revoke / permission reduction:
Use a short selective Seata global transaction:
  1. Sharing DB: revoke/reduce share and increment iam_share_projection_epoch
  2. Authorization DB: increment iam_share_security_epoch / related permission version
```

If this short security transaction cannot be committed, the revoke/reduction command fails rather than reporting a successful but unsafe partial revoke.

Runtime:

```text
Authorization produces expectedShareEpoch.
Business local ACL projection exposes last contiguous checkpoint.
checkpoint < expectedShareEpoch => SHARED branch DENY / fail closed.
checkpoint > authorization plan epoch => refresh authorization plan; if unavailable => DENY.
```

Expiration remains safe without Seata because every runtime decision enforces `start <= now < expire`; PowerJob only converges persisted status.

This addendum supersedes any earlier wording implying that a cross-service revoke can be completed by one local database transaction.
