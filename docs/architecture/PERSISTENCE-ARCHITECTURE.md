# Persistence Architecture

```text
REST / MQ / Job Adapter
        |
        v
Application Service
        |
        +-------------------+
        |                   |
        v                   v
Domain Repository       Query Port
        ^                   ^
        |                   |
MyBatis Repository     Query Mapper
Adapter                / Read Model SQL
        |
        v
MyBatis Mapper / DO
        |
        v
Tenant + Data Permission + Field Update Guard
        |
        v
MySQL
```

## Rules
- Domain does not depend on MyBatis/MyBatis-Plus.
- Controller/Application never inject Mapper directly.
- Large-table pagination uses cursor/seek.
- Authorization runtime must avoid N+1 and synchronous cross-service fan-out.
- Protected business queries are filtered in SQL, never by Java post-filtering.
