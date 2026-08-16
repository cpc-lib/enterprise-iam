# SPEC 02 — Domain & Bounded Context

## V1.0 Frozen Baseline

Bounded Context：Auth、Identity、Organization、Authorization、Sharing、Audit、Job。
禁止跨服务直接访问数据库；跨域读取通过内部 API、事件和 Projection。

---

## Final Consistency Addendum - File Context Elevation

本基线的 Bounded Context 清单由 SPEC 36 §3 / §13 / §16 扩展为八个：

```text
Auth、Identity、Organization、Authorization、Sharing、File、Audit、Job
```

- 服务：`iam-file-service`；独立数据库：`iam_file`（见 docs/database/TABLE-OWNERSHIP.md）。
- File 上下文拥有：logical file / physical object / multipart upload session / part state / instant upload / business reference / download-preview policy / scan state / retention-reconcile 元数据。
- MinIO 仅是存储，永远不是授权来源。

本 Addendum 仅回写 SPEC 36 已冻结事实，取代本文件早于 SPEC 36 的七上下文措辞，不改变任何功能。
