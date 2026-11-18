---
description: Add multiple operations to an existing module (one /add-operation pass per operation)
argument-hint: <module-identifier> <operation1,operation2,operation3>
---

Execute the Add Multiple Operations workflow for module: $1
Operations to add: $2 (comma-separated)

## How it runs

Split `$2` by comma into operations. For **each** operation:

1. Run the `/add-operation $1 <op>` flow through Gates 1–5.
2. Wait for completion before starting the next.
3. If a gate fails, stop the whole sequence — fix and retry that operation, then resume.

The first operation pays the cost of Phase 0 (connector: secret check; plain module: nothing to check); subsequent operations skip it as long as the slot state hasn't changed.

## What stays the same across operations

- The gradle project path: `:<vendor>:[<suite>:]<service>`
- The module kind (connector or plain) and, for a connector, the slot's secret
- The base test files (the tag's unit test file under `test/unit/`; for a connector `test/e2e/<name>.test.ts`) — operations are added to existing files, not new ones, unless a new tag is introduced

## What gets re-run per operation

- `zbb generate` (cheap, gradle caches)
- `zbb compile`, `zbb test`, and for a connector `zbb testDirect`
- The `mapping` skill's field check for the new mapper
- `zbb gate` runs **once at the end** of the whole batch, in the order the `gates` skill gives, because it is the one expensive step. Gates 1–5 per operation are checked with the single tasks above.

## Example

```
/add-operations avigilon-alta-access listWebhooks,getWebhook,createWebhook
```

Adds three operations sequentially. Each goes through Gates 1–5; Gate 6 (`zbb gate`) runs once at the end and writes the final `gate-stamp.json`. One batch commit or one conventional commit per operation: the owner decides, so ask before committing (`git-and-sdlc` skill).

## Failure handling

- If operation N fails a gate, stop. Operations 1..N-1 are complete; N is in progress.
- Resume after fixing: re-run `/add-operations $1 <opN>,<opN+1>,...` from the failing operation onward.
- Run `zbb gate` only after every operation has cleared Gates 1–5: an earlier run is a long build that has to be repeated, because any later edit invalidates its stamp.
