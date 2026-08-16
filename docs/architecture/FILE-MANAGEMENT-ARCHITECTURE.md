# File Management Architecture

```text
Browser / React
   |          \
   |           \ presigned multipart PUT
   v            v
Gateway      MinIO
   |
   v
iam-file-service
   |-- iam-authorization-service
   |-- iam_file MySQL
   |-- Outbox/RabbitMQ
   |-- Redis optional
   `-- MinIO metadata/control API
```

## Security rule
A physical object match never grants business access. Every logical file/reference/download remains tenant- and authorization-scoped.
