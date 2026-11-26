---
name: testing
description: Writes and fixes the tests of a Hub module in this repo - mocha unit tests under test/unit with nock and committed fixtures, and e2e tests under test/e2e with describeModule, constants.ts and zbb secrets. Use when asked to add or repair unit tests, mock HTTP with nock, load a fixture, write e2e or integration tests, set up test credentials or a zbb secret, or when testDirect, testDocker or "zbb test" fails or skips everything. Covers what a plain, profile-less module does instead.
---

# Testing a module

A module has two test layers. Both are mocha + chai, run through tsx, and both are hashed into the gate stamp
(everything under `test/`).

| Layer | Directory | Network | Run by | Mocha spec |
|---|---|---|---|---|
| Unit | `test/unit/` | none (nock) | `zbb test` | `mocha --recursive test/unit/` |
| e2e direct | `test/e2e/` | real API, impl in-process | `zbb testDirect` | `test/e2e/**/*.test.ts` |
| e2e docker | `test/e2e/` | real API, through the module container | `zbb testDocker` | `test/e2e/**/*.test.ts` |

Which layers a module has depends on its kind. Decide this first, because the wrong layout fails the gate:

| Module kind | Example | `test/unit` | `test/e2e` |
|---|---|---|---|
| Connector (has `connectionProfile.yml`) | `package/avigilon/alta/access`, `package/readyplayerme/readyplayerme` | nock against the vendor base URL | `describeModule<T>` + a `zbb secret` |
| Plain / profile-less (`"interface": true`, no profile) | `package/nist/cmvp` | nock against the real host | none - see "Plain modules" below |
| Container module with no credentials | `package/x12/x12` | none in TypeScript | own wrapper, docker mode only |

The gate checklist (which tasks must pass, the stamp, staging before the final run) is owned by the `gates` skill.
This skill covers the shape of the tests only.

## Running

Run from the module directory. `--slot` is a global zbb flag and may sit anywhere on the line.

```bash
zbb --slot <slot> test          # unit (+ test/integration/ when that directory exists)
zbb --slot <slot> testDirect    # e2e, TEST_MODE=direct
zbb --slot <slot> testDocker    # e2e, TEST_MODE=docker (builds and starts the container)
```

From the repo root the same tasks are `./gradlew :<vendor>:[<suite>:]<product>:test` (for example `:nist:cmvp:test`).
Each task is skipped when its directory does not exist, so a module without `test/unit` (readyplayerme) or
without `test/e2e` (cmvp) still passes; the stamp records that layer as `skipped`.

`.mocharc.json` holds only `{"extension":["ts"],"node-option":["import=tsx/esm"]}`. Gradle passes the spec, so
do not add a `spec` entry or npm test scripts.

## Unit tests

Full walk-through with excerpts: `references/unit-tests.md`. The rules that decide whether the tests work:

1. **Any `.ts` file under `test/unit/` is loaded**, whatever its name. The generator writes
   `test/unit/<Class>ApiTest.ts`; cmvp uses `CertificateProducerTest.test.ts`, avigilon `*Test.ts`. Pick one
   style per module. Helper files in the same directory (cmvp `Common.ts`) are loaded too, so keep them free
   of side effects.
2. **Add nock yourself.** The generator's devDependencies do not include it. Copy the exact pin from a
   gate-green module (`package/nist/cmvp/package.json`), then regenerate the lockfile.
3. **Import nock as a default import**: `import nock from 'nock';`. Nock 14 under ESM has no usable namespace
   import, so `import * as nock` breaks at runtime.
4. **Block the network and clean up**, so a missed mock fails the test instead of calling the vendor:

   ```typescript
   before(() => { nock.disableNetConnect(); });
   afterEach(() => { nock.cleanAll(); });
   after(() => { nock.enableNetConnect(); });
   ```

5. **Fixtures live in `test/unit/fixtures/` and are committed.** Load them relative to the test file with
   `fileURLToPath(import.meta.url)`; `__dirname` does not exist under ESM:

   ```typescript
   const __dirname = path.dirname(fileURLToPath(import.meta.url));
   const fixture = (name: string) => readFileSync(path.join(__dirname, 'fixtures', name), 'utf8');
   ```

