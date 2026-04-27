# Confluence Mapping

## Overview

How an Atlassian Confluence instance (Cloud, Server, or Data Center) surfaces
through the DataProducer interface. Spaces become Containers; pages become
Documents (with optional child-page Container behavior); attachments become
Binary objects; comments and version history become Collections. Page bodies
are exposed in Markdown — the producer is responsible for converting to and
from Confluence's XHTML storage format on the wire.

For the foundational rules this mapping follows, see
[`../Concepts.md`](../Concepts.md), [`../SchemaIds.md`](../SchemaIds.md),
[`../CoreDataTypes.md`](../CoreDataTypes.md), and
[`../FilterSyntax.md`](../FilterSyntax.md).

## Conceptual Mapping

| Confluence concept   | DataProducer concept                  | Notes                                                                |
|----------------------|---------------------------------------|----------------------------------------------------------------------|
| Site                 | Root container                        | `id == name == "/"`                                                  |
| Space                | Container                             | One per `spaceKey`.                                                  |
| Page                 | Document + Container                  | Document body is the page; Container exposes child pages.            |
| Blog post            | Document                              | Lives directly under the space; not a page child.                    |
| Attachment           | Binary                                | Child of the page that owns it.                                      |
| Comment              | Collection element                    | Each page has a `comments` Collection.                               |
| Page version         | Collection element                    | Read-only Collection per page.                                       |
| Label                | Tag on the owning Object              | Surfaces in `Object.tags`, not as a separate object.                 |
| CQL search           | Function                              | RFC4515 filter is translated to CQL by the producer.                 |
| Macro                | Embedded in the document body         | Round-tripped through the Markdown converter; preserved as-is when no equivalent. |

## Object Mappings

### Space (Container)

```yaml
Object:
  id: "/spaces/TEAM"
  name: "TEAM"
  objectClass: ["container"]
  description: "Team collaboration space"
  path: ["spaces", "TEAM"]
  tags: ["space", "global"]
```

### Page (Document + Container)

```yaml
Object:
  id: "/spaces/TEAM/pages/987654321"
  name: "Getting Started"
  objectClass: ["document", "container"]
  description: "Getting started guide for new team members"
  path: ["spaces", "TEAM", "Getting Started"]
  documentSchema: "schema:shared:confluence_page"
  versionId: "12"
  etag: "W/\"v12-987654321\""
  created:  "2024-02-01T09:00:00.000Z"
  modified: "2025-10-15T16:30:00.000Z"
  tags: ["page", "onboarding", "documentation"]
```

`versionId` carries the page's current version number; `etag` is derived from
`pageId + version`. Updates require an `If-Match` matching `etag`; mismatch
yields `412 Precondition Failed`.

```yaml
Schema:
  id: "schema:shared:confluence_page"
  dataTypes:
    - name: "string"
    - name: "integer"
    - name: "url"
  properties:
    - name: "pageId"
      dataType: "string"
      primaryKey: true
      required: true
    - name: "title"
      dataType: "string"
      required: true
    - name: "body"
      dataType: "string"
      required: true
      description: "Page body, Markdown — converted from XHTML storage format on read, back to XHTML on write"
    - name: "excerpt"
      dataType: "string"
    - name: "version"
      dataType: "integer"
      required: true
    - name: "status"
      dataType: "string"
      description: "current | draft | trashed"
    - name: "url"
      dataType: "url"
      description: "Web UI link to the page"
```

### Blog Post (Document)

```yaml
Object:
  id: "/spaces/TEAM/blog/55512345"
  name: "Q4 2025 Team Updates"
  objectClass: ["document"]
  documentSchema: "schema:shared:confluence_blog_post"
  versionId: "1"
  created: "2025-10-24T10:00:00.000Z"
  tags: ["blog-post", "announcement"]
```

### Attachment (Binary)

