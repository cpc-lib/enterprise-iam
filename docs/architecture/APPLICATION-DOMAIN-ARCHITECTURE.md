# Application & Domain Architecture

```text
REST / Internal RPC / MQ / Job
              |
              v
      Application Service
       /      |       \
      /       |        \
 AuthZ     Aggregate   Query Port
            /   \
           /     \
     Repository   Domain Event
          |            |
          v            v
 Infrastructure     Outbox Mapper
          |
          v
      MySQL/Redis/MQ
```

## Key rules
- Application Service owns orchestration and local transaction boundaries.
- Domain owns invariants and state transitions.
- Aggregates never depend on Spring MVC, MyBatis, Redis, MQ or Feign.
- Domain Events are mapped to versioned Integration Events before entering Outbox.
- Commands use trusted TenantContext/ActorContext; clients do not choose trusted tenant/user identities.
- Queries use dedicated Query Ports instead of loading giant aggregates.