6. **Relative imports end in `.js`** (`'../../src/CmvpImpl.js'`), because the module is ESM NodeNext.
7. **Assert on the generated value types, not on `Date`.** `format: date` fields are `DateFormat` and
   `format: date-time` fields are `DateTime`; `deep.equal` against `new Date(...)` fails. Compare the string
   form or the class:

   ```typescript
   expect(cert.validationDate).to.be.instanceOf(DateFormat);
   expect(cert.validationDate?.toString()).to.equal('2026-07-19');
   expect(cert.lastAcquired).to.be.instanceOf(DateTime);
   ```

   Enum fields are `EnumValue` instances: compare with `.eq(CmvpStatus.Active)`.
8. **Paged list operations return a page, not an array.** Through the API wrapper,
   `getCertificateApi().list(1, 50)` yields `count`, `pageNumber`, `pageSize` and `items`. When calling a
   producer directly, pass a `PagedResults` (avigilon `UserProducerApiImplTest.ts`).
9. **Cover each operation's success path and each error mapping** with the specific core error
   (`NoSuchObjectError`, `IllegalArgumentError`, `UnexpectedError`, `InvalidCredentialsError`), using
   `try { ...; assert.fail('should have thrown'); } catch (e) { expect(e).to.be.instanceOf(...) }`.
   `instanceOf(Error)` proves nothing about the mapping.
10. **Unit tests need no environment and no credentials.** They run inside the gate on any machine. Use
    literal dummy values; do not read `process.env` in new unit tests.

What to nock:

- Connector: nock the vendor base URL the client uses, mock the login call if `connect()` makes one, then
  call `connect(profile)` with dummy values (avigilon `test/unit/UserProducerApiImplTest.ts`).
- Plain module: there is no profile and no `connect()`. Construct the impl (`new CmvpImpl()`) and nock the
  real host, exported from the client (`test/unit/Common.ts` re-exports `DEFAULT_BASE_URL`). Do not write a
  connection test; the generated interface has no lifecycle methods.

When a unit test fails, fix the implementation or the mock. Do not skip unit tests or loosen the assertion.

## e2e tests (connector modules)

Full walk-through: `references/e2e-tests.md`. The shape, from
`package/readyplayerme/readyplayerme/test/e2e/readyplayerme.test.ts`:

```typescript
import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { ReadyPlayerMe } from '../../hub-sdk/generated/api/index.js';
import { READYPLAYERME_APP_ID, hasCredentials } from './constants.js';

describeModule<ReadyPlayerMe>('Ready Player Me Module', (client) => {
  before(function () { if (!hasCredentials()) { this.skip(); } });

  describe('UserApi.create', () => {
    it('should create a user successfully', async () => {
      const res = await client.getUserApi().create({ data: { applicationId: READYPLAYERME_APP_ID } });
      expect(res.data).to.have.property('id');
    });
  });
}, (data) => CoreError.deserialize(data));
```

- **File name ends in `.test.ts`.** Unlike unit tests, the e2e spec is the glob `test/e2e/**/*.test.ts`;
  a file with another suffix is silently not run.
- **`describeModule<T>` owns the client.** It connects once per secret and hands the same test body an
  in-process impl (direct) or a wire client (docker), selected by `TEST_MODE`, which gradle sets. Do not
  construct the impl, call `connect()`, or use nock in e2e. Use `client` only inside `it`/`before`/`after`.
- **`T` is a type-only import** from `hub-sdk/generated/api/index.js`.
- **Pass `(data) => CoreError.deserialize(data)` as the third argument.** Without it, errors that cross the
  wire in docker mode arrive as plain objects and `instanceOf(NoSuchObjectError)` fails.
- **`test/e2e/constants.ts` is the only file that reads `process.env`.** Tests import from `./constants.js`.
  One reader keeps the environment contract in a single place. Give every constant a default (`?? ''`) and
  skip when a required one is empty, as `hasCredentials()` does.

### Credentials: zbb secrets

Credentials are a `zbb secret` in the slot, never a `.env` file, never a committed file, and there is no
`dotenv` dependency. `describeModule` lists the secrets tagged with the module's package name and runs the
suite once per secret; the secret's keys are passed to the module as its connection profile, so key names are
the property names of `connectionProfile.yml` (see the `connection-profile` skill).

