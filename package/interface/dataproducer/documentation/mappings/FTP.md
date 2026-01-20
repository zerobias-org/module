# FTP/FTPS Mapping to Dynamic Data Producer Interface

## Overview

This document describes how FTP (File Transfer Protocol) and FTPS (FTP Secure) servers map to the Dynamic Data Producer Interface, enabling unified access to FTP filesystems through the generic object model. The interface is implemented via a translation layer that converts generic operations into FTP commands and protocol interactions.

**Protocol Standards**: RFC 959 (FTP), RFC 4217 (FTPS), RFC 3659 (Extensions), RFC 2428 (IPv6)

## Conceptual Mapping

### FTP Server → Object Model

```
FTP Server Root
├── /                                  → Root Container (FTP root directory)
│   ├── uploads/                      → Container Object (Directory)
│   │   ├── file1.txt                 → Binary Object (File)
│   │   └── document.pdf              → Binary Object (File)
│   ├── documents/                    → Container Object (Directory)
│   │   ├── reports/                  → Container Object (Subdirectory)
│   │   │   └── report.xlsx           → Binary Object (File)
│   │   └── archive.zip               → Binary Object (File)
│   └── public/                       → Container Object (Directory)
```

## Detailed Object Mappings

### 1. FTP Server Root → Root Container
```yaml
Object:
  id: "/"
  name: "FTP Server"
  objectClass: ["container"]
  description: "FTP server root directory"
  tags: ["ftp", "filesystem"]
  metadata:
    protocol: "ftp"  # or "ftps"
    serverAddress: "ftp.example.com"
    port: 21  # or 990 for implicit FTPS
    secure: false
    tlsMode: null  # "explicit" or "implicit" for FTPS
```

### 2. Directory → Container Object
```yaml
Object:
  id: "dir_uploads"
  name: "uploads"
  objectClass: ["container"]
  description: "Upload directory"
  path: ["/", "uploads"]
  tags: ["directory", "writable"]
  created: "2024-01-15T10:30:00.000Z"
  modified: "2025-10-20T14:22:00.000Z"
  metadata:
    ftpPath: "/uploads"
    permissions: "drwxr-xr-x"
    permissionsOctal: "0755"
    owner: "ftpuser"
    group: "ftpgroup"
```

### 3. File → Binary Object
```yaml
Object:
  id: "file_report"
  name: "report.pdf"
  objectClass: ["binary"]
  description: "Quarterly report PDF file"
  path: ["/", "documents", "report.pdf"]
  tags: ["file", "pdf"]
  created: "2025-09-15T08:45:00.000Z"
  modified: "2025-10-15T16:30:00.000Z"
  # Binary properties
  mimeType: "application/pdf"
  fileName: "report.pdf"
  size: 2458624
  etag: "1729008600"  # Unix timestamp
  metadata:
    ftpPath: "/documents/report.pdf"
    permissions: "-rw-r--r--"
    permissionsOctal: "0644"
    owner: "ftpuser"
    group: "ftpgroup"
```

## API Usage Examples with FTP Translation

### Directory Navigation

**List Root Directory:**
```http
GET /objects/{root}/children
# Translates to: FTP LIST / or MLSD /
```

**List Subdirectory:**
```http
GET /objects/dir_uploads/children
# Translates to: FTP CWD /uploads; LIST
```

**Search Files Recursively:**
```http
GET /objects/{root}/search?keywords=*.pdf&scope=subtree
# Translates to: Recursive directory traversal with pattern matching
```

### File Operations

**Download File:**
```http
GET /objects/file_report/download
# Translates to:
# 1. FTP TYPE I (binary mode)
# 2. FTP PASV (passive mode)
# 3. FTP RETR /documents/report.pdf

# Response: Binary file content
Content-Type: application/pdf
Content-Disposition: attachment; filename="report.pdf"
ETag: "1729008600"
```

**Upload File:**
```http
POST /objects/dir_uploads/children
Content-Type: application/json

{
  "name": "newfile.pdf",
  "objectClass": ["binary"],
  "mimeType": "application/pdf",
  "content": "<base64-encoded-data>"
}

# Translates to:
# 1. FTP CWD /uploads
# 2. FTP TYPE I
# 3. FTP PASV
# 4. FTP STOR newfile.pdf
```

**Resume Interrupted Download:**
```http
GET /objects/file_largefile/download
Range: bytes=5242880-

# Translates to:
# 1. FTP TYPE I
# 2. FTP REST 5242880
# 3. FTP RETR largefile.zip

# Response:
HTTP/1.1 206 Partial Content
Content-Range: bytes 5242880-10485760/10485760
```

**Delete File:**
```http
DELETE /objects/file_oldfile
# Translates to: FTP DELE /path/to/oldfile.txt
```

**Rename/Move File:**
```http
PUT /objects/file_document
Content-Type: application/json

{
  "name": "report_final.pdf",
  "path": ["/", "archives", "report_final.pdf"]
}

# Translates to:
# 1. FTP RNFR /documents/report.pdf
# 2. FTP RNTO /archives/report_final.pdf
```

### Directory Operations

**Create Directory:**
```http
POST /objects/dir_documents/children
Content-Type: application/json

{
  "name": "archives",
  "objectClass": ["container"]
}

# Translates to:
# 1. FTP CWD /documents
# 2. FTP MKD archives
```

**Delete Directory:**
```http
DELETE /objects/dir_empty?recursive=false
# Translates to: FTP RMD /path/to/empty
```

## Translation Layer Capabilities

### 1. **FTP Command Mapping**

