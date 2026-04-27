# OpenAPI / REST Mapping

## Overview

How an OpenAPI-described REST API surfaces through the DataProducer interface.
Unlike SQL or LDAP, REST is itself a transport — and DataProducer is
intentionally transport-agnostic. So an OpenAPI mapping requires an
implementation that adopts **two** sub-interfaces:

- **DataProducer** — what callers see: Containers, Collections, Functions,
  Schemas, RFC4515 filters. No HTTP details.
- **HttpModule** *(separate sub-interface, defined elsewhere)* — what the
  producer needs internally: request routing, headers, content negotiation,
  retry/timeout policy, response shaping.

Callers interact with DataProducer alone. The HttpModule's existence is an
implementation detail of the OpenAPI producer; it is **not** visible on the
DataProducer Function object.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| OpenAPI concept                       | DataProducer concept                  | Notes                                                              |
|---------------------------------------|---------------------------------------|--------------------------------------------------------------------|
| API root                              | Root Container (`id == "/"`)           |                                                                    |
| Tag / namespace                        | Container                              | Optional grouping; many OpenAPI specs lack tags.                   |
| Resource collection (`/users`)         | Collection                              | If the API supports filterable list + per-id CRUD.                 |
| Singleton resource (`/me`)             | Document                                | Bound to one logical record, no list semantics.                     |
| Operation (`POST /users`, `getUserById`) | Function                              | One per `operationId` in the spec.                                  |
| `components.schemas.X`                 | (referenced via `schema:type:...`)     | Surfaces inside `inputSchema` / `outputSchema` / `documentSchema` — not as a top-level Object. |
| Path parameter, query, header, body    | Properties of the Function's `inputSchema` | Producer is responsible for routing them to the right HTTP slot. |
| Response body schema                   | Function's `outputSchema`               |                                                                    |
| Error response (4xx/5xx) schemas       | Entries in the Function's `throws` map  | Keys are domain-level error codes, not HTTP statuses.              |

## Object Mappings

### Resource Collection

```yaml
Object:
  id: "/api/users"
  name: "users"
  objectClass: ["collection"]
  description: "User resource collection"
  collectionSchema: "schema:type:api.resources.user"
  tags: ["resource"]
```

```yaml
Schema:
  id: "schema:type:api.resources.user"
  dataTypes:
    - name: "uuid"
    - name: "string"
    - name: "email"
    - name: "date-time"
  properties:
    - name: "id"
      dataType: "uuid"
      primaryKey: true
      required: true
    - name: "username"
      dataType: "string"
      required: true
    - name: "email"
      dataType: "email"
      required: true
    - name: "createdAt"
      dataType: "date-time"
```

### Operation (Function)

A single OpenAPI `operationId` becomes one Function object. The DataProducer
metadata exposes only what the abstract interface defines — schemas and
declared error map. The `httpMethod`/`httpPath`/headers/timeout policy live on
the producer's HttpModule side and are not visible here.

```yaml
Object:
  id: "/api/operations/createUser"
  name: "createUser"
  objectClass: ["function"]
  description: "Create a new user"
  inputSchema:  "schema:function:api.operations.createUser:input"
  outputSchema: "schema:function:api.operations.createUser:output"
  throws:
    validation_failed:  "schema:shared:rest_validation_error"
    duplicate_user:     "schema:shared:rest_conflict_error"
    rate_limited:       "schema:shared:rest_rate_limit_error"
  tags: ["operation", "create"]
```

```yaml
Schema:
  id: "schema:function:api.operations.createUser:input"
  dataTypes:
    - name: "string"
    - name: "email"
  properties:
    - name: "username"
      dataType: "string"
      required: true
      description: "3–32 chars; will be unique"
    - name: "email"
      dataType: "email"
      required: true
    - name: "displayName"
      dataType: "string"
```

The Function's input schema **flattens** path parameters, query parameters,
headers, and body into a single property bag. The producer's HttpModule is
responsible for routing each property to the correct HTTP slot when invoking.

For example, an OpenAPI operation `GET /users/{userId}?includeDeleted=false`
exposes a Function whose input schema has `userId` (`uuid`, required) and
`includeDeleted` (`boolean`); the producer knows internally that `userId`
goes in the path and `includeDeleted` in the query string.

## Operation Mappings

