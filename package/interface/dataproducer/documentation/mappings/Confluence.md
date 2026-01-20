# Atlassian Confluence Mapping to Dynamic Data Producer Interface

## Overview

This document describes how Atlassian Confluence spaces, pages, and content map to the Dynamic Data Producer Interface, enabling unified access to wiki/knowledge base content through the generic object model. The interface is implemented via a translation layer that converts generic operations into Confluence REST API calls and HTML content into Markdown format.

**Platform**: Atlassian Confluence (Cloud, Server, Data Center)
**API**: Confluence REST API v2 (latest)
**Content Conversion**: HTML/XHTML Storage Format → Markdown

## Conceptual Mapping

### Confluence Instance → Object Model

```
Confluence Instance
├── /                                  → Root Container (Confluence)
│   ├── spaces/                       → Container Object (Space List)
│   │   ├── TEAM                      → Container Object (Space)
│   │   │   ├── pages                 → Container Object (Page Container)
│   │   │   │   ├── Getting Started   → Document Object (Page)
│   │   │   │   │   ├── children      → Container (Child Pages)
│   │   │   │   │   ├── attachments   → Container + Binary Objects
│   │   │   │   │   ├── comments      → Collection Object (Comments)
│   │   │   │   │   └── versions      → Collection Object (Page History)
│   │   │   │   ├── Project Overview  → Document Object (Page)
│   │   │   │   └── Meeting Notes     → Document Object (Page)
│   │   │   ├── blogs                 → Collection Object (Blog Posts)
│   │   │   └── labels                → Collection Object (Labels/Tags)
│   │   └── DOCS                      → Container Object (Space)
│   ├── search/                       → Container Object (Search Functions)
│   │   ├── searchContent             → Function Object (CQL Search)
│   │   ├── searchPages               → Function Object (Page Search)
│   │   └── searchSpaces              → Function Object (Space Search)
│   └── operations/                   → Container Object (Bulk Operations)
│       ├── exportSpace               → Function Object (Export)
│       └── copyPage                  → Function Object (Copy)
```

## Detailed Object Mappings

### 1. Confluence Instance → Root Container
```yaml
Object:
  id: "/"
  name: "Confluence"
  objectClass: ["container"]
  description: "Confluence knowledge base"
  tags: ["confluence", "wiki", "knowledge-base"]
  metadata:
    baseUrl: "https://your-domain.atlassian.net/wiki"
    apiVersion: "v2"
    platform: "cloud"  # or "server", "datacenter"
    authentication: "bearer"  # or "basic"
```

### 2. Space → Container Object
```yaml
Object:
  id: "space_team"
  name: "TEAM"
  objectClass: ["container"]
  description: "Team collaboration space"
  path: ["/", "spaces", "TEAM"]
  tags: ["space", "team"]
  created: "2024-01-15T10:30:00.000Z"
  modified: "2025-10-20T14:22:00.000Z"
  metadata:
    spaceKey: "TEAM"
    spaceId: "123456789"
    type: "global"  # or "personal"
    status: "current"  # or "archived"
    homepageId: "987654321"
    # Permissions
    permissions: ["read", "write", "admin"]
    # Statistics
    pageCount: 342
    blogPostCount: 28

Schema:
  id: "confluence_space_schema"
  properties:
    - name: "key"
      dataType: "string"
      primaryKey: true
      required: true
      description: "Space key (unique identifier)"
    - name: "name"
      dataType: "string"
      required: true
    - name: "description"
      dataType: "string"
      description: "Space description (HTML)"
    - name: "type"
      dataType: "string"
      description: "Space type: global, personal"
    - name: "status"
      dataType: "string"
      description: "current or archived"
    - name: "homepageId"
      dataType: "string"
      description: "ID of space homepage"
```

