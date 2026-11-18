---
name: api-spec
description: Rules for writing and reviewing a module's OpenAPI spec (api.yml) - operationId, x-method-name, tags, x-impl-name, pagination with the types-core $refs and the link header, sortBy/sortDir, schema and enum naming, date and date-time formats, response codes, and the redocly rules that fail the validate task. Use when creating or editing api.yml, adding an operation or schema, adding a missing enum value after "is not a valid <Enum>", or fixing a redocly lint or validate failure.
---

# api.yml rules

`api.yml` is the source of truth of a module: the generated interfaces, models, the hub SDK and the
operation manifest all come from it. Every rule below was checked against the gate-green specs in this
repo. When a rule and a real spec disagree, the spec is right; read these first:

| Spec | What it shows |
|---|---|
| `package/nist/cmvp/api.yml` | smallest complete spec: plain (profile-less) module, one paged list, one get, named enums, `date` / `date-time` / `url` / `uuid` |
| `package/readyplayerme/readyplayerme/api.yml` | connector: `sortBy`/`sortDir`, `201`/`204`, `requestBodies`, binary responses, `allOf` with a `Base` schema |
| `package/avigilon/alta/access/api.yml` | large read-only connector: root `tags`, per-operation `security`, `Summary`/`Info` schema families |
| `package/x12/x12/api.yml` | DataProducer module: paths are `$ref`s into the interface package, hand-written connection and health operations |

Per-rule examples with line references are in `references/rule-catalogue.md`. Read it when you need the
exact YAML shape for a rule.

## Quick check

Run the offline checker on the spec, then the real lint:

```bash
${CLAUDE_SKILL_DIR}/scripts/check-spec.sh package/<vendor>/[<suite>/]<product>/api.yml
zbb validate        # from the module directory; runs redocly lint
```

The script catches what redocly accepts but codegen or review does not (missing `link` header, stale core
`$ref`s, duplicate `operationId`, non-core parameter names). How `validate` fits in the build and the gate
is in the `build-lifecycle` and `gates` skills.

## 1. File header

```yaml
openapi: 3.0.3
info:
  version: 0.0.0
  title: '@zerobias-org/module-<vendor>-<product>'
  description: <Product name>
  x-impl-name: <name>
  x-product-infos:
    - $ref: './node_modules/@zerobias-org/product-<vendor>-<product>/catalog.yml#/Product'
```

- Use `openapi: 3.0.3`. Every spec in the repo uses it and the toolchain is only exercised with it.
- `info.title` is the npm package name of the module.
- Leave `info.version` alone in a pull request. Versions are not bumped inside PRs (see `git-and-sdlc`).
- `x-impl-name` names the generated top-level interface and the `src/<Name>Impl.ts` class. Casing in the
  file is free because codegen capitalises it: `cmvp` gives `interface Cmvp` / `CmvpImpl.ts`,
  `ReadyPlayerMe` gives `ReadyPlayerMeImpl.ts`, `Access` gives `AccessImpl.ts`.
- `x-product-infos` points at the product package's `catalog.yml#/Product`. That package has to be in the
  module's `package.json` dependencies, otherwise the `$ref` does not resolve and `validate` fails.
- Do not add root-level `servers:` or `security:`. No spec in the repo has them; the base URL and the
  credentials come from the connection profile at runtime (a plain module has neither). A
  `components.securitySchemes` block with per-operation `security:` is accepted (avigilon) and optional
  (cmvp and readyplayerme have none).

## 2. Operations: operationId, x-method-name, tags

Each operation carries three names, and they are different things:

| Field | Shape | Becomes |
|---|---|---|
| `tags: [<tag>]` | one tag, singular, lowerCamel (`certificate`, `identityProvider`, `avatar3d`) | `generated/api/<Tag>Api.ts` and the producer file `src/<Tag>Producer…Impl.ts` |
| `operationId` | lowerCamel verb + full resource, unique in the spec (`listCertificates`, `listUserRoles`) | the key in `generated/api/manifest.json`; what hub clients call |
| `x-method-name` | the short method on that tag's API (`list`, `get`, `listRoles`) | the TypeScript method you implement |

