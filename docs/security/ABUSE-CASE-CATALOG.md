# Abuse Case Catalog

Security tests should be implemented against these abuse paths:

1. Spoof tenant/user/resource/operation headers.
2. Forge JWT or use wrong issuer/audience/algorithm.
3. Reuse rotated refresh token.
4. Access another tenant's resource ID.
5. Access same-tenant resource outside Data Scope.
6. Submit hidden/unwritable fields manually.
7. Attempt raw-field reveal without REVEAL permission.
8. Share more operations or fields than grantor owns.
9. Use child share after parent revoke.
10. Access expired share while PowerJob is stopped.
11. Hit newly discovered but unmapped API.
12. Call `/internal/**` externally.
13. Force Authorization/Redis failures and ensure Fail Closed.
14. Replay HTTP/MQ/Job operations.
15. Exploit stale projection/checkpoint.
16. Abuse PUBLIC policy changes.
17. Attempt self-privilege escalation through Role/TeamRole editing.
18. Replay DLQ event.
19. Abuse SYSTEM_INTERNAL mapper mode.
20. Verify Audit/Explain do not leak protected internals.