### 3. Page → Document Object
```yaml
Object:
  id: "page_getting_started"
  name: "Getting Started"
  objectClass: ["document", "container"]
  description: "Getting started guide for new team members"
  path: ["/", "spaces", "TEAM", "pages", "Getting Started"]
  documentSchema: "confluence_page_schema"
  tags: ["page", "documentation", "onboarding"]
  created: "2024-02-01T09:00:00.000Z"
  modified: "2025-10-15T16:30:00.000Z"
  metadata:
    pageId: "987654321"
    spaceKey: "TEAM"
    version: 12
    status: "current"  # or "draft", "trashed"
    parentId: null  # null for top-level pages
    position: 1
    # Content metadata
    contentType: "page"  # or "blogpost"
    bodyFormat: "storage"  # HTML storage format
    wordCount: 1842
    # Collaboration
    hasChildren: true
    childrenCount: 5
    attachmentCount: 3
    commentCount: 8
    likeCount: 15
    watcherCount: 23
    # Access
    restrictions: []  # page-level restrictions if any

Schema:
  id: "confluence_page_schema"
  properties:
    - name: "pageId"
      dataType: "string"
      primaryKey: true
      required: true
    - name: "title"
      dataType: "string"
      required: true
      description: "Page title"
    - name: "body"
      dataType: "string"
      required: true
      description: "Page content in Markdown (converted from HTML)"
    - name: "excerpt"
      dataType: "string"
      description: "Brief excerpt or summary"
    - name: "version"
      dataType: "integer"
      required: true
      description: "Current version number"
    - name: "status"
      dataType: "string"
      description: "current, draft, trashed"
    - name: "labels"
      dataType: "array"
      multi: true
      description: "Page labels/tags"

# Document Data (with Markdown conversion):
Document Data:
{
  "pageId": "987654321",
  "title": "Getting Started",
  "body": "# Welcome to the Team\n\nThis guide will help you get started...\n\n## Prerequisites\n\n- Access to Confluence\n- Team credentials\n\n## Steps\n\n1. Review team handbook\n2. Complete onboarding checklist\n3. Schedule 1:1 with manager",
  "excerpt": "Getting started guide for new team members",
  "version": 12,
  "status": "current",
  "labels": ["onboarding", "new-hire", "documentation"]
}
```

### 4. Blog Post → Collection Element
```yaml
Object:
  id: "collection_blogs"
  name: "blogs"
  objectClass: ["collection"]
  description: "Team blog posts"
  path: ["/", "spaces", "TEAM", "blogs"]
  collectionSchema: "confluence_blog_schema"
  collectionSize: 28
  tags: ["blogs", "announcements"]
  metadata:
    spaceKey: "TEAM"
    sortOrder: "created"  # Chronological by default
    sortDirection: "descending"

Schema:
  id: "confluence_blog_schema"
  properties:
    - name: "blogPostId"
      dataType: "string"
      primaryKey: true
      required: true
    - name: "title"
      dataType: "string"
      required: true
    - name: "body"
      dataType: "string"
      required: true
      description: "Blog content in Markdown"
    - name: "publishDate"
      dataType: "date-time"
      required: true
    - name: "author"
      dataType: "string"
      description: "Author username or ID"
    - name: "labels"
      dataType: "array"
      multi: true
```

### 5. Attachment → Binary Object
```yaml
Object:
  id: "attachment_diagram"
  name: "architecture-diagram.png"
  objectClass: ["binary"]
  description: "System architecture diagram"
  path: ["/", "spaces", "TEAM", "pages", "Getting Started", "attachments", "architecture-diagram.png"]
  tags: ["attachment", "image", "diagram"]
  created: "2025-09-15T08:45:00.000Z"
  modified: "2025-09-15T08:45:00.000Z"
  # Binary properties
  mimeType: "image/png"
  fileName: "architecture-diagram.png"
  size: 245678
  metadata:
    attachmentId: "att123456"
    pageId: "987654321"
    version: 1
    comment: "Updated architecture diagram"
```

