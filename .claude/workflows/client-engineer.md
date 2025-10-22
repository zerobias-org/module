# Client Engineer Workflow

## Overview

This workflow covers implementing the Client class that handles ONLY connection management. All business logic goes in Producer classes.

## Input

- Connection profile schema (connectionProfile.yml)
- Connection state schema (connectionState.yml)
- Generated connector interface
- Producer classes

## Output

- Client class (src/{Service}Client.ts)
- connect() method implemented
- isConnected() method implemented
- disconnect() method implemented
- Producer initialization
- Connection state management

## Key Principles

- **Client ONLY handles connection** - No business logic
- **All operations delegate to producers**
- **No environment variables** in src/
- **Use core profiles** when possible
- **Store minimal state**
- **expiresIn MANDATORY** when tokens expire

## Detailed Steps

### Step 1: Review Connection Profile

- Read connectionProfile.yml from module directory
- Identify authentication method (token, OAuth, basic, etc.)
- Determine required vs optional fields
- Check if core profile can be used
- **Core profiles:** See "Core Profile Usage" in @.claude/rules/connection-profile-design.md

### Step 2: Review Connection State

- Read connectionState.yml from module directory
- Identify what state needs persistence
- Check if token expiration is involved (expiresIn required)
- Determine if refresh token needed
- **State patterns:** See "ConnectionState Pattern" in @.claude/rules/implementation.md

### Step 3: Create Client File

- Create src/{Service}Client.ts
- Follow naming convention: {Service}Client (PascalCase)

### Step 4: Import Dependencies

Import required modules:
- axios and AxiosInstance
- Generated connector interface
- Producer classes
- Core error types (NotConnectedError, etc.)
- **Import patterns:** See "Client Class Structure" in @.claude/rules/implementation.md

### Step 5: Create Client Class Structure

- Implement {Service}Connector interface
- Declare private fields: httpClient, connectionProfile, connectionState
- Declare producer instances as private fields
- Define method signatures: connect(), isConnected(), disconnect()
- Add delegation methods for all operations
- **Structure template:** See "Client Class Pattern" in @.claude/rules/implementation.md

### Step 6: Implement connect() Method

- Accept connection profile parameter
- Store profile in private field
- Configure axios HTTP client (baseURL, timeout, headers)
- Set up authentication headers
- Add error interceptors
- Initialize all producer instances with HTTP client
- Store connection state (include expiresIn if tokens expire)
- **connect() patterns:** See "connect() Implementation" in @.claude/rules/implementation.md
- **HTTP client setup:** See "Client Configuration" in @.claude/rules/http-client-patterns.md

### Step 7: Implement isConnected()

- Check if HTTP client exists
- Make real API call to verify connection (NOT just state check)
- Return true if API call succeeds
- Return false if API call fails
- **isConnected() patterns:** See "isConnected() Implementation" in @.claude/rules/implementation.md

### Step 8: Implement disconnect()

- Clean up HTTP client (set to undefined)
- Clean up all producer instances
- Clear connection state
- Clear connection profile
- **disconnect() patterns:** See "disconnect() Implementation" in @.claude/rules/implementation.md

### Step 9: Delegate Operations to Producers

For each operation in connector interface:
- Check if producer is initialized (throw NotConnectedError if not)
- Delegate to appropriate producer method
- Pass through parameters unchanged
- Return producer result directly
- **Delegation patterns:** See "Operation Delegation" in @.claude/rules/implementation.md

### Step 10: Validate Implementation

Run validation checks:
- Verify client implements generated connector interface
- Confirm no business logic in client (only connection + delegation)
- Check no environment variables used
- Verify producers initialized with HTTP client
- Confirm connection state includes expiresIn (if tokens expire)
- **Validation scripts:** See "Client Validation" in @.claude/rules/implementation.md

### Step 11: Build and Test

- Run npm run build (must pass)
- Run npm run test:unit (unit tests must pass)
- Fix any compilation or test errors

## Connection State Patterns

### When to Use Each Pattern

**Simple Token (No Refresh):**
- Static API token that never expires
- No connection state needed
- Just configure HTTP client with token
- **Pattern:** See "Simple Token Pattern" in @.claude/rules/implementation.md

**OAuth with Refresh:**
- Token expires and needs refresh
- Connection state MUST include: accessToken, refreshToken, expiresIn
- Framework persists state automatically
- **Pattern:** See "OAuth Pattern" in @.claude/rules/implementation.md

**Using Core Profiles:**
- When service matches core profile type (TokenProfile, OAuthClientProfile, etc.)
- Import from @auditmation/types-core-js
- Reduces code duplication
- **Pattern:** See "Core Profile Usage" in @.claude/rules/connection-profile-design.md

## Success Criteria

Client implementation is complete when:

- ✅ Client focuses only on connection management
- ✅ All operations delegate to producers
- ✅ No environment variables in client
- ✅ HTTP client properly configured
- ✅ Producers initialized correctly
- ✅ Connection state includes expiresIn (if tokens expire)
- ✅ Uses core profiles when applicable
- ✅ isConnected() makes real API call (not state check)
- ✅ disconnect() cleans up all resources
- ✅ Build passes
- ✅ Unit tests pass

## Related Documentation

**Primary Rules:**
- @.claude/rules/implementation.md - Client patterns, connection state rules, delegation
- @.claude/rules/http-client-patterns.md - HTTP client configuration and error handling
- @.claude/rules/connection-profile-design.md - Profile patterns and core profiles

**Supporting Rules:**
- @.claude/rules/error-handling.md - Core error usage
- @.claude/rules/security.md - Secure authentication implementation
- @.claude/rules/failure-conditions.md - Common implementation failures
