# Error handling in `src/`

Read this when writing `handleAxiosError`, choosing a core error, or reviewing error paths.

## Contents

- [Status to core error](#status-to-core-error)
- [Core error constructors](#core-error-constructors)
- [Reference `handleAxiosError`](#reference-handleaxioserror)
- [Calling it](#calling-it)
- [Per-call handling or interceptor](#per-call-handling-or-interceptor)
- [What the real handlers get wrong](#what-the-real-handlers-get-wrong)

## Status to core error

One table for every module. All classes are exported by `@zerobias-org/types-core-js`.

| Upstream condition | Core error | Used in |
|---|---|---|
| 400, 422 | `InvalidInputError(type, value)` | avigilon, readyplayerme (400) |
| 401 | `InvalidCredentialsError()` | avigilon |
| 403 | `UnauthorizedError()` | avigilon |
| 403 whose message says rate limit | `RateLimitExceededError()` | avigilon |
| 404 | `NoSuchObjectError(type, id)` | cmvp, avigilon |
| 429 | `RateLimitExceededError()` | cmvp, avigilon |
| 408, 504, axios code `ECONNABORTED` | `TimeoutError(Duration.fromMilliseconds(ms))` | cmvp |
| any other status, or no response at all | `UnexpectedError(message, status)` | cmvp, avigilon |
| missing or empty credential in the profile | `InvalidCredentialsError()` | readyplayerme, avigilon |
| operation called before `connect()` | `NotConnectedError()` | readyplayerme, avigilon |
| 2xx with a body that is not the expected shape | `UnexpectedError(message)` | avigilon lists |
| 2xx whose body says the object does not exist | `NoSuchObjectError(type, id)` | cmvp `get()` |
| required field missing in a response (mapper) | `InvalidStateError(message)` | cmvp, avigilon `ensureProperties` |

Notes:

- `ForbiddenError` (403) and `UnauthenticatedError` (401) also exist in the package. No module in this repo
  throws them; keep to the table so that the same upstream status yields the same error in every module.
- `NotFoundError(message)` exists for a 404 where no type and id are known. Prefer `NoSuchObjectError`,
  because the call site almost always knows both.
- Pass the timeout the Client was created with to `TimeoutError`, so the error reports the real limit.

## Core error constructors

Leading parameters only. Every constructor also accepts optional trailing `timestamp?: DateTime` and
`cause?: Error`; module code does not pass them.

| Error | Constructor |
|---|---|
| `InvalidCredentialsError` | `()` |
| `UnauthorizedError` | `()` |
| `UnauthenticatedError` | `()` |
| `ForbiddenError` | `()` |
| `NotConnectedError` | `()` |
| `NoSuchObjectError` | `(type: string, id: string)` |
| `NotFoundError` | `(obj: string)` |
| `InvalidInputError` | `(type: string, value: any, examples?: Array<string \| number>)` |
| `ParameterRequiredError` | `(paramName: string)` |
| `IllegalArgumentError` | `(msg: string)` |
| `InvalidStateError` | `(msg: string)` |
| `ConflictError` | `(msg: string)` |
| `RateLimitExceededError` | `()` (optional `timestamp`, `callCount`, `duration` when known) |
| `TimeoutError` | `(timeout: Duration)` |
| `UnexpectedError` | `(msg: string, statusCode?: number)` |
| `ResultLimitExceededError` | `(requested: number, returned: number)` |

If a constructor here does not compile, read the class's `.d.ts` under
`node_modules/@zerobias-org/types-core-js/dist/src/errors/` in the module; the installed package is the
authority.

## Reference `handleAxiosError`

`package/nist/cmvp/src/util.ts`, complete:

```typescript
export function handleAxiosError(error: any, resourceType: string, resourceId: string): never {
  const status = error.response?.status ?? error.status;

  if (status === 404) {
    throw new NoSuchObjectError(resourceType, resourceId);
  }
  if (status === 429) {
    throw new RateLimitExceededError();
  }
  if (error.code === 'ECONNABORTED' || status === 408 || status === 504) {
    throw new TimeoutError(Duration.fromMilliseconds(30000));
  }
  throw new UnexpectedError(`CMVP request failed: ${error.message ?? 'unknown error'}`, status ?? 500);
}
```

Why it is shaped this way:

- Return type `never`: TypeScript then accepts `let html: string; try { html = ... } catch (e) {
  handleAxiosError(...) }` followed by a use of `html`, and a `.catch(handleAxiosError)` keeps the resolved
  type of the request.
- `error.response?.status ?? error.status`: a network failure has no `response`, so the status may be
  undefined and the function still has to end in a throw.
- Resource type and id are parameters, so a 404 says which object was missing.
- The message uses `error.message`, not the error object or the response body, which can carry request
  headers and upstream internals.

CMVP is a public registry with no credentials, so its handler has no 400/401/403 branches. A connector adds
them from the table above, before the final `UnexpectedError`:

```typescript
if (status === 400 || status === 422) {
  throw new InvalidInputError(resourceType, error.response?.data?.message ?? error.message);
}
if (status === 401) {
  throw new InvalidCredentialsError();
}
if (status === 403) {
  throw new UnauthorizedError();
}
```

## Calling it

Two call styles are in use; both rely on the `never` return type.

`try/catch`, when the handler needs arguments (cmvp):

```typescript
let html: string;
try {
  const response = await this.client.httpClient.get<string>(`${DETAIL_PATH_PREFIX}${certificateNumber}`);
  html = response.data;
} catch (error: any) {
  handleAxiosError(error, 'cmvp-certificate', certificateNumber);
}
```

`.catch(...)`, when the handler takes only the error (readyplayerme):

```typescript
const { data } = await apiClient
  .request({ url: `/assets/${assetId}`, method: 'post', data: JSON.stringify(updateAssetRequest) })
  .catch(handleAxiosError);
```

To keep the `.catch` style with type and id, wrap the call:
`.catch((e) => handleAxiosError(e, 'asset', assetId))`.

## Per-call handling or interceptor

Avigilon registers the mapping once as a response interceptor on the axios instance
(`package/avigilon/alta/access/src/AvigilonAltaAccessClient.ts`, `handleError`), so its producers contain no
error code at all. The price is visible in the same file: the interceptor cannot know what was requested and
throws `NoSuchObjectError('resource', 'unknown')`.

Choose one per module:

| | Per-call `handleAxiosError` | Response interceptor |
|---|---|---|
| 404 names the object | yes | no |
| producer code | one `catch` per request | none |
| forgotten call | request error escapes as a raw axios error | not possible |

Do not use both. An interceptor has already replaced the axios error with a core error by the time a
per-call handler runs; that core error has no `.response`, so the handler reads an undefined status and
rethrows it as `UnexpectedError(..., 500)`, hiding the real 404 or 429.

New modules use per-call handling.

## What the real handlers get wrong

Known gaps in the reference modules. Do not copy them, and fix them when you touch the file.

| File | Gap | Should be |
|---|---|---|
| `package/readyplayerme/readyplayerme/src/util.ts` | ends in `console.log(e)` and `throw new Error('Error handler not implemented yet')` | final `UnexpectedError(message, status)`; no logging of the axios error |
| same | 404 mapped to `NotFoundError` with the response body as message | `NoSuchObjectError(type, id)` |
| same | no 401/403/429 branches | as in the table |
| `package/avigilon/alta/access/src/AccessImpl.ts` | `refresh()` throws `new Error('Method not implemented.')` | delegate to the Client's `refresh()` |
| `package/avigilon/alta/access/src/AvigilonAltaAccessClient.ts` | `error.response?.status \|\| 500` hides timeouts and network failures as 500 | timeout branch as in cmvp |