### 6. Comments → Collection Object
```yaml
Object:
  id: "collection_comments"
  name: "comments"
  objectClass: ["collection"]
  description: "Page comments"
  path: ["/", "spaces", "TEAM", "pages", "Getting Started", "comments"]
  collectionSchema: "confluence_comment_schema"
  collectionSize: 8
  tags: ["comments", "discussion"]

Schema:
  id: "confluence_comment_schema"
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
      description: "For threaded comments"
```

### 7. Page Versions → Collection Object
```yaml
Object:
  id: "collection_versions"
  name: "versions"
  objectClass: ["collection"]
  description: "Page version history"
  path: ["/", "spaces", "TEAM", "pages", "Getting Started", "versions"]
  collectionSchema: "confluence_version_schema"
  collectionSize: 12
  tags: ["versions", "history", "read-only"]

Schema:
  id: "confluence_version_schema"
  properties:
    - name: "version"
      dataType: "integer"
      primaryKey: true
      required: true
    - name: "when"
      dataType: "date-time"
      required: true
    - name: "by"
      dataType: "string"
      description: "Author of this version"
    - name: "message"
      dataType: "string"
      description: "Version comment"
    - name: "minorEdit"
      dataType: "boolean"
      description: "Whether this was a minor edit"
```

### 8. CQL Search → Function Object
```yaml
Object:
  id: "func_search_content"
  name: "searchContent"
  objectClass: ["function"]
  description: "Search Confluence content using CQL"
  inputSchema: "confluence_search_input"
  outputSchema: "confluence_search_output"
  tags: ["search", "cql"]

Schema (Input):
  id: "confluence_search_input"
  properties:
    - name: "cql"
      dataType: "string"
      required: true
      description: "Confluence Query Language expression"
    - name: "limit"
      dataType: "integer"
      default: 25
      description: "Maximum results to return"
    - name: "start"
      dataType: "integer"
      default: 0
      description: "Pagination offset"
    - name: "includeArchivedSpaces"
      dataType: "boolean"
      default: false

Schema (Output):
  id: "confluence_search_output"
  properties:
    - name: "results"
      dataType: "array"
      multi: true
      references:
        schemaId: "confluence_search_result_schema"
    - name: "size"
      dataType: "integer"
      description: "Number of results returned"
    - name: "totalSize"
      dataType: "integer"
      description: "Total matching results"

Schema:
  id: "confluence_search_result_schema"
  properties:
    - name: "id"
      dataType: "string"
      required: true
    - name: "type"
      dataType: "string"
      description: "page, blogpost, attachment, space"
    - name: "title"
      dataType: "string"
    - name: "excerpt"
      dataType: "string"
      description: "Search result excerpt with highlights"
    - name: "url"
      dataType: "url"
    - name: "spaceKey"
      dataType: "string"
```

## API Usage Examples with Confluence Translation

### Space Operations

**List All Spaces:**
```http
GET /objects/spaces/children
# Translates to: GET /wiki/api/v2/spaces

# Response:
[
  {
    "id": "space_team",
    "name": "TEAM",
    "objectClass": ["container"],
    "metadata": {
      "spaceKey": "TEAM",
      "type": "global",
      "pageCount": 342
    }
  },
  {
    "id": "space_docs",
    "name": "DOCS",
    "objectClass": ["container"],
    "metadata": {
      "spaceKey": "DOCS",
      "type": "global",
      "pageCount": 156
    }
  }
]
```

**Get Space Details:**
```http
GET /objects/space_team
# Translates to: GET /wiki/api/v2/spaces/{spaceId}
```

### Page Operations

**List Pages in Space:**
```http
GET /objects/space_team/children?type=document
# Translates to: GET /wiki/api/v2/spaces/{spaceId}/pages

# Returns top-level pages in space
```

