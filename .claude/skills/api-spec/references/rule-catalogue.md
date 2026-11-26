# api.yml rule catalogue

Per-rule examples for the `api-spec` skill. Every example is copied from, or reduced from, a gate-green
spec in this repo; the source is named by operation or schema so it can be found with a search even after
the file changes. Open the source when the surrounding context matters.

## Contents

1. [Header and info block](#1-header-and-info-block)
2. [Operation naming](#2-operation-naming)
3. [Paged list, page-number style](#3-paged-list-page-number-style)
4. [Paged list, cursor style](#4-paged-list-cursor-style)
5. [Sorting and filter parameters](#5-sorting-and-filter-parameters)
6. [Path parameters and component keys](#6-path-parameters-and-component-keys)
7. [Schema families: plain, Summary, Info, Base, Ref](#7-schema-families)
8. [Request bodies and envelopes](#8-request-bodies-and-envelopes)
9. [Enums](#9-enums)
10. [Formats and generated types](#10-formats-and-generated-types)
11. [Response codes and bodies](#11-response-codes-and-bodies)
12. [Security schemes](#12-security-schemes)
13. [DataProducer specs](#13-dataproducer-specs)
14. [Redocly failures and their fixes](#14-redocly-failures-and-their-fixes)
15. [Rules from older guidance that the specs do not support](#15-rules-the-specs-do-not-support)

---

## 1. Header and info block

`package/nist/cmvp/api.yml` (plain module):

```yaml
openapi: 3.0.3
info:
  version: 0.0.0
  title: '@zerobias-org/module-nist-cmvp'
  description: Cryptographic Module Validation Program
  x-impl-name: cmvp
  x-product-infos:
    - $ref: "./node_modules/@zerobias-org/product-nist-cmvp/catalog.yml#/Product"
```

`x-impl-name` across the specs and what it produces:

| Spec | `x-impl-name` | Implementation class file |
|---|---|---|
| nist/cmvp | `cmvp` | `src/CmvpImpl.ts` (generated interface `Cmvp`) |
| x12/x12 | `x12` | not a TypeScript implementation |
| readyplayerme | `ReadyPlayerMe` | `src/ReadyPlayerMeImpl.ts` |
| avigilon/alta/access | `Access` | `src/AccessImpl.ts` |

A module may reference more than one product (`package/x12/x12/api.yml` lists the generic DataProducer
product and its own). Each referenced product package is a dependency in `package.json`.

## 2. Operation naming

| Spec | Tag | `operationId` | `x-method-name` |
|---|---|---|---|
| cmvp | `certificate` | `listCertificates` | `list` |
| cmvp | `certificate` | `getCertificate` | `get` |
| readyplayerme | `application` | `listApplicationAssets` | `listAssets` |
| readyplayerme | `avatar` | `getAvatarMetadata` | `getMetadata` |
| readyplayerme | `avatar3d` | `get3DAvatarModel` | `get3DModel` |
| readyplayerme | `asset` | `addAssetToApplication` | `addToApplication` |
| readyplayerme | `asset` | `lockAssetForUser` | `lock` |
| readyplayerme | `user` | `getUserToken` | `getToken` |
| avigilon | `user` | `listUsers` | `list` |
| avigilon | `user` | `listUserRoles` | `listRoles` |
| avigilon | `group` | `listGroupUsers` | `listUsers` |
| avigilon | `audit` | `listAuditLogs` | `listAuditLogs` |

Reading the table:

- The tag is the resource the method lives on. `listUserRoles` is tagged `user`, so the method is
  `listRoles` on the user API; `listRoles` (all roles of the organization) is tagged `role`.
- The same `x-method-name` can repeat across tags (`list` on `user`, `group`, `site`, `zone`), never
  within one tag.
- The tag becomes file names: `certificate` gives `generated/api/CertificateApi.ts` and
  `src/CertificateProducerImpl.ts`; `avatar3d` gives `src/Avatar3dProducerImpl.ts`; `identityProvider`
  gives `src/IdentityProviderProducerApiImpl.ts` (avigilon uses the `…ProducerApiImpl` file suffix; both
  suffixes pass, see the `implementation` skill).
- `generated/api/manifest.json` maps `operationId` to `<Tag>Api.<x-method-name>`, for example
  `"listCertificates": "CertificateApi.list"`.

Summaries as written in the specs: "List CMVP Certificates", "Get a CMVP Certificate", "List Application
Assets", "Retrieves all users in the organization". No fixed verb.

## 3. Paged list, page-number style

`listCertificates` in `package/nist/cmvp/api.yml`:

```yaml
  /certificates:
    get:
      summary: List CMVP Certificates
      operationId: listCertificates
      x-method-name: list
      tags:
        - certificate
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

What codegen makes of it (`generated/api/CertificateApi.ts`):

```ts
// caller-facing API
list(pageNumber?: number, pageSize?: number, pageToken?: string): Promise<PagedResults<CertificateSummary>>;
// producer interface you implement
list(results: PagedResults<CertificateSummary>): Promise<void>;
```

The same operation without the `headers: link:` block generates
`list(pageNumber, pageSize): Promise<Array<CertificateSummary>>`. The implementation that goes with the
paged form is `package/nist/cmvp/src/CertificateProducerImpl.ts`.

Component key names differ between specs and all work:

| Spec | Parameter keys | Header key |
|---|---|---|
| cmvp | `pageNumber`, `pageSize` | `pagedLinkHeader` |
| readyplayerme | `pageNumber`, `pageSize`, `sortBy`, `sortDir` | `pagedLink` |
| avigilon | `pageNumberParam`, `pageSizeParam` | `pagedLinkHeader` |

Within one spec pick one style and keep it.

## 4. Paged list, cursor style

`package/microsoft/azure/azureresourcegraph/api.yml` pages with the provider's skip token:

```yaml
components:
  parameters:
    pageSizeParam:
      $ref: "./node_modules/@zerobias-org/types-core/schema/pageSizeParam.yml"
    pageTokenParam:
      $ref: "./node_modules/@zerobias-org/types-core/schema/pageTokenParam.yml"
```

```yaml
      parameters:
        - $ref: '#/components/parameters/pageSizeParam'
        - $ref: '#/components/parameters/pageTokenParam'
        - $ref: '#/components/parameters/subscriptionIdsParam'
      responses:
        '200':
          description: List of resources
          headers:
            link:
              $ref: '#/components/headers/pagedLinkHeader'
```

The `link` header and the plain array body are the same as in the page-number style.

## 5. Sorting and filter parameters

`listApplicationAssets` in `package/readyplayerme/readyplayerme/api.yml`:

```yaml
      parameters:
        - $ref: '#/components/parameters/appId'
        - $ref: '#/components/parameters/sortBy'
        - $ref: '#/components/parameters/sortDir'
        - $ref: '#/components/parameters/pageNumber'
        - $ref: '#/components/parameters/pageSize'
        - $ref: '#/components/parameters/name'
        - $ref: '#/components/parameters/type'
```

```yaml
    sortDir:
      $ref: './node_modules/@zerobias-org/types-core/schema/sortDirParam.yml'
    sortBy:
      $ref: './node_modules/@zerobias-org/types-core/schema/sortByParam.yml'
```

Both core parameters are arrays; `sortDir` entries pair up with `sortBy` entries by position.

A multi-value filter typed by an enum:

```yaml
    type:
      name: type
      in: query
      required: false
      schema:
        type: array
        items:
          $ref: '#/components/schemas/AssetType'
      style: form
      explode: true
      description: Filter to find assets by their type. Supports multiple values
      example: ["outfit", "top"]
```

New optional parameters go at the end of an operation's list, because the list order is the positional
argument order of the generated method (comment on `getChildren` in
`package/interface/dataproducer/api.yml`).

## 6. Path parameters and component keys

```yaml
    certificateNumber:
      name: certificateNumber
      in: path
      required: true
      description: The CMVP certificate number, from the source registry URL
      schema:
        type: string
        example: '5425'
```

Avigilon suffixes its keys with `Param` (`userIdParam` with `name: userId`). Readyplayerme reuses one wire
name under several keys because the description and example differ per operation:

| Component key | `name` | Used by |
|---|---|---|
| `avatarId` | `avatarId` | `equipAssetToAvatar`, `unequipAssetFromAvatar` |
| `avatar3DId` | `avatarId` | `get3DAvatarModel` (id with `.glb` ending) |
| `avatar2DId` | `avatarId` | `get2DAvatarRender` |
| `avatarMetadataId` | `avatarId` | `getAvatarMetadata` (id with `.json` ending) |

Provider scopes in paths that pass the gate: `/organizations/{organizationId}/users/{userId}/roles`
(avigilon), `/applications/{appId}/assets` (readyplayerme).

## 7. Schema families

Plain plus `Summary` (cmvp): the list returns `CertificateSummary` (two required fields, a handful of
optional ones), the get returns `Certificate` (the full record). Use this split when the list source
really carries less than the detail source; do not pad the summary with fields the list cannot fill.

Plain plus `Info` with `allOf` (avigilon):

```yaml
    UserInfo:
      allOf:
        - $ref: '#/components/schemas/User'
        - type: object
          properties:
            organizationId:
              type: string
```

`listUsers` returns `User`, `getUser` returns `UserInfo`.

`Base` (readyplayerme): `AssetBase` holds the shared properties; `Asset` and `AssetInfo` each add one
array through `allOf`.

`Summary` for nesting (avigilon): `GroupSummary` is "Summary representation of a group for nested
contexts", referenced from other resources instead of repeating the full `Group`.

`Ref` (avigilon): `OrganizationRef`, `ZoneSiteRef` for a pointer to another resource.

Owner-prefixed nested types (avigilon): `EntryZone`, `EntryZoneSite`, `UserEntryZone`, `UserCredential`,
`ZoneEntryAcu`. The prefix order is owner first, then the nested thing.

Optional-by-design fields, with the reason in the description (cmvp):

```yaml
    CertificateSummary:
      type: object
      description: >-
        A single row from the CMVP validated-modules listing page. Some entries
        are listed with their module and vendor names withheld by CMVP, so only
        the certificate number and its source URL are always present.
      required:
        - certificateNumber
        - detailUrl
```

Free-form maps use `additionalProperties` with a value type (avigilon `customFields`:
`type: object`, `additionalProperties: { type: string }`).

A single-item `allOf` around a `$ref` is used in avigilon (`accessRules.items`); it lets a sibling
`description` sit next to a reference.

## 8. Request bodies and envelopes

Readyplayerme defines each body twice on purpose: the schema under `components.schemas` and a reusable
body under `components.requestBodies` with the same key.

```yaml
      requestBody:
        $ref: '#/components/requestBodies/EquipAssetRequest'
```

```yaml
  requestBodies:
    CreateUserRequest:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/CreateUserRequest'
```

The provider wraps payloads in `data`, and the spec keeps that envelope in `*Request` / `*Response`
schemas (`AssetResponse` is `{ data: Asset }`). Keeping the provider's envelope is accepted. For a list,
return the bare array with the `link` header instead of an envelope, so that paging works (section 3).

## 9. Enums

Named, module-prefixed, values chosen by the module because the source has only labels (cmvp):

```yaml
    CmvpStatus:
      type: string
      description: Current validation status of a CMVP certificate
      enum:
        - ACTIVE
        - HISTORICAL
        - REVOKED
```

Named, provider wire values kept as they are (readyplayerme):

```yaml
    Avatar2DPose:
      type: string
      description: Avatar pose for 2D render
      enum:
        - power-stance
        - relaxed
        - standing
        - thumbs-up
    Avatar3DTextureAtlas:
      type: string
      description: Texture atlas resolution
      enum:
        - none
        - '256'
        - '512'
        - '1024'
```

Numeric-looking values are quoted so they stay strings.

Inline with descriptions for cryptic provider codes (avigilon `User.status`):

```yaml
        status:
          type: string
          enum: [a, s, i]
          description: User's current status
          x-enum-descriptions:
            - "Active - User account is active and can be used"
            - "Suspended - User account has been suspended"
            - "Inactive - User account is inactive"
```

What the generator emits for a named enum (`generated/model/CmvpStatus.ts`):

```ts
from(val: string | number): EnumValue {
  ...
  throw new IllegalArgumentError(`${val} is not a valid CmvpStatus`);
}
```

Worked example of the unknown-value fix. A run fails with `QUARANTINED is not a valid WidgetStatus`:

1. Read the provider's current documentation for the status field. It lists `ACTIVE`, `DISABLED`,
   `QUARANTINED`, `PENDING_REVIEW`; the spec has `ACTIVE`, `DISABLED`, `ARCHIVED`.
2. Edit the enum to `ACTIVE`, `DISABLED`, `ARCHIVED`, `QUARANTINED`, `PENDING_REVIEW`. Both missing values
   are added; `ARCHIVED` stays although the documentation dropped it.
3. Regenerate, run the tests, add a fixture or test value for a new member.
4. Leave the mapper strict. The generated model has to keep throwing on a value nobody has seen yet.

## 10. Formats and generated types

From cmvp `Certificate` and its generated model (`generated/model/Certificate.ts`):

| Property | Spec | Generated |
|---|---|---|
| `validationDate`, `sunsetDate` | `type: string`, `format: date` | `DateFormat` |
| `lastAcquired` | `type: string`, `format: date-time` | `DateTime` |
| `detailUrl`, `securityPolicyUrl` | `type: string`, `format: url` | `URL` |
| `productId`, `vendorId` | `type: string`, `format: uuid` | `UUID` |
| `overallLevel` | `type: integer`, `minimum: 1`, `maximum: 4` | `number` |

The four classes are imported from `@zerobias-org/types-core-js`. `DateFormat` takes an ISO calendar date
only; the CMVP site prints `MM/DD/YYYY`, so `package/nist/cmvp/src/mappers.ts` converts to ISO before
building the value. Decide `date` versus `date-time` from what the provider actually sends.

Other formats that appear in gate-green specs: `int32`, `int64`, `double`, `binary`, `email`, `uri`. Their
generated classes are listed in the `mapping` skill.

## 11. Response codes and bodies

| Case | Example | Shape |
|---|---|---|
| read or list | cmvp `getCertificate`, all avigilon operations | `'200'` + JSON schema |
| create returning the object | readyplayerme `createAsset` | `'201'` + JSON schema |
| provider answers a create with 200 | readyplayerme `createUser` | `'200'` + JSON schema |
| action with no body | readyplayerme `equipAssetToAvatar`, `lockAssetForUser` | `'204'` + `description`, no `content` |
| binary | readyplayerme `get3DAvatarModel`, `get2DAvatarRender` | `'200'` + `model/gltf-binary` or `image/png` / `image/jpeg`, `type: string`, `format: binary` |

```yaml
      responses:
        '204':
          description: If the asset were successfully equipped to the avatar, it would return status 204. No response body.
```

Mirror the code the provider returns; the spec documents the provider call the module makes.

## 12. Security schemes

Optional. Avigilon documents the provider's scopes:

```yaml
  securitySchemes:
    access-token:
      type: oauth2
      flows:
        authorizationCode:
          authorizationUrl: https://api.openpath.com/auth/login
          tokenUrl: https://api.openpath.com/auth/accessTokens
          scopes:
            read:users: Read access to user information
```

```yaml
      security:
        - access-token: ["read:users"]
```

The requirement sits on each operation, never at the root. cmvp (no credentials) and readyplayerme have no
security block. How credentials really reach the module is the `connection-profile` skill.

## 13. DataProducer specs

`package/x12/x12/api.yml` does not define the DataProducer operations; it references the interface
package path by path, with `/` escaped as `~1` in the JSON pointer:

```yaml
paths:
  /objects/{objectId}/children:
    $ref: './node_modules/@zerobias-org/module-interface-dataproducer/dist/module-interface-dataproducer.yml#/paths/~1objects~1{objectId}~1children'
```

A path the module does not implement is left out rather than referenced (x12 omits `/objects/{objectId}/search`
and says why in a comment). Error schemas are referenced the same way: x12 takes `illegalArgumentError`
from the interface package, and the interface spec itself (`package/interface/dataproducer/api.yml`) takes
them from `./node_modules/@zerobias-org/types-core/schema/errors/…`. Everything else about that kind of
module is in the `dataproducer-implementation` skill.

## 14. Redocly failures and their fixes

| Symptom (described, not the literal redocly message) | Rule | Fix |
|---|---|---|
| unresolved `$ref` to `./node_modules/@zerobias-org/types-core/schema/params.yml#/…` or `headers.yml#/…` | `no-unresolved-refs` | use the per-item files: `pageSizeParam.yml`, `pageNumberParam.yml`, `pageTokenParam.yml`, `sortByParam.yml`, `sortDirParam.yml`, `pagedLinkHeader.yml` |
| unresolved `$ref` to a product `catalog.yml` | `no-unresolved-refs` | add the product package to `package.json` dependencies and install |
| unresolved `$ref` to any `node_modules` path on a fresh checkout | `no-unresolved-refs` | install the module's dependencies first (see `build-lifecycle`) |
| unresolved `$ref` to `#/components/…` | `no-unresolved-refs` | typo in the key, or the component sits under the wrong section (`parameters` vs `headers` vs `schemas`) |
| property not expected / must be of type … | `struct` | key misspelled or at the wrong indentation level; `required` placed inside `properties`; a parameter without `name` / `in` |
| response missing `description` | `struct` | every response object needs a `description`, including `'204'` |
| example does not match the schema | `no-invalid-media-type-examples` | correct the `example` under the media type, or the schema it contradicts |

Not failures: no `servers`, no `security`, no 4xx responses, unused components. Those four rules are off
in the generated config.

Besides the paging and sorting files, the types-core schema directory holds connection files (`tokenProfile.yml`, `basicConnection.yml`, `oauthClientProfile.yml`,
`oauthTokenProfile.yml`, `oauthTokenState.yml`, `tokenConnectionState.yml`, `baseConnectionState.yml`,
`connectionState.yml`, `connectionMetadata.yml`, `connectionStatus.yml`), `pagedResults.yml`,
`pageCountHeader.yml`, `sortDirection.yml`, `type.yml`, and the `errors/` directory. List the directory in
the module's own `node_modules/@zerobias-org/types-core/schema/` before referencing a file; it is the only
reliable catalogue.

## 15. Rules the specs do not support

Older guidance for this repo carried the following. Each is contradicted by a gate-green spec, so do not
enforce it in review:

| Old rule | Counter-example |
|---|---|
| `openapi: 3.0.0` | every spec is `3.0.3` |
| `x-method-name` equals `operationId` | cmvp `listCertificates` / `list`; equal names are allowed, not required |
| only `get`, `list`, `search`, `create`, `update`, `delete` prefixes | readyplayerme `addAssetToApplication`, `equipAssetToAvatar`, `lockAssetForUser` |
| sort parameters `orderBy` / `orderDir` | core files are `sortByParam.yml` / `sortDirParam.yml` |
| `$ref` to `params.yml#/pageSizeParam`, `headers.yml#/linksHeader`; header named `links` | per-item files; header `link` |
| success responses `200` / `201` only | readyplayerme `'204'` on four operations |
| `x-impl-name` is PascalCase | cmvp `cmvp`, x12 `x12` |
| `*Request` / `*Response` schema names are invalid | readyplayerme `CreateAssetRequest`, `AssetResponse`, `TokenResponse` |
| `x-enum-descriptions` is required | absent in cmvp and readyplayerme |
| no parent scope such as `/orgs/{orgId}` in paths | avigilon `/organizations/{organizationId}/…`, readyplayerme `/applications/{appId}/assets` |
| every nested object is a `$ref` | readyplayerme inline `data` objects, x12 `HealthStatus` |
| id fields are always `string` | avigilon `IdentityProvider.id` is `integer` |
| component parameter keys follow `{param}{Operation}Param` | `pageNumber`, `sortBy`, `appId`, `userIdParam` all pass |
| summaries start with "Retrieve" | "List CMVP Certificates", "List Application Assets" |
| lint with spectral or `swagger-cli` | the only spec linter in the toolchain is redocly, run by `validate` |
