# SPEC 10 — Distributed Consistency

## V1.0 Frozen Baseline

优先级：Local TX → Local TX + Outbox → MQ → Saga → Seata。
Permission Version 负责撤权立即生效；MQ/Projection 负责传播和读性能；Reconcile 负责最终收敛。
