# FTP Mapping

## Overview

How FTP, FTPS, and SFTP filesystems surface through the DataProducer interface.
A producer translates between Object operations and the wire protocol's
commands; the public contract is identical across the three variants. The
distinguishing trait of this mapping is that **filtering is virtually always
post-fetch** — FTP servers do not accept structured queries, so the producer
parses RFC4515 with `@zerobias-org/util-lite-filter` and evaluates in-process
against directory listings.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| Native concept             | DataProducer concept                | Notes                                                             |
|----------------------------|-------------------------------------|-------------------------------------------------------------------|
| Server / login session     | Root container (`id == "/"`)         |                                                                   |
| Directory                  | Container                            |                                                                   |
| Regular file               | Binary                               | `size`, `mimeType`, `modified` populate from listing metadata.    |
| Symbolic link              | Container or Binary (per target)     | Target resolution is implementation-defined; loops must be detected. |
| Permissions / owner / group | Tags or extended metadata           | Surface only when the FTP variant exposes them (MLSD, SFTP `attrs`). |
| Transfer mode (ASCII/binary) | (not modeled)                      | Connection-layer concern; producer chooses based on `mimeType`.   |
| Passive/active, FTPS, SFTP | (not modeled)                        | Connection-layer concern; transparent to callers.                 |

There are no Collections, Functions, or Documents in a vanilla FTP filesystem.
Containers and Binaries cover the model end-to-end.

## Object Mappings

### Directory (Container)

```yaml
Object:
  id: "/uploads"
  name: "uploads"
  objectClass: ["container"]
  description: "Upload directory"
  path: ["/", "uploads"]
  created:  "2024-01-15T10:30:00.000Z"
  modified: "2025-10-20T14:22:00.000Z"
  tags: ["directory", "owner:ftpuser", "perm:0755"]
```

Object IDs are server-side absolute paths verbatim. Permissions and ownership,
when the listing exposes them (MLSD `UNIX.mode`, SFTP `attrs`), surface as
tags rather than first-class fields — the base interface does not model POSIX
attributes.

### File (Binary)

```yaml
Object:
  id: "/documents/report.pdf"
  name: "report.pdf"
  objectClass: ["binary"]
  description: "Quarterly report"
  path: ["/", "documents", "report.pdf"]
  mimeType: "application/pdf"
  fileName: "report.pdf"
  size: 2458624
  modified: "2025-10-15T16:30:00.000Z"
  etag: "1729008600"
  tags: ["file", "owner:ftpuser", "perm:0644"]
```

`mimeType` is guessed from the filename extension by default. Producers that
have richer metadata (e.g. an SFTP server with extended attributes) may
populate it more accurately. `etag` is typically derived from the modification
timestamp (or `MDTM`) — sufficient for `If-Match`-style optimistic concurrency
on `uploadBinaryContent`.

## Operation Mappings

### Browsing

| Operation                          | FTP / SFTP                                                                |
|------------------------------------|----------------------------------------------------------------------------|
| `getRootObject`                    | (synthetic — `id == name == "/"`)                                          |
| `getChildren` on directory         | `MLSD` (preferred) or `LIST`; SFTP `readdir`                                |
| `getObject(id)`                    | `MLST` for one path; SFTP `stat`                                           |
| `searchChildObjects`               | `MLSD`/`LIST` then post-fetch RFC4515 evaluation (see Filter Translation)  |

### Container writes

| Operation                          | FTP / SFTP                                                                |
|------------------------------------|----------------------------------------------------------------------------|
| `createChildObject` (directory)    | `MKD` / SFTP `mkdir`                                                        |
| `createChildObject` (file)         | `STOR` / SFTP `open+write`                                                  |
| `updateObject` (rename/move)       | `RNFR` + `RNTO` / SFTP `rename`                                             |
| `deleteObject` (file)              | `DELE` / SFTP `remove`                                                      |
| `deleteObject` (directory)         | `RMD` / SFTP `rmdir`; recursive flag requires producer-side traversal       |

### Binary

| Operation                          | FTP / SFTP                                                                |
|------------------------------------|----------------------------------------------------------------------------|
| `downloadBinary`                   | `TYPE I`, `PASV`, `RETR` — streaming; HTTP `Range` maps to `REST` + `RETR` |
| `uploadBinaryContent`              | `TYPE I`, `PASV`, `STOR` — streaming                                        |