```yaml
Object:
  id: "/spaces/TEAM/pages/987654321/attachments/att123456"
  name: "architecture-diagram.png"
  objectClass: ["binary"]
  description: "System architecture diagram"
  mimeType: "image/png"
  fileName: "architecture-diagram.png"
  size: 245678
  versionId: "1"
  created:  "2025-09-15T08:45:00.000Z"
  modified: "2025-09-15T08:45:00.000Z"
  tags: ["attachment", "image"]
```

### Comments (Collection)

```yaml
Object:
  id: "/spaces/TEAM/pages/987654321/comments"
  name: "comments"
  objectClass: ["collection"]
  collectionSchema: "schema:shared:confluence_comment"
  collectionSize: 8
  tags: ["comments"]
```

```yaml
Schema:
  id: "schema:shared:confluence_comment"
  dataTypes:
    - name: "string"
    - name: "date-time"
  properties:
    - name: "commentId"
      dataType: "string"
      primaryKey: true
      required: true
    - name: "body"
      dataType: "string"
      required: true
      description: "Comment text in Markdown"
    - name: "author"
      dataType: "string"
      required: true
    - name: "created"
      dataType: "date-time"
      required: true
    - name: "parentCommentId"
      dataType: "string"
      description: "Set for replies in a thread"
```

### Page Versions (Read-only Collection)

```yaml
Object:
  id: "/spaces/TEAM/pages/987654321/versions"
  name: "versions"
  objectClass: ["collection"]
  collectionSchema: "schema:shared:confluence_page_version"
  collectionSize: 12
  tags: ["versions", "read-only"]
```

The version-history schema declares `version: integer, primaryKey: true` plus
`when: date-time`, `by: string` (author account ID), `message: string`,
`minorEdit: boolean`. Element-level writes are unsupported (no `addElement`,
`updateElement`, `deleteElement`).

### CQL Search (Function)

```yaml
Object:
  id: "/searches/cql"
  name: "cqlSearch"
  objectClass: ["function"]
  description: "Search Confluence content with a CQL expression"
  inputSchema:  "schema:shared:confluence_cql_input"
  outputSchema: "schema:shared:confluence_cql_output"
  tags: ["search", "cql"]
```

```yaml
Schema:
  id: "schema:shared:confluence_cql_input"
  dataTypes:
    - name: "string"
    - name: "integer"
    - name: "boolean"
  properties:
    - name: "cql"
      dataType: "string"
      required: true
      description: "Confluence Query Language expression"
    - name: "limit"
      dataType: "integer"
    - name: "start"
      dataType: "integer"
    - name: "includeArchivedSpaces"
      dataType: "boolean"
```

## Operation Mappings

| DataProducer operation                | Confluence REST API                                                                |
|---------------------------------------|------------------------------------------------------------------------------------|
| `getRootObject`                       | (synthetic — site identity)                                                        |
| `getChildren` on root                 | `GET /wiki/api/v2/spaces`                                                          |
| `getChildren` on space                | `GET /wiki/api/v2/spaces/{spaceId}/pages?depth=root`                                |
| `getChildren` on page                 | `GET /wiki/api/v2/pages/{pageId}/children`                                          |
| `getObject` (page)                    | `GET /wiki/api/v2/pages/{pageId}?body-format=storage` + XHTML→Markdown              |
| `getDocumentData`                     | Same as `getObject`, returning the body in Markdown                                 |
| `updateDocumentData`                  | `PUT /wiki/api/v2/pages/{pageId}` with Markdown→XHTML and `version.number = N+1`    |
| `createChildObject` (page)            | `POST /wiki/api/v2/pages` with `parentId` and converted body                        |
| `deleteObject` (page)                 | `DELETE /wiki/api/v2/pages/{pageId}` (moves to trash)                               |
| `getCollectionElements` (comments)    | `GET /wiki/api/v2/pages/{pageId}/footer-comments`                                   |
| `addCollectionElement` (comments)     | `POST /wiki/api/v2/footer-comments`                                                  |
| `deleteCollectionElement` (comments)  | `DELETE /wiki/api/v2/footer-comments/{commentId}`                                    |
| `getCollectionElements` (versions)    | `GET /wiki/api/v2/pages/{pageId}/versions`                                          |
| `downloadBinary` (attachment)         | `GET /wiki/download/attachments/{pageId}/{filename}` (supports Range)               |
| `uploadBinaryContent`                 | `POST /wiki/rest/api/content/{pageId}/child/attachment`                             |
| `objectSearch` / `searchChildObjects` | `GET /wiki/api/v2/search?cql=...` (RFC4515 filter translated to CQL)                |
| `invokeFunction` (cqlSearch)          | `GET /wiki/api/v2/search?cql=...`                                                    |

