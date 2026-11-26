# <Vendor> <Product> Module

`@zerobias-org/module-<vendor>-<product>` <one or two sentences: which system it reads or
controls, and what a caller gets from it>.

<Optional second paragraph: how it talks to the source (REST API, scraping, file drop) and
anything a consumer has to know about the data, such as audit-trail fields or rate limits.>

## Installation

```bash
npm install @zerobias-org/module-<vendor>-<product>
```

Node 22 and the `@zerobias-org` registry (`https://pkg.zerobias.org/`) are required; see the
repository README for `ZB_TOKEN` setup.

## Authentication

<!-- Keep exactly one of the two variants below. -->

<!-- Variant A: plain / profile-less module -->
None. <Source> is public and unauthenticated, so this module has **no connection profile** and no
`connect()` lifecycle: it is usable as soon as it is constructed.

<!-- Variant B: connector -->
The module connects with a connection profile (`connectionProfile.yml`):

| Field | Required | Description |
|-------|----------|-------------|
| `<field>` | yes | <what it is and where the user obtains it> |
| `<field>` | no | <what it is; default when omitted> |

```typescript
import { new<Class>, ConnectionProfile } from '@zerobias-org/module-<vendor>-<product>';

const <instance> = new<Class>();
const profile = new ConnectionProfile(/* field values, never real credentials */);
await <instance>.connect(profile);
```

## Usage

```typescript
import { new<Class> } from '@zerobias-org/module-<vendor>-<product>';

const <instance> = new<Class>();
const <things> = <instance>.get<Tag>Api();

const page = await <things>.list(1, 50);
console.log(page.count, page.items[0].<property>);

const one = await <things>.get('<id>');
console.log(one.<property>);
```

## Operations

| Operation | Method | Source |
|-----------|--------|--------|
| `<operationId>` | `get<Tag>Api().<method>(<args>)` | `<HTTP method and path, or page, at the provider>` |

<What each method returns (`PagedResults<Model>`, `Model`) and which errors a caller should
expect, for example `NoSuchObjectError` for an id that does not exist.>

## Examples

<!-- Optional. Keep it only when it shows something Usage does not, such as error handling. -->

```typescript
import { NoSuchObjectError } from '@zerobias-org/types-core-js';
import { new<Class> } from '@zerobias-org/module-<vendor>-<product>';

const <things> = new<Class>().get<Tag>Api();

try {
  const one = await <things>.get('<id that does not exist>');
  console.log(one.<property>);
} catch (e) {
  if (e instanceof NoSuchObjectError) {
    console.log('not found');
  } else {
    throw e;
  }
}
```

## Testing

From this module directory:

```bash
zbb --slot <slot> test         # unit tests
zbb --slot <slot> testDirect   # e2e, in-process        (connectors with test/e2e)
zbb --slot <slot> testDocker   # e2e, against the image (connectors with test/e2e)
zbb --slot <slot> gate         # full gate (writes gate-stamp.json; commit it)
```

<Say what the tests run against: fixtures and nock for unit tests, which slot secret the e2e
tests need. A plain module without `test/e2e` deletes the two e2e lines and says how to verify
against the live source by hand.>
