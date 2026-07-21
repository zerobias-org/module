# NIST CMVP Module

Data connector for NIST's [Cryptographic Module Validation Program](https://csrc.nist.gov/projects/cryptographic-module-validation-program)
(CMVP) registry — the authoritative list of FIPS 140-1/140-2/140-3 validated
cryptographic modules.

The registry is a **public web page with no REST API** — this module scrapes
it with [cheerio](https://cheerio.js.org/). It's the first HTML-scraping
module in this org (every other module wraps a documented API); see the
connector's design docs for the full rationale.

## Authentication and authorization

**None.** The registry is fully public and unauthenticated. `connectionProfile.yml`
is intentionally near-empty — `connect()` performs a reachability check
against the listing page rather than presenting credentials. The one
optional field, `baseUrl`, exists only to point the module at a mirror or
adapt to a future URL change; omit it to use the real NIST site.

```typescript
import { newCmvp } from '@zerobias-org/module-nist-cmvp';

const api = newCmvp();
await api.connect({}); // no credentials required
```

## Usage

```typescript
import { newCmvp } from '@zerobias-org/module-nist-cmvp';

const api = newCmvp();
await api.connect({});

const certApi = api.getCertificateApi();

// List validated modules (client-side paged — the registry itself has no
// native pagination, see the module's pagination-approach note in src/).
const page = await certApi.list(1, 50);

// Get one certificate's full detail record, including the verbatim Caveat
// text and a captured HTML fragment of the source entry.
const cert = await certApi.get('5425');
console.log(cert.caveatText); // read this before treating a cert as valid for a config
```

**On `caveatText`:** it states the *specific conditions* (OS/software
version, configuration, key types) under which a validation actually
applies. A certificate is not blanket-valid — always check `caveatText`
before citing a module as FIPS-validated for a particular deployment.

## Notes for reviewers

- `productId` / `vendorId` on `Certificate` are shipped but intentionally
  left unset in this version — catalog matching (fuzzy-matching CMVP's
  printed vendor/module names against the ZeroBias catalog) is an explicit
  follow-up, not in scope here.
- The listing operation cross-checks its parsed row count against the
  page's own reported total (`#divResults[data-total-records]`) and throws
  rather than silently under-reporting on a mismatch.

## Test

This module is built and tested via Gradle + zbb. From the repo root:

```bash
./gradlew :nist:cmvp:build   # validate → generate → compile → buildImage

# Local test modes (run from this module dir, inside a loaded zbb slot)
zbb test --slot local         # unit tests (nock-mocked, no network)
zbb testDirect --slot local   # e2e direct — hits the real live registry
zbb testDocker --slot local   # e2e docker (container, wire protocol)
zbb gate --slot local         # full gate (writes gate-stamp.json — commit it)
```

`describeModule<T>` e2e tests require at least one secret registered for
the module (even though no credentials are needed):

```bash
zbb secret create cmvp-connection --module @zerobias-org/module-nist-cmvp \
  baseUrl=https://csrc.nist.gov
```
