# NIST CMVP Module

`@zerobias-org/module-nist-cmvp` reads NIST's [Cryptographic Module Validation
Program](https://csrc.nist.gov/projects/cryptographic-module-validation-program)
(CMVP) registry: the authoritative record of which cryptographic modules are
FIPS 140-1/140-2/140-3 validated and under which conditions.

The registry has no API. The module scrapes the public validated-modules
listing and the per-certificate detail pages with cheerio, and maps them to
typed `CertificateSummary` and `Certificate` records. Every `Certificate`
carries the original detail-page HTML fragment (`rawEntryHtml`) and the
canonical source URL (`detailUrl`) as its audit trail.

## Installation

```bash
npm install @zerobias-org/module-nist-cmvp
```

Node 22 and the `@zerobias-org` registry (`https://pkg.zerobias.org/`) are
required; see the repository README for `ZB_TOKEN` setup.

## Authentication

None. The CMVP registry is a public, unauthenticated web site, so this module
has **no connection profile** and no `connect()` lifecycle: it is usable as soon
as it is constructed, and a collector can call it directly without a Hub
connection.

## Usage

```typescript
import { newCmvp } from '@zerobias-org/module-nist-cmvp';

const cmvp = newCmvp();
const certificates = cmvp.getCertificateApi();

// Page 1, 50 entries. The registry returns one flat listing, so paging is
// applied client-side over the full scraped list.
const page = await certificates.list(1, 50);
console.log(page.count, page.items[0].certificateNumber);

// Full detail record for one certificate number.
const cert = await certificates.get('5425');
console.log(cert.moduleName, cert.standard, cert.status, cert.caveatText);
```

## Operations

| Operation | Method | Source page |
|-----------|--------|-------------|
| `listCertificates` | `getCertificateApi().list(pageNumber?, pageSize?)` | `/validated-modules/search/all` listing table |
| `getCertificate` | `getCertificateApi().get(certificateNumber)` | `/certificate/{certificateNumber}` detail page |

`list` returns a `PagedResults<CertificateSummary>`; `get` returns a
`Certificate`. A certificate number that does not exist rejects with
`NoSuchObjectError`. A listing whose parsed row count disagrees with the
page's own reported total rejects with `UnexpectedError` rather than
under-reporting silently.

`productId` / `vendorId` on `Certificate` are reserved for matching entries
against the ZeroBias catalog and are not populated yet.

## Examples

```typescript
import { NoSuchObjectError } from '@zerobias-org/types-core-js';
import { newCmvp } from '@zerobias-org/module-nist-cmvp';

const certificates = newCmvp().getCertificateApi();

try {
  const cert = await certificates.get('999999999');
  console.log(cert.moduleName);
} catch (e) {
  if (e instanceof NoSuchObjectError) {
    console.log('no such certificate');
  } else {
    throw e;
  }
}
```

## Testing

Unit tests run against committed HTML fixtures with nock (no network). From
this module directory:

```bash
zbb test --slot local         # unit tests (nock + fixtures)
zbb gate --slot local         # full gate (writes gate-stamp.json — commit it)
```

There is no `test/e2e` suite yet: `describeModule` from
`@zerobias-org/module-test-client` connects through a slot secret and a
`ConnectionProfile`, which a profile-less module does not have, and the gate's
`testDocker` step expects a container that a profile-less module never builds.
Until the tooling supports profile-less modules, verify the scraper against the
live registry by hand:

```typescript
import { newCmvp } from '@zerobias-org/module-nist-cmvp';

const api = newCmvp().getCertificateApi();
console.log((await api.list(1, 5)).count);        // ~750 at the time of writing
console.log((await api.get('5425')).moduleName);  // CorSSL
```
