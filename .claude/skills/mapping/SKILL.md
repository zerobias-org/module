---
name: mapping
description: Writes and reviews module mappers in src/mappers.ts, the functions that turn a raw provider record into a generated model. Use when writing a mapper, converting a field to a core type (DateFormat, DateTime, URL, UUID, Email), mapping a date or an enum, handling null or missing fields, fixing "is not a valid <Enum>", or checking that every field of an API response is mapped.
---

# Mapping

A mapper is a pure function `to<Model>(raw: any): <Model>` in `src/mappers.ts`. It takes one raw
provider record and returns the generated model from `generated/model/`. Producers call mappers;
mappers do no HTTP and no paging (see the `implementation` skill for producers and clients).

The reference is `package/nist/cmvp/src/mappers.ts` with its helpers in
`package/nist/cmvp/src/util.ts`. Write new mappers that way.

## The mapper shape

Every mapper has two steps: check the required fields, then build one typed `const output` literal.

```typescript
import { map, toEnum } from '@zerobias-org/util-hub-module-utils';
import { DateFormat, DateTime, URL } from '@zerobias-org/types-core-js';
import { Certificate, CmvpStatus, CmvpStatusDef } from '../generated/model/index.js';
import { ensureProperties, optional } from './util.js';

export function toCertificate(raw: any): Certificate {
  // 1. Check for required fields
  ensureProperties(raw, ['certificateNumber', 'status', 'detailUrl', 'lastAcquired']);

  // 2. Create output object
  const output: Certificate = {
    certificateNumber: raw.certificateNumber,                         // plain value
    moduleName: optional(raw.moduleName),                             // optional plain value
    status: toEnum(CmvpStatus, raw.status, toEnumKey) as CmvpStatusDef, // enum
    validationDate: map(DateFormat, toIsoDate(raw.validationDate)),   // format: date
    detailUrl: map(URL, raw.detailUrl),                               // format: url
    lastAcquired: map(DateTime, raw.lastAcquired),                    // format: date-time
  };

  return output;
}
```

Rules, with the reason for each:

- Type the literal (`const output: Certificate = { … }`) and return it. The compiler then reports a
  missing required property or a wrong field type at the line that causes it.
- Do not call generated constructors (`new Certificate(…)`) in a mapper. The owner rejected that in
  review: constructor arguments are positional, so adding or reordering a property in `api.yml`
  silently shifts values into the wrong field.
- List every property of the model in the literal, including the ones you leave `undefined` with a
  comment (cmvp does this for `productId` / `vendorId`). A reader can then tell "not mapped yet"
  from "forgotten".
- Name mappers `to<Model>`, one per generated model, all in the single file `src/mappers.ts`.
  Nested objects and arrays call the nested model's own mapper.
- Use `.js` on relative imports (`./util.js`, `../generated/model/index.js`); the modules compile
  as NodeNext ESM and an extensionless import fails to resolve.

Other gate-green modules look different and stay as they are: `package/readyplayerme/readyplayerme/src/mappers.ts`
uses `X.newInstance(data)`, and `package/avigilon/alta/access/src/Mappers.ts` uses a capitalised file
name. Do not copy those shapes into new code and do not rewrite them unless asked.

## The four helpers

| Helper | Lives in | Behaviour to know |
|---|---|---|
| `map(Type, value)` | `@zerobias-org/util-hub-module-utils` | Returns `new Type(value)`, or `undefined` when `value` is falsy. Use it to wrap a value in a core class. Because the test is truthiness, do not use it for numbers or booleans where `0` / `false` are real values. |
| `toEnum(Enum, value, transform?)` | `@zerobias-org/util-hub-module-utils` | Returns `undefined` for `undefined`, `null` or `''`; otherwise calls `Enum.from(transform(value))`, which throws `IllegalArgumentError` for an unknown value. The default transform is `snakeCase`. It returns `EnumValue`, so cast to the generated `<Enum>Def` type. |
| `optional(value)` | the module's own `src/util.ts` | `value ?? undefined`: turns `null` into `undefined` and keeps `0`, `false` and `''`. |
| `ensureProperties(raw, [names])` | the module's own `src/util.ts` | Throws `InvalidStateError('Missing required field: <name>')` when a listed property is `null` or `undefined`; `0`, `false` and `''` pass. |

`optional` and `ensureProperties` are not exported by the utils package. A new module copies them
from `package/nist/cmvp/src/util.ts`. The utils package also exports `mapArray(mapper, data)`, which
skips `undefined` elements and returns `undefined` when `data` is missing.

## Missing fields and null

One policy, taken from the cmvp code:

1. A property that is `required` in `api.yml` goes into the `ensureProperties` list. A record
   without it fails with `InvalidStateError` instead of producing a half-empty model.
2. A property that is optional in `api.yml` is mapped with `optional(raw.x)` (plain values) or
   `map(...)` / `toEnum(...)` (typed values). The output holds `undefined`, never `null`, because
   the generated model types optional properties as `T | undefined`.
3. Do not invent defaults (`?? ''`, `?? 0`, `|| 'unknown'`) for data the provider did not send. A
   default hides the gap from the consumer.
4. When the provider really does omit a field that the spec marks required, change the spec: make
   the property optional in `api.yml` and regenerate. cmvp did this for entries whose module name
   and vendor are withheld (fixture `test/unit/fixtures/detail-5501-withheld.html`).

## Format to class

The generated model is the authority: open `generated/model/<Model>.ts` after `zbb generate` and
map each field to the type declared there. These rows were read from generated models and mappers
in this repository:

