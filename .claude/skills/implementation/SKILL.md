---
name: implementation
description: Writes and reviews the TypeScript source of a module under package/<vendor>/[<suite>/]<product>/src - the Impl class, the Client, producers, pagination of list operations, HTTP error handling, src/index.ts exports, logging and comments. Use when implementing or fixing an operation, a producer, the client, connect/disconnect, a paginated list, handleAxiosError, or when a review asks whether src/ follows the repo's conventions.
---

# Module implementation (`src/`)

How module source is written in this repo, taken from the modules that pass the gate:

| Module | Kind | Read it for |
|---|---|---|
| `package/nist/cmvp/src` | plain, no connection profile | smallest complete module, `handleAxiosError`, `results.ingest(...)` |
| `package/readyplayerme/readyplayerme/src` | connector, API key | connector lifecycle, Client getters, per-call `.catch(handleAxiosError)` |
| `package/avigilon/alta/access/src` | connector, login + token | `connect()` returning a `ConnectionState`, offset/limit lists, response interceptor |

Read the closest one before writing code. Where this text and those files disagree, the files win.

Owned elsewhere, do not restate here: `api.yml` rules (the `api-spec` skill), mappers and type conversion
(the `mapping` skill), tests (the `testing` skill), profile and state schemas (the `connection-profile`
skill), gate commands (the `gates` and `build-lifecycle` skills).

## Before writing code

1. Finish `api.yml` and regenerate first. Every signature in `src/` comes from `generated/api/` and
   `generated/model/`; a producer written before generation is a guess. The `build-lifecycle` skill has the
   command.
2. Open `generated/api/index.ts` and copy the method signatures from the `<Tag>ProducerApi` interface you
   are implementing: parameter order, `PagedResults<T>` versus `Array<T>`, and return types are decided by
   codegen, not by you.
3. Never edit anything under `generated/`; it is overwritten on the next build.

## Layout and naming

`src/` is flat. There is no `src/impl/` for the module's own producers. The one exception is a
DataProducer implementation, which lives in `src/impl/dataproducer/` (see the
`dataproducer-implementation` skill).

| File | Class / content | Real example |
|---|---|---|
| `src/index.ts` | factory `new<Class>()` plus generated re-exports | `package/nist/cmvp/src/index.ts` |
| `src/<Class>Impl.ts` | implements the generated module interface | `CmvpImpl.ts`, `ReadyPlayerMeImpl.ts`, `AccessImpl.ts` |
| `src/<Name>Client.ts` | owns the axios instance and connection state | `CmvpClient.ts`, `ReadyPlayerMeClient.ts` |
| `src/<Tag>ProducerApiImpl.ts` | implements `<Tag>ProducerApi` | avigilon |
| `src/<Tag>ProducerImpl.ts` | same thing, older suffix | cmvp, readyplayerme |
| `src/mappers.ts` | raw response to generated model (`mapping` skill) | cmvp, readyplayerme (`Mappers.ts` in avigilon) |
| `src/util.ts` | `handleAxiosError`, `optional`, `ensureProperties` | `package/nist/cmvp/src/util.ts` |

Both producer suffixes pass the gate. In a new module use `<Tag>ProducerApiImpl` (the class name then
states the generated interface it implements) and `mappers.ts`; in an existing module keep the suffix
it already has, because mixing the two in one package makes the Impl's
imports hard to scan. `<Tag>` is the api.yml tag, so one producer file per tag.

## Imports

`tsconfig.json` uses `module: NodeNext`, so every relative import carries the `.js` suffix even though the
file on disk is `.ts`. Without it the compile step fails to resolve the module.

```typescript
import { CertificateProducerApi } from '../generated/api/index.js';
import { Certificate } from '../generated/model/index.js';
import { CmvpClient } from './CmvpClient.js';
```

Package imports (`@zerobias-org/types-core-js`, `axios`) take no suffix. Core errors, `PagedResults`,
`ConnectionMetadata` and the other core types come from `@zerobias-org/types-core-js`.

## The Impl class

The Impl is a thin wrapper: it creates the Client, forwards lifecycle calls to it, and returns one wrapped
producer per tag. It holds no HTTP code, so the connection logic can be tested and changed in one place.

Each tag gets a getter that wraps the producer with the generated `wrap<Tag>Producer` function:

```typescript
getCertificateApi(): CertificateApi {
  return wrapCertificateProducer(new CertificateProducerImpl(this.client));
}
```

**Connector module** (has `connectionProfile.yml`, `zb.typescript-connector` in `build.gradle.kts`): the
class `implements <Class>Connector` and delegates `connect`, `isConnected`, `disconnect`, `metadata` and
`isSupported` to the Client. Copy `package/readyplayerme/readyplayerme/src/ReadyPlayerMeImpl.ts`.

- `metadata()` returns `new ConnectionMetadata(ConnectionStatus.Down)` and `isSupported()` returns
  `OperationSupportStatus.Maybe`. Both are placeholders the platform replaces, so do not add logic to them.
