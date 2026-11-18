# e2e tests - worked reference

Read this when writing `test/e2e/` for a connector, wiring test credentials, or working out why an e2e task
skipped or failed. Paths are relative to the repo root.

## Contents

1. Files to read first
2. What `describeModule` does
3. `constants.ts`
4. Secrets step by step
5. What reaches the test process
6. Test body patterns
7. Modes and module kinds
8. A module with a container and no credentials (x12)
9. Checks before handing over

## 1. Files to read first

| File | Shows |
|---|---|
| `package/readyplayerme/readyplayerme/test/e2e/readyplayerme.test.ts` | the smallest complete suite |
| `package/readyplayerme/readyplayerme/test/e2e/constants.ts` | constants with defaults and `hasCredentials()` |
| `package/avigilon/alta/access/test/e2e/avigilon-alta-access.test.ts` | a large suite: one `describeModule`, one `describe` per API operation |
| `package/x12/x12/test/e2e/helpers.ts` | `describeReceiver`: a secret-less replacement for `describeModule` |
| `package/x12/x12/test/e2e/constants.ts` | the variables gradle sets (`TEST_MODE`, `MODULE_DIR`, `CONTAINER_URL`) |
| `package/nist/cmvp/README.md`, section "Testing" | why a profile-less module has no e2e suite |

The generator writes `test/e2e/constants.ts` and `test/e2e/<name>.test.ts` with an empty `describeModule`
body; fill that file rather than adding a second one.

## 2. What `describeModule` does

`describeModule<T>(name, (client) => { ... }, errorDeserializer)` from `@zerobias-org/module-test-client`:

1. Reads the module's package name from `package.json` and lists the slot's secrets tagged with it.
2. With no secret, registers a single skipped test and returns. The task is green and nothing ran.
3. Otherwise opens one `describe` per secret, named `<name> [<mode>] (<secret>)`, and in its `before` hook
   builds the client for the current `TEST_MODE`:
   - `direct`: imports `<Class>Impl` from the module, builds a `ConnectionProfile` from the secret and
     calls `connect()`.
   - `docker`: registers the secret as the connection profile on the container gradle started, and returns
     a client that speaks the container's wire protocol.
4. Disconnects in `after`.

Consequences for the test file:

- `client` is a proxy that is filled in by the `before` hook. Using it at `describe` level (outside `it`,
  `before`, `after`) throws "Client not initialized".
- Connection handling is already exercised; do not add connect/disconnect tests to the e2e suite.
- The whole suite runs once per secret, so tests must not assume they are the only run against the account
  (create resources with unique names, clean up in `after`).

## 3. `constants.ts`

The only file in a new module that reads `process.env` (older modules also read it in unit tests). From
`package/readyplayerme/readyplayerme/test/e2e/constants.ts`:

```typescript
export const READYPLAYERME_APP_ID = process.env.READYPLAYERME_APP_ID ?? '';
export const READYPLAYERME_USER_ID = process.env.READYPLAYERME_USER_ID ?? '';

export function hasCredentials(): boolean {
  return !!READYPLAYERME_APP_ID;
}
```

- Name variables `<SERVICE>_<PURPOSE>` in upper case (`AVIGILON_ORG_ID`).
- Every constant has a default or `''`. A test that needs a value skips when it is empty, at suite level
  (`before(function () { if (!hasCredentials()) { this.skip(); } })`) or per test
  (`it('...', function () { if (!TEAM) { this.skip(); } ... })`). Use `function`, not an arrow, so `this`
  is the mocha context.
- Constants hold test data (ids, names of test resources). The credential itself is not a constant: it
  comes from the secret through `describeModule`.
- `src/` never reads `process.env`; configuration reaches the module through its connection profile.

## 4. Secrets step by step

```bash
cd package/<vendor>/[<suite>/]<product>

# create: key names are the property names of connectionProfile.yml
zbb --slot <slot> secret create <name> apiToken=<value> url=<value>

# confirm it is visible for this module (the filter value is the "name" field of package.json)
zbb --slot <slot> secret list --module @zerobias-org/module-<vendor>-<product>

# run
zbb --slot <slot> testDirect
```

- Run `secret create` inside the module directory: zbb tags the secret with the package name it finds
  there, and `describeModule` filters on that tag. A secret created elsewhere is not found.
- `zbb secret create` also accepts `@file.yml` (values from a file) and `--type @connectionProfile.yml`
  (uses the profile schema to lay out the keys). Keep any such file outside the repo.
- Secrets live in the slot's state directory on the developer's machine. Nothing about them belongs in the
  module tree: no `.env`, no `.env.example`, no profile JSON, no `dotenv` import.
- Several secrets for one module (for example two auth methods) run the suite once each. To run one,
  set `SECRET_NAME=<name>` for the test task.
