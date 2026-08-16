# Service Interaction Matrix

| Caller | Callee | Mode | Purpose | Notes |
|---|---|---|---|---|
| Gateway | Authorization | Sync | API mapping + coarse authorization | Runtime critical |
| Auth | Identity | Sync | Resolve user / credential / status | Login path |
| Organization | Identity | Sync/Projection | Validate user existence | Admin write only |
| Sharing | Authorization | Sync | Grantability validation | Share create/re-share |
| Sharing | Business Metadata Provider | Sync | Instance/owner validation | Share create |
| Business Service | Authorization | Sync | Fine-grained authorization | Runtime critical |
| Identity | Authorization | Event | User/role projection | Outbox |
| Organization | Authorization | Event | Team/team-role projection | Outbox |
| Sharing | Authorization | Event | Share/ACL read model | Outbox |
| Sharing | Business ACL Projection | Event | Local row visibility | Outbox |
| Core Services | Audit | Event | Audit/security | Async |
| Job | Domain Service | Sync | Scheduled command | Application API only |

| File Service | Authorization | Sync | Upload/reference/preview/download authorization | Security critical |
| File Service | MinIO | Sync | Multipart control, head, presign, lifecycle | Storage critical |
| File Service | Audit | Event | File operation/security audit | Async |
| File Service | Scan Worker | Event/Async | Malware/content scan | File availability |