`downloadBinary` must support resumable transfers: the inbound HTTP `Range`
header translates to an FTP `REST` (restart marker) before the `RETR`. SFTP's
random-access `read` covers the same need natively.

## Filter Translation Example

FTP listings are not server-filterable. The producer fetches the directory
listing, parses the caller's RFC4515 filter, and evaluates each entry
in-process.

```
RFC4515 (caller):
  (&(objectClass=binary)(name=*.pdf)(size>=1048576)(modified>=2025-01-01))

Strategy:
  1. List directory:                 MLSD /documents
  2. Materialize entries as Objects: name, size, modified, objectClass
  3. Parse filter (lite-filter):     parse('(&(...)(...))')
  4. For each entry: expression.matches(entry)
  5. Apply pageSize / pageToken to the filtered set.
```

```typescript
import { parse } from '@zerobias-org/util-lite-filter';

const expression = parse(filterString);
const entries    = await listDirectory(dirPath);     // MLSD/LIST/SFTP readdir
const matched    = entries
  .map(toObject)
  .filter((obj) => expression.matches(obj));
```

For deep recursive search (`scope=subtree`), the producer walks subdirectories
and filters the union. Producers should bound traversal — depth and total-entry
limits — to avoid pathological recursion on large trees.

## Core Types Reference

FTP exposes a thin slice of metadata. The relevant core types:

| Field semantics                | `dataType`     | Notes                                            |
|--------------------------------|----------------|--------------------------------------------------|
| File name                      | `string`       | On `Object.name` and `Object.fileName`.           |
| File size in bytes             | `integer`      | On `Object.size`.                                |
| Modification timestamp         | `date-time`    | From `MDTM` or MLSD `modify=`.                   |
| Creation timestamp             | `date-time`    | Often unavailable on FTP; SFTP exposes via `attrs`. |
| MIME type                      | `mimeType`     | Guessed from extension; surface in `Object.mimeType`. |
| File contents                  | `byte`         | Only when inlining; prefer `downloadBinary` streaming. |
| Owner / group / mode           | `string`       | Surface as tags rather than typed fields.         |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog. Note
there is no `ipv4`/`ipv6` format string — server addresses, when modeled,
use `ipAddress` or `hostname` on connection-layer entities outside this mapping.

## Edge Cases

- **Connection-layer variants.** FTP vs FTPS (explicit `AUTH TLS` vs implicit
  port 990) vs SFTP (SSH subsystem) are configuration concerns of the
  underlying connection, not interface concerns. Object IDs and operations
  are identical across all three.
- **Transfer modes.** Producers select `TYPE I` (binary) by default; `TYPE A`
  (ASCII) is acceptable only when `mimeType` is text-flavored *and* the
  caller has not requested byte-exact content. When in doubt, use binary.
- **Passive vs active.** Default to `PASV`/`EPSV` for firewall traversal.
  Active mode is implementation-configurable; not surfaced to callers.
- **Listing format.** Prefer `MLSD` (RFC 3659) — its `type=`, `size=`,
  `modify=` facts are machine-parseable. Fall back to `LIST` with per-server
  regex parsing only when `FEAT` reports no MLSD.
- **Filename encoding.** FTP does not mandate UTF-8. Try UTF-8 first;
  fall back to Latin-1. SFTP uses UTF-8 by spec.
- **Symbolic links.** Resolve to their target's class (`container` or
  `binary`) when the target is reachable; otherwise expose with a `broken-link`
  tag and refuse `downloadBinary`/`getChildren`. Loop detection is the
  producer's responsibility.
- **Permissions / ownership.** When `MLSD` returns `UNIX.mode`/`UNIX.owner`
  facts, surface as tags (`perm:0644`, `owner:ftpuser`). Mutating them is out
  of scope — `SITE CHMOD` is not portable.
- **Atomic writes.** FTP has no transactions. For atomic `uploadBinaryContent`,
  `STOR` to a temporary path then `RNFR`/`RNTO` to the final path on success.
- **`etag` and concurrency.** Most FTP servers do not provide a true ETag.
  Modification time is acceptable as a coarse proxy; producers must document
  whether `If-Match` is enforced precisely or best-effort.
- **Resumable downloads.** `Range` is required to map to `REST`+`RETR`.
  Producers must reject `Range` on servers that do not advertise `REST` in
  `FEAT`.
- **Directory delete recursion.** `RMD` requires an empty directory.
  Recursive delete is producer-side: depth-first traversal with bounded
  parallelism, surfacing the first error.