**Get Page Content:**
```http
GET /objects/page_getting_started/document
# Translates to: GET /wiki/api/v2/pages/{pageId}
# Content conversion: HTML storage format → Markdown

# Response:
{
  "pageId": "987654321",
  "title": "Getting Started",
  "body": "# Welcome to the Team\n\nThis guide will help...",
  "version": 12,
  "status": "current",
  "labels": ["onboarding", "new-hire"]
}
```

**Create New Page:**
```http
POST /objects/space_team_pages/children
Content-Type: application/json

{
  "name": "New Team Process",
  "objectClass": ["document"],
  "documentData": {
    "title": "New Team Process",
    "body": "# Process Overview\n\nThis document describes...",
    "status": "current",
    "labels": ["process", "workflow"]
  }
}

# Translates to: POST /wiki/api/v2/pages
# Content conversion: Markdown → HTML storage format
```

**Update Page Content:**
```http
PUT /objects/page_getting_started/document
Content-Type: application/json

{
  "body": "# Welcome to the Team (Updated)\n\nNew content...",
  "version": 13
}

# Translates to: PUT /wiki/api/v2/pages/{pageId}
# Requires current version for optimistic locking
```

**Delete Page:**
```http
DELETE /objects/page_getting_started
# Translates to: DELETE /wiki/api/v2/pages/{pageId}
# Page moved to trash (can be restored)
```

### Page Hierarchy Navigation

**Get Child Pages:**
```http
GET /objects/page_getting_started/children
# Translates to: GET /wiki/api/v2/pages/{pageId}/children

# Returns immediate child pages
```

**Get Page Ancestors:**
```http
GET /objects/page_getting_started?expand=ancestors
# Translates to: GET /wiki/api/v2/pages/{pageId}?expand=ancestors

# Returns breadcrumb trail: Space → Parent → ... → Current Page
```

### Blog Post Operations

**List Blog Posts:**
```http
GET /objects/space_team_blogs/collection/elements?sortBy=publishDate&sortDirection=descending
# Translates to: GET /wiki/api/v2/blogposts?space-id={spaceId}&sort=-created-date

# Response: Blog posts in chronological order
```

**Create Blog Post:**
```http
POST /objects/space_team_blogs/collection/elements
{
  "title": "Q4 2025 Team Updates",
  "body": "# Q4 Updates\n\nKey highlights from this quarter...",
  "publishDate": "2025-10-24T10:00:00.000Z",
  "labels": ["announcement", "quarterly"]
}

# Translates to: POST /wiki/api/v2/blogposts
```

### Attachment Operations

**List Attachments:**
```http
GET /objects/page_getting_started_attachments/children
# Translates to: GET /wiki/api/v2/pages/{pageId}/attachments

# Response:
[
  {
    "id": "attachment_diagram",
    "name": "architecture-diagram.png",
    "objectClass": ["binary"],
    "mimeType": "image/png",
    "size": 245678
  }
]
```

**Download Attachment:**
```http
GET /objects/attachment_diagram/download
# Translates to: GET /wiki/download/attachments/{pageId}/{filename}

# Response: Binary file content with appropriate headers
```

**Upload Attachment:**
```http
POST /objects/page_getting_started_attachments/children
Content-Type: multipart/form-data

{
  "name": "requirements.pdf",
  "objectClass": ["binary"],
  "content": "<base64-encoded-data>",
  "mimeType": "application/pdf"
}

# Translates to: POST /wiki/rest/api/content/{pageId}/child/attachment
```

### Comment Operations

**List Comments:**
```http
GET /objects/page_getting_started_comments/collection/elements
# Translates to: GET /wiki/api/v2/pages/{pageId}/footer-comments

# Response: All comments on page
```

**Add Comment:**
```http
POST /objects/page_getting_started_comments/collection/elements
{
  "body": "Great documentation! One question about step 3...",
  "author": "john.doe"
}

# Translates to: POST /wiki/api/v2/footer-comments
```

**Delete Comment:**
```http
DELETE /objects/page_getting_started_comments/collection/elements/comment123
# Translates to: DELETE /wiki/api/v2/footer-comments/{commentId}
```

