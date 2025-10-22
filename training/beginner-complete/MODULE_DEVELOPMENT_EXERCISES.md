# Module Development Exercises
## Hands-On Practice Workbook

**Purpose**: Reinforce learning through practical exercises
**Approach**: Build real modules step-by-step
**Difficulty**: Progressive (Beginner → Intermediate → Advanced)

---

## Exercise 1: Your First Module (Beginner)

**Objective**: Create a simple module with basic authentication and one operation

**API**: JSONPlaceholder (https://jsonplaceholder.typicode.com)
**Time**: 2-3 hours
**Difficulty**: ⭐ Beginner

### Specifications

**Module Details:**
- Vendor: jsonplaceholder
- Service: jsonplaceholder
- Module ID: jsonplaceholder-jsonplaceholder

**Authentication:**
- Type: None (public API)
- connectionProfile: Use tokenProfile.yml
- connectionState: Use tokenConnectionState.yml
- Note: Use dummy token for structure (API doesn't require auth)

**First Operation:**
- Operation: getUser
- Endpoint: GET /users/{id}
- Response: User object with id, name, email, etc.

### Step-by-Step Tasks

#### Task 1.1: Research
- [ ] Visit https://jsonplaceholder.typicode.com
- [ ] Review API documentation
- [ ] Test GET /users/1 with curl
- [ ] Save response to file
- [ ] Document response structure

**Validation:**
```bash
curl https://jsonplaceholder.typicode.com/users/1 | jq .
# Should return user object
```

#### Task 1.2: Scaffold
- [ ] Run Yeoman generator
- [ ] Run npm run sync-meta
- [ ] Run npm install
- [ ] Create .env file (can be empty for public API)

**Validation:**
```bash
ls package.json api.yml connectionProfile.yml connectionState.yml
npm run sync-meta
npm install
```

#### Task 1.3: Design API Specification
- [ ] Update api.yml with /users/{userId} endpoint
- [ ] Create User schema with id, name, email, username
- [ ] Use camelCase for all properties
- [ ] Only include 200 response
- [ ] Validate against 12 rules

**Challenge**: The API returns snake_case, but your spec should use camelCase!

**Validation:**
```bash
grep "_" api.yml | grep -v "x-" | grep -v "#"  # Should return nothing
npx swagger-cli validate api.yml  # Should pass
npm run clean && npm run generate  # Should succeed
```

#### Task 1.4: Implement Client
- [ ] Create JSONPlaceholderClient.ts
- [ ] Implement connect() (no real auth, just set dummy state)
- [ ] Implement isConnected() (check API health endpoint or always return true)
- [ ] Implement disconnect()
- [ ] Add apiClient getter

**Challenge**: Public API has no auth - how do you handle connect()?

**Solution**: Accept token from profile but don't use it. Return dummy state.

#### Task 1.5: Implement Mapper
- [ ] Create toUser() function
- [ ] Validate required fields (id, name, email)
- [ ] Map all fields from response
- [ ] Use map(Email, ...) for email
- [ ] Handle optional fields with optional()

**Challenge**: API returns id as number, but spec expects string!

**Solution**: `id: raw.id.toString()`

#### Task 1.6: Implement Producer
- [ ] Create UserProducer.ts
- [ ] Implement getUser(userId: string): Promise<User>
- [ ] Use this.client.apiClient for request
- [ ] Apply toUser() mapper
- [ ] Handle errors with handleAxiosError

#### Task 1.7: Write Unit Tests
- [ ] Create test/unit/Common.ts (mock connection)
- [ ] Create test/unit/UserProducerTest.ts
- [ ] Mock GET /users/1 with nock
- [ ] Test successful user retrieval
- [ ] Test user not found (404)
- [ ] Verify all tests pass

**Validation:**
```bash
npm run test:unit
# Should show: 3+ passing
```

#### Task 1.8: Write Integration Tests
- [ ] Create test/integration/Common.ts
- [ ] Create test/integration/UserProducerTest.ts
- [ ] Add debug logging
- [ ] Test with real API
- [ ] Verify tests pass

**Validation:**
```bash
npm run test:integration
# Should show: 2+ passing
```

#### Task 1.9: Build
- [ ] Run npm run build
- [ ] Run npm run shrinkwrap
- [ ] Verify dist/ created
- [ ] Verify npm-shrinkwrap.json exists

**Validation:**
```bash
npm run build && npm run shrinkwrap
ls dist/ npm-shrinkwrap.json
```

#### Task 1.10: Validate All Gates
- [ ] Run complete validation script
- [ ] Verify all 6 gates pass

**Validation:**
```bash
# All gates should pass
./validate-all-gates.sh
```

### Expected Outcome

After completing Exercise 1:
- ✅ Working module with 1 operation
- ✅ All 6 gates passing
- ✅ Understanding of complete workflow
- ✅ Confidence with basic patterns

---

## Exercise 2: OAuth Authentication (Intermediate)

**Objective**: Create module with OAuth authentication and multiple operations

**API**: GitHub API (https://docs.github.com/en/rest)
**Time**: 3-4 hours
**Difficulty**: ⭐⭐ Intermediate

### Specifications

**Module Details:**
- Vendor: github
- Service: github
- Module ID: github-github

**Authentication:**
- Type: Personal Access Token (Bearer token)
- connectionProfile: tokenProfile.yml
- connectionState: tokenConnectionState.yml

**Operations to Implement:**
1. getAuthenticatedUser - GET /user
2. listRepositories - GET /user/repos
3. getRepository - GET /repos/{owner}/{repo}

### Prerequisites

**Get GitHub Personal Access Token:**
1. Go to https://github.com/settings/tokens
2. Generate new token (classic)
3. Select scopes: repo, user
4. Copy token
5. Add to .env: GITHUB_TOKEN=your_token

### Tasks

#### Task 2.1: Research
- [ ] Review GitHub REST API docs
- [ ] Test authentication with curl
- [ ] Test all 3 operations with curl
- [ ] Save and sanitize responses

**Test commands:**
```bash
TOKEN="your_github_token"

curl -H "Authorization: Bearer $TOKEN" https://api.github.com/user
curl -H "Authorization: Bearer $TOKEN" https://api.github.com/user/repos
curl -H "Authorization: Bearer $TOKEN" https://api.github.com/repos/octocat/Hello-World
```

#### Task 2.2: API Specification
- [ ] Design User schema
- [ ] Design Repository schema
- [ ] Create getAuthenticatedUser operation
- [ ] Create listRepositories operation (with pagination)
- [ ] Create getRepository operation
- [ ] All properties camelCase
- [ ] Only 200 responses

**Challenge**: Repository schema has many fields - which ones are required vs optional?

**Hint**: Check what GitHub actually returns in EVERY response vs sometimes.

#### Task 2.3: Implementation
- [ ] Implement GitHubClient
- [ ] connect() stores token, makes /user call to validate
- [ ] isConnected() calls /user to check validity
- [ ] Create UserProducer with getAuthenticatedUser()
- [ ] Create RepositoryProducer with listRepositories() and getRepository()
- [ ] Create toUser() and toRepository() mappers

**Challenge**: listRepositories returns array directly, not wrapped in data object!

**Solution:**
```typescript
async listRepositories(results: PagedResults<Repository>): Promise<void> {
  const { data } = await this.client.apiClient.request({
    url: '/user/repos',
    method: 'get'
  });

  // data is ALREADY the array
  results.items = data.map(toRepository) || [];
}
```

#### Task 2.4: Testing
- [ ] Unit tests for all 3 operations
- [ ] Integration tests for all 3 operations
- [ ] Add GITHUB_TOKEN to .env
- [ ] Add test repository owner/name to .env
- [ ] All tests passing

**Challenge**: How do you test listRepositories with real API when you don't know how many repos exist?

**Solution:**
```typescript
it('should list repositories', async () => {
  const results = new PagedResults<Repository>();
  await repoApi.listRepositories(results);

  // Don't assert exact count, just that it's an array
  expect(results.items).to.be.an('array');
  if (results.items.length > 0) {
    expect(results.items[0].id).to.be.a('string');
  }
});
```

#### Task 2.5: Complete & Validate
- [ ] Build successful
- [ ] All 6 gates pass
- [ ] Module ready

### Expected Outcome

After Exercise 2:
- ✅ Working GitHub module
- ✅ Understanding of real authentication
- ✅ Multiple operations implemented
- ✅ Pagination handling
- ✅ Complex mapper creation

---

## Exercise 3: Full CRUD Module (Advanced)

**Objective**: Create complete module with all CRUD operations

**API**: Your choice (suggestions: Stripe, SendGrid, Twilio)
**Time**: 4-6 hours
**Difficulty**: ⭐⭐⭐ Advanced

### Requirements

**Minimum Operations:**
- [ ] list{Resource} (with pagination and filtering)
- [ ] get{Resource}
- [ ] create{Resource}
- [ ] update{Resource}
- [ ] delete{Resource}

**Advanced Features:**
- [ ] Multiple resource types (2+ producers)
- [ ] Complex nested schemas
- [ ] Query parameters and filtering
- [ ] Error handling for all cases
- [ ] Rate limiting consideration

### Tasks

#### Task 3.1: Complete API Research
- [ ] Document ALL available endpoints
- [ ] Identify all resource types
- [ ] Map relationships between resources
- [ ] Test CRUD operations with curl
- [ ] Document rate limits

#### Task 3.2: Design Complete Specification
- [ ] api.yml with all operations
- [ ] All resource schemas
- [ ] Reusable parameters in components
- [ ] Proper pagination support
- [ ] Filter parameters documented

#### Task 3.3: Implement All Producers
- [ ] At least 2 resource producers
- [ ] All CRUD operations
- [ ] Proper error handling
- [ ] Connection context NOT duplicated

#### Task 3.4: Comprehensive Testing
- [ ] Unit tests for ALL operations
- [ ] Integration tests for ALL operations
- [ ] Error case coverage
- [ ] Edge case coverage
- [ ] 20+ test cases total

#### Task 3.5: Production Ready
- [ ] All 6 gates pass
- [ ] Complete documentation
- [ ] Performance optimized
- [ ] Security reviewed

### Expected Outcome

After Exercise 3:
- ✅ Production-ready module
- ✅ Mastery of all patterns
- ✅ Confidence with complex APIs
- ✅ Ready to work independently

---

## Self-Assessment Quiz

### Quiz 1: API Specification

**1. Which property naming is correct?**
   a) user_id
   b) userId
   c) UserId
   d) USERID

