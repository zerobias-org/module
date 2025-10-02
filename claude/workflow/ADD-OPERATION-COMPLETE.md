# 🚨 COMPLETE ADD-OPERATION WORKFLOW

## CRITICAL: DO NOT STOP UNTIL ALL STEPS COMPLETE

### The Full Sequence (ALL MANDATORY)

```
1. Update API Spec
   ↓ VALIDATE
2. Generate Types
   ↓ VALIDATE
3. Implement Operation
   ↓ VALIDATE
4. Write Tests
   ↓ VALIDATE
5. Run Tests
   ↓ VALIDATE
6. Build Project
   ↓ VALIDATE
7. DONE
```

## 📋 MANDATORY TodoWrite List

When starting ANY add-operation task, IMMEDIATELY create:

```typescript
todos: [
  { content: "Update API specification with new operation", status: "pending" },
  { content: "Run npm generate to create types", status: "pending" },
  { content: "Implement operation in producer class", status: "pending" },
  { content: "Write unit tests for operation", status: "pending" },
  { content: "Write integration tests for operation", status: "pending" },
  { content: "Run tests and ensure they pass", status: "pending" },
  { content: "Run build to validate", status: "pending" },
  { content: "Final validation of all changes", status: "pending" }
]
```

## Step 1: Update API Specification

### Actions:
1. Add operation to `api.yml`
2. Use correct naming (get, not describe)
3. Add descriptions from vendor docs
4. NO error responses (only 200/201)

### VALIDATION GATE 1:
```bash
✓ Operation uses correct prefix (get/list/search/create/update/delete)?
✓ Summary uses "Retrieve" for get/list?
✓ Description added from vendor docs?
✓ No 4xx/5xx responses?
✓ Properties are camelCase?

IF ANY FAIL → STOP AND FIX
```

## Step 2: Generate Types

### Actions:
```bash
npm run generate
```

### VALIDATION GATE 2:
```bash
✓ Generation succeeded?
✓ New types created in generated/?
✓ No TypeScript errors?

IF ANY FAIL → STOP AND FIX
```

## Step 3: Implement Operation

### Actions:
1. Add method to producer class
2. Use generated types (NO any)
3. Implement mappers if needed
4. Handle errors properly

### VALIDATION GATE 3:
```bash
✓ Using generated types (not any)?
✓ Method signature correct?
✓ Mappers implemented?
✓ Error handling in place?

IF ANY FAIL → STOP AND FIX
```

## Step 4: Write Tests

### CRITICAL: TESTS ARE MANDATORY

#### Unit Tests:
```typescript
describe('OperationName', () => {
  it('should call the correct endpoint', async () => {
    // Mock setup
    // Call operation
    // Assert endpoint called correctly
  });

  it('should handle response correctly', async () => {
    // Mock response
    // Call operation
    // Assert correct mapping
  });

  it('should handle errors', async () => {
    // Mock error
    // Call operation
    // Assert error handled
  });
});
```

#### Integration Tests:
```typescript
it('should retrieve actual resource', async () => {
  // Use real client
  // Call operation
  // Assert real response
});
```

### VALIDATION GATE 4:
```bash
✓ Unit tests written?
✓ Integration tests written?
✓ Error cases covered?
✓ Mocks properly set up?

IF ANY FAIL → STOP AND FIX
```

## Step 5: Run Tests

### Actions:
```bash
npm test
# or specific test file
npm test -- test/SomeProducerTest.ts
```

### VALIDATION GATE 5:
```bash
✓ All tests pass?
✓ No regression in existing tests?
✓ Coverage adequate?

IF ANY FAIL → STOP AND FIX
```

## Step 6: Build Project

### Actions:
```bash
npm run build
```

### VALIDATION GATE 6:
```bash
✓ Build succeeds?
✓ No TypeScript errors?
✓ No linting errors?

IF ANY FAIL → STOP AND FIX
```

## Step 7: Final Validation

### Checklist:
- [ ] Operation added to API spec
- [ ] Types generated
- [ ] Operation implemented
- [ ] Unit tests written and passing
- [ ] Integration tests written and passing
- [ ] Build successful
- [ ] No regression

## 🚨 FAILURE TO COMPLETE = TASK FAILURE

If Claude stops before completing ALL steps:
- Task is INCOMPLETE
- Module is BROKEN
- Tests are MISSING
- Build may FAIL

## Example Complete Execution

```bash
# Step 1: Update API
Edit api.yml
✓ Validation Gate 1

# Step 2: Generate
npm run generate
✓ Validation Gate 2

# Step 3: Implement
Edit src/ResourceProducer.ts
✓ Validation Gate 3

# Step 4: Write Tests
Edit test/ResourceProducerTest.ts
Edit test/integration/ResourceIntegrationTest.ts
✓ Validation Gate 4

# Step 5: Run Tests
npm test
✓ Validation Gate 5

# Step 6: Build
npm run build
✓ Validation Gate 6

# Step 7: Final Check
✓ All steps completed
✓ All validations passed
```

## DO NOT STOP UNTIL YOU SEE:

```
✅ API Spec Updated
✅ Types Generated
✅ Operation Implemented
✅ Tests Written
✅ Tests Passing
✅ Build Successful
✅ OPERATION COMPLETE
```

## Remember

**INCOMPLETE OPERATIONS BREAK MODULES**

Every operation MUST have:
- API specification
- Generated types
- Implementation
- Unit tests
- Integration tests
- Passing build

NO EXCEPTIONS!