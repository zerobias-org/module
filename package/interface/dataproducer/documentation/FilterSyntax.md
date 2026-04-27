# Filter Syntax

DataProducer uses **RFC4515** (LDAP filter grammar) as the canonical wire format
for structured filter expressions on `objectSearch`, `searchChildObjects`, and
`searchCollectionElements`. Backends translate as needed; the public contract
is always RFC4515.

## Why RFC4515

- Compact, parseable, well-specified.
- LDAP backends pass it through unchanged.
- Boolean composition, presence checks, prefix/substring matching, and
  range comparisons all expressible without invented syntax.
- A single shared parser (`@zerobias-org/util-lite-filter`) means every
  implementation reads filters the same way.

## Grammar Summary

```
(attribute=value)          # equality
(attribute!=value)         # inequality
(attribute>=value)         # greater-than-or-equal
(attribute<=value)         # less-than-or-equal
(attribute~=value)         # approximate / fuzzy
(attribute=*)              # presence (not null/empty)
(attribute=foo*)           # prefix match
(attribute=*foo*)          # substring match

(&(c1)(c2)(c3))            # AND
(|(c1)(c2)(c3))            # OR
(!(c1))                    # NOT

# Extended (lite-filter): function-style operators use a colon
(price:between:10,100)
(name:in:Alice,Bob,Carol)
```

The full grammar — including the `:function:` extension space — is documented
in `org/util/packages/lite-filter/README.md`.

## Reference Library

`@zerobias-org/util-lite-filter` is the canonical RFC4515 parser/evaluator for
the platform. Implementations should not roll their own.

```typescript
import { parse } from '@zerobias-org/util-lite-filter';

const expression = parse('(&(objectClass=document)(modified>=2025-01-01))');
expression.matches(someObject);                   // boolean
```

The library exposes both an in-memory `matches` evaluator (useful when the
backend can't filter natively and the producer must filter post-fetch) and the
parsed AST (useful when translating to the backend's native filter language).

## Translation Strategies

How a producer applies an RFC4515 filter depends on what its backend can do.

### 1. Native (LDAP)

Pass through unchanged.

```
RFC4515:  (&(objectClass=person)(uid=*alice*))
LDAP:     (&(objectClass=person)(uid=*alice*))
```

### 2. AST → Native Query Language (SQL, GraphQL, DynamoDB, …)

Walk the parsed expression and emit the backend's syntax.

| RFC4515                     | SQL `WHERE`                     | DynamoDB FilterExpression       |
|-----------------------------|---------------------------------|---------------------------------|
| `(name=Alice)`              | `name = 'Alice'`                | `name = :v1`                    |
| `(age>=18)`                 | `age >= 18`                     | `age >= :v1`                    |
| `(name=A*)`                 | `name LIKE 'A%'`                | `begins_with(name, :v1)`        |
| `(name=*ice*)`              | `name LIKE '%ice%'`             | `contains(name, :v1)`           |
| `(email=*)`                 | `email IS NOT NULL`             | `attribute_exists(email)`       |
| `(&(a=1)(b=2))`             | `a = 1 AND b = 2`               | `a = :v1 AND b = :v2`           |
| `(\|(a=1)(b=2))`            | `a = 1 OR b = 2`                | `a = :v1 OR b = :v2`            |
| `(!(active=true))`          | `NOT (active = true)`           | `NOT (active = :v1)`            |

A worked SQL example, end to end:

```
RFC4515:
  (&(active=true)(|(country=US)(country=CA))(created>=2025-01-01))

Parsed AST (lite-filter):
  AND
    EQ active true
    OR
      EQ country "US"
      EQ country "CA"
    GTE created "2025-01-01"

Emitted SQL:
  WHERE active = TRUE
    AND (country = 'US' OR country = 'CA')
    AND created >= '2025-01-01'
```

### 3. Post-fetch Filtering (fallback)

When the backend cannot express the filter, fetch and filter in-process using
`expression.matches(row)`. Acceptable for small result sets; a producer should
prefer push-down when possible.

## Field Name Mapping

The attribute names inside a filter refer to **schema property names**, not
backend column names. If a producer's backend uses different names internally
(snake_case, prefixed columns, etc.), the producer is responsible for mapping
schema property name → backend identifier when emitting the native query.

## Extension Functions

The lite-filter `:function:` form lets producers expose backend capabilities
that don't have a direct RFC4515 equivalent. Examples a producer may surface:

```
(modified:between:2025-01-01,2025-12-31)
(country:in:US,CA,MX)
(location:within:37.7749,-122.4194,10km)
```

Producers that accept extension functions must document the supported set in
their mapping document.

## Mutual Exclusion with Keyword Search

`objectSearch` accepts either `keywords` or `filter`, never both. If a caller
sends both, the operation must reject the request with a `400`. Filters are
structured; keywords are unstructured full-text — they are not composable.

## See Also

- `Concepts.md` — where filters fit in the operations matrix.
- `org/util/packages/lite-filter/README.md` — full grammar, AST shape,
  extension function reference.
- Per-backend translation specifics in the relevant `mappings/*.md`.
