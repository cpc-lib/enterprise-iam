# iam-outbox-spring-boot-starter

Reusable MySQL 8 / Spring JDBC outbox foundation for IAM services.

- The writer joins an existing non-read-only business transaction; it never
  opens one.
- The disabled-by-default relay claims with short `READ_COMMITTED`
  `FOR UPDATE SKIP LOCKED` transactions and handles rows outside database locks.
- Terminal updates are guarded by claim owner and state.
- Delivery is at-least-once; consumers must be idempotent.
- Retry is bounded exponential with deterministic jitter. Unsupported schemas
  and deterministic poison data are dead-lettered.
- Micrometer tags contain only the bounded outcome enum.

Enable with `iam.outbox.relay.enabled=true` only after the owning service has
installed `sys_outbox_event`, registered at least one handler, and proved its
producer writes business state and the outbox row in the same physical
transaction. See SPEC 45 for the operational contract.
