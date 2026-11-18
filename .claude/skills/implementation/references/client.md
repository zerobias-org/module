# The Client class

Read this when writing or changing `src/<Name>Client.ts`, `connect()`, `isConnected()`, `disconnect()` or
`refresh()`.

## Contents

- [The shape](#the-shape)
- [Plain module: `CmvpClient`](#plain-module-cmvpclient)
- [Connector with a static key: `ReadyPlayerMeClient`](#connector-with-a-static-key-readyplayermeclient)
- [Connector with login and expiring token: `AvigilonAltaAccessClient`](#connector-with-login-and-expiring-token-avigilonaltaaccessclient)
- [How the Impl uses it](#how-the-impl-uses-it)
- [Rules](#rules)

## The shape

All three gate-green modules use the same division of work:

- The Client creates and owns the axios instance (or instances) and any credential or token.
- The Client exposes that instance to producers. It does not wrap it in its own `get`/`post` helpers and it
  has no resource methods such as `listUsers`.
- Producers call axios directly through the exposed instance.
- The Impl creates one Client and passes it to every producer.

What differs between modules is only when the instance is created and how it is exposed.

| Module | Instance created | Exposed as | Not-connected guard |
|---|---|---|---|
| cmvp | constructor | `public readonly httpClient` | none needed |
| readyplayerme | `connect()` | getters `apiClient`, `modelClient` | getter throws `NotConnectedError` |
| avigilon | `connect()` after login | method `getHttpClient()` | method throws `NotConnectedError` |

For a new connector use the getter form: it reads as a property in producers
(`const { apiClient } = this.client;`) and is evaluated on each call.

## Plain module: `CmvpClient`

`package/nist/cmvp/src/CmvpClient.ts`, 26 lines. The registry is public, so there is no profile and no
lifecycle; the client is usable as soon as it is constructed.

```typescript
export class CmvpClient {
  public readonly baseUrl: string;

  public readonly httpClient: AxiosInstance;

  constructor(baseUrl: string = DEFAULT_BASE_URL) {
    this.baseUrl = baseUrl;
    this.httpClient = axios.create({
      baseURL: baseUrl,
      timeout: 30000,
      headers: { 'User-Agent': 'zerobias-module-nist-cmvp' },
      validateStatus: (status) => status >= 200 && status < 300,
    });
  }
}
```

Points to copy:

- The base URL and the path constants are exported from this file (`DEFAULT_BASE_URL`, `LISTING_PATH`,
  `DETAIL_PATH_PREFIX`) and reused by the producer and the mappers.
- The constructor takes the base URL with a default. It is an argument, not an environment read.
- A plain module has no `connect`, `isConnected`, `disconnect`, `metadata` or `isSupported` anywhere,
  neither on the Client nor on the Impl.

## Connector with a static key: `ReadyPlayerMeClient`

`package/readyplayerme/readyplayerme/src/ReadyPlayerMeClient.ts`. The profile carries one field,
`apiToken`, which the vendor expects in an `x-api-key` header.

- `connect(connectionProfile)` throws `InvalidCredentialsError` when `apiToken` is missing, stores the key,
  and creates two axios instances (API host and model host), both with the key header and the 2xx-only
  `validateStatus`. It returns `Promise<void>` because there is no connection state.
- `connect()` makes no request. A wrong key therefore surfaces on the first operation, as a 401 mapped by the
  error handler. Add a cheap authenticated call to `connect()` only when the vendor has one and you want the
  failure at connect time.
- The `apiClient` and `modelClient` getters throw `NotConnectedError` until `connect()` has run.
- `isConnected()` returns `!!this._apiKey`: a state check with no network call.
- `disconnect()` sets the key and both instances to `undefined`, so later calls hit the getter's guard.
- `metadata()` returns `new ConnectionMetadata(ConnectionStatus.Down)` and `isSupported()` returns
  `OperationSupportStatus.Maybe`; the Impl forwards to them. In avigilon the same two placeholders live on
  the Impl instead. Either place is fine; the values are fixed.

## Connector with login and expiring token: `AvigilonAltaAccessClient`

`package/avigilon/alta/access/src/AvigilonAltaAccessClient.ts`. The profile carries `email`, `password` and
an optional `totpCode`; the API issues a token with an expiry.

- `connect(profile)` validates the fields, posts to the login endpoint with a temporary axios instance,
  stores `accessToken` and `tokenExpiresAt`, then creates the authenticated instance with the
  `Authorization: Bearer` header. It returns a `ConnectionState` (`accessToken`, `expiresIn` in seconds),
  which is why the generated interface declares `connect(...): Promise<ConnectionState>` for this module.
- `isConnected()` is a state check plus an expiry comparison; still no network call.
- `refresh()` on the Client posts to the refresh endpoint, stores the new token, updates the default
  `Authorization` header on the existing instance and returns a new `ConnectionState`.
- `disconnect()` clears the instance, the token and the expiry.
- Error mapping is a response interceptor registered in `connect()`. See `errors.md` in this folder for the
  trade-off and why new modules handle errors per call instead.

Known debt in this module, not to be copied: `AccessImpl.refresh(profile, state)` throws
`new Error('Method not implemented.')` instead of delegating to the Client's `refresh()`.

Which fields belong in a profile or a state, and when a state is needed at all, is decided in the
`connection-profile` skill.

## How the Impl uses it

The Impl holds the Client in a private field and gives it to each producer:

```typescript
export class ReadyPlayerMeImpl implements ReadyPlayerMeConnector {
  private client = new ReadyPlayerMeClient();

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    return this.client.connect(connectionProfile);
  }

  getUserApi(): UserApi {
    return wrapUserProducer(new UserProducerImpl(this.client));
  }
}
```

Two producer-creation styles exist:

- New producer on every getter call (cmvp, readyplayerme). Simple, and safe because producers are
  stateless.
- Lazily created and cached in a field (avigilon). Required there because its producers read
  `client.getHttpClient()` in their constructor, which throws before `connect()`.

Prefer the first style together with producers that read the axios instance per call.

## Rules

- One Client class per module, in `src/<Name>Client.ts`; no second axios instance created in a producer.
- `axios.create` sets `baseURL`, `timeout` and `validateStatus: (status) => status >= 200 && status < 300`.
- Credentials come only from the `ConnectionProfile` argument of `connect()`. No `process.env`, no literals.
- Missing credential: `InvalidCredentialsError`. Use before connect: `NotConnectedError`.
- `isConnected()` checks local state. A network probe on every check slows each operation and spends the
  vendor's rate limit.
- `disconnect()` drops every credential and instance the Client holds, so a disconnected module cannot
  keep calling the API.
- No resource or business methods on the Client.
