# DataProducer review: anti-patterns and completion checklist

Read this before the final gate run, and when reviewing someone else's DataProducer implementation.

## Anti-patterns

Each of these has broken a real implementation.

| Avoid | Do instead | Why |
|---|---|---|
| Positional model constructors padded with `undefined` | Typed object literals with named fields | A long positional call cannot be reviewed and mis-orders same-typed arguments without a compiler error |
| Collection-type lists duplicated across builder, dispatcher, children and schema lookup | One `COLLECTIONS` registry, everything derived from it | The copies drift and a collection becomes reachable in one place but not another |
| Hardcoded `collectionSize` or child counts | Derive from the registry or the API | A hardcoded number is wrong as soon as the data changes |
| `results.items = []` for an unsupported search | Throw `UnexpectedError` | A consumer cannot tell "no matches" from "not supported" |
| Fetch everything, then filter, to fake a scoped listing | Scope server-side, or expose the broader collection | Filtering after the fetch returns short pages without any signal |
| Holding the raw client in `DataProducerImpl` | Delegate through the module Impl's tag producers | The wrapped producers already validate, map errors and paginate |
| `Promise<any>` returns in binary or document mappers | Type the envelope | `any` hides shape errors until a consumer hits them |
| Dead ID prefixes kept "for compatibility" | Delete unreachable branches | They make the grammar ambiguous for the next prefix added |
| Ad-hoc schema IDs (`acme_org_schema`) | Canonical IDs per the interface package's `documentation/SchemaIds.md` | `getSchema` round-trips these IDs and consumers store them |
| Pinning the interface with `^` or "latest" | Exact pin matching `bundle/package.json` | The bundle pin is the repo-wide contract |

## Completion checklist

- [ ] `@zerobias-org/module-interface-dataproducer` is exact-pinned to the version in `bundle/package.json`; `@zerobias-org/data-utils` is a dependency
- [ ] All 15 paths are `$ref`'d against the interface package's `dist/` spec; the shared components are re-exported
- [ ] `zbb generate` passes; the six `*ProducerApi` interfaces are present in `generated/api/`
- [ ] `src/impl/dataproducer/` holds the five files; no other file implements DataProducer operations
- [ ] The ID grammar is documented in the `DataProducerImpl` docblock; every emitted ID round-trips
- [ ] Every `SCHEMA_IDS` entry is a canonical ID; `getSchema` round-trips each one
- [ ] Every object is built as a typed literal, with `objectClass` and the class-specific fields from the table in `SKILL.md`
- [ ] The error policy table is honored: every unimplemented write throws, and nothing is a silent no-op
- [ ] Six getters on the module Impl, sharing one memoized `DataProducerImpl`
- [ ] The DataProducer test blocks exist, including the child-ID round-trip and one assertion per declined write
- [ ] `zbb gate` passes after the new files are staged; `gate-stamp.json` is committed
