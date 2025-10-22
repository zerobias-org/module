# Module Development with AI Assistance
## Hybrid Approach - Know When to Use AI & When to Go Manual

**Audience**: Developers learning to work effectively with AI coding assistants
**Philosophy**: AI is a tool, not a replacement for understanding
**Goal**: Leverage AI for speed while maintaining quality and learning

---

## 🎯 The Hybrid Philosophy

### Core Principle
**Use AI for boilerplate, use your brain for critical decisions**

```
AI Strengths          Your Strengths         Best Outcome
  ↓                      ↓                        ↓
Code generation     Understanding         AI generates +
Repetitive tasks    Validation           You validate +
Pattern matching    Critical thinking    Quality assured
Speed               Judgment             Fast + Correct
```

---

## 🚦 The Traffic Light System

### 🟢 GREEN: AI Excels (Use AI Confidently)

**These tasks are safe to delegate to AI:**

1. **Scaffolding & Boilerplate**
   ```
   ✅ Running Yeoman generator
   ✅ Creating file structure
   ✅ Installing dependencies
   ✅ Basic setup commands
   ```

2. **Code Generation from Templates**
   ```
   ✅ util.ts helper functions (standard patterns)
   ✅ Test file structure
   ✅ Import statements
   ✅ Basic type definitions
   ```

3. **Repetitive Implementations**
   ```
   ✅ Multiple similar mappers
   ✅ CRUD operations (once pattern established)
   ✅ Test cases (following established pattern)
   ✅ Standard error handling
   ```

**AI Prompt Examples:**
```
"Create util.ts with handleAxiosError, ensureProperties, optional, mapWith, and toEnum functions"

"Generate unit test for UserProducer.getUser() using nock"

"Create 5 mapper functions following the to<Model> pattern for these schemas: User, Organization, Group, Role, Permission"
```

---

### 🟡 YELLOW: AI Needs Guidance (Use with Verification)

**These tasks need your review and validation:**

1. **API Specification Design**
   ```
   ⚠️ AI can draft api.yml, but YOU must validate against 12 rules
   ⚠️ AI can suggest schemas, but YOU must verify against real API responses
   ⚠️ AI can create operations, but YOU must ensure all are included
   ```

   **Workflow:**
   ```
   1. AI: Draft api.yml based on API docs
   2. YOU: Validate with curl against real API
   3. YOU: Check all 12 critical rules
   4. YOU: Run validation script
   5. AI: Fix issues you identified
   ```

2. **Mapper Implementation**
   ```
   ⚠️ AI can create mapper structure, but YOU must verify field completeness
   ⚠️ AI can apply patterns, but YOU must check required field validation
   ⚠️ AI can handle types, but YOU must ensure ZERO missing fields
   ```

   **Workflow:**
   ```
   1. AI: Generate toUser() mapper
   2. YOU: Compare to interface (count fields)
   3. YOU: Verify ensureProperties() includes ALL required fields
   4. YOU: Check optional() used correctly
   5. AI: Add any missing fields you found
   ```

3. **connectionProfile/State Selection**
   ```
   ⚠️ AI can suggest core profile, but YOU must verify it matches auth method
   ⚠️ AI can draft schema, but YOU must ensure baseConnectionState extended
   ⚠️ AI can add fields, but YOU must verify expiresIn present
   ```

   **Workflow:**
   ```
   1. YOU: Test auth with curl, identify exact method
   2. AI: Suggest appropriate core profile
   3. YOU: Verify profile matches (check node_modules/@auditmation/types-core/schema/)
   4. AI: Create connectionState extending baseConnectionState
   5. YOU: Verify expiresIn field present
   ```

---

### 🔴 RED: AI Often Fails (Do Manual or Verify Heavily)

**These tasks require deep understanding - AI often gets them wrong:**