**2. Which operationId is correct for retrieving a single user?**
   a) describeUser
   b) fetchUser
   c) getUser
   d) retrieveUser

**3. Which response codes should be in api.yml?**
   a) 200, 404, 500
   b) 200, 201
   c) All HTTP status codes
   d) Only successful responses (200-299)

**4. Where should duplicate parameters be defined?**
   a) In each operation
   b) In components/parameters
   c) In a separate file
   d) Parameters can't be reused

**5. What must connectionState.yml extend?**
   a) Nothing
   b) tokenProfile.yml
   c) baseConnectionState.yml
   d) connectionProfile.yml

**Answers:**
1. b) userId - camelCase only (Rule 5)
2. c) getUser - correct verb (Rule 10)
3. b) 200, 201 - only success codes (Rule 12)
4. b) In components/parameters (Rule 4)
5. c) baseConnectionState.yml (for expiresIn)

### Quiz 2: Implementation

**1. Where should business operations be implemented?**
   a) Client
   b) Producer
   c) Mapper
   d) Connector

**2. What should Producer methods receive?**
   a) apiKey, userId, baseUrl
   b) client, apiKey, userId
   c) Only business parameters
   d) All connection context

**3. What's the correct mapper naming?**
   a) mapUser
   b) toUser
   c) convertUser
   d) userMapper