- `connect()` returns `Promise<void>` when there is no connection state (readyplayerme) and
  `Promise<ConnectionState>` when the module has a `connectionState.yml` (avigilon). The generated
  interface tells you which.
- `refresh(profile, state)` exists on the interface only when there is a connection state.

**Plain module** (no `connectionProfile.yml`, `zb.typescript`, `"interface": true` in `package.json`): the
class `implements <Class>` (the generated interface without `Connector`), has no lifecycle methods at all,
and contains only the producer getters. Copy `package/nist/cmvp/src/CmvpImpl.ts` (11 lines). The
`scaffolding` skill covers turning a generated stub into this shape.

## The Client

One shape is used by all three modules: the Client owns the axios instance and hands it to producers.
It has no `get`/`list`/`create` business methods; those belong to producers, so that one tag's operations
live in one file.

| | Connector | Plain |
|---|---|---|
| axios instance created | inside `connect(profile)` | in the constructor |
| exposed as | getter that throws `NotConnectedError` until connected | `public readonly httpClient` |
| `isConnected()` | a state check (`!!this._apiKey`, or token not expired); no API call | not present |
| `disconnect()` | clears the token and the axios instance | not present |
| missing credential | `throw new InvalidCredentialsError()` | not applicable |

Rules that hold for both:

- Set `baseURL`, a `timeout` and `validateStatus: (status) => status >= 200 && status < 300` in
  `axios.create`, so that every non-2xx response reaches the error handler instead of being mapped as data.
- Read credentials only from the `ConnectionProfile` passed to `connect()`. Profile fields are flat
  (`connectionProfile.apiToken`), named as in `connectionProfile.yml`.
- Keep URL constants (base URL, path prefixes) exported from the Client file so tests and mappers can
  reuse them, as `CmvpClient.ts` does.

Walkthrough of the three real Clients, including token login and refresh: `references/client.md`.

## Producers

A producer `implements <Tag>ProducerApi`, receives the Client in its constructor and does three things per
operation: call the API, pass failures to the error handler, return the mapper's result.

```typescript
export class UserProducerImpl implements UserProducerApi {
  constructor(private client: ReadyPlayerMeClient) {}

  async getToken(userId: string, partner: string): Promise<TokenResponse> {
    const { apiClient } = this.client;
    const { data } = await apiClient
      .request({ url: '/auth/token', method: 'get', params: { userId, partner } })
      .catch(handleAxiosError);
    return toTokenResponse(data);
  }
}
```

- Producers take no credentials, tokens or base URLs as parameters; everything connection-related comes
  from the Client. A producer that builds its own axios instance bypasses `disconnect()` and the
  not-connected check.
- Return generated model types. `Promise<any>` hides mapping mistakes from the compiler.
- Do the conversion in `mappers.ts`, not inline in the producer (`mapping` skill).
- When a `200` can still mean "not found" (cmvp returns an HTML page without the certificate), check the
  body and throw `NoSuchObjectError` yourself; see `get()` in
  `package/nist/cmvp/src/CertificateProducerImpl.ts`.
- If the producer reads the axios instance in its constructor (avigilon: `client.getHttpClient()`), the
  Impl has to create the producer after `connect()`. Reading it per call (readyplayerme:
  `const { apiClient } = this.client`) has no such ordering constraint and is the safer default.

## List operations and pagination

Codegen decides the signature. A list operation gets `list(results: PagedResults<T>, ...): Promise<void>`
only when its `200` response declares the `link` header in `api.yml`; without the header it is generated as
`Promise<Array<T>>` and cannot be paged. If you expected `PagedResults` and got an array, fix `api.yml`
(`api-spec` skill) and regenerate rather than paging by hand.

With `PagedResults`, fill the object instead of returning a value. Pick the one mode the upstream API
supports:

| Mode | Upstream API | What the producer does | Real code |
|---|---|---|---|
| 1 offset/limit | accepts page or offset parameters | converts `results.pageNumber`/`pageSize` to request params, sets `results.items` and `results.count` | `package/avigilon/alta/access/src/SiteProducerApiImpl.ts` |
| 2 client-side | returns everything in one response | `results.ingest(all, results.pageNumber, results.pageSize)` | `package/nist/cmvp/src/CertificateProducerImpl.ts` |
| 3 token | returns a cursor for the next page | sends `results.pageToken`, sets `results.items` and the next `results.pageToken` | `package/microsoft/azure/azureresourcegraph/src/ResourceProducerApiImpl.ts` |

`results.ingest(...)` is the supported way to page a full listing: it slices the array, sets `count`, and
rejects an out-of-range page. Do not also assign `items` or `count` after calling it.

Read `references/pagination.md` before implementing any list operation; it has the code for each mode, what
`ingest` does, and the mistakes that produce wrong pages.

## Errors