1. **API Research & Discovery** 🔴
   ```
   ❌ AI cannot test real APIs for you
   ❌ AI cannot obtain credentials
   ❌ AI cannot validate API responses match docs
   ❌ AI cannot identify authentication nuances
   ```

   **YOU MUST:**
   - Test authentication with curl yourself
   - Capture real API responses
   - Identify actual authentication flow
   - Document response structure from actual data
   - Sanitize test data
   - Store credentials in .env

   **Why AI fails:**
   - Can't access external APIs
   - Can't run curl commands
   - Can't test with your credentials
   - Hallucinates API responses

2. **The 12 Critical Rules Validation** 🔴
   ```
   ❌ AI often misses snake_case violations
   ❌ AI includes error responses (4xx/5xx)
   ❌ AI uses 'describe' prefix
   ❌ AI forgets to check all 12 rules
   ```

   **YOU MUST:**
   ```bash
   # Run these yourself, don't trust AI
   grep -E "describe[A-Z]" api.yml
   grep -E "'40[0-9]'|'50[0-9]'" api.yml
   grep "_" api.yml | grep -v "x-" | grep -v "#"
   grep "nullable:" api.yml
   grep -E "^servers:|^security:" api.yml
   ```

   **Why AI fails:**
   - Doesn't strictly enforce all rules
   - Forgets some rules under token pressure
   - Generates "reasonable" but wrong specs

3. **Test Data Management** 🔴
   ```
   ❌ AI hardcodes test IDs in integration tests
   ❌ AI doesn't properly use .env for test data
   ❌ AI creates invalid test scenarios
   ```

   **YOU MUST:**
   - Add ALL test data to .env yourself
   - Export from test/integration/Common.ts
   - Verify no hardcoded IDs: `grep -E "const.*Id = ['\"]" test/integration/`
   - Check hasCredentials() works

   **Why AI fails:**
   - Defaults to hardcoding for simplicity
   - Doesn't follow .env patterns strictly
   - Forgets to export from Common.ts

4. **expiresIn Calculation** 🔴
   ```
   ❌ AI stores expiresAt instead of expiresIn
   ❌ AI forgets to convert timestamp to seconds
   ❌ AI doesn't extend baseConnectionState
   ```

   **YOU MUST:**
   ```typescript
   // Check AI generated this:
   const expiresAtTimestamp = new Date(response.data.expires_at).getTime();
   const nowTimestamp = Date.now();
   const expiresIn = Math.floor((expiresAtTimestamp - nowTimestamp) / 1000);

   // Check expiresIn is SECONDS, not milliseconds
   // Check connectionState extends baseConnectionState
   ```

   **Why AI fails:**
   - Confuses expiresAt vs expiresIn
   - Forgets conversion to seconds
   - Doesn't always extend baseConnectionState

5. **Gate Validation** 🔴
   ```
   ❌ AI claims gates pass without running commands
   ❌ AI skips gate validation steps
   ❌ AI doesn't catch all violations
   ```

   **YOU MUST:**
   - Run every gate validation command yourself
   - Check actual output, not AI's claim
   - Verify npm test actually passes
   - Confirm build exits with 0

   **Why AI fails:**
   - Can't execute commands in your environment
   - Optimistically assumes success
   - Misses edge cases

---

## 📋 The Hybrid Workflow

### Phase 1: Research (🔴 MANUAL - AI Can't Help)

**YOU DO THIS:**
```bash
# 1. Test auth
curl -X POST "https://api.service.com/auth/login" \
  -d '{"email":"test@example.com","password":"testpass"}'

# 2. Save token
TOKEN="actual-token-from-response"

# 3. Test operation
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.service.com/users/123" | tee response.json | jq .

# 4. Create .env
cat > .env << EOF
SERVICE_EMAIL=test@example.com
SERVICE_PASSWORD=testpass
SERVICE_TEST_USER_ID=123
EOF
```

**AI CAN'T:**
- Access your credentials
- Make real API calls
- Validate responses
- Test authentication

**CHECKPOINT:** You have real API response saved and credentials in .env

---