| DataProducer op                        | OpenAPI side                                                          |
|----------------------------------------|-----------------------------------------------------------------------|
| `getRootObject`                        | (synthetic) — describes the API as a whole.                           |
| `getChildren`                          | Tag-grouped list of resources / operations.                            |
| `getCollectionElements`                | List operation (e.g. `GET /users`) on the resource.                    |
| `searchCollectionElements`             | List operation with filter; producer translates RFC4515 to native query semantics. |
| `getCollectionElement(key)`            | Read-by-id (e.g. `GET /users/{id}`).                                   |
| `addCollectionElement`                 | Create (e.g. `POST /users`).                                           |
| `updateCollectionElement(key)`         | Update (e.g. `PUT /users/{id}` or `PATCH`).                            |
| `deleteCollectionElement(key)`         | Delete (e.g. `DELETE /users/{id}`).                                    |
| `invokeFunction`                       | The single declared operation. Producer assembles the HTTP call.       |
| `validateFunctionInput`                | Local validation against the input schema (no HTTP call).              |
| `getDocumentData` / `updateDocumentData` | Singleton-resource read / replace.                                   |

## Filter Translation Example

OpenAPI APIs vary widely in how they accept filters — by query parameter
(`?status=active`), by structured query bodies, by ad-hoc syntaxes (`q=`,
JSON:API filter spec, OData, etc.). The producer parses RFC4515 once and emits
the API's native form.

```
RFC4515:
  (&(status=active)(role=admin))

Lite-filter AST:
  AND
    EQ status "active"
    EQ role "admin"

Emitted to a JSON:API-flavored API:
  GET /users?filter[status]=active&filter[role]=admin

Emitted to a flat-query API:
  GET /users?status=active&role=admin

Emitted to an OData-flavored API:
  GET /users?$filter=status eq 'active' and role eq 'admin'
```

When the API has no native filter capability, the producer falls back to
in-memory matching after fetching:

```typescript
import { parse } from '@zerobias-org/util-lite-filter';
const expr = parse(filterString);
const all  = await fetchEverything();
return all.filter(item => expr.matches(item));
```

The OpenAPI mapping document for a specific producer (a downstream
implementation) should document its filter dialect explicitly.

## Core Types Reference

| OpenAPI `format` / type                | `dataType`         | Notes                                              |
|----------------------------------------|--------------------|----------------------------------------------------|
| `string`                               | `string`            |                                                    |
| `string` + `format: email`              | `email`             | Always.                                            |
| `string` + `format: uri` / `url`        | `url`               |                                                    |
| `string` + `format: uuid`               | `uuid`              |                                                    |
| `string` + `format: date`               | `date`              |                                                    |
| `string` + `format: date-time`          | `date-time`         |                                                    |
| `string` + `format: byte` / `binary`    | `byte`              | Base64-encoded over JSON.                           |
| `string` + `format: hostname`           | `hostname`          |                                                    |
| `string` + `format: ipv4` / `ipv6`      | `ipAddress`         | Single core type covers both families.             |
| `integer`, `int32`, `int64`             | `integer`           |                                                    |
| `number`, `float`, `double`             | generic numeric, or `decimal` if precise math is required | Currency must be `decimal`.    |
| `boolean`                               | `boolean`           |                                                    |
| `array`                                 | parent `dataType` + `multi: true` |                                              |
| Enum constraint                          | `string` + `references.schemaId` to `schema:enum:...`  | Schema body lists allowed values. |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Authentication.** OpenAPI security schemes (Bearer, API key, OAuth flows)
  belong to the connection layer, not the DataProducer Function object.
  Function inputs do not carry credentials.
- **Content negotiation.** When an operation supports multiple content types
  (`application/json`, `application/xml`), the producer chooses one
  internally; callers pass and receive structured data, not raw bodies.
- **Multi-status responses (`207`).** Map to a Collection bulk operation when
  the OpenAPI shape allows; otherwise expose the individual results as the
  Function's output schema.
- **Streaming responses (`text/event-stream`, NDJSON).** Outside the scope of
  the basic Function model; consider modeling as a Binary download or a
  cursor-paged Collection.
- **Discriminator / `oneOf` / `anyOf` schemas.** Translate to a property whose
  schema reference resolves to a parent type with the discriminator field;
  consumers fetch the discriminator value and look up the concrete schema.
- **Long-running operations.** OpenAPI `202` + polling URL pattern translates
  poorly to a synchronous Function. Either model as a kickoff Function plus a
  status Collection, or expose only the eventual-consistency Collection view.
- **Rate limiting / retry policy.** These are HttpModule concerns. The
  DataProducer Function does not expose a retry policy field; the producer
  applies its policy internally and surfaces only the final outcome (success
  or a declared error from `throws`).
- **HTTP method choice.** Determined by the OpenAPI operation; DataProducer
  callers do not see or care which method was used.