- `x-method-name` is normally shorter than `operationId` because the tag already names the resource:
  `listCertificates` / `list`, `getUser` / `get`, `listUserRoles` / `listRoles` under tag `user`,
  `listApplicationAssets` / `listAssets` under tag `application`. It may equal the `operationId` when no
  shorter name is clearer (avigilon `listAuditLogs` / `listAuditLogs`). Both forms pass the gate.
- `x-method-name` has to be unique within its tag, since it becomes a method on one class.
- Start `operationId` with a verb that says what the call does. `list`, `get`, `create`, `update` cover
  most cases; action verbs are fine when the provider's operation is an action (`addAssetToApplication`,
  `removeAssetFromApplication`, `equipAssetToAvatar`, `lockAssetForUser`).
- Give every operation a `summary`. Add a `description` when there is something the summary cannot say
  (cmvp explains that paging is applied client-side). Wording is free; there is no required verb.
- Declare tags at root level only if you want descriptions for them (avigilon does, cmvp and
  readyplayerme do not).
- Parameter order is load-bearing: codegen mirrors the `parameters:` list into positional TypeScript
  arguments. Append new optional parameters at the end so existing callers keep compiling (see the comment
  on `getChildren` in `package/interface/dataproducer/api.yml`).
- Connection operations: a TypeScript connector does not declare `connect` / `disconnect` in `api.yml`
  (readyplayerme and avigilon have none); the lifecycle comes from the connector tooling. A plain module
  such as cmvp has no connection lifecycle at all. `x12` declares `/connect`, `/disconnect`, `/metadata`
  and `/healthz` by hand because its implementation is not a TypeScript connector; do not copy that into a
  TypeScript module. For DataProducer modules see the `dataproducer-implementation` skill.

## 3. Pagination

A list operation is paged when all three pieces are present:

```yaml
      parameters:
        - $ref: '#/components/parameters/pageNumber'
        - $ref: '#/components/parameters/pageSize'
      responses:
        '200':
          description: A page of CMVP certificate summaries
          headers:
            link:
              $ref: '#/components/headers/pagedLinkHeader'
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/CertificateSummary'
components:
  parameters:
    pageNumber:
      $ref: './node_modules/@zerobias-org/types-core/schema/pageNumberParam.yml'
    pageSize:
      $ref: './node_modules/@zerobias-org/types-core/schema/pageSizeParam.yml'
  headers:
    pagedLinkHeader:
      $ref: './node_modules/@zerobias-org/types-core/schema/pagedLinkHeader.yml'
```

- The response header is named `link` (singular). It is the switch for codegen: with it, the producer
  method is `list(results: PagedResults<T>): Promise<void>` and the caller-facing method returns
  `Promise<PagedResults<T>>`; without it, codegen emits `list(pageNumber, pageSize): Promise<Array<T>>`
  and the paging metadata is lost. Redocly does not notice a missing header, so check it yourself.
- The response schema stays a plain `type: array` of the item schema. Do not wrap it in an envelope with
  `items` / `total`; `PagedResults` carries that.
- Reference the core files once under `components` and use the local `#/components/...` name in
  operations. The component key is your choice (`pageNumber` in cmvp, `pageNumberParam` in avigilon,
  `pagedLink` in readyplayerme); the parameter's wire name comes from the core file.
- Core files are one file per item. There is no `params.yml` or `headers.yml`, and no `linksHeader`.

| Core file (`./node_modules/@zerobias-org/types-core/schema/…`) | Wire name | Notes |
|---|---|---|
| `pageNumberParam.yml` | `pageNumber` | int32, 1-indexed, default 1 |
| `pageSizeParam.yml` | `pageSize` | int32, default 50 |
| `pageTokenParam.yml` | `pageToken` | string cursor; use with `pageSize` for cursor paging (`package/microsoft/azure/azureresourcegraph/api.yml`) |
| `sortByParam.yml` | `sortBy` | array of column names |
| `sortDirParam.yml` | `sortDir` | array of `sortDirection.yml` values, parallel to `sortBy` |
| `pagedLinkHeader.yml` | header `link` | first / prev / next / last links |

