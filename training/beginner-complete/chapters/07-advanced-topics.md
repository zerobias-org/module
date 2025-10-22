## Chapter 12: Adding Operations to Existing Modules

### 12.1 The Add Operation Workflow

Adding operations to existing modules follows a condensed 6-phase process (skip scaffolding):

**Phase 1: Research the Operation**
- Review API documentation
- Test with curl
- Document request/response

**Phase 2: Update API Specification**
- Add to api.yml
- Gate 1: Validate spec

**Phase 3: Generate Types**
- Run npm run clean && npm run generate
- Gate 2: Validate types

**Phase 4: Implement Operation**
- Add to Producer
- Update Mappers if needed
- Gate 3: Validate implementation

**Phase 5: Write Tests**
- Unit tests (mocked)
- Integration tests (real API)
- Gates 4 & 5: Validate tests

**Phase 6: Build & Finalize**
- Build and shrinkwrap
- Gate 6: Validate build

### 12.2 Add Operation Checklist

- [ ] ✅ Operation researched and tested with curl
- [ ] ✅ API specification updated (api.yml)
- [ ] ✅ Types regenerated
- [ ] ✅ Producer method implemented
- [ ] ✅ Mapper updated (if needed)
- [ ] ✅ Unit tests written (3+ cases)
- [ ] ✅ Integration tests written (2+ cases)
- [ ] ✅ Test data added to .env
- [ ] ✅ All 6 gates passed

---

## Chapter 13: Troubleshooting & Debugging

### 13.1 Common Build Failures

**TypeScript Compilation Errors:**
```bash
# Regenerate types
npm run clean && npm run generate
npm run build
```

**Module Not Found:**
```bash
# Install missing dependency
npm install <missing-package>
npm run build
```

**InlineResponse Types:**
```yaml
# Move inline schemas to components/schemas in api.yml
# Then regenerate
npm run clean && npm run generate
```

### 13.2 Common Test Failures

**Nock Not Matching:**
- Check path matches exactly
- Verify HTTP method matches
- Check headers if needed

**Integration Tests Skipping:**
- Verify .env file exists
- Check hasCredentials() returns true
- Ensure credentials are valid

**Invalid Credentials:**
- Test credentials with curl
- Regenerate API key if needed
- Update .env file

### 13.3 Debugging Techniques

1. Add debug logging to code
2. Run tests with LOG_LEVEL=debug
3. Inspect generated types
4. Test mapper in isolation
5. Compare API response to spec

---

## Chapter 14: Best Practices & Common Pitfalls

### 14.1 Best Practices

**API Specification:**
- Follow all 12 critical rules
- Use camelCase for properties
- Only 200/201 responses

**Implementation:**
- Separate concerns (Client/Producer/Mapper)
- Use generated types (no Promise<any>)
- Validate required fields in mappers

**Testing:**
- Write both unit and integration tests
- Test error cases
- Store test data in .env
- Use debug logging in integration tests

**Error Handling:**
- Use core error types
- Map HTTP status codes correctly

### 14.2 Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Using snake_case | Use camelCase for all properties |
| Including error responses | Remove all 4xx/5xx from spec |
| Using Promise<any> | Use generated types |
| Skipping type generation | Always run npm run clean && npm run generate |
| Not writing tests | Write both unit and integration |
| Hardcoding test values | Store in .env |
| Forgetting shrinkwrap | Always run npm run shrinkwrap |

---

# Part 4: Reference Materials