### Version History

**List Page Versions:**
```http
GET /objects/page_getting_started_versions/collection/elements?sortDirection=descending
# Translates to: GET /wiki/api/v2/pages/{pageId}/versions

# Response: Version history, newest first
```

**Get Specific Version:**
```http
GET /objects/page_getting_started_versions/collection/elements/8
# Translates to: GET /wiki/api/v2/pages/{pageId}/versions/{version}

# Returns page content as it was in version 8
```

### Search Operations

**Search Pages by Title:**
```http
GET /objects/spaces_team_pages/search?keywords=getting started
# Translates to: GET /wiki/api/v2/search?cql=type=page AND space=TEAM AND title~"getting started"
```

**CQL Search:**
```http
POST /objects/func_search_content/invoke
{
  "cql": "type=page AND space=TEAM AND label=onboarding AND lastModified >= 2025-10-01",
  "limit": 50
}

# Translates to: GET /wiki/api/v2/search?cql=...
# Returns matching pages, blog posts, attachments
```

**Search Across All Spaces:**
```http
POST /objects/func_search_content/invoke
{
  "cql": "text~\"API documentation\" AND type=page",
  "limit": 100
}

# Full-text search across all accessible content
```

## Translation Layer Capabilities

### 1. **Confluence REST API Mapping**

| Generic Operation | Confluence API | Notes |
|-------------------|---------------|-------|
| `children()` (spaces) | `GET /spaces` | List all spaces |
| `children()` (pages) | `GET /spaces/{id}/pages` | Top-level pages |
| `children()` (child pages) | `GET /pages/{id}/children` | Child pages |
| `documentData()` (page) | `GET /pages/{id}` | Get page with HTML→MD |
| `documentData()` (update) | `PUT /pages/{id}` | Update with MD→HTML |
| `createChild()` (page) | `POST /pages` | Create new page |
| `deleteObject()` (page) | `DELETE /pages/{id}` | Move to trash |
| `collectionElements()` (blogs) | `GET /blogposts` | List blog posts |
| `collectionElements()` (comments) | `GET /pages/{id}/footer-comments` | Page comments |
| Search | `GET /search?cql=...` | CQL query |

### 2. **HTML to Markdown Conversion**
- **Storage Format**: Confluence uses XHTML storage format
- **Conversion**: Translate HTML → Markdown for document body
- **Macros**: Convert common macros to Markdown equivalents
  - `{info}` → Markdown blockquote with emoji
  - `{code}` → Markdown code blocks
  - `{toc}` → Generated table of contents
- **Reverse Conversion**: Markdown → HTML for updates

### 3. **RFC4515 to CQL Translation**

| RFC4515 Filter | CQL Expression | Notes |
|----------------|----------------|-------|
| `(title=Getting Started)` | `title="Getting Started"` | Exact match |
| `(type=page)` | `type=page` | Content type |
| `(space=TEAM)` | `space=TEAM` | Space filter |
| `(label=onboarding)` | `label=onboarding` | Label filter |
| `(&(type=page)(space=TEAM))` | `type=page AND space=TEAM` | AND logic |
| `(\|(space=TEAM)(space=DOCS))` | `space IN (TEAM,DOCS)` | OR logic |
| `(lastModified>=2025-10-01)` | `lastModified >= 2025-10-01` | Date comparison |
| `(text~documentation)` | `text~"documentation"` | Full-text search |

### 4. **Content Type Mapping**

| Confluence Type | Object Class | Collection Type |
|-----------------|--------------|-----------------|
| Space | Container | - |
| Page | Document + Container | Pages (navigable) |
| Blog Post | Collection Element | Blogs (chronological) |
| Attachment | Binary | - |
| Comment | Collection Element | Comments |
| Version | Collection Element | Versions (read-only) |
| Label | Collection Element | Labels |

## Implementation Considerations

### 1. **Authentication**