| Generic Operation | FTP Commands | Notes |
|-------------------|--------------|-------|
| `children()` | `LIST` or `MLSD` | Directory listing |
| `download()` | `TYPE I`, `PASV`, `RETR` | Binary download |
| `uploadContent()` | `TYPE I`, `PASV`, `STOR` | File upload |
| `createChild()` (dir) | `MKD` | Create directory |
| `createChild()` (file) | `STOR` | Upload new file |
| `deleteObject()` (file) | `DELE` | Delete file |
| `deleteObject()` (dir) | `RMD` | Remove directory |
| `updateObject()` | `RNFR`, `RNTO` | Rename/move |
| Resume download | `REST`, `RETR` | Partial transfer |

### 2. **Path Resolution**
- **Capability**: Object path arrays convert to FTP path strings
- **Implementation**: ["/", "documents", "report.pdf"] → "/documents/report.pdf"
- **Normalization**: Handle Unix (/) vs Windows (\) path separators

### 3. **Transfer Mode Selection**
- **Binary Mode (TYPE I)**: Used for executables, images, archives, PDFs
- **ASCII Mode (TYPE A)**: Used for text files, HTML, XML, CSV
- **Auto-detection**: Based on file extension or MIME type

### 4. **Connection Modes**
- **Passive Mode (PASV/EPSV)**: Client connects to server (firewall-friendly)
- **Active Mode (PORT/EPRT)**: Server connects to client
- **Default**: Prefer passive mode for compatibility

## Implementation Considerations

### 1. **FTPS Security Integration**

**Explicit FTPS (AUTH TLS):**
```yaml
metadata:
  protocol: "ftps"
  secure: true
  tlsMode: "explicit"
  port: 21
  authCommand: "AUTH TLS"
  dataChannelProtection: "PROT P"
  tlsVersion: "TLS 1.2"
```

**Implicit FTPS:**
```yaml
metadata:
  protocol: "ftps"
  secure: true
  tlsMode: "implicit"
  port: 990
  tlsVersion: "TLS 1.3"
```

### 2. **Extended Features (RFC 3659)**
- **MLST/MLSD**: Machine-readable directory listings (prefer over LIST)
- **MDTM**: File modification time queries
- **SIZE**: File size queries before download
- **REST**: Resume interrupted transfers

### 3. **Directory Listing Parsing**

**Unix LIST format:**
```
-rw-r--r--   1 user  group      2048 Oct 15 09:00 README.txt
drwxr-xr-x   5 user  group      4096 Oct 20 14:22 uploads
```

**MLSD format (preferred):**
```
type=file;size=2048;modify=20251015090000;perm=r; README.txt
type=dir;modify=20251020142200;perm=flcdmpe; uploads
```

**Strategy**:
- Prefer MLSD if available (standardized, machine-readable)
- Fall back to LIST with regex parsing for different server types
- Extract: name, type, size, modified, permissions, owner, group

### 4. **Error Handling**

| FTP Response Code | HTTP Status | Error Type |
|-------------------|-------------|------------|
| `530` Not logged in | 401 | `UnauthenticatedError` |
| `550` File not found | 404 | `noSuchObjectError` |
| `553` Permission denied | 403 | `ForbiddenError` |
| `552` Storage exceeded | 507 | `storage_exceeded` |
| `421` Timeout | 408 | `TimeoutError` |
| `5xx` Server errors | 500 | `UnexpectedError` |

### 5. **Performance Optimization**
- **Connection Pooling**: Reuse FTP connections for multiple operations
- **Passive Mode**: Default to PASV/EPSV for firewall compatibility
- **Streaming**: Stream large files to avoid memory exhaustion
- **Feature Caching**: Cache FEAT response to avoid repeated queries
- **Directory Caching**: Cache directory listings with TTL

## Limitations and Workarounds

### 1. **No Atomic Operations**
- **Issue**: FTP lacks transaction support
- **Workaround**: Use temporary filenames, then rename on success

### 2. **Limited Metadata**
- **Issue**: Basic file information only
- **Workaround**: Use MLST/MLSD for extended attributes when available

### 3. **Encoding Variability**
- **Issue**: Filename encoding varies by server
- **Workaround**: Try UTF-8, fall back to Latin-1

### 4. **Permission Management**
- **Issue**: SITE CHMOD not universally supported
- **Workaround**: Check FEAT response before attempting

## Security Best Practices

1. **Prefer FTPS**: Always use explicit or implicit FTPS over plain FTP
2. **TLS Version**: Require TLS 1.2+ for FTPS connections
3. **Certificate Validation**: Verify server certificates in FTPS mode
4. **Path Validation**: Sanitize paths to prevent directory traversal attacks
5. **Connection Timeouts**: Implement idle connection timeouts
6. **Passive Mode**: Use passive mode to reduce firewall complexity

## Conclusion

The Dynamic Data Producer Interface provides effective abstraction for FTP/FTPS servers through a translation layer that converts generic operations into FTP commands.

**Key Benefits:**
- **Unified Interface**: Same API patterns work across FTP, databases, and other sources
- **Protocol Support**: FTP, FTPS (explicit/implicit), ASCII/Binary modes
- **Resume Support**: Interrupted transfers can be resumed via FTP REST command
- **Security**: FTPS support with TLS encryption and certificate validation
- **Streaming**: Memory-efficient binary transfers with HTTP Range header support

**Implementation Strategy:**
The translation layer handles FTP protocol complexity, path conversion, and directory listing parsing while providing a consistent REST-like interface. Performance optimizations include connection pooling, feature caching, and passive mode defaults for firewall compatibility.

**Perfect Use Cases:**
- Legacy FTP server integration
- Secure file transfer workflows (FTPS)
- Bulk file upload/download operations
- Directory synchronization and backup
- Cross-protocol file management (FTP + cloud storage + databases)