Throw core errors from `@zerobias-org/types-core-js`, never a bare `Error`: the platform serialises core
errors with their status and fields, and a generic `Error` reaches the caller as an opaque failure.

| Upstream status | Core error | Constructor |
|---|---|---|
| 400, 422 | `InvalidInputError` | `(type, value)` |
| 401 | `InvalidCredentialsError` | `()` |
| 403 | `UnauthorizedError` | `()` |
| 404 | `NoSuchObjectError` | `(type, id)` |
| 429 | `RateLimitExceededError` | `()` |
| 408, 504, axios `ECONNABORTED` | `TimeoutError` | `(Duration.fromMilliseconds(<client timeout>))` |
| anything else | `UnexpectedError` | `(message, status)` |
| not connected | `NotConnectedError` | `()` |

Put the mapping in one function, `handleAxiosError` in `src/util.ts`, typed to return `never` so the
compiler knows the call does not fall through. `package/nist/cmvp/src/util.ts` is the reference
implementation; it takes the resource type and id so a 404 names what was missing:

```typescript
} catch (error: any) {
  handleAxiosError(error, 'cmvp-certificate', certificateNumber);
}
```

Use one mechanism per module: either per-call handling (cmvp `try/catch`, readyplayerme
`.catch(handleAxiosError)`) or a response interceptor on the axios instance (avigilon). Combining them
breaks the mapping: the interceptor throws a core error that has no `.response`, and the per-call handler
then turns it into `UnexpectedError` with status 500. Prefer per-call handling in new modules because only
the call site knows the resource type and id.

Full table with every verified constructor, the reference `handleAxiosError`, and the known gaps in the
three real handlers: `references/errors.md`.

## `src/index.ts`

```typescript
import { CmvpImpl } from './CmvpImpl.js';

export * from '../generated/api/index.js';
export * from '../generated/model/index.js';

export function newCmvp() {
  return new CmvpImpl();
}
```

- The factory is a named export called `new<Class>`, the entry point consumers use to create the module.
  No default export.
- Re-export both generated barrels with `export *`, so consumers get the profile, API interfaces, models and
  enums without the list needing maintenance when a model is added.
- An explicit return type on the factory is optional: readyplayerme declares
  `: ReadyPlayerMeConnector`, cmvp lets TypeScript infer it.
- Export the Impl, Client or mappers only when a real consumer needs them. Avigilon does; the other two do
  not.
- `package.json` has `"main": "dist/src/index.js"` because `outDir` is `dist` and the `src/` prefix is kept.

## No environment access, no secrets

- No `process.env` anywhere in `src/`. A module runs inside a Hub Node where its only input is the
  connection profile and the operation arguments; an environment read works on a laptop and silently
  returns `undefined` in production. Test-only values belong under `test/` (`testing` skill).
- No credentials, tokens, tenant ids or customer URLs as literals in `src/` or in comments. Public base
  URLs of the vendor API are fine.
- Do not put credentials in error messages or log lines. An axios error object contains the request
  headers, so logging it whole writes the API key or bearer token to the log.
- Use HTTPS base URLs.

Run the check script on a module directory before the gate; it reports `process.env`, relative imports
without `.js`, `console.*` calls and bare `throw new Error(` in `src/`:

```bash
bash ${CLAUDE_SKILL_DIR}/scripts/check-src.sh package/nist/cmvp
```

It exits non-zero when it finds something. Two of the reference modules still carry findings
(`console.log(e)` and an unfinished handler in readyplayerme's `util.ts`, a `Method not implemented` stub in
avigilon's `refresh`); treat those as debt, not as patterns to copy.

## Logging

Most producers need no logging: failures surface as core errors and the platform records the call. When a
log line is useful, take a logger from `@zerobias-org/logger`, which the reference modules already
depend on, the way `package/avigilon/alta/access/src/ScheduleProducerApiImpl.ts` does:

```typescript
import { LoggerEngine } from '@zerobias-org/logger';

private readonly logger = LoggerEngine.root();
```

Do not use `console.*` in `src/`: it bypasses the node's log levels and formatting. Log identifiers and
status codes, not response bodies or request headers.

## Comments

- Comment why, not what. A comment that repeats the method name or the next line is noise that goes stale.
- Keep a comment where the upstream API behaves unexpectedly, such as the note in cmvp's `list()` that the
  registry has no native pagination, or the casing note above `toEnumKey` in its `mappers.ts`.
- JSDoc is one or two lines on exported helpers and on the Client class; do not add `@param`/`@returns`
  blocks that restate the TypeScript signature.
- No section banners, no numbered "Step 1" comments, no commented-out code.
- A `TODO` states what is missing and why it cannot be done now.

## Finishing

1. Run `${CLAUDE_SKILL_DIR}/scripts/check-src.sh <module dir>` and clear its findings.
2. Check that every method of every `<Tag>ProducerApi` in `generated/api/index.ts` is implemented with the
   generated signature and no `any` return type.
3. Write or update the tests (`testing` skill), then run the gate (`gates` skill).