```bash
cd package/<vendor>/[<suite>/]<product>        # the secret is tagged with this package's name
zbb --slot <slot> secret create <name> apiToken=<value> url=<value>
zbb --slot <slot> secret create <name> @profile.yml      # or from a file kept outside the repo
zbb --slot <slot> secret list --module @zerobias-org/module-<vendor>-<product>
zbb --slot <slot> secret get <name> [key]
zbb --slot <slot> secret update <name> key=value
zbb --slot <slot> secret delete <name>
```

`--module` is a filter on `secret list`. Check `zbb --help` for the current surface before writing these
commands into a README.

With no secret for the module, `describeModule` records one skipped test and the task stays green, so a
"passing" `testDirect` may have run nothing. Read the stamp's `tests.e2e` counts (or the mocha output) to
confirm the suite really ran.

### Direct and docker modes

| | direct (`testDirect`) | docker (`testDocker`) |
|---|---|---|
| Client | `<Class>Impl` loaded from `src/`, connected with the secret | wire client against the container gradle starts |
| Needs | `test/e2e`, a secret | `test/e2e`, a secret, Docker, a module that builds an image |
| Proves | the implementation | the packaged container and the wire names |

Write one test body for both. When direct passes and docker fails, look at serialization differences (enum
values and dates arrive in wire form) and at the missing error deserializer before suspecting logic.

## Plain modules: no `test/e2e`

A profile-less module (`"interface": true` in `package.json`, no `connectionProfile.yml`) keeps unit tests
only. The reasons, from the cmvp repair:

- `describeModule` needs a secret; without one it skips, and with one direct mode fails because it builds a
  `ConnectionProfile` the module does not have.
- `testDocker` runs whenever `test/e2e` exists, but an interface module starts no container, so the task
  fails with "module-container.json not found".

So adding `test/e2e` to such a module turns a green gate red. Cover the behaviour in unit tests against
fixtures, and check the live source by hand through the module's factory (`newCmvp()`); the cmvp README
documents this under "Testing". Do not invent a secret to satisfy the harness.

A module that has a container but no credentials (x12) writes its own wrapper instead of `describeModule`:
`describeReceiver` in `package/x12/x12/test/e2e/helpers.ts` connects with an empty profile through
`createWireProtocolClient` and fails rather than skips when the container is missing.

## `test/integration/`

The build runs `mocha --recursive test/integration/` as part of `test` when the directory exists, so an old
suite there does not break anything. New work goes to `test/unit` (mocked) or `test/e2e` (real API); do not
create `test/integration`, and do not delete a working one as a side effect of another change.

## Fixtures

- Take fixture content from a real response of the vendor (trimmed), so the mapper is tested against the
  actual shape. Name files after what they show: `listing-3-rows.html`, `detail-5501-withheld.html`,
  `error-404-not-found.json`.
- Remove personal data and secrets before committing: real names, e-mail addresses, phone numbers, tokens,
  internal ids. Fixtures are committed and published with the repo history.
- Add a fixture for each edge the mapper handles (missing optional fields, error pages returned with status
  200, count mismatches). cmvp's `detail-5501-withheld.html` exists because live entries lacked fields the
  spec had marked required.
- After adding or changing files under `test/`, `git add` them before the final gate run: the stamp hashes
  tracked files only (see the `gates` skill).

## Quick diagnosis

| Symptom | Cause |
|---|---|
| `nock is not a function` / namespace import error | `import * as nock` - use the default import |
| `__dirname is not defined` | ESM - derive it from `import.meta.url` |
| `Cannot find module './constants'` | relative import without `.js` |
| Unit test hits the real API or fails with a NetConnect error | URL or path of the mock differs from what the client calls; compare against the client's constants |
| `deep.equal` fails on a date field | value is `DateFormat`/`DateTime`, not `Date` |
| e2e task green, zero tests ran | no secret for this module in the slot, or the file is not `*.test.ts` |
| `instanceOf(NoSuchObjectError)` fails only in docker mode | third argument of `describeModule` missing |
| `testDocker`: "module-container.json not found" | `test/e2e` exists on an `"interface": true` module |
| e2e constant is always empty | the variable does not reach the test process; see `references/e2e-tests.md` |

## Related skills

- `gates` - which test tasks the gate runs, stamp fields, stage-before-gate.
- `connection-profile` - profile fields that the secret's keys must match; connector versus plain decision.
- `implementation` and `mapping` - the error mapping and value types these tests assert on.
- `scaffolding` - what the generator writes into `test/` and the post-scaffold fixes.
