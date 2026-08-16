# SLO Baseline — V1.0

> Initial engineering targets. Calibrate with production-like load tests.

| SLI | Initial Target |
|---|---|
| Authorization Availability | 99.95% |
| Authorization L1 P99 | < 10 ms |
| Authorization Redis/ReadModel P99 | < 30 ms |
| Authorization DB Fallback P99 | < 150 ms |
| Login P95 | < 500 ms |
| Login P99 | < 1000 ms |
| Projection Freshness P95 | < 2 s |
| Projection Freshness P99 | < 5 s |
| Outbox Delivery P95 | < 2 s |
| Outbox Delivery P99 | < 10 s |
| High-Risk Audit Delivery | durable, 99.99% target |
| Immediate Revocation | next new protected request must not use stale ALLOW |

## Zero-Tolerance Security Properties
- Cross-tenant unauthorized ALLOW: 0
- Privilege escalation caused by stale permission: 0
- Expired/revoked share unauthorized ALLOW: 0
- Sensitive field raw leakage when denied/masked: 0
