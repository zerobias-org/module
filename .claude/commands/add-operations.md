---
description: Add multiple operations to an existing module (one /add-operation pass per operation)
argument-hint: <module-identifier> <operation1,operation2,operation3>
---

Execute the Add Multiple Operations workflow for module: $1
Operations to add: $2 (comma-separated)

## How it runs

Split `$2` by comma into operations. For **each** operation:

1. Run the full `/add-operation $1 <op>` flow (6 gates).
2. Wait for completion before starting the next.
3. If a gate fails, stop the whole sequence — fix and retry that operation, then resume.

The first operation pays the cost of Phase 0 (credential check); subsequent operations skip it as long as the slot state hasn't changed.

## What stays the same across operations

- The gradle project path: `:<vendor>:[<suite>:]<service>`
- The slot's credentials (set once before starting)
- The base test files (`test/unit/<Tag>ProducerTest.test.ts`, `test/e2e/<name>.test.ts`) — operations are added to existing files, not new ones, unless a new tag is introduced

## What gets re-run per operation

- `:bundleSpec` + `:generate` (cheap, gradle caches)
- The relevant `:test` + `zbb testDirect` slices
- Mapper runtime validation pass for the new mapper
- `zbb gate --slot local` is run **once at the end** of the whole batch, not after each operation — that's the one expensive step. The intermediate gates per operation are checked by the gate skills, not by re-running `zbb gate` each time.

## Example

```
/add-operations github-github listWebhooks,getWebhook,createWebhook
```

Adds three operations sequentially. Each goes through Gates 1–5; Gate 6 (`zbb gate`) runs once at the end and writes the final `gate-stamp.json`. Commit one batch or a sequence of conventional commits (one per operation) — your call.

## Failure handling

- If operation N fails a gate, stop. Operations 1..N-1 are complete and committable; N is in progress.
- Resume after fixing: re-run `/add-operations $1 <opN>,<opN+1>,...` from the failing operation onward.
- Never run `zbb gate` until every operation has cleared Gates 1–5 — running it earlier just wastes a long build that you'll redo.