**4. Which is correct for optional fields?**
   a) raw.name || undefined
   b) optional(raw.name)
   c) raw.name ? raw.name : undefined
   d) raw.name ?? null

**5. Which mocking library for unit tests?**
   a) jest
   b) sinon
   c) nock
   d) fetch-mock

**Answers:**
1. b) Producer - operations only in producers
2. c) Only business parameters - no connection context
3. b) toUser - to<Model> pattern
4. b) optional(raw.name) - preserves "", 0, false
5. c) nock - ONLY nock for HTTP mocking

### Quiz 3: Testing

**1. Where should test credentials be stored?**
   a) Hardcoded in tests
   b) In .env file
   c) In test files as constants
   d) In package.json

**2. Unit test Common.ts should have:**
   a) Real credentials from .env
   b) No environment variables
   c) Mock credentials only
   d) Both real and mock

**3. Integration tests should:**
   a) Mock all HTTP calls
   b) Use real API calls
   c) Skip if no credentials
   d) Both b and c

**4. Debug logging is mandatory for:**
   a) Unit tests only
   b) Integration tests only
   c) All tests
   d) No tests

**5. Test data IDs should be:**
   a) Hardcoded in test files
   b) Generated randomly
   c) In .env and imported from Common.ts
   d) Inline in each test

