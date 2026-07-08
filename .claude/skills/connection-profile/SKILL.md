---
name: connection-profile
description: Connection profile schema design and implementation. Use when designing or implementing connectionProfile.yml or connectionState.yml files.
---

# Connection Profile & State Design Rules

**Rules for designing connectionProfile.yml and connectionState.yml schemas**

## 🚨 CRITICAL RULES

### Rule 1: Always Extend Core Profiles

**NEVER create custom profiles from scratch - ALWAYS extend core profiles**

```yaml
# ❌ WRONG - Custom profile
type: object
properties:
  apiKey:
    type: string

# ✅ CORRECT - Extend core profile
$ref: './node_modules/@zerobias-org/types-core/schema/tokenProfile.yml'
```

### Rule 2: connectionState MUST Extend baseConnectionState

**🚨 MANDATORY**: connectionState.yml MUST extend baseConnectionState.yml for expiresIn

```yaml
# ✅ CORRECT - Extends base with expiresIn
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/baseConnectionState.yml'
  - type: object
    properties:
      accessToken:
        type: string

# ❌ WRONG - Missing baseConnectionState
type: object
properties:
  accessToken:
    type: string
  # Missing expiresIn!
```

**WHY**: Server uses expiresIn to schedule automatic token refresh cronjobs

### Rule 3: expiresIn Must Be in Seconds

```yaml
# ✅ CORRECT
expiresIn:
  type: integer
  description: Token expiry time in SECONDS from now

# ❌ WRONG
expiresAt:
  type: string  # Don't use expiresAt, convert to expiresIn
```

**Calculation**: `expiresIn = Math.floor((expiresAt - now) / 1000)`

## Core Profile Selection

### Available Core Profiles

| Profile | When to Use | Contains |
|---------|-------------|----------|
| **tokenProfile.yml** | API Key, Bearer Token, PAT | token |
| **oauthClientProfile.yml** | OAuth2 Client Credentials | clientId, clientSecret |
| **oauthTokenProfile.yml** | OAuth2 with Refresh | clientId, clientSecret |
| **basicConnection.yml** | Username/Password, Email/Password | username, password |

**Path**: `./node_modules/@zerobias-org/types-core/schema/{profileName}.yml`

### Selection Decision Tree

```
@credential-manager provides authMethodType:

IF authMethodType == "bearer-token" OR "api-key"
  → EXTEND tokenProfile.yml

IF authMethodType == "oauth2-client-credentials"
  → EXTEND oauthClientProfile.yml

IF authMethodType == "oauth2-authorization-code"
  → EXTEND oauthTokenProfile.yml

IF authMethodType == "basic-auth"
  → EXTEND basicConnection.yml
```

## Available Core States

| State | When to Use | Contains |
|-------|-------------|----------|
| **tokenConnectionState.yml** | Simple token auth | accessToken + expiresIn |
| **oauthTokenState.yml** | OAuth with refresh | accessToken + refreshToken + expiresIn |

### State Selection

```
IF requiresRefresh == false
  → EXTEND tokenConnectionState.yml

IF requiresRefresh == true
  → EXTEND oauthTokenState.yml
```

## Common Patterns

### Pattern 1: Simple API Token

```yaml
# connectionProfile.yml
$ref: './node_modules/@zerobias-org/types-core/schema/tokenProfile.yml'

# connectionState.yml
$ref: './node_modules/@zerobias-org/types-core/schema/tokenConnectionState.yml'
```

### Pattern 2: OAuth Client Credentials

```yaml
# connectionProfile.yml
$ref: './node_modules/@zerobias-org/types-core/schema/oauthClientProfile.yml'

# connectionState.yml (no refresh)
$ref: './node_modules/@zerobias-org/types-core/schema/tokenConnectionState.yml'
```

### Pattern 3: OAuth with Refresh Token

```yaml
# connectionProfile.yml
$ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenProfile.yml'

# connectionState.yml
$ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenState.yml'
```

## 🚨 CRITICAL: OAuth "Click-to-Connect" = Module Half + Platform Half

