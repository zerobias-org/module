# Unit tests - worked reference

Read this when writing a new unit test file or a nock mock. Paths are relative to the repo root.

## Contents

1. Files to read first
2. File skeleton (plain module)
3. Connector variant
4. Nock patterns
5. Fixtures
6. Assertions on generated types
7. Layout variants that pass the gate
8. Checks before handing over

## 1. Files to read first

| File | Shows |
|---|---|
| `package/nist/cmvp/test/unit/CertificateProducerTest.test.ts` | the reference: nock setup, fixture loader, paging, error mapping, date and enum assertions |
| `package/nist/cmvp/test/unit/Common.ts` | plain module: the mock base URL is the real host |
| `package/nist/cmvp/test/unit/CmvpApiTest.ts` | the file the generator emits (factory smoke test), kept as is |
| `package/avigilon/alta/access/test/unit/UserProducerApiImplTest.ts` | connector: mocked login, `connect(profile)`, `PagedResults` passed to a producer |
| `package/avigilon/alta/access/test/utils/nock-helpers.ts` | optional helper layer (auth headers, paged replies, fixture loader) |

## 2. File skeleton (plain module)

From `package/nist/cmvp/test/unit/CertificateProducerTest.test.ts`:

```typescript
import { expect, assert } from 'chai';
import nock from 'nock';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { DateFormat, DateTime, EnumValue, NoSuchObjectError } from '@zerobias-org/types-core-js';
import { CmvpStatus } from '../../generated/model/index.js';
import { DETAIL_PATH_PREFIX, LISTING_PATH } from '../../src/CmvpClient.js';
import { CmvpImpl } from '../../src/CmvpImpl.js';
import { MOCK_BASE_URL } from './Common.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixture = (name: string) => readFileSync(path.join(__dirname, 'fixtures', name), 'utf8');

before(() => { nock.disableNetConnect(); });
afterEach(() => { nock.cleanAll(); });
after(() => { nock.enableNetConnect(); });

describe('CertificateApi.list', () => {
  it('parses listing rows and cross-checks the reported total', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, fixture('listing-3-rows.html'));

    const page = await client.getCertificateApi().list(1, 50);

    expect(page.count).to.equal(3);
    expect(page.items).to.have.lengthOf(3);
    expect(nock.pendingMocks()).to.be.empty;
  });
});
```

Points worth copying:

- Paths and the base URL are imported from the client (`LISTING_PATH`, `DEFAULT_BASE_URL`), so the mock cannot
  drift from what the code requests.
- `expect(nock.pendingMocks()).to.be.empty` proves the code made the call the test set up.
- The test goes through the public API (`client.getCertificateApi()`), which exercises the generated wrapper,
  the producer and the mapper together.

## 3. Connector variant

A connector has a profile and a connect lifecycle, so the test connects first. From
`package/avigilon/alta/access/test/unit/UserProducerApiImplTest.ts` (shortened):

```typescript
beforeEach(async () => {
  client = new AvigilonAltaAccessClient();
  const profile: ConnectionProfile = { email: new Email('test@example.com'), password: 'testpass' };

  nock(baseUrl).post('/auth/login').reply(200, { data: { token: 'mock-token-123', expiresAt: '...' } });
  await client.connect(profile);

  producer = new UserProducerApiImpl(client);
  nock.disableNetConnect();
});
```

and a paged call on the producer:

```typescript
const results = new PagedResults<User>();
await producer.list(results, orgId);
```

The avigilon files read `process.env.AVIGILON_EMAIL || 'test@example.com'`. That passes the gate, but it makes
a unit test depend on the developer's environment; write the literal dummy value in new tests.

A connector's unit tests also cover the lifecycle (`connect`, `isConnected`, `disconnect`, `metadata`):
see `package/avigilon/alta/access/test/unit/AccessImplTest.ts`. A plain module has none of these methods and
no such test.

## 4. Nock patterns

All chained from `nock(baseUrl)`; these are the forms used in the two reference modules.

```typescript
nock(base).get('/things/42').reply(200, body);                    // GET
nock(base).get('/things').query({ limit: 50, offset: 0 }).reply(200, body);   // exact query
nock(base).post('/auth/login').reply(200, { data: { token: 't' } });          // POST
nock(base).get('/things/missing').reply(404);                     // error status, no body
nock(base).get('/things').reply(503);                             // upstream failure
nock(base).matchHeader('authorization', 'Bearer t').get('/things').reply(200, body);  // header check
```