**Answers:**
1. b) In .env file
2. b) No environment variables
3. d) Both real API calls AND skip if no credentials
4. b) Integration tests only (mandatory)
5. c) In .env and imported from Common.ts

---

## Challenge Exercises

### Challenge 1: Fix Failing Module

**Scenario**: You've been given a module with failing gates. Fix it.

**Given:**
```yaml
# api.yml (has issues!)
paths:
  /users/{id}:
    get:
      operationId: describeUser
      tags:
        - users
      responses:
        '200':
          schema:
            type: object
            properties:
              user_id:
                type: string
        '404':
          description: Not found
```

**Tasks:**
- [ ] Identify all violations
- [ ] Fix api.yml
- [ ] Validate fixes

**Violations to find:**
- Using 'describe' instead of 'get'
- Plural tag 'users' instead of singular 'user'
- snake_case 'user_id' instead of camelCase 'userId'
- Error response 404 included
- Path parameter 'id' should be 'userId'

### Challenge 2: Complex Mapper

**Scenario**: API returns deeply nested structure. Create proper mappers.

**API Response:**
```json
{
  "id": 123,
  "user": {
    "id": 456,
    "email": "user@example.com",
    "profile": {
      "first_name": "John",
      "last_name": "Doe",
      "address": {
        "street": "123 Main St",
        "city": "Boston"
      }
    }
  }
}
```

**Tasks:**
- [ ] Design schema hierarchy
- [ ] Create helper mappers for nested objects
- [ ] Use mapWith() correctly
- [ ] Validate required fields at each level
- [ ] Handle snake_case to camelCase conversion

**Expected mappers:**
- toAddress() (helper)
- toUserProfile() (helper)
- toUserNested() (helper)
- toResource() (main, exported)

### Challenge 3: Pagination Variations

**Scenario**: Implement 3 different pagination patterns.

**Pattern 1: Offset/Limit**
```
GET /users?offset=0&limit=10
Response: { data: [...], total: 100 }
```

**Pattern 2: Page/Size**
```
GET /users?page=1&size=10
Response: { items: [...], totalPages: 10 }
```

**Pattern 3: Token-Based**
```
GET /users?pageToken=abc123&pageSize=10
Response: { data: [...], nextPageToken: "xyz789" }
```

**Tasks:**
- [ ] Implement all 3 patterns in different producers
- [ ] Convert PagedResults.pageNumber/pageSize correctly
- [ ] Handle nextPageToken for token-based
- [ ] Update results.count appropriately

---

## Practice Projects

### Project 1: Weather API Module
**API**: OpenWeatherMap API
**Operations**: getCurrentWeather, getForecast
**Auth**: API Key
**Focus**: Simple module, good for beginners

### Project 2: GitHub Full Module
**API**: GitHub REST API
**Operations**: Users, Repos, Issues (15+ operations)
**Auth**: Personal Access Token
**Focus**: Multiple resources, complex schemas

### Project 3: Stripe Payments Module
**API**: Stripe API
**Operations**: Customers, Charges, Subscriptions
**Auth**: API Key with test mode
**Focus**: Complex business logic, idempotency

### Project 4: SendGrid Email Module
**API**: SendGrid API
**Operations**: Send email, Templates, Lists
**Auth**: API Key
**Focus**: Different request/response patterns

---

## Debugging Challenges

### Challenge 4: Fix Failing Tests

**Given**: Integration tests are failing with mysterious errors.

**Symptoms:**
```
Error: connect ECONNREFUSED
Error: Invalid credentials
Error: User not found
```

**Tasks:**
- [ ] Diagnose each error
- [ ] Check .env file
- [ ] Test credentials with curl
- [ ] Verify API endpoints
- [ ] Fix issues systematically

### Challenge 5: Fix Build Errors

**Given**: Module won't compile.

**Symptoms:**
```
error TS2304: Cannot find name 'User'
error TS2339: Property 'id' does not exist
error TS2307: Cannot find module '../generated/api'
```

**Tasks:**
- [ ] Identify root cause
- [ ] Check if types were generated
- [ ] Verify imports
- [ ] Fix compilation errors
- [ ] Verify build passes

---

## Advanced Scenarios

### Scenario 1: API Breaking Change

**Problem**: API changed response structure. Tests now failing.

**Old Response:**
```json
{
  "id": "123",
  "name": "John Doe"
}
```

**New Response:**
```json
{
  "userId": "123",
  "fullName": "John Doe"
}
```