**Choosing an OAuth `authorization_code` flow (the Hub "Connect" button) is only HALF a
self-serve task.** The module declares the capability; a **ZeroBias insider** must register
the OAuth app and wire it to the platform before the Connect button does anything. If you
ship the module half without opening the insider task, the button stays dark.

### The module half — YOU author this (fully self-serve)

| File | What to author |
|------|----------------|
| `connectionProfile.yml` | Extend `oauthTokenProfile.yml` (clientId, clientSecret, url). Add the provider hook: `x-oauth-providers: [<vendor>.oauth]` — this is what makes the Hub UI offer the OAuth popup. |
| `connectionState.yml` | Extend `oauthTokenState.yml` (accessToken, **refreshToken**, expiresIn in SECONDS, scope) — MUST extend `baseConnectionState` so the server schedules refresh. |
| `src/<Class>Impl.ts` (`connect()`/`refresh()`) | Exchange/refresh tokens. Receive `clientId`/`clientSecret` at runtime via the `oauthDetails` argument — **never hardcode or persist them**. Validate `oauthDetails.clientId`, `.clientSecret`, `connectionProfile.refreshToken`; throw `InvalidCredentialsError` if missing. |

```yaml
# connectionProfile.yml — OAuth authorization-code, with the provider hook
type: object
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenProfile.yml'
  - type: object
    x-oauth-providers:
      - <vendor>.oauth        # e.g. microsoft.oauth for Entra-based products (Wiz, etc.)
```

The `<vendor>.oauth` code refers to an **`OAuthProvider` resource** in the `zerobias-org/oauth`
repo. Reuse an existing provider when one fits (e.g. `microsoft` /
`login.microsoftonline.com/organizations/oauth2/v2.0/authorize` already exists) rather than
inventing a new one.

### The platform half — a ZeroBias INSIDER must do this (a contributor CANNOT self-serve)

The client_id/secret live in AWS Secrets Manager, and the redirect URI is ZeroBias's shared
Global App callback — none of this is authorable from the module repo. So **when OAuth
authorization-code is chosen, tell the user to open a registration task** with this body:

```
Title: Register <vendor> OAuth app + link to <module-package> connectionProfile

The <module-package> connector is OAuth-capable (authorization-code + refresh) but its
"Connect" button won't work until the platform-side OAuth wiring exists:
  1. Register (or confirm) the <vendor> OAuth app with the provider → obtain client_id +
     client_secret; confirm ZeroBias's Global App redirect URI is allow-listed and the
     required scopes / admin-consent are granted.
  2. Store client_id/client_secret in AWS Secrets Manager (dev + prod).
  3. Load the OAuthProvider resource (zerobias-org/oauth) if it does not already exist,
     and LINK it to the module's connectionProfile (referenced via x-oauth-providers:
     [<vendor>.oauth]) so the Store advertises OAuth support.

Outcome: OAuth-enabled <vendor> connections show the click-to-Connect button in Hub.
```

> OAuth **client-credentials** (Pattern 2) is different: it needs no click-to-connect popup
> and no provider link — the user supplies clientId/clientSecret directly. No insider task.
> The registration task is only for the **authorization_code** ("Connect" button) flow.

### Pattern 4: Custom Fields (Extend Core)

```yaml
# connectionProfile.yml - Add organizationId
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/tokenProfile.yml'
  - type: object
    required: [organizationId]
    properties:
      organizationId:
        type: string
        description: Organization identifier for API access

# connectionState.yml - Add extra state
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/tokenConnectionState.yml'
  - type: object
    properties:
      organizationName:
        type: string
        description: Cached organization name
```

## Connection Profile Scope

### What Belongs in connectionProfile

**ONLY connection parameters - minimal set needed to CONNECT**

```yaml
# ✅ GOOD - Connection parameters
- token / apiKey / clientId / clientSecret
- baseUrl (if API has multiple environments)
- organizationId (if required to authenticate)

# ❌ BAD - Operation parameters
- limit (pagination parameter)
- projectId (operation scope, not connection scope)
- fields (query parameter)
```

**Rule**: If you can connect WITHOUT it, it's an operation parameter (not profile)

### Check for Additional Optional Parameters

**Always review API docs for environment-specific optional parameters:**