- Use these names for paging and sorting, not `limit`, `offset`, `orderBy` or `orderDir`, even when the
  provider uses those; the producer translates. The platform and `PagedResults` only know the core names.
- Offer `pageNumber` + `pageSize` when the provider pages by number or does not page at all (cmvp pages
  client-side), `pageSize` + `pageToken` when it pages by cursor.
- How the producer fills `PagedResults` belongs to the `implementation` skill.

## 4. Parameters

- Define each parameter once under `components.parameters` and `$ref` it from operations. All three
  hand-written specs do this for every parameter.
- Name path parameters after the thing they identify (`certificateNumber`, `assetId`, `organizationId`),
  not `id`, so a path with two of them stays readable.
- A provider scope in the path is fine when the provider's API has it
  (`/organizations/{organizationId}/users`, `/applications/{appId}/assets`).
- When two operations need the same wire name with different meaning or schema, give the components
  different keys and keep the wire `name` (readyplayerme: `avatar3DId`, `avatar2DId`, `avatarMetadataId`
  all have `name: avatarId`).
- Array query parameters use `style: form` with `explode: true` (readyplayerme `type`, `gender`, `ids`).

## 5. Schemas

- Schema names are PascalCase; property names are camelCase. No snake_case key appears in any hand-written
  spec; when the provider uses snake_case, rename in the mapper (see the `mapping` skill).
- One suffix list, taken from what the specs ship:

| Suffix | Use | Seen in |
|---|---|---|
| none (`Certificate`, `User`) | the resource | all |
| `Summary` | a reduced view: the row a list returns when the detail call returns more, or the short form nested inside another resource | `CertificateSummary`, `GroupSummary` |
| `Info` | the resource plus extra detail, usually `allOf` the plain schema; what a get returns when the list returns the plain one | `UserInfo`, `AssetInfo`, `RoleInfo` |
| `Base` | shared properties, combined with `allOf` | `AssetBase` |
| `Ref` | a pointer to another resource | `OrganizationRef`, `ZoneSiteRef` |
| `Request` / `Response` | a request body, or a provider envelope returned as-is | `CreateAssetRequest`, `AssetResponse`, `TokenResponse` |

- Prefix nested or context-specific types with the owning resource (`EntryZone`, `UserCredential`,
  `CmvpStatus`) so a later operation cannot collide with them. Before adding a schema, search the spec for
  the name.
- List the properties that are always present under `required` and leave the rest optional. When the
  source can withhold a value, make it optional and say so in the description (cmvp `CertificateSummary`).
  `nullable` is not used in any gate-green spec; an absent property is the convention.
- Describe every schema and every property that is not self-evident. The description is what a user of
  the generated SDK and of the catalog sees.
- `$ref` a named schema for anything reused; small one-off nested objects may stay inline (readyplayerme
  `data:` wrappers, x12 `HealthStatus`). To share properties use `allOf` with a `Base` schema
  (readyplayerme `Asset`, `AssetInfo`).
- Identifier types follow the provider: mostly `string`, `integer` where the provider's id is numeric
  (avigilon `IdentityProvider.id`).

## 6. Enums

- Prefer a named enum schema, prefixed with the resource or module (`CmvpStatus`, `AssetType`,
  `Avatar2DPose`), referenced by `$ref`. Inline `enum:` on a property also passes (avigilon
  `User.status`), but a named schema gives one generated class to map into.
- Values are the provider's values as they appear on the wire (`outfit`, `power-stance`, `'256'`, `a`).
  When the source has no machine values (cmvp scrapes HTML labels), pick `UPPER_SNAKE` constants and map
  the labels in the mapper.