**Cloud (API Token):**
```yaml
metadata:
  authentication: "bearer"
  apiToken: "ATATT3xFfGF0..."
  email: "user@example.com"
  baseUrl: "https://your-domain.atlassian.net/wiki"
```

**Server/Data Center (Basic Auth):**
```yaml
metadata:
  authentication: "basic"
  username: "admin"
  password: "password"
  baseUrl: "https://confluence.company.com"
```

### 2. **Performance Optimization**
- **Expand Parameters**: Use `?expand=body.storage,version,ancestors` to reduce API calls
- **Pagination**: Default 25 results, support up to 250 per request
- **Caching**: Cache space metadata, page hierarchy for navigation
- **Lazy Loading**: Load page content only when document is requested
- **Bulk Operations**: Batch page updates when possible

### 3. **Error Handling**

| Confluence Error | HTTP Status | Error Type |
|------------------|-------------|------------|
| `404 Not Found` | 404 | `noSuchObjectError` |
| `401 Unauthorized` | 401 | `UnauthenticatedError` |
| `403 Forbidden` | 403 | `ForbiddenError` |
| `409 Conflict` | 409 | `ConflictError` (version mismatch) |
| `413 Payload Too Large` | 413 | `payload_too_large` |
| `429 Too Many Requests` | 429 | `RateLimitExceededError` |

### 4. **Versioning and Conflict Resolution**
- **Optimistic Locking**: Require current version number for updates
- **Conflict Detection**: Check version before PUT
- **Version History**: Read-only access to all versions
- **Restore**: Support restore from version history

### 5. **Permission Model**
- **Space Permissions**: Read, Write, Admin at space level
- **Page Restrictions**: Additional restrictions on individual pages
- **Inheritance**: Child pages inherit parent restrictions
- **API Respect**: All operations respect Confluence permissions

## Limitations and Workarounds

### 1. **Macro Handling**
- **Issue**: Confluence macros may not translate perfectly to Markdown
- **Workaround**: Convert common macros, preserve complex ones as HTML blocks

### 2. **Nested Page Depth**
- **Issue**: Deep hierarchies (5+ levels) can be slow to navigate
- **Workaround**: Implement breadcrumb caching, lazy load deep children

### 3. **Large Page Content**
- **Issue**: Pages with 100+ MB of content rare but possible
- **Workaround**: Stream content, implement chunking for very large pages

### 4. **Real-time Collaboration**
- **Issue**: No real-time editing API (users may conflict)
- **Workaround**: Version conflict detection, merge conflict warnings

## Security Best Practices

1. **API Token Security**: Store tokens encrypted, rotate regularly
2. **Least Privilege**: Request only required permissions
3. **Audit Logging**: Log all content modifications
4. **Rate Limiting**: Respect Confluence rate limits (avoid throttling)
5. **Content Sanitization**: Validate Markdown→HTML conversion for XSS
6. **Access Control**: Verify user permissions before operations

## Conclusion

The Dynamic Data Producer Interface provides effective abstraction for Atlassian Confluence through a translation layer that converts generic operations into Confluence REST API calls and automatically handles HTML-to-Markdown content conversion.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across Confluence, databases, file systems
- **Content Conversion**: Automatic HTML→Markdown conversion for easy consumption
- **Hierarchical Navigation**: Natural mapping of space/page hierarchy to containers
- **Full CRUD**: Complete create, read, update, delete operations on all content types
- **Advanced Search**: CQL search via function objects with RFC4515 translation

**Implementation Strategy:**
The translation layer handles Confluence API complexity, content format conversion, version management, and permission checking while providing a consistent REST-like interface. Performance optimizations include expand parameter usage, caching, and lazy loading.

**Perfect Use Cases:**
- Knowledge base content synchronization and backup
- Multi-platform documentation aggregation (Confluence + GitHub Wiki + SharePoint)
- Content migration and transformation
- Automated documentation generation and publishing
- Cross-system content search and discovery