| `api.yml` | Generated field type | Mapper expression |
|---|---|---|
| `type: string, format: date` | `DateFormat` | `map(DateFormat, isoDateString)` |
| `type: string, format: date-time` | `DateTime` | `map(DateTime, isoStringOrDate)` |
| `type: string, format: url` | `URL` | `map(URL, raw.x)` |
| `type: string, format: uuid` | `UUID` | `map(UUID, raw.x)` |
| `type: string, format: email` | `Email` | `map(Email, raw.x)` |
| `type: string` with `enum` | `<Enum>Def` | `toEnum(<Enum>, raw.x) as <Enum>Def` |
| `type: string`, `integer`, `number`, `boolean` without a class format | `string` / `number` / `boolean` | `raw.x` or `optional(raw.x)` |

Import the classes from `@zerobias-org/types-core-js`. `URL` in particular is the core class, not
the Node global, so the import is required. The package ships more classes (see
`node_modules/@zerobias-org/types-core-js/dist/src/types/`); use one only when the generated model
field is typed with it.

Modules on the older dependency lines (avigilon: `types-core-js ^1`, `util-codegen ^2`) map dates
with `map(Date, …)`. On the dependency lines the generator emits today, `format: date` and
`format: date-time` generate `DateFormat` and `DateTime`, as in cmvp. Check the generated model
before choosing.

## Dates

`DateFormat` accepts a `Date` or a string matching the core `date` pattern (ISO `YYYY-MM-DD`), sets
the time to 00:00 UTC, and prints `YYYY-MM-DD`. `DateTime` accepts a `Date` or an ISO date-time
string. Both throw `InvalidInputError` for any other string.

Do not parse a provider's local date format through `Date`. `new Date('11/09/2026')` is midnight in
the machine's local zone; converted to UTC it becomes the previous day on any machine east of UTC,
so the same record maps to different dates depending on where the code runs. Rebuild the string
instead, as `toIsoDate` in `package/nist/cmvp/src/mappers.ts` does:

```typescript
function toIsoDate(raw: string | undefined): string | undefined {
  const match = raw?.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!match) return undefined;
  const [, month, day, year] = match;
  return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
}
// validationDate: map(DateFormat, toIsoDate(raw.validationDate)),
```

In unit tests compare `value?.toString()` with the expected ISO string; a `deep.equal` against a
`Date` fails because the field holds a `DateFormat` / `DateTime` instance (see the `testing` skill).

## Enums

A mapper does not tolerate unknown enum values. When the provider returns a value the generated
enum does not have, `Enum.from()` throws `<value> is not a valid <Enum>`. That error is the signal
that the spec is behind the provider, and it has to stay loud so the next new value is noticed too.

The fix is in the spec, in this order:

1. Add the value to the enum in `api.yml` and regenerate (`zbb generate`; see the `build-lifecycle`
   skill). Leave the mapper unchanged: no `try/catch` around `toEnum`, no fallback to `undefined`,
   no pass-through string, no drop-with-warning.
2. Open the provider's current API documentation for that field and add every documented value the
   enum is missing, not only the one that failed. Each missing value is a failure waiting to happen.
3. Do not remove existing values, even when the provider's documentation no longer lists them; old
   records can still carry them.
4. Search the consumers of the module (the `zerobias-org/collectorbot` collectors) for a mapping of
   this module enum onto a schema enum. If one exists, the new values need mapping there as well:
   open a task on the collectorbot board linked to the module task. If nothing maps the enum, the
   module change is the whole fix.
5. Say in the PR body which case applies and where the consumer mapping lives.

Provider spelling that differs from the enum spelling is handled by the transform argument, not by
a lookup table of tolerated values. cmvp normalises `"Software-hybrid"` to `SOFTWARE_HYBRID` with
`toEnumKey` and passes it as the third argument of `toEnum`.

## Check that every field is mapped

Static check, for every mapper:

1. Open `generated/model/<Model>.ts` and count the properties.
2. Every property appears once in the `const output` literal.
3. Every property that is `required` in `api.yml` is in the `ensureProperties` list.
4. `zbb compile` passes (a missing required property is a type error).

Run `${CLAUDE_SKILL_DIR}/scripts/check-mappers.sh <module-dir>` for the patterns this skill rules
out: `Date` used for date fields, `newInstance` or generated constructors in new mappers, and
`try/catch` in the mapper file. It prints findings and exits non-zero when it has any; findings in
the older modules are expected.

### Temporary raw-response logging

The static check cannot see a provider field that the spec never declared, or a field the mapper
reads under the wrong name (`mobilePhone` vs `phoneNumber`). To find those, compare a real payload
with the mapper once:

1. In the producer, log the raw payload before any mapper runs, tagged so it is easy to find and
   remove: `this.logger.debug('[TEMP-RAW-API] listUsers', JSON.stringify(response.data))`.
2. Run the tests that reach the live API and capture the output to a file outside the repository.
   For a connector with `test/e2e` that is `zbb testDirect --slot <slot>`.
3. For each `[TEMP-RAW-API]` payload, list its keys and compare them with the mapper and the model:
   - key in the payload and in `api.yml`, absent from the mapper: add it to the mapper;
   - key in the payload, absent from `api.yml`: decide whether the model needs it; if yes add it to
     `api.yml`, regenerate, then map it (see the `api-spec` skill);
   - property read by the mapper, never present in the payload: the name is wrong or the field is
     conditional; check the provider documentation.
4. Remove the logging. `grep -rn 'TEMP-RAW-API' src/` returns nothing before the commit, and the
   captured file is not committed, because raw payloads can contain personal data.

Plain / profile-less modules such as `package/nist/cmvp` have no `test/e2e`; their raw input is
the committed fixture set under `test/unit/fixtures/`, so compare the mapper against those
fixtures instead of logging.

Do this after implementing a new operation, after a spec change that touches a model, and when a
consumer reports a field that is empty but present at the provider.
