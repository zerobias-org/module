# Client Engineer Workflow

## Overview

This workflow covers the Client class, which owns the axios instance and the connection state, and the
lifecycle delegation in the Impl class. Business logic goes in producers.

The code shape is defined in "The Client" and "The Impl class" of @.claude/skills/implementation/SKILL.md and
walked through in `references/client.md` of that skill. Read the closest real Client named there before
writing code.

## Input

- `connectionProfile.yml` (connector modules only; a plain module has none)
- `connectionState.yml` (only when the module mints an expiring token)
- Generated interface in `generated/api/` (`<Class>Connector` for a connector, `<Class>` for a plain module)
- Producer classes

## Output

- Client class (`src/<Name>Client.ts`)
- Connector: `connect()`, `isConnected()`, `disconnect()`, and `refresh()` when a state exists
- Plain module: axios instance created in the constructor, no lifecycle
- `src/<Class>Impl.ts` forwarding the lifecycle to the Client and returning one wrapped producer per tag

## Key Principles

- The Client handles the connection only; it has no resource methods and no `get`/`post` helpers
- Producers call axios through the instance the Client exposes
- No `process.env` in `src/`; credentials come from the profile passed to `connect()`
- A state is returned only when a token expires, and then it carries `expiresIn`

## Detailed Steps

### Step 1: Review Connection Profile

- Check whether the module is a connector or plain: see "1. Decide: does the source need credentials?" in
  @.claude/skills/connection-profile/SKILL.md. For a plain module skip to Step 3.
- Read `connectionProfile.yml` and the generated `ConnectionProfile` model
- Identify the authentication method and the required and optional fields
- **Profile shapes:** See "2. Pick the profile shape (connector)" in @.claude/skills/connection-profile/SKILL.md

### Step 2: Review Connection State

- Check whether `connectionState.yml` exists; the generated interface shows the resulting `connect()` signature
- If it exists, note the token lifetime field and whether there is a refresh token
- **State rules:** See "4. Add `connectionState.yml` only when a token expires" in
  @.claude/skills/connection-profile/SKILL.md

### Step 3: Create Client File

- Create `src/<Name>Client.ts`
- **Naming:** See "Layout and naming" in @.claude/skills/implementation/SKILL.md

### Step 4: Import Dependencies

- axios, the generated `ConnectionProfile` (and `ConnectionState`) models, core errors
- **Import rules:** See "Imports" in @.claude/skills/implementation/SKILL.md

### Step 5: Create Client Class Structure

- Private fields for the axios instance and the credential or token
- Expose the axios instance to producers (getter for a connector, `public readonly` for a plain module)
- **Structure:** See "The shape" in @.claude/skills/implementation/references/client.md

### Step 6: Implement connect() Method

- Read credentials from the profile argument
- Create the axios instance with base URL, timeout and `validateStatus`
- Log in and build the state when the module has one
- **connect() and axios setup:** See "The Client" in @.claude/skills/implementation/SKILL.md and the
  matching module section in @.claude/skills/implementation/references/client.md

### Step 7: Implement isConnected()

- **isConnected():** See "Rules" in @.claude/skills/implementation/references/client.md

### Step 8: Implement disconnect()

- Clear the token or credential and the axios instance
- **disconnect():** See "The Client" in @.claude/skills/implementation/SKILL.md

### Step 9: Wire the Impl

- The Impl creates one Client, forwards the lifecycle calls to it, and returns a wrapped producer per tag
- Producers receive the Client in their constructor
- **Impl and producer creation:** See "The Impl class" in @.claude/skills/implementation/SKILL.md and
  "How the Impl uses it" in @.claude/skills/implementation/references/client.md

### Step 10: Validate Implementation

- Run the `check-src.sh` script described under "No environment access, no secrets" in
  @.claude/skills/implementation/SKILL.md and clear its findings
- The Impl implements the generated interface for the module kind
- No resource methods and no second axios instance outside the Client
- A returned state includes `expiresIn`

### Step 11: Build and Test

- `zbb compile`, then `zbb --slot <slot> test` from the module directory
- Fix compilation and test errors in the implementation; commands are in
  @.claude/skills/build-lifecycle/SKILL.md

## Connection State Patterns

| Situation | Where to read |
|---|---|
| Static API key, no state | "Connector with a static key: `ReadyPlayerMeClient`" in @.claude/skills/implementation/references/client.md |
| Login with an expiring token, state returned | "Connector with login and expiring token: `AvigilonAltaAccessClient`" in the same file |
| OAuth authorization code with refresh | @.claude/skills/oauth-click-to-connect/SKILL.md |
| Core profile or a custom one | "2. Pick the profile shape (connector)" in @.claude/skills/connection-profile/SKILL.md |
| No credentials at all | "Plain module: `CmvpClient`" in @.claude/skills/implementation/references/client.md |

## Success Criteria

- Client holds connection code only; producers hold the operations
- No `process.env` in `src/`
- axios instance configured as the skill describes
- Returned state includes `expiresIn` when a token expires
- `disconnect()` clears the token and the axios instance
- `zbb compile` and unit tests pass

## Related Documentation

- @.claude/skills/implementation/SKILL.md - Client, Impl, producers, errors, check script
- @.claude/skills/connection-profile/SKILL.md - connector or plain, profile and state design
- @.claude/skills/gates/SKILL.md - checkpoints and failure table
