# Dynamic Data Producer Interface — AI/Developer Notes

This file is the rules layer for AI working on this module. The interface
specification lives in [`documentation/`](documentation/) and is the source
of truth — the rules below are about *how* to work on this codebase, not
*what* the interface is.

> Consumer-facing overview: see [`README.md`](README.md).

## Module One-Liner

A unified browse/query/invoke interface for heterogeneous data sources
(databases, directories, filesystems, REST APIs). Everything is an Object
with one or more capability classes (`container`, `collection`, `function`,
`document`, `binary`).

## Where Things Live

| Concern                         | Source of truth                                               |
|---------------------------------|---------------------------------------------------------------|
| Object model, classes, ops      | [`documentation/Concepts.md`](documentation/Concepts.md)      |
| Schema ID format, `getSchema`   | [`documentation/SchemaIds.md`](documentation/SchemaIds.md)    |
| Picking `dataType`              | [`documentation/CoreDataTypes.md`](documentation/CoreDataTypes.md) |
| RFC4515 + per-backend translation | [`documentation/FilterSyntax.md`](documentation/FilterSyntax.md) |
| Error model                     | [`documentation/Errors.md`](documentation/Errors.md)          |
| Per-source mappings             | [`documentation/mappings/`](documentation/mappings/)          |
| On-the-wire contract            | [`api.yml`](api.yml)                                          |

If `api.yml` and the docs disagree, **the docs are authoritative** — fix
`api.yml` to match, not the other way around.

## Hard Constraints — Non-negotiable

These exist because bad examples here propagate into real implementations.

1. **Schema IDs use the canonical form.** Always
   `schema:{type}:{catalog}.{schema}.{name}[:{direction}]`. Never invent
   shortcut names like `customers_schema` in examples — even in throwaway
   illustrations. See `documentation/SchemaIds.md`.

2. **Use core DataTypes; never custom `format:` strings as a substitute.**
   `dataType: string` + `format: email` is wrong; `dataType: email` is
   right. Same for `geoCountry`, `geoSubdivision`, `phoneNumber`, `url`,
   `decimal`, `byte`, `mimeType`, `ipAddress`, `date-time`, etc. See
   `documentation/CoreDataTypes.md`.

3. **No transport metadata at the interface level.** The base DataProducer
   has no `httpMethod`, `httpPath`, `httpHeaders`, `timeout`, `retryPolicy`,
   `/rest` operation, or `RestRequest`/`RestResponse` schemas. If an
   implementation needs HTTP-specific routing, it lives on a sub-interface
   (e.g. `HttpModule`). Function `timeout` is not a transport concern — if a
   particular function definer needs a timeout argument, they put it in their
   `inputSchema`.

4. **Schemas are referenced, never inlined.** Object metadata uses Schema IDs
   (strings). Full schema bodies come from `getSchema`. Never define a
   schema's structure inline in an Object.

5. **Filters are RFC4515.** Don't invent JSON filter shapes. Producers that
   can't filter natively translate via `@zerobias-org/util-lite-filter`.

6. **Never modify generated/.** Files under `generated/` and `dist/` come
   from `api.yml` and the codegen pipeline. Changes go in the source.

## Workflow

This module follows the parent meta-repo's flat, two-level agent structure
(see `../CLAUDE.md`). For this module specifically:

| Step                              | Delegate to                                |
|-----------------------------------|--------------------------------------------|
| API specification design          | `api-researcher`                           |
| Schema/spec validation            | `api-reviewer`                             |
| Type generation                   | `build-validator`                          |
| Unit test review                  | `ut-reviewer`                              |
| Integration test review           | `it-reviewer`                              |
| Build review                      | `build-reviewer`                           |

Memory and intermediate artifacts go in
`.claude/.localmemory/{workflow}-{module}/` and are never committed.

## Build Gotcha — Redocly $ref in Arrays

Redocly CLI's `bundle --dereferenced` does **not** dereference `$ref` inside
arrays (issues [#1012](https://github.com/Redocly/redocly-cli/issues/1012),
[#1602](https://github.com/Redocly/redocly-cli/issues/1602)). The
`x-product-infos` array in `api.yml` uses a `$ref` that needs resolving for
downstream tooling.

The post-processing script `dereference-product-infos.sh` runs after the
bundle step and uses `yq` to inline the value. It's wired into the
`generate:inflate` script. If you change how `x-product-infos` is shaped or
where it points, update the script accordingly.

## Doc Edits — Do Not Drift

When editing any of `README.md`, `documentation/*.md`, or `api.yml`:

- **`README.md`** is the *consumer* entry point. Keep it short, link into
  `documentation/` for depth. Do not paste spec content in.
- **`documentation/*.md`** are *canonical*. Spec content lives here once.
- **This file (`CLAUDE.md`)** is *rules*. Keep it short.
- **`api.yml`** mirrors the canonical docs. If you change the contract, you
  change both.

The auto-generated reference in `README.md` (everything after the
`<!-- START doctoc -->` marker) regenerates from `api.yml`. Don't hand-edit
it.

## Testing

- Unit tests run with `mocha` (configured in `package.json`).
- Type checking via `tsc`.
- The interface itself has no integration tests — those live in implementing
  modules.

## See Also

- Parent meta-repo workflow: `../CLAUDE.md`
- Parent module conventions: `../../CLAUDE.md` (the `org/module/` doc)
