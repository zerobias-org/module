# Errors

DataProducer reuses the platform-wide error model from
`@zerobias-org/types-core`. Implementations should not invent new error
shapes — they extend by populating the `throws` map on Function objects.

## Standard Errors

| Error                       | HTTP | Raised when                                                   |
|-----------------------------|------|---------------------------------------------------------------|
| `noSuchObjectError`         | 404  | The requested object ID or schema ID does not resolve.        |
| `noSuchObjectError`         | 404  | A `references.schemaId` points at a schema that no longer exists. |
| `UnsupportedOperationError` | 400  | The operation is not valid for any of the object's `objectClass` capabilities. |
| `illegalArgumentError`      | 400  | A required parameter is missing, malformed, or mutually exclusive with another supplied parameter. |
| `preconditionFailedError`   | 412  | `If-Match` ETag does not match current state (optimistic concurrency). |

Schemas for these live in
`@zerobias-org/types-core/schema/errors/*.yml` and are referenced from
`api.yml` under `components/responses`.

## When to Raise Which

- **Object missing** → `noSuchObjectError` with the requested ID.
- **Object exists, but doesn't support the op** (e.g. `getCollectionElements`
  on a binary-only object) → `UnsupportedOperationError`.
- **Bad input shape** (filter syntax error, page size out of range, both
  `keywords` and `filter` provided) → `illegalArgumentError`.
- **Concurrent modification** (ETag mismatch on update) →
  `preconditionFailedError`.
- **Auth failure** — not a DataProducer concern; the underlying connection
  layer raises platform auth errors before the request reaches the producer.

## Function-Specific Errors

Function objects can declare per-error schemas via the `throws` map:

```yaml
objectClass: [function]
inputSchema:  schema:function:mydb.public.calculate_tax:input
outputSchema: schema:function:mydb.public.calculate_tax:output
throws:
  insufficient_balance:    schema:shared:funds_error
  rate_limit_exceeded:     schema:shared:rate_limit_error
  unknown_jurisdiction:    schema:shared:jurisdiction_error
```

Keys are **error codes** chosen by the producer (snake_case is conventional);
values are Schema IDs whose schema describes the error payload. The keys are
**not** HTTP status codes — they are domain-level identifiers that survive
across transports.

When `invokeFunction` raises a declared error, the response body must conform
to the matching schema. The HTTP status used for declared function errors is
implementation-defined; `400` is conventional for client-attributable errors,
`502` / `503` for upstream failures.

## Circular References

Detecting cycles in object graphs (e.g. a Container that has itself as a child
via shared references, or two collections referencing each other) is the
**caller's** responsibility. The interface does not commit to detecting
cycles, and producers should not loop indefinitely trying to.

## Error Body Shape

All standard errors use the platform error envelope from `types-core`:

```json
{
  "code": "noSuchObjectError",
  "message": "Object not found",
  "details": {
    "objectId": "schema:table:mydb.public.customers"
  }
}
```

Function-declared errors follow the schema named in `throws[code]` and may
have any shape — they need not include the `code` / `message` envelope.

## See Also

- `@zerobias-org/types-core/schema/errors/` — canonical error schemas.
- `Concepts.md` — operations matrix (which class raises which error).
