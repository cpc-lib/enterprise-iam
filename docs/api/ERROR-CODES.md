# IAM Error Code Catalog — V1.1 Baseline

| Code | HTTP | Meaning |
|---|---:|---|
| IAM_AUTH_INVALID_CREDENTIALS | 401 | Invalid login credentials |
| IAM_AUTH_TOKEN_EXPIRED | 401 | Access token expired |
| IAM_AUTH_REFRESH_REUSED | 401 | Rotated refresh token reused |
| IAM_AUTH_USER_DISABLED | 401 | User disabled |
| IAM_AUTH_SESSION_REVOKED | 401 | Session revoked or invalidated |
| IAM_AUTH_SESSION_EXPIRED | 401 | Session absolute or idle limit reached |
| IAM_AUTH_SESSION_LIMIT_REACHED | 409 | Maximum concurrent login-session limit reached |
| IAM_AUTH_RATE_LIMITED | 429 | Authentication rate limit reached |
| IAM_AUTHZ_DENIED | 403 | Authorization denied |
| IAM_AUTHZ_UNKNOWN_API | 403 | Protected API has no active mapping |
| IAM_AUTHZ_RESOURCE_DISABLED | 403 | Resource is disabled |
| IAM_AUTHZ_OPERATION_DISABLED | 403 | Operation is disabled |
| IAM_AUTHZ_STALE_VERSION | 403 | Permission version is stale |
| IAM_AUTHZ_DEPENDENCY_UNAVAILABLE | 503 | Security dependency unavailable; request failed closed |
| IAM_AUTHZ_DATA_SCOPE_DENIED | 403 | Data scope denied |
| IAM_FIELD_WRITE_DENIED | 403 | Field is not writable |
| IAM_FIELD_UNKNOWN | 403 | Unknown protected field |
| IAM_SHARE_OPERATION_ESCALATION | 403 | Share operation exceeds grantor rights |
| IAM_SHARE_FIELD_ESCALATION | 403 | Share field rights exceed grantor rights |
| IAM_SHARE_PARENT_INVALID | 409 | Parent share invalid |
| IAM_IDEMPOTENCY_KEY_REQUIRED | 400 | Idempotency key required |
| IAM_IDEMPOTENCY_KEY_CONFLICT | 409 | Same key with different request |
| IAM_VERSION_CONFLICT | 409 | Optimistic version conflict |
| IAM_TENANT_CONTEXT_INVALID | 401 | Trusted tenant context missing or invalid |
| IAM_RESOURCE_NOT_FOUND | 404 | Resource absent or deliberately hidden |
| IAM_REQUEST_INVALID | 400 | Syntax or request validation failed |
| IAM_BUSINESS_RULE_VIOLATION | 422 | Business invariant rejected the command |
| IAM_ORG_HIERARCHY_CYCLE | 422 | Organization or Team parent would create a cycle |
| IAM_TEAM_ROLE_SCOPE_INVALID | 403 | TeamRole does not belong to the subject Team |
| IAM_DATA_SCOPE_UNSUPPORTED_QUERY | 403 | Query shape cannot be safely scoped |
| IAM_DATA_SCOPE_SCHEMA_INVALID | 422 | Resource data schema contains invalid metadata |
| IAM_FIELD_QUERY_DENIED | 403 | Field cannot be used for filter sort group or export |
| IAM_SHARE_RESHARE_ESCALATION | 403 | Child share exceeds parent or resharer rights |
| IAM_SHARE_PROJECTION_STALE | 503 | Shared ACL projection is below required epoch |
| IAM_FILE_TOO_LARGE | 413 | File or part exceeds tenant policy |
| IAM_FILE_QUOTA_EXCEEDED | 429 | Tenant logical or reserved quota exceeded |
| IAM_FILE_UPLOAD_EXPIRED | 409 | Upload session expired |
| IAM_FILE_PART_MISMATCH | 422 | Reported part differs from authoritative storage state |
| IAM_FILE_HASH_MISMATCH | 422 | Whole-file integrity verification failed |
| IAM_FILE_SCAN_PENDING | 423 | File is not available until scan completes |
| IAM_FILE_INFECTED | 423 | File is infected and quarantined |
| IAM_FILE_SCAN_FAILED | 423 | Scan failed and access is denied |
| IAM_FILE_LEGAL_HOLD | 423 | File is protected by legal hold |
| IAM_FILE_REFERENCE_ACTIVE | 409 | Live file reference prevents purge |

Error responses MUST use the HTTP status declared in OpenAPI. A completed
authorization decision uses HTTP `200` with `decision=DENY`; transport or
authentication failures use the corresponding non-2xx status.