```yaml
# Example: Service has optional region parameter
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/tokenProfile.yml'
  - type: object
    properties:
      region:
        type: string
        description: Optional region for multi-region deployments
        enum: [us-east-1, eu-west-1, ap-southeast-1]
    # region is optional, but include it if API docs mention it
```

**Don't omit parameters that some environments might need!**

## 🚨 CRITICAL: Check Parent Schema First

**Before adding ANY property, check what the parent schema provides:**

```bash
# Check what tokenProfile.yml provides
cat node_modules/@zerobias-org/types-core/schema/tokenProfile.yml

# Check what basicConnection.yml provides
cat node_modules/@zerobias-org/types-core/schema/basicConnection.yml

# Check what baseConnectionState.yml provides
cat node_modules/@zerobias-org/types-core/schema/baseConnectionState.yml
```

### Avoid Semantic Duplication

```yaml
# ❌ WRONG - basicConnection already has uri
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/basicConnection.yml'
  - type: object
    properties:
      url:    # Duplicate! basicConnection has uri
        type: string

# ✅ CORRECT - Use parent's uri, or extend if truly different
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/basicConnection.yml'
  # uri is already provided by basicConnection
```

**Semantic duplicates to avoid:**
- url / uri / baseUrl
- token / accessToken
- username / user / userName
- password / pass / pwd

## 🚨 CRITICAL: ALWAYS Check Product Documentation

**Before finalizing connectionProfile, check product docs for ALL auth parameters:**

```yaml
# Example: Missing mfaCode from product docs
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/basicConnection.yml'
  - type: object
    properties:
      mfaCode:    # Found in product docs!
        type: string
        description: Multi-factor authentication code (optional)
```

**Don't assume - verify:**
1. Read product documentation authentication section
2. Look for optional parameters (region, environment, mfaCode, etc.)
3. Include ALL mentioned parameters (even if optional)
4. Don't omit parameters some environments might need

## 🚨 CRITICAL: Connection Scope MUST NOT Limit Operations

**NEVER add organization/project/workspace IDs to connection:**

```yaml
# ❌ WRONG - Limits connection to single organization
connectionState:
  allOf:
    - $ref: './node_modules/@zerobias-org/types-core/schema/baseConnectionState.yml'
    - type: object
      properties:
        accessToken:
          type: string
        organizationId:    # NO! Limits scope
          type: string

# ✅ CORRECT - Connection works across all organizations
connectionState:
  allOf:
    - $ref: './node_modules/@zerobias-org/types-core/schema/baseConnectionState.yml'
    - type: object
      properties:
        accessToken:
          type: string
  # organizationId is operation parameter, NOT connection parameter
```

**WHY**: Connection should be reusable across all scopes. Use operation parameters for scope.

**Scope identifiers belong in operation parameters, NOT connection:**
- ❌ organizationId in connection
- ❌ workspaceId in connection
- ❌ projectId in connection
- ❌ teamId in connection
- ✅ These are operation parameters (use in API paths)

## 🚨 CRITICAL: Only Add Used Fields (for connectionState)

**For connectionState - don't add fields "just in case" - only add what's actually USED:**

```yaml
# ❌ WRONG - identityId not used anywhere
connectionState:
  properties:
    accessToken: string
    identityId: string    # Where is this used? Nowhere!

# ✅ CORRECT - Only fields that are actually used
connectionState:
  properties:
    accessToken: string
  # If connect() doesn't return identityId, don't add it
```

**Ask:**
1. Does connect() method return this field?
2. Do operations use this field?
3. Is it needed for refresh/reconnect?
If NO to all → don't add it

**For connectionProfile - include environment-optional fields:**

```yaml
# ✅ CORRECT - Include optional fields that some environments need
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/tokenProfile.yml'
  - type: object
    properties:
      region:
        type: string
        description: Optional region (needed in some deployments)
      mfaCode:
        type: string
        description: Multi-factor authentication code (optional)
    # These aren't required, but include them if product docs mention them
```

**connectionProfile vs connectionState:**
- **connectionProfile**: Include optional fields that might be needed in some environments (region, mfaCode, etc.)
- **connectionState**: Only fields actually used by connect() or operations

## Validation Checklist

Before finalizing connection schemas:

