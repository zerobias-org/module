---
name: module-exports
description: src/index.ts shape and package.json export wiring for ESM Hub modules. Use when defining or reviewing a module's public API.
---

# Module exports

The generator scaffolds the canonical `src/index.ts`. Public API surface is small on purpose: a factory function, the generated API + model types, and nothing else unless a real consumer needs it.

## Canonical `src/index.ts`

```typescript
import { <Class>Impl } from './<Class>Impl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

export function new<Class>() {
  return new <Class>Impl();
}
```

Rules baked into that shape:

- **ESM imports end in `.js`** — `./<Class>Impl.js`, `../generated/api/index.js`, etc. Required by `module: NodeNext`.
- **Named exports only** — no `export default`. The factory is `export function new<Class>()`.
- **Wildcards from `generated/`** — re-export everything the codegen produced. Consumers need `ConnectionProfile`, the API interfaces, resource types, enums. Cherry-picking causes consumer churn every time we add a model.
- **Order matches the generator template** — generated re-exports above the factory. Not a load-bearing rule, just consistency.

The generator's factory has no explicit return type. That's fine — TypeScript infers `<Class>Impl`. Add a return type only if you also change what the factory returns (e.g. wrap it in something that satisfies an interface).

## `package.json` wiring

The generator's `package.template.json` sets:

```json
{
  "type": "module",
  "main": "dist/src/index.js",
  "files": [
    "dist",
    "generated/api/manifest.json",
    "api.yml",
    "connectionProfile.yml"
  ]
}
```

- `"type": "module"` — module is ESM. Non-negotiable.
- `"main"` — `dist/src/index.js` because TypeScript's `outDir: dist` preserves the `src/` prefix.
- `"files"` — what ends up in the published tarball. Everything else (`node_modules`, `test/`, `generated/api/index.*` minus the manifest, `hub-sdk/`) stays local.
- `"exports"` — not used at the top level today. The codegen-emitted hub-sdk uses `exports`, the module itself relies on `main`.

`connectionProfile.yml` is in `files` only for connector modules; the generator omits it from `package.template.json` when `--moduleType=plain`.

## What to add (when actually needed)

The factory + generated wildcards cover ~95% of modules. Add the rest only when a real consumer needs it.

```typescript
// Custom error class — only if you throw it and want consumers to catch by type
export { ServiceSpecificError } from './errors.js';

// Custom profile extension — only if the connection profile is augmented in code
export type { ServiceProfile } from './<Class>Client.js';

// Impl class — only if a consumer needs `instanceof` or wants to extend it
export { <Class>Impl } from './<Class>Impl.js';
```

Each addition needs:
- A real, named consumer (e.g. another module, an integration test in another repo)
- The `.js` extension on the relative import
- A line in the PR explaining the consumer

## What NOT to export

These are implementation details; consumers should not depend on them:

- Producer classes (`<Tag>ProducerImpl`) — internal
- HTTP client wrappers (`<Class>Client`) — internal, leaks `axios` types
- Mappers (`src/mappers/*`) — internal, generated-type-shaped
- Helpers / interceptors / retry utilities — internal

If a test legitimately needs one of these, import the file directly:

```typescript
// In test/unit/UserMapperTest.ts
import { mapUser } from '../../src/mappers/userMapper.js';
```

That keeps the file private to the module's tarball while still letting in-repo tests reach it.

## Consumer usage shape

This is what consumers see when they do `import … from '@zerobias-org/module-<vendor>-<service>'`:

```typescript
import { new<Class>, ConnectionProfile, <Tag>Api, <Resource> }
  from '@zerobias-org/module-<vendor>-<service>';

const api = new<Class>();
const profile: ConnectionProfile = { /* … */ };
await api.connect(profile);

const tagApi: <Tag>Api = api.get<Tag>Api();
const resources: <Resource>[] = await tagApi.list<Resource>();
```

Everything in that snippet comes from the four exports above (factory + two wildcards). If a consumer needs anything else, that's a signal that either the API spec is missing an operation or we're exporting the wrong layer.

## Validation

```bash
# Factory exists, named, prefixed `new`
grep -nE "^export function new[A-Z]" src/index.ts \
  && echo "✓ factory" || echo "✗ no factory function"

# No default exports
! grep -n "^export default" src/index.ts \
  && echo "✓ no default exports" || echo "✗ default export present"

# Generated re-exports present
grep -nE "export \* from ['\"]\.\./generated/(api|model)/index\.js['\"]" src/index.ts | wc -l
# Expect: 2

# ESM extensions on all relative imports
grep -rnE "from '\.\.?\/[^']*'" src/ | grep -v "\.js'" \
  && echo "✗ missing .js extension" || echo "✓ ESM imports OK"
```

## Common issues

- **`Cannot find module './<Class>Impl'`** at compile or test time — missing `.js` extension.
- **`'newFoo' is not exported from …`** — factory was renamed or moved. Use `new<Class>` exactly; that's what the generator emits and what consumers/test fixtures grep for.
- **Consumer can't import `ConnectionProfile`** — `export * from '../generated/model/index.js'` was removed or replaced with a cherry-pick.
- **Dataloader fails with "missing manifest"** — `generated/api/manifest.json` not in `files`. Restore the canonical `files` array.

## Related

- @.claude/skills/typescript-config/SKILL.md — ESM compile rules
- @.claude/skills/scaffolding/SKILL.md — generator output reference
- @.claude/skills/impl-wrapper/SKILL.md — Impl class structure (consumed by the factory)