**Tasks:**
- [ ] Update api.yml schema
- [ ] Update mapper to handle new fields
- [ ] Update tests
- [ ] Ensure backward compatibility if possible
- [ ] Document breaking change

### Scenario 2: Rate Limiting

**Problem**: Integration tests hitting rate limits.

**Tasks:**
- [ ] Identify rate limit from API docs
- [ ] Implement exponential backoff
- [ ] Add rate limit error handling
- [ ] Test rate limit scenario
- [ ] Document rate limits in USERGUIDE.md

### Scenario 3: Authentication Token Refresh

**Problem**: Tokens expire, need refresh implementation.

**Tasks:**
- [ ] Design connectionState with refreshToken
- [ ] Implement refresh() method in Client
- [ ] Store expiresIn correctly (in seconds!)
- [ ] Test token expiration scenario
- [ ] Verify framework calls refresh automatically

---

## Mastery Checklist

**You've mastered module development when you can:**

- [ ] ✅ Create a module from scratch passing all 6 gates
- [ ] ✅ Pass all 6 gates on first try
- [ ] ✅ Debug failing tests systematically
- [ ] ✅ Design API specs that match reality
- [ ] ✅ Write mappers handling all edge cases
- [ ] ✅ Implement authentication for any auth type
- [ ] ✅ Add operations to modules efficiently
- [ ] ✅ Review and fix other developers' modules
- [ ] ✅ Explain module architecture to others
- [ ] ✅ Contribute to module development standards

---

## Next Steps After Training

### Level 1: Beginner (Weeks 1-2)
- Complete Exercise 1 (JSONPlaceholder)
- Complete Exercise 2 (GitHub)
- Build 1-2 simple modules (API Key auth)

### Level 2: Intermediate (Weeks 3-4)
- Complete Exercise 3 (Full CRUD)
- Build 2-3 OAuth modules
- Add operations to existing modules
- Review other developers' code

### Level 3: Advanced (Weeks 5-8)
- Build complex multi-resource modules
- Handle edge cases (rate limits, pagination variations)
- Optimize performance
- Contribute to standards and templates

### Level 4: Expert (Weeks 9-12)
- Mentor other developers
- Design new patterns
- Improve tooling and automation
- Lead code reviews

---

## Learning Resources

**Official Documentation:**
- OpenAPI Specification: https://swagger.io/specification/
- TypeScript Handbook: https://www.typescriptlang.org/docs/
- Mocha Testing: https://mochajs.org/
- Nock HTTP Mocking: https://github.com/nock/nock

**Internal Resources:**
- Full Training Manual: MODULE_DEVELOPMENT_TRAINING.md
- Quick Reference: MODULE_DEVELOPMENT_QUICKSTART.md
- Validation Checklists: MODULE_DEVELOPMENT_CHECKLISTS.md
- Development Rules: .claude/rules/

**Practice APIs:**
- JSONPlaceholder: https://jsonplaceholder.typicode.com (beginner)
- GitHub API: https://docs.github.com/en/rest (intermediate)
- Stripe API: https://stripe.com/docs/api (advanced)
- Your organization's internal APIs (real-world)

---

## Tips for Success

**1. Start Simple**
- Begin with public APIs (no auth complexity)
- Implement 1 operation first
- Add more operations incrementally

**2. Test Early, Test Often**
- Use curl before implementing
- Write tests alongside code
- Run gates incrementally

**3. Use the Tools**
- Validation scripts are your friend
- Debug logging saves time
- Templates speed up development

**4. Learn from Existing Modules**
- Read code in package/ directory
- Copy working patterns
- Understand why things are done certain ways

**5. Ask Questions**
- Review training manual sections
- Check .claude/rules/ for specific patterns
- Test assumptions with curl

**6. Build Muscle Memory**
- Repeat the process
- Build 5+ modules for proficiency
- Patterns will become automatic

---

## Mastery Checklist

**You've mastered module development when you can:**

- [ ] Create a complete module passing all 6 gates
- [ ] Add operations to existing modules passing all gates
- [ ] Debug and fix any failing gate systematically
- [ ] Explain the architecture to a colleague
- [ ] Review PRs and identify violations
- [ ] Handle any authentication method
- [ ] Work with any REST API structure
- [ ] Write tests that actually catch bugs
- [ ] Optimize module performance
- [ ] Contribute improvements to standards

---

**Happy Coding!** 🚀

Remember: Quality over speed. All 6 gates must pass!
