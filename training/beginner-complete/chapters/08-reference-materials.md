## Chapter 15: Quick Reference Guide

### 15.1 Common Commands

```bash
# Development Cycle
npm run clean && npm run generate  # After api.yml changes
npm run build                       # Compile TypeScript
npm run lint                        # Check code style
npm run test:unit                   # Run unit tests
npm run test:integration            # Run integration tests
npm run shrinkwrap                  # Lock dependencies
```

### 15.2 Pattern Quick Reference

**Client:**
```typescript
class ServiceClient {
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {}
  async isConnected(): Promise<boolean> {}
  async disconnect(): Promise<void> {}
  get apiClient(): AxiosInstance {}
}
```

**Producer:**
```typescript
class ResourceProducer {
  constructor(private client: ServiceClient) {}
  async listResources(results: PagedResults<Resource>): Promise<void> {}
  async getResource(id: string): Promise<Resource> {}
}
```

**Mapper:**
```typescript
export function toResource(raw: any): Resource {
  ensureProperties(raw, ['id', 'name']);
  const output: Resource = {
    id: raw.id.toString(),
    name: raw.name,
    email: map(Email, raw.email),
    createdAt: map(DateTime, raw.created_at)
  };
  return output;
}
```

---

## Chapter 16: Validation Checklists

### 16.1 The 12 Critical API Rules

- [ ] **Rule 1**: No servers/security at root
- [ ] **Rule 2**: Singular nouns for tags/schemas
- [ ] **Rule 3**: All operations included
- [ ] **Rule 4**: Reusable parameters in components
- [ ] **Rule 5**: camelCase properties only
- [ ] **Rule 6**: orderBy/orderDir for sorting
- [ ] **Rule 7**: Descriptive path parameters
- [ ] **Rule 8**: id > name for identifiers
- [ ] **Rule 9**: Singular lowercase tags
- [ ] **Rule 10**: Correct operationId verbs
- [ ] **Rule 11**: pageTokenParam for pagination
- [ ] **Rule 12**: Only 200/201 responses

### 16.2 Pre-Commit Checklist

- [ ] All 6 quality gates pass
- [ ] All tests pass
- [ ] Build successful
- [ ] No ESLint errors
- [ ] Dependencies locked
- [ ] No hardcoded credentials
- [ ] .env file NOT committed

---

## Chapter 17: Code Templates

### 17.1 Complete File Templates

See the training manual for complete templates for:
- Client (ServiceClient.ts)
- Producer (ResourceProducer.ts)
- Mapper (Mappers.ts)
- Utilities (util.ts)
- Unit Test Common (test/unit/Common.ts)
- Integration Test Common (test/integration/Common.ts)

All templates include:
- Proper error handling
- Type safety
- Validation
- Best practices

---

## Conclusion

**Congratulations!** You now have complete knowledge of module development.

**What you've learned:**
- ✅ 8-phase module creation process
- ✅ All 6 quality gates
- ✅ API specification design (12 rules)
- ✅ Implementation patterns
- ✅ Testing strategies
- ✅ Troubleshooting techniques

**Your next steps:**
1. Create your first module from scratch
2. Add operations to existing modules
3. Build 2-3 modules for proficiency

**Remember:**
- API Spec is source of truth
- Client = Connection ONLY
- Producers = Operations ONLY
- Mappers = Transform ONLY
- All 6 gates must pass

**Good luck with your module development!** 🚀

---

**END OF TRAINING MANUAL**