- The generated enum throws `IllegalArgumentError("<value> is not a valid <Enum>")` for a value that is
  not listed. That is intended: it is how a new provider value gets noticed. When it happens:
  1. Open the provider's current API documentation for that enum.
  2. Add every documented value that is missing, not only the one that failed.
  3. Keep the existing values, even ones the documentation no longer lists; removing a value breaks
     consumers that already hold it.
  4. Regenerate (see `build-lifecycle`) and re-run the tests.
  Do not make the mapper tolerant instead (no pass-through string, no `try/catch`, no drop-with-warning);
  the `mapping` skill holds the mapper side of this rule.
- `x-enum-descriptions` (a list parallel to `enum`) is optional; avigilon uses it for cryptic values
  (`a`, `s`, `i`), the other specs do not.

## 7. Formats

`format` decides the generated TypeScript type, so set it whenever the value has one:

| Spec | Generated type (`@zerobias-org/types-core-js`) | Notes |
|---|---|---|
| `type: string`, `format: date` | `DateFormat` | calendar date, ISO `YYYY-MM-DD` only; a provider format such as `MM/DD/YYYY` is converted in the mapper |
| `type: string`, `format: date-time` | `DateTime` | timestamp |
| `type: string`, `format: url` | `URL` | |
| `type: string`, `format: uuid` | `UUID` | |
| `type: string`, `format: binary` | file / binary response body | used under non-JSON media types (`image/png`, `model/gltf-binary`) |
| `type: integer`, `format: int32` / `int64` | `number` | |

Use `date` for a value with no time of day and `date-time` for an instant; choosing `date-time` for a bare
date invents a midnight in some time zone. The full format-to-class table and how to build the values are
in the `mapping` skill.

## 8. Responses

- Declare the success code the provider returns: `'200'` with a body, `'201'` for a create that returns
  the created object, `'204'` with a `description` and no `content` for a call that returns nothing
  (readyplayerme `equipAssetToAvatar`). All three are gate-green.
- Do not list `4xx` / `5xx` responses on ordinary operations. cmvp, readyplayerme and avigilon declare
  none: errors are thrown as core error types by the implementation (see `implementation`), and the
  redocly rule that would ask for them is switched off. `x12` declares `400` / `500` / `503` on its
  hand-written connection and health operations, which is specific to that module.
- Response keys are quoted strings (`'200':`).
- Non-JSON bodies use their real media type with `type: string`, `format: binary`.

## 9. What fails `validate`

`validate` runs `@redocly/cli lint api.yml` with a config that build-tools writes to
`generated/redocly.yaml` (read that file after a run for the current rule set). It extends redocly's
`minimal` ruleset and sets:

| Rule | Level | Typical cause in a module spec |
|---|---|---|
| `struct` | error | not valid OpenAPI 3.0: misspelled or misplaced key, response without `description`, wrong type for a field, bad indentation |
| `no-unresolved-refs` | error | `$ref` to a component that does not exist, to a core file that does not exist (`params.yml`, `headers.yml`), or to a package that is not installed / not in `package.json` |
| `no-invalid-media-type-examples` | error | an `example` under a media type that does not match its schema |
| `no-empty-servers`, `security-defined`, `operation-4xx-response`, `no-unused-components` | off | so: no `servers`, no security requirement, no 4xx responses, and unused components do not fail |

`node_modules` `$ref`s resolve only after the module's dependencies are installed, so an "unresolved ref"
on a core or product file on a fresh checkout usually means install first, not a spec bug.

Redocly passing is not enough: it does not check the `link` header, core parameter names, naming, or
enum completeness. Those are sections 2 to 6 and the checker script.

## After editing api.yml

Regenerate and fix the compile errors the change causes in `src/` and the tests; never edit files under
`generated/`. The commands and their order are in `build-lifecycle`; the stamp consequences of a spec
change are in `gates`. A connector's `connectionProfile.yml` is a separate file with its own skill
(`connection-profile`); adding an operation is not a reason to change it.