## Filter Translation Example

The producer parses RFC4515 with `@zerobias-org/util-lite-filter` and emits CQL.

```
RFC4515:
  (&(type=page)(|(space=TEAM)(space=DOCS))(label=onboarding)(modified>=2025-10-01))

AST (lite-filter):
  AND
    EQ type "page"
    OR
      EQ space "TEAM"
      EQ space "DOCS"
    EQ label "onboarding"
    GTE modified "2025-10-01"

Emitted CQL:
  type = "page"
    AND space IN ("TEAM", "DOCS")
    AND label = "onboarding"
    AND lastModified >= "2025-10-01"
```

| RFC4515                     | CQL                                  |
|-----------------------------|--------------------------------------|
| `(title=Onboarding)`        | `title = "Onboarding"`                |
| `(title=Onb*)`              | `title ~ "Onb*"`                      |
| `(text~=*architecture*)`    | `text ~ "architecture"`               |
| `(label=*)`                 | `label is not null`                   |
| `(!(status=trashed))`       | `NOT (status = "trashed")`            |

CQL has no exact equivalent for arbitrary substring (`*foo*`) on every field —
when a field doesn't support `~`, fall back to fetching the candidate set and
applying `expression.matches(item)` post-fetch.

## Core Types Reference

| Confluence field         | `dataType`     | Notes                                           |
|--------------------------|----------------|-------------------------------------------------|
| Page / blog body         | `string`       | Markdown on the wire; XHTML at the source.       |
| Page title               | `string`       |                                                 |
| Created / modified       | `date-time`    | ISO 8601 with offset.                           |
| Version number            | `integer`     | Monotonic per page.                              |
| Web UI link               | `url`         |                                                  |
| Attachment bytes          | `byte`        | When inlined; prefer Binary `downloadBinary`.    |
| MIME type                  | `mimeType`   |                                                  |
| Account ID (author)        | `string`     | Atlassian account IDs are opaque strings.        |

See [`../CoreDataTypes.md`](../CoreDataTypes.md) for the full catalog.

## Edge Cases

- **Optimistic concurrency.** Confluence requires `version.number` on PUT.
  The producer maps `If-Match` etag → expected version. Missing/mismatched
  etag returns `412`.
- **Macro round-tripping.** Macros without a Markdown equivalent are preserved
  as raw XHTML blocks in the Markdown body so a subsequent write doesn't lose
  them. Document this in the producer's USERGUIDE.
- **Trash semantics.** `deleteObject` on a page moves it to trash; subsequent
  `getObject` returns the page with `status: trashed`. Permanent purge is
  administrative and not exposed.
- **Restricted pages.** Page restrictions inherited from the connecting user.
  A `getObject` on a restricted page the user can't read returns
  `noSuchObjectError`, not a permission error, to avoid leaking existence.
- **Cloud vs Server pagination.** Cloud uses cursor tokens; Server uses
  offset (`start`/`limit`). The producer surfaces both via the standard
  `pageToken` / `pageNumber` modes.
- **Rate limiting.** Cloud enforces per-tenant limits; the producer should
  honor `Retry-After` and surface `429` as a transient error.
- **HTML sanitization.** Markdown→XHTML conversion must sanitize against XSS
  before submitting to Confluence, even though Confluence sanitizes on its
  side — defense in depth for self-hosted instances.
