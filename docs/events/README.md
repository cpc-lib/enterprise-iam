# Event Contracts

`asyncapi-code-phase-01.yaml` is normative for CODE PHASE 01.

`asyncapi-code-phase-02-v1.yaml` is normative for organization policy sharing
and file-security facts in CODE PHASE 02–05.

Rules:

- Event names include their major schema version.
- The envelope is validated before dispatch.
- Producers write business mutation and outbox record in one local transaction.
- Consumers deduplicate by `(consumer_name, event_id)` and apply aggregate
  versions monotonically.
- Breaking payload changes require a new event version and a dual-read or
  dual-publish migration plan.