- [ ] **Checked parent schema** - no semantic duplicates (url/uri, token/accessToken)
- [ ] **Checked product docs thoroughly** - all auth parameters included (mfaCode, region, etc.)
- [ ] connectionProfile extends a core profile (NOT custom)
- [ ] connectionState extends baseConnectionState (includes expiresIn)
- [ ] expiresIn is in seconds (integer), not expiresAt timestamp
- [ ] If refresh capability: state extends oauthTokenState
- [ ] If simple token: state extends tokenConnectionState
- [ ] Custom fields use allOf to extend core
- [ ] All required fields marked as required
- [ ] Checked API docs for optional connection parameters (region, environment, etc.)
- [ ] Profile contains ONLY connection parameters (not operation parameters)
- [ ] **NO scope limitation** - no organizationId/projectId/workspaceId in connection
- [ ] **Only used fields** - every state field is actually used by connect() or operations
- [ ] Minimal set needed to connect (don't add operation scope parameters)

## 🚨 CRITICAL: Build-Time Schema Resolution

**ALWAYS use $ref in committed files - resolve at build time**

### Why Build-Time Resolution?

- **Clean source files**: `connectionProfile.yml` and `connectionState.yml` stay as `$refs` (single source of truth)
- **No duplicate maintenance**: Changes to core schemas automatically propagate
- **CI-friendly**: Resolution happens automatically during gradle's `:generate` task.

### The Pattern

**Committed files (source of truth):**
```yaml
# connectionProfile.yml - KEEP AS $REF
type: object
allOf:
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenProfile.yml'
  - $ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenState.yml'
  - type: object
    x-ui-required:
      - accessToken
    x-ui-hidden:
      - tokenType
      - refreshToken
    x-oauth-providers:
      - vendor.oauth

# connectionState.yml - KEEP AS $REF
$ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenState.yml'
```

**Build-time resolution:**

Run `zbb generate` — it chains `syncMeta → assembleSpec → bundleSpec → generate*` automatically. `bundleSpec` natively inlines every `$ref` in `api.yml` + `connectionProfile.yml` (+ optional `connectionState.yml`); no manual `$ref` resolution step is needed.

```bash
zbb generate
```

### ❌ DON'T: Commit Resolved Files

```yaml
# ❌ WRONG - Committed resolved/flattened file
type: object
properties:
  tokenType:
    type: string
  accessToken:
    type: string
  # ... all properties inlined
```

**Problems:**
- Duplicate maintenance when core schemas change
- Merge conflicts
- Harder to see what's custom vs inherited

### ✅ DO: Commit $ref Files, Resolve at Build

```yaml
# ✅ CORRECT - Committed as $ref
$ref: './node_modules/@zerobias-org/types-core/schema/oauthTokenState.yml'
```

**Benefits:**
- Single source of truth
- Auto-inherits core schema updates
- Clear distinction between core and custom

## Common Mistakes

### ❌ Creating Custom Profile
```yaml
# DON'T DO THIS
type: object
properties:
  apiKey: string
  baseUrl: string
```
**Fix**: Extend tokenProfile.yml

### ❌ Missing baseConnectionState
```yaml
# DON'T DO THIS
type: object
properties:
  accessToken: string
```
**Fix**: Extend baseConnectionState or tokenConnectionState

### ❌ Using expiresAt Instead of expiresIn
```yaml
# DON'T DO THIS
expiresAt:
  type: string
  format: date-time
```
**Fix**: Convert to expiresIn (seconds) in connect() method

### ❌ No expiresIn for Refresh Tokens
```yaml
# DON'T DO THIS
properties:
  accessToken: string
  refreshToken: string
  # Missing expiresIn!
```
**Fix**: MUST extend baseConnectionState for expiresIn

## Remember

1. **Always extend core profiles** - Never create custom from scratch
2. **Always extend baseConnectionState** - Server needs expiresIn for cronjobs
3. **expiresIn in seconds** - Not timestamps, not milliseconds
4. **Use allOf for extensions** - Add custom fields via composition
5. **Receive data from @credential-manager** - Don't guess auth method

---

**Connection schemas are YAML schemas - design them like api.yml schemas!**