### Phase 2: Scaffolding (🟢 AI EXCELS)

**AI DOES THIS:**
```
"Run Yeoman generator for vendor-service module with my email your.email@company.com"
```

**AI will execute:**
```bash
yo @auditmation/hub-module ...
cd package/vendor/service
npm run sync-meta
npm install
```

**YOU VERIFY:**
```bash
# Quick check
ls package.json api.yml connectionProfile.yml connectionState.yml
grep "^title:" api.yml  # Should have title from sync-meta
```

**CHECKPOINT:** Module structure created, dependencies installed

---

### Phase 3: API Specification (🟡 AI WITH HEAVY VALIDATION)

**AI DOES THIS:**
```
"Create api.yml with getUser operation based on this response: [paste response.json]

Use:
- operationId: getUser (not describeUser)
- tag: user (singular)
- All properties camelCase
- Only 200 response
- User schema with required: id, email, firstName, createdAt
"
```

**YOU MUST VALIDATE** (Don't skip!):
```bash
# 1. Check 12 critical rules (MANUAL!)
grep -E "describe[A-Z]" api.yml  # Should be nothing
grep -E "'40[0-9]'|'50[0-9]'" api.yml  # Should be nothing
grep "_" api.yml | grep -v "x-" | grep -v "#"  # Should be nothing
grep "nullable:" api.yml  # Should be nothing
grep -E "^servers:|^security:" api.yml  # Should be nothing

# 2. Validate syntax
npx swagger-cli validate api.yml

# 3. Generate types
npm run clean && npm run generate

# 4. Check no inline types
grep "InlineResponse" generated/ || echo "✅ Good"
```

**IF VALIDATION FAILS:**
```
❌ Don't ask AI to fix blindly
✅ YOU identify specific violations
✅ Tell AI: "Change user_id to userId in api.yml"
✅ YOU re-validate after fix
```

**CRITICAL:** connectionState must extend baseConnectionState

```yaml
# AI might generate this (WRONG):
type: object
properties:
  accessToken:
    type: string

# YOU MUST ensure this (CORRECT):
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'
  - type: object
    properties:
      accessToken:
        type: string
```

**CHECKPOINT:** api.yml passes all 12 rules, types generated successfully

---

### Phase 4: Implementation (🟢 AI GOOD, 🟡 VERIFY KEY PARTS)

**AI DOES THIS:**
```
"Create ServiceClient.ts with connect/isConnected/disconnect for OAuth email/password auth.
Endpoint: POST /auth/login
Response fields: access_token, expires_in
Calculate expiresIn from expires_in (already in seconds)"
```

**YOU VERIFY** (Critical parts):
```typescript
// 1. Check expiresIn calculation
const state: ConnectionState = {
  accessToken: response.data.access_token,
  expiresIn: response.data.expires_in  // ✅ If already seconds
  // or
  expiresIn: Math.floor((expiresAt - Date.now()) / 1000)  // ✅ If timestamp
};

// 2. Check NO business operations in Client
// Should ONLY have: connect, isConnected, disconnect, apiClient getter

// 3. Check interceptors setup correctly
this.httpClient.interceptors.request.use(config => {
  if (this.connectionState?.accessToken) {
    config.headers['Authorization'] = `Bearer ${this.connectionState.accessToken}`;
  }
  return config;
});
```

**AI DOES THIS:**
```
"Create UserProducer with getUser operation.
Import User from generated/api.
Use toUser mapper.
Use handleAxiosError."
```

**YOU VERIFY:**
```typescript
// Check NO connection context parameters
async getUser(userId: string): Promise<User> {  // ✅ Only userId
  // NOT: async getUser(userId: string, apiKey: string) ❌
}

// Check uses generated types
import { User } from '../generated/api';  // ✅
async getUser(userId: string): Promise<User> {  // ✅ Not Promise<any>
}
```

**AI DOES THIS:**
```
"Create toUser mapper for this API response: [paste response.json]
- Validate required: id, email, firstName, createdAt
- Use map() for Email and DateTime
- Use optional() for lastName
- Convert snake_case to camelCase"
```

**YOU VERIFY:**
```typescript
// 1. Count fields
// Interface has N fields → Mapper must have N fields
export interface User {
  id: string;        // 1
  email: Email;      // 2
  firstName: string; // 3
  lastName?: string; // 4
  createdAt: Date;   // 5
}
// Mapper must map all 5 fields!

// 2. Check required validation
ensureProperties(raw, ['id', 'email', 'firstName', 'createdAt']);
// Must include ALL required fields from interface

// 3. Check no missing fields
const output: User = {
  id: raw.id.toString(),
  email: map(Email, raw.email),
  firstName: raw.first_name,
  lastName: optional(raw.last_name),
  createdAt: map(DateTime, raw.created_at)
};
// All 5 fields present ✅
```

**CHECKPOINT:** Build passes, no Promise<any>, ESLint clean

---

### Phase 5: Testing (🟡 AI GENERATES, YOU VALIDATE)

**AI DOES THIS:**
```
"Create unit test for UserProducer.getUser() using nock.
Mock GET /users/123 returning this fixture: [paste sanitized response]"
```

**YOU VERIFY:**
```typescript
// 1. Check uses nock ONLY (not jest/sinon)
import * as nock from 'nock';  // ✅

// 2. Check NO environment variables in unit test Common.ts
// Should NOT have:
import { config } from 'dotenv';  // ❌ in unit tests
process.env.SERVICE_API_KEY;      // ❌ in unit tests

// 3. Check tests actually test something
expect(user.email).to.be.instanceof(Email);  // ✅ Good
expect(user).to.exist;  // ⚠️ Too weak
```

**AI DOES THIS:**
```
"Create integration test for UserProducer.getUser()
- Import TEST_USER_ID from Common.ts
- Add debug logging before and after call
- Skip if no credentials"
```

**YOU MUST VERIFY** (AI often fails here!):
```bash
# 1. Check NO hardcoded IDs
grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts
# Should return nothing OR only "non-existent"/"invalid"

# 2. Check test data exported from Common.ts
cat test/integration/Common.ts | grep "export const.*TEST"
# Should see: export const SERVICE_TEST_USER_ID = ...

# 3. Check .env has test data
cat .env | grep TEST_USER_ID
# Should see: SERVICE_TEST_USER_ID=actual-id

# 4. Check debug logging present
grep "logger.debug" test/integration/*.ts
# Should find multiple instances
```

**MANUAL FIX if AI hardcoded:**
```typescript
// ❌ AI generated (WRONG):
const userId = '12345';  // Hardcoded!

// ✅ YOU fix to:
import { SERVICE_TEST_USER_ID } from './Common';
const userId = SERVICE_TEST_USER_ID;  // From .env
```

**CHECKPOINT:** Tests pass, no hardcoded data, debug logging present

---

### Phase 6-8: Documentation & Build (🟢 AI GOOD)

**AI DOES THIS:**
```
"Create USERGUIDE.md with credential instructions for this service"
"Run npm run build && npm run shrinkwrap"
```

**YOU VERIFY:**
```bash
# Quick checks
grep -E "your-actual-key|real-password" USERGUIDE.md  # Should be nothing
npm run build && echo $?  # Should be 0
ls npm-shrinkwrap.json  # Should exist
```

**CHECKPOINT:** Documentation complete, build successful

---

## 🎓 When AI Gets It Wrong - You Must Catch It

### AI Failure Pattern 1: Forgetting 12 Critical Rules

**AI might generate:**
```yaml
# AI output (has violations!)
paths:
  /users/{id}:  # ❌ Should be {userId}
    get:
      operationId: describeUser  # ❌ Should be getUser
      tags:
        - users  # ❌ Should be singular: user
      responses:
        '200':
          ...
        '404':  # ❌ Should not have error responses
          description: Not found
```

**YOU CATCH & FIX:**
```bash
# Run validation commands yourself
grep -E "describe[A-Z]" api.yml  # Catches describeUser
grep -E "'40[0-9]'|'50[0-9]'" api.yml  # Catches 404 response

# Tell AI specific fixes:
"Change operationId from describeUser to getUser"
"Remove 404 response from api.yml"
"Change tags from users to user"
"Change path parameter from {id} to {userId}"
```

---

### AI Failure Pattern 2: Hardcoding Test Data

**AI might generate:**
```typescript
// AI output (WRONG!)
it('should get user', async () => {
  const user = await api.getUser('12345');  // ❌ Hardcoded!
  expect(user.id).to.equal('12345');
});
```

**YOU CATCH & FIX:**
```bash
# Detect hardcoding
grep -E "const [a-z]*Id = ['\"]" test/integration/*.ts

# Fix manually or tell AI:
"Move all test IDs to .env and import from Common.ts"
```

**Then verify AI fixed it:**
```typescript
// ✅ Correct
import { SERVICE_TEST_USER_ID } from './Common';

it('should get user', async () => {
  const user = await api.getUser(SERVICE_TEST_USER_ID);
  expect(user.id).to.equal(SERVICE_TEST_USER_ID);
});
```

---

### AI Failure Pattern 3: Missing baseConnectionState

**AI might generate:**
```yaml
# AI output (WRONG!)
type: object
properties:
  accessToken:
    type: string
# Missing expiresIn!
```

**YOU CATCH & FIX:**
```bash
# Check manually
grep "baseConnectionState" connectionState.yml || echo "❌ MISSING!"

# Tell AI:
"connectionState.yml must extend baseConnectionState.yml using allOf"
```

**Verify fix:**
```yaml
# ✅ Correct
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'
  - type: object
    properties:
      accessToken:
        type: string
```

---

### AI Failure Pattern 4: Promise<any> in Signatures

**AI might generate:**
```typescript
// AI output (WRONG!)
async getUser(userId: string): Promise<any> {  // ❌ any!
  const { data } = await this.client.apiClient.request(...);
  return data;
}
```

**YOU CATCH:**
```bash
grep "Promise<any>" src/*.ts  # Catches the violation
```

**YOU FIX:**
```
"Change Promise<any> to Promise<User> and apply toUser mapper"
```

---

## 🛠️ Hybrid Workflow Example

### Creating a Complete Module (Hybrid Approach)

**Step 1: Research** (🔴 YOU - 10 min)
```bash
# YOU test manually
curl -X POST "https://api.service.com/auth" -d '{"email":"...","password":"..."}'
curl -H "Authorization: Bearer $TOKEN" "https://api.service.com/users/123" > response.json
cat > .env << EOF
SERVICE_EMAIL=your-email
SERVICE_PASSWORD=your-password
SERVICE_TEST_USER_ID=123
EOF
```

**Step 2: Scaffold** (🟢 AI - 5 min)
```
YOU: "Scaffold module for vendor-service with my email your.email@company.com"
AI: Runs Yeoman, sync-meta, npm install
YOU: Verify with `ls api.yml package.json`
```

**Step 3: API Spec** (🟡 AI DRAFT, YOU VALIDATE - 15 min)
```
YOU: "Create api.yml for getUser operation based on this response: [paste response.json]
     Requirements:
     - operationId: getUser (NOT describe)
     - tag: user (singular)
     - All properties camelCase
     - Only 200 response
     - connectionState must extend baseConnectionState"

AI: Generates api.yml, connectionProfile.yml, connectionState.yml

YOU VALIDATE:
grep -E "describe[A-Z]|'40[0-9]'|_" api.yml  # Must be nothing
grep "baseConnectionState" connectionState.yml  # Must be present
npm run clean && npm run generate  # Must succeed
```

**Step 4: Implementation** (🟢 AI GENERATES, 🟡 YOU CHECK - 20 min)
```
YOU: "Create ServiceClient, UserProducer, and toUser mapper using standard patterns"

AI: Generates code

YOU VERIFY:
grep "Promise<any>" src/*.ts  # Must be nothing
grep -E "async getUser.*apiKey|token|baseUrl" src/*Producer.ts  # Must be nothing
npm run build  # Must succeed
```

**Step 5: Testing** (🟡 AI GENERATES, 🔴 YOU FIX HARDCODING - 20 min)
```
YOU: "Create unit and integration tests for UserProducer.getUser()"

AI: Generates tests

YOU MUST FIX:
# AI will hardcode this:
const userId = '123';  # ❌

# YOU change to:
const userId = SERVICE_TEST_USER_ID;  # ✅
```

**Step 6-8: Finalize** (🟢 AI - 5 min)
```
YOU: "Create USERGUIDE.md, run build, run shrinkwrap, validate all gates"

AI: Executes commands

YOU VERIFY gates passed:
npm run test:unit && npm run test:integration && npm run build
```

---

## 🎯 Best Practices for AI-Assisted Development

### DO: Give AI Context

```
✅ GOOD: "Create toUser mapper for this API response: [paste actual response]
         Required fields: id, email, firstName, createdAt
         Use map() for Email and DateTime
         Use optional() for lastName
         Validate with ensureProperties()"

❌ BAD: "Create user mapper"
```

### DO: Validate AI Output

```
✅ After AI generates code:
   1. Run validation commands yourself
   2. Check critical patterns
   3. Test with real API
   4. Run all gates

❌ Don't trust AI blindly:
   AI says "gates pass" ≠ gates actually pass
   Always run commands yourself!
```

### DO: Iterate with Specific Feedback

```
✅ GOOD: "The api.yml has snake_case properties. Change user_id to userId and first_name to firstName"

❌ BAD: "Fix the api.yml"
```

### DO: Keep AI Focused

```
✅ GOOD: "Create just the toUser mapper"
        "Now create just UserProducer"
        "Now create unit tests"

❌ BAD: "Create the entire module" (AI loses details)
```

---

## ⚠️ Critical: What YOU Must Always Do

**Never delegate these to AI:**

### 1. API Testing with Real Credentials
```bash
# YOU do this, not AI
curl with your actual credentials
Test authentication yourself
Capture real responses
Store in .env
```

### 2. The 12 Rules Validation
```bash
# Run these commands yourself
grep -E "describe[A-Z]" api.yml
grep -E "'40[0-9]'|'50[0-9]'" api.yml
grep "_" api.yml | grep -v "x-" | grep -v "#"
# etc...
```

### 3. Gate Validation
```bash
# Execute yourself, check actual output
npm run test:unit
npm run test:integration
npm run build
# Don't just ask "did tests pass?" - run them!
```

### 4. Hardcoded Data Detection
```bash
# Check yourself
grep -E "const.*Id = ['\"][0-9]" test/integration/*.ts
cat .env  # Verify has test data
```

### 5. Field Completeness in Mappers
```typescript
// Count yourself
// Interface: 8 fields
// Mapper: ? fields
// Must match exactly!
```

---

## 📚 When to Consult Full Documentation

### AI Struggles → Read Beginner Guide

| AI Issue | Beginner Guide Chapter |
|----------|------------------------|
| API spec violations | Chapter 6: Phase 3 - API Specification Design |
| Mapper missing fields | Chapter 7.5: Implement Mappers |
| Test data hardcoded | Chapter 8.3: Integration Tests |
| Connection context in producer | Chapter 7.6: Implement Producers |
| expiresIn confusion | Chapter 7.3.2: OAuth Authentication |
| InlineResponse types | Chapter 6.3.2: Design First Operation Path |

**When in doubt:**
```
🔴 AI giving errors repeatedly?
   → Read beginner guide chapter
   → Understand the pattern
   → Then ask AI to implement specific pattern
```

---

## 🎓 Learning Strategy with AI

### Week 1: Learn Fundamentals (Without AI)
```
Day 1-2: Read beginner guide Chapters 1-8
Day 3: Complete Exercise 1 manually (no AI)
Day 4-5: Build first module manually
```

**Why**: You need to understand WHY before using AI

### Week 2: Hybrid Approach
```
Day 1: Use AI for scaffolding, you validate
Day 2: AI drafts spec, you run 12-rule validation
Day 3: AI generates code, you check patterns
Day 4-5: Build module with AI, catching its mistakes
```

**Why**: Learn where AI helps and where it fails

### Week 3+: Efficient AI Usage
```
- AI: Boilerplate, repetitive code
- YOU: Validation, critical decisions, testing
- Speed: 45-70 min per operation (vs 2+ hours manual)
```

---

## 🚫 AI Anti-Patterns (What Not to Do)

### ❌ Blind Trust
```
AI: "All gates pass!"
YOU: "Great!" ← WRONG! Run commands yourself!

✅ CORRECT:
AI: "All gates pass!"
YOU: Run `npm test`, `npm run build` yourself
```

### ❌ Vague Prompts
```
"Create the module" ← AI will miss details

✅ CORRECT:
"Create api.yml for getUser operation:
 - Use actual response from response.json
 - operationId must be getUser (not describe)
 - Only 200 response
 - Properties in camelCase"
```

### ❌ Not Validating Critical Parts
```
AI: "Created connectionState.yml"
YOU: "Thanks!" ← WRONG! Check baseConnectionState!

✅ CORRECT:
AI: "Created connectionState.yml"
YOU: grep "baseConnectionState" connectionState.yml
```

---

## 📊 AI Effectiveness by Phase

| Phase | AI Effectiveness | Your Role | Time Saved |
|-------|------------------|-----------|------------|
| 1: Research | 🔴 Low | Manual testing | 0% (must do manual) |
| 2: Scaffold | 🟢 High | Verify completion | 80% |
| 3: API Spec | 🟡 Medium | Validate 12 rules | 40% (AI drafts, you validate) |
| 4: Implementation | 🟢 High | Check patterns | 60% |
| 5: Testing | 🟡 Medium | Fix hardcoding | 50% |
| 6: Documentation | 🟢 High | Quick review | 70% |
| 7: Build | 🟢 High | Verify success | 80% |
| 8: Validation | 🔴 Low | Run commands | 0% (you must validate) |

**Overall time savings with AI**: ~40-50% (but only if you validate correctly!)

---

## 🎯 The Hybrid Success Formula

```
AI Speed + Your Validation = Quality + Efficiency

AI generates (fast)
    ↓
YOU validate (critical)
    ↓
Iterate until correct
    ↓
Production-ready module (fast + correct)
```

---

## ✅ Hybrid Development Checklist

**Using AI effectively:**

- [ ] ✅ YOU test API with curl first
- [ ] ✅ YOU create .env with credentials
- [ ] ✅ AI scaffolds module
- [ ] ✅ YOU verify structure
- [ ] ✅ AI drafts api.yml
- [ ] ✅ YOU validate 12 rules manually
- [ ] ✅ YOU run npm run generate yourself
- [ ] ✅ AI generates Client/Producer/Mapper
- [ ] ✅ YOU check no Promise<any>
- [ ] ✅ YOU check field counts in mappers
- [ ] ✅ AI generates tests
- [ ] ✅ YOU fix hardcoded test data
- [ ] ✅ YOU verify debug logging
- [ ] ✅ YOU run all gates yourself
- [ ] ✅ YOU verify actual output (not AI's claim)

---

## 🚀 Quick Start (Hybrid Mode)

### Your First Module with AI

**Phase 1: Manual (10 min)**
```bash
curl -X POST "..." > auth.json
curl -H "Authorization: ..." "..." > response.json
cat > .env << EOF
SERVICE_EMAIL=...
SERVICE_PASSWORD=...
SERVICE_TEST_USER_ID=123
EOF
```

**Phase 2-4: AI with Validation (30 min)**
```
Prompt: "Create module for vendor-service based on this API response..."
Validate: Run 12-rule checks, verify patterns
Fix: Tell AI specific violations
```

**Phase 5: AI with Manual Fixes (20 min)**
```
Prompt: "Create tests..."
Fix: Remove hardcoded IDs, add to .env
Validate: Run tests yourself
```

**Phase 6-8: AI Execution (10 min)**
```
Prompt: "Build and validate..."
Verify: Check actual exit codes
```

**Total time**: ~70 minutes (vs ~150 minutes fully manual)

---

## 📖 Reference Documents

**When AI struggles, consult:**

- **Beginner Complete Guide**: Deep explanations
  - Location: `training/beginner-complete/MODULE_DEVELOPMENT_TRAINING.md`
  - Use for: Understanding WHY patterns exist

- **Validation Checklists**: Quality assurance
  - Location: `training/beginner-complete/MODULE_DEVELOPMENT_CHECKLISTS.md`
  - Use for: Gate validation, pre-commit checks

- **Troubleshooting Guide**: Problem solving
  - Location: `training/beginner-complete/MODULE_DEVELOPMENT_TROUBLESHOOTING.md`
  - Use for: When AI keeps failing on something

---

## 💡 Pro Tips for AI-Assisted Development

1. **"curl first, AI second"** - Always test API manually before asking AI to implement

2. **"Validate, don't assume"** - AI says it's done ≠ it's actually correct

3. **"Specific prompts"** - Tell AI exact requirements, don't be vague

4. **"Incremental generation"** - One component at a time, not entire module

5. **"You own validation"** - Run commands yourself, check actual output

6. **"Fix don't regenerate"** - Tell AI specific fixes instead of "regenerate everything"

7. **"Learn patterns first"** - Do 1-2 modules manually before using AI

8. **"Trust but verify"** - AI is helpful but not infallible

---

## 🎓 Skill Progression (Hybrid Mode)

**Week 1**: Manual (no AI)
- Learn fundamentals
- Understand patterns
- Build 1 module manually

**Week 2**: Hybrid (AI + heavy validation)
- AI generates, you validate everything
- Catch AI mistakes
- Build 2-3 modules

**Week 3+**: Efficient hybrid
- AI for boilerplate
- You for critical parts
- 45-70 min per operation

**Result**: Fast + correct development! 🚀

---

## ⚠️ Warning: AI Limitations

**AI cannot (as of 2025):**
- Test real APIs with your credentials
- Execute commands in your environment
- See actual validation output
- Know your specific API's quirks
- Remember all 12 rules under pressure
- Guarantee zero missing mapper fields

**YOU must:**
- Provide real API responses
- Run validation commands
- Check actual output
- Verify completeness
- Enforce quality standards

---

## 🎯 Success with Hybrid Approach

**Best results when:**
- ✅ You understand fundamentals (manual training first)
- ✅ You validate AI output rigorously
- ✅ You run commands yourself
- ✅ You catch AI mistakes quickly
- ✅ You iterate with specific feedback

**Poor results when:**
- ❌ Blindly trusting AI
- ❌ Skipping validation steps
- ❌ Not understanding patterns
- ❌ Letting AI control everything

---

## 📞 When to Read Full Documentation

**Read beginner guide when:**
- Starting from scratch (no module experience)
- AI keeps making same mistakes
- Need to understand WHY patterns exist
- Debugging complex issues
- Want deep expertise

**Stay with hybrid guide when:**
- Have module experience
- Understand core concepts
- Just need speed boost
- Know how to validate

---

**Remember**: AI is your assistant, not your replacement. You're the engineer - AI is the tool! 🛠️

**For complete training**: See `training/beginner-complete/`

**For this hybrid approach**: Keep this guide open while developing!
