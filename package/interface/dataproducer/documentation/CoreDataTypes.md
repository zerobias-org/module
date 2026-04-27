# Core DataTypes

Properties name a `dataType` from `@zerobias-org/types-core`. Picking the right
core type is **load-bearing**: it drives validation, parsing, formatting, and
client behavior across the platform. Generic types (`string`, `integer`) plus a
custom `format:` string is **not** a substitute — it bypasses every one of
those benefits and creates examples that get copied into other modules.

## The Rule

> If a core type matches the data, use the core type. Never use `dataType:
> string` with a custom `format:` to label something a core type already covers.

## Common Types

The full catalog lives at `node_modules/@zerobias-org/types-core/data/types/types.json`,
or programmatically via `CoreType.listTypes()`. The most commonly needed:

| `dataType`         | JSON   | What it is                                    | Example                                  |
|--------------------|--------|-----------------------------------------------|------------------------------------------|
| `string`           | string | Free text                                     | `"any text"`                             |
| `integer`          | number | Whole numbers                                 | `42`, `-100`                             |
| `decimal`          | number | Arbitrary-precision decimal (currency, math)  | `19.99`, `100.50`                        |
| `boolean`          | boolean | True/false                                   | `true`                                   |
| `date`             | string | ISO 8601 date                                 | `"2025-10-24"`                           |
| `date-time`        | string | ISO 8601 timestamp                            | `"2025-10-24T10:30:00.000Z"`             |
| `duration`         | string | ISO 8601 duration                             | `"P3Y6M4DT12H30M5S"`                     |
| `email`            | string | Validated email                                | `"user@example.com"`                     |
| `phoneNumber`      | string | International phone number                    | `"+12673103464"`                         |
| `url`              | string | URL/URI (format alias: `uri`)                 | `"https://example.com"`                  |
| `hostname`         | string | DNS hostname                                  | `"github.com"`                           |
| `ipAddress`        | string | IPv4 or IPv6                                  | `"192.168.1.1"`, `"::1"`                 |
| `geoCountry`       | string | ISO 3166-1 alpha-2 country code               | `"US"`, `"DE"`, `"FR"`                   |
| `geoSubdivision`   | string | ISO 3166-2 subdivision code                   | `"US-CA"`, `"DE-BE"`                     |
| `uuid`             | string | UUID                                          | `"b7168568-af49-11ea-8b0b-47ecc4197a7f"` |
| `byte`             | string | Base64-encoded binary (aliases: `b64`, `base64`) | `"SGVsbG8sIHdvcmxkCg=="`              |
| `mimeType`         | string | MIME type                                     | `"application/json"`                     |

DataType records carry validation pattern, examples, JSON type, and HTML input
hint, all available via `new CoreType(name)`.

## Picking the Right Type — Examples

| Field semantics            | ❌ Wrong                                        | ✅ Right                          |
|----------------------------|-------------------------------------------------|-----------------------------------|
| Country code (US, DE, …)   | `dataType: string, format: iso3166-alpha2`     | `dataType: geoCountry`            |
| State / province           | `dataType: string, format: iso3166-2`          | `dataType: geoSubdivision`        |
| Email                      | `dataType: string, format: email`              | `dataType: email`                 |
| URL                        | `dataType: string, format: uri`                | `dataType: url`                   |
| Phone                      | `dataType: string, format: phone`              | `dataType: phoneNumber`           |
| Currency amount            | `dataType: number`                             | `dataType: decimal`               |
| Timestamp                  | `dataType: timestamp, format: ISO8601`         | `dataType: date-time`             |
| IP address                 | `dataType: string, format: ipv4` / `ipv6`      | `dataType: ipAddress`             |
| File contents              | `dataType: string, format: base64`             | `dataType: byte`                  |
| MIME type                  | `dataType: string`                             | `dataType: mimeType`              |
| LDAP DN                    | `dataType: dn` (no such type)                  | `dataType: string` + `description` explaining DN format |

## When `format:` Is Legitimate

`format:` is a display/input hint. Use it for things core types don't cover:

- Ordering of date components for UI (`MM/DD/YYYY` vs `DD/MM/YYYY`).
- Number presentation (`%.2f`, thousands separator).
- Custom mask for a string the system already validates as `string`.

`format:` should never carry information that changes how the value is *parsed*
or *validated* — that belongs in the `dataType` choice.

## Programmatic Discovery

```typescript
import { CoreType } from '@zerobias-org/types-core-js';

const all = CoreType.listTypes();         // every available dataType name
const t   = new CoreType('email');
t.description;                              // "A valid email"
t.examples;                                 // ["john.doe01@gmail.com", ...]
t.pattern;                                  // regex used for validation
t.htmlInput;                                // HTML <input type="..."> hint
```

## Migrating Existing Schemas

When auditing an existing module's schemas, scan for these antipatterns:

- `dataType: string` accompanied by `format: <something specific>` — find the
  matching core type.
- Any `dataType` value not in `CoreType.listTypes()` — fix immediately. The
  generator will silently treat unknown types as plain strings.
- Numeric fields that hold money — switch to `decimal`. Floats are not
  acceptable for currency.

Bad examples in mapping documentation propagate into real implementations
quickly. Treat fixing them as higher-priority than other doc work.