- A mock is consumed by one request. A test that triggers two calls to the same path needs two mocks.
- Set mocks inside each `it` or `beforeEach`, not once in `before`: a half-consumed mock from one test would
  otherwise answer the next one.
- A string body is returned as is (HTML fixtures); an object is serialized as JSON.
- With `disableNetConnect()` active, an unmatched request rejects with a nock "no match" error naming the
  method and URL. Compare that URL with the mock: a trailing slash, a missing query parameter or a different
  host are the usual differences.
- Nock is the only mocking library in these modules. Do not add sinon, jest mocks or fetch-mock; mocking at
  the HTTP boundary keeps the client, the producer and the mapper under test together.

## 5. Fixtures

- Location: `test/unit/fixtures/` (cmvp). avigilon keeps JSON templates in `test/fixtures/templates/` with a
  loader in `test/utils/nock-helpers.ts`; both are under `test/`, so both are hashed into the stamp. Use
  `test/unit/fixtures/` for new modules.
- Format follows the source: `.html` for a scraped page, `.json` for a REST body. Read with `readFileSync`
  and, for JSON, `JSON.parse`.
- One fixture per distinct shape: a normal item, a list page, an item with optional fields missing, each
  error body the client distinguishes. cmvp has `listing-3-rows.html`, `listing-count-mismatch.html`,
  `detail-5425.html`, `detail-5501-withheld.html`, `detail-not-found.html`.
- Capture from the real API, trim to the fields and rows the test needs, then replace personal data and
  secrets with obvious dummies (`user@example.com`, `mock-token-123`) before `git add`.
- Fixtures are committed. The module `.gitignore` does not exclude them, and the gate stamp hashes them.

## 6. Assertions on generated types

| Field type in `api.yml` | Runtime value | Assert with |
|---|---|---|
| `format: date` | `DateFormat` | `.toString()` equals `'YYYY-MM-DD'`, `instanceOf(DateFormat)` |
| `format: date-time` | `DateTime` | `instanceOf(DateTime)` |
| `format: url` | URL type from types-core-js | `.toString()` |
| enum | `EnumValue` | `(value as EnumValue).eq(CmvpStatus.Active)` is true |
| optional field absent in the source | `undefined` | `expect(x).to.be.undefined` |

Assert the exact date string for at least one value. A mapper that parses a vendor format such as
`MM/DD/YYYY` through `new Date(...)` shifts by a day in time zones east of UTC, and only an exact-date
assertion catches it.

Error mapping (one test per mapped status):

```typescript
try {
  await client.getCertificateApi().get('999999');
  assert.fail('should have thrown');
} catch (e) {
  expect(e).to.be.instanceOf(NoSuchObjectError);
}
```

## 7. Layout variants that pass the gate

| Module | Unit test files | Notes |
|---|---|---|
| cmvp | `<Tag>ProducerTest.test.ts`, `<Class>ApiTest.ts`, `Common.ts` | fixtures in `test/unit/fixtures/` |
| avigilon | `<Tag>ProducerApiImplTest.ts`, `<Class>ImplTest.ts`, `<Class>ClientTest.ts` | helpers in `test/utils/`, fixtures in `test/fixtures/` |
| readyplayerme | none | stamp shows `unit` skipped; the module relies on its e2e suite |
| generator output | `test/unit/<Class>ApiTest.ts` | a factory smoke test; keep it and add real tests next to it |

File names do not affect discovery in `test/unit/`. A new module should still have unit tests for every
operation: they are the only tests that run without credentials.

## 8. Checks before handing over

Run from the module directory:

```bash
grep -rn "import \* as nock" test/                                  # expect no output
grep -rl "__dirname" test/ | xargs grep -L "import.meta.url"       # expect no output: __dirname derived from import.meta.url
grep -rnE "from '\.\.?/[^']*'" test/ | grep -v "\.js'"              # expect no output: relative imports end in .js
grep -rn "cleanAll" test/                                           # expect a hit: mocks are cleaned after each test
grep -n '"nock"' package.json                                       # expect a hit in devDependencies
```

Then run `zbb --slot <slot> test` and read the mocha summary: the number of passing tests should match what
you wrote, with none pending.