- In a README or user guide, describe the profile fields and write the commands with placeholders. Do not
  paste values.

## 5. What reaches the test process

Gradle sets these for the e2e tasks; tests should not set them:

| Variable | Set for | Meaning |
|---|---|---|
| `TEST_MODE` | direct, docker | `direct` or `docker` (`hub` exists in the client, outside the gate) |
| `MODULE_DIR` | direct, docker | absolute path of the module package |
| `CONTAINER_URL` | docker | base URL of the container gradle started |

Everything else in `constants.ts` comes from the environment zbb builds for the command. zbb passes the
slot's and stack's declared variables, not the calling shell's: a variable exported in the shell, or one
that is not declared in the repo's `zbb.yaml`, does not arrive, and `zbb env set` rejects an undeclared
name. The root `zbb.yaml` declares no per-module test variables. So:

- Prefer tests that discover their data through the API (list, then use the first item) over tests that
  need an id from the environment.
- Where a constant is unavoidable, keep the default-or-skip pattern so the suite stays green without it, and
  tell the user that the variable has to be declared in `zbb.yaml` before `zbb env set <VAR> <value>` can
  provide it. Do not add declarations to `zbb.yaml` on your own; it is shared by every module.
- `zbb --slot <slot> env list` shows exactly what a command will receive.

## 6. Test body patterns

One `describe` per operation, named `<Tag>Api.<operation>`, with the success path and the error paths:

```typescript
describe('UserApi.get', () => {
  it('should return the user', async () => {
    const user = await client.getUserApi().get(userId);

    expect(user.id).to.equal(userId);
  });

  it('should throw NoSuchObjectError for an unknown id', async () => {
    try {
      await client.getUserApi().get('does-not-exist');
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(NoSuchObjectError);
    }
  });
});
```

- State that a test needs is created in `before` and removed in `after`, inside the `describeModule`
  callback, using the same `client`.
- Assert on shape and on values the test controls (the id it just created), not on totals in a shared
  account.
- The same body runs in direct and docker mode. Values that are class instances in direct mode can arrive in
  wire form in docker mode; where a test compares enums or dates, compare their string form
  (`String(value)`), as `names()` in `package/x12/x12/test/e2e/helpers.ts` does.
- No nock, no `new <Class>Impl()`, no `process.env` in the test file.

## 7. Modes and module kinds

| Module | `testDirect` | `testDocker` |
|---|---|---|
| Connector with a secret in the slot | runs | runs (needs Docker) |
| Connector, no secret | one skipped test, green | one skipped test, green |
| Plain, `"interface": true`, no `test/e2e` (cmvp) | task skipped | task skipped |
| Plain, `"interface": true`, with `test/e2e` | skips without a secret; with one, fails building a `ConnectionProfile` | fails: "module-container.json not found" |
| Container module with its own wrapper (x12) | not used | runs, no secret needed |

The second row is why a green e2e task is not evidence. The stamp's `tests.e2e` block
(`expected`, `ran`, `status`) and the mocha summary show whether tests ran; compare
`package/readyplayerme/readyplayerme/gate-stamp.json` (e2e passed) with `package/nist/cmvp/gate-stamp.json`
(e2e skipped, unit passed).

The fourth row is a tooling limit, not something to work around in the module: keep a profile-less module
free of `test/e2e` until `module-test-client` and the build support it, and verify against the live source
by hand as the cmvp README describes.

## 8. A module with a container and no credentials (x12)

x12's connection profile carries no credentials (the container reads its configuration at boot, not from
the profile), so a secret could only be invented. Its suite
replaces `describeModule` with `describeReceiver` (`package/x12/x12/test/e2e/helpers.ts`), which:

- posts an empty profile to `CONTAINER_URL/connections` and builds the client with
  `createWireProtocolClient<T>({ mode: 'docker', baseUrl, connectionId, moduleDir, errorDeserializer })`
  from `@zerobias-org/module-test-client`;
- throws in `before` when there is no container, so the suite fails instead of reporting a skipped run;
- still reads the environment only in `test/e2e/constants.ts`.

Use this pattern only for a module in the same situation (a container, no credentials). A connector with
credentials uses `describeModule`.

## 9. Checks before handing over

Run from the module directory:

```bash
ls test/e2e/*.test.ts                                              # e2e files end in .test.ts
grep -rn "process\.env" test/e2e/ | grep -v "/constants.ts:"       # expect no output
grep -rn "from 'nock'" test/e2e/                                   # expect no output
grep -rn "CoreError.deserialize" test/e2e/                         # expect a hit
grep -rn "dotenv" package.json src/ test/                          # expect no output
grep -q '"interface": true' package.json && test -d test/e2e && echo "plain module with test/e2e: the gate will fail"
```

Then run `zbb --slot <slot> testDirect` and confirm in the output that the suite name carries a secret name
and that tests passed rather than skipped.
