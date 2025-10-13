# Build and Quality Rules

## 🚨 CRITICAL RULES (Immediate Failure)

### 1. Build Must Pass
- `npm run build` MUST exit with code 0
- Build failures STOP all work immediately
- Fix ALL compilation errors before proceeding
- No implementation with broken build

### 2. Lint Compliance
- `npm run lint` MUST pass without errors
- Lint failures require immediate fix
- No proceeding with lint errors

### 3. Clean Git State for Updates
- NO uncommitted changes before starting updates
- Git status must be clean
- User must commit or stash before proceeding

## 🟡 STANDARD RULES

### Mandatory Build Gate Checkpoints
Build MUST pass with exit code 0 after EACH of these steps:

1. **Pre-Implementation Validation** (MANDATORY)
   - Add `"skipLibCheck": true` to tsconfig.json
   - Run `npm run build` - MUST exit with code 0
   - Fix ALL compilation errors before proceeding

2. **Post-Dependency Install**
   - After `npm install` for new dependencies
   - Verify build still passes

3. **Post-HTTP Client Creation**
   - After creating `src/{ServiceName}Client.ts`
   - Build must pass before continuing

4. **Post-Mappers Creation**
   - After creating `src/Mappers.ts`
   - Build must pass before continuing

5. **Post-Producer Implementation** (Each)
   - After EACH producer implementation
   - Build must pass after every producer

6. **Post-Connector Implementation**
   - After main connector class
   - Build must pass before continuing

7. **Post-Entry Point Update**
   - After updating `src/index.ts`
   - Build must pass before continuing

8. **FINAL BUILD GATE** (Task Completion)
   - **ABSOLUTE REQUIREMENT**: `npm run build` exits with code 0
   - Task CANNOT be completed if build fails
   - Zero TypeScript warnings allowed
   - Zero compilation errors allowed

**CRITICAL**: If `npm run build` fails at ANY checkpoint, STOP all work immediately.

### TypeScript Configuration
```json
{
  "compilerOptions": {
    "skipLibCheck": true,  // MANDATORY - Add before implementation
    "strict": true,         // Maintain strict checking
    "noImplicitAny": true,
    "strictNullChecks": true
  }
}
```

### Package.json Scripts
Required scripts that must exist and work:
- `build`: Compile TypeScript
- `clean`: Remove build artifacts
- `lint`: Check code style
- `test`: Run unit tests
- `test:integration`: Run integration tests

### Dependency Installation
- Install exact versions when specified
- Run `npm install` after adding dependencies
- Verify no peer dependency warnings
- Check for security vulnerabilities

### Code Generation Rules
- NEVER modify files in `generated/` directory
- Generated files are read-only
- Failed generation triggers API spec fix
- Regenerate after any API spec changes

## 🟢 GUIDELINES

### Performance Considerations
- Avoid synchronous file operations
- Use async/await properly
- Implement appropriate timeouts
- Handle large datasets efficiently

### Memory Management
- Clean up resources in disconnect methods
- Avoid memory leaks in long-running operations
- Use streaming for large responses when possible

### Error Recovery
- Implement proper cleanup on errors
- Don't leave partial state
- Log errors appropriately
- Provide meaningful error messages

### Code Quality Metrics
- Maintain consistent code style
- Follow existing patterns
- Keep functions focused and small
- Avoid deep nesting

### Validation Sequence
```bash
# Standard validation sequence
npm run clean
npm run build
npm run lint
npm run test
npm run test:integration  # If credentials available
```

## 📝 EXCEPTIONS LOG

### When Build Can Fail Temporarily
- During iterative development (but must fix before commit)
- When updating dependencies (fix immediately after)
- Never leave build broken between tasks

### Lint Exception Handling
- Use inline disable comments sparingly
- Document reason for disabling:
```typescript
// eslint-disable-next-line @typescript-eslint/no-explicit-any
// Reason: External API returns untyped response
```