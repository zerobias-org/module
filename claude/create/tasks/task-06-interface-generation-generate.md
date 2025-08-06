# Task 06: Generate TypeScript Interfaces

## Overview

This task generates TypeScript interfaces from the validated OpenAPI specification using hub-generator. It creates the complete type system, connector interfaces, and producer APIs that will be used by the module implementation.

## Input Requirements

- Task 05 output file: `.claude/.localmemory/{action}-{module-identifier}/task-05-output.json`
- Validated `api.yml` file and `connectionProfile.yml` (if required)

## Process Steps

### 1. Clean Previous Generation

Clean any existing generated files:

```bash
cd ${module_path}
npm run clean
```

### 2. Generate All Interfaces and Specifications

Execute the complete generation process:

```bash
cd ${module_path}
npm run generate
```

### 3. Validate Generated TypeScript Code

Verify the generated interfaces compile correctly:

```bash
cd ${module_path}
npm run transpile
```

This command will:
- Compile all generated TypeScript interfaces
- Identify any type errors or compilation issues in the generated code
- Validate that the generated code is syntactically correct
- Ensure proper imports and exports

**Note**: Transpilation errors in implementation files (`src/`) should not be considered generation failures. Only focus on errors within the `generated/` directory. Implementation file errors indicate interface name mismatches that need to be fixed in the next task.

### 4. Verify Generation Success

Check that all expected files and interfaces were generated:

```bash
cd ${module_path}
ls -la generated/api/
grep -c 'Connector' generated/api/index.ts
grep -c 'Api' generated/api/index.ts
```

Expected outputs:
- `full.yml` and `module-{vendor}{-suite?}-{product}.yml` - Bundled specifications
- `generated/api/index.ts` - Main TypeScript interfaces with Connector
- `generated/api/manifest.json` - Module metadata
- Individual API files (e.g., `EnterpriseApi.ts`, `OrganizationApi.ts`, `UserApi.ts`)

### 5. Handle Generation Issues

If generation fails or transpilation has errors:

1. **Check for anyOf/oneOf schema issues**: Try interchanging `anyOf` and `oneOf` in `api.yml`
2. **Verify API specification**: Ensure all paths have proper tags and security schemes
3. **Check Java version**: Hub-generator requires Java 11+
4. **Review generation logs**: Look for specific error messages in the hub-generator output

Common fixes:
- Ensure tags use singular nouns (e.g., `user`, `organization`)
- Verify all response schemas are properly defined
- Check that path parameters are correctly specified

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-06-output.json`

```json
{
  "interfaceGeneration": {
    "status": "completed|failed",
    "timestamp": "${iso_timestamp}",
    "modulePath": "${module_path}",
    "files": {
      "bundledSpecification": {
        "path": "${module_path}/module-${vendor}{-suite?}-${product}.yml",
        "status": "created"
      },
      "generatedInterfaces": {
        "path": "${module_path}/generated/api/index.ts",
        "status": "created"
      },
      "manifest": {
        "path": "${module_path}/generated/api/manifest.json",
        "status": "created"
      }
    },
    "validations": [
      {
        "check": "TypeScript transpilation successful",
        "command": "npm run transpile",
        "expected_exit_code": 0,
        "status": "passed|failed"
      },
      {
        "check": "Connector interface generated",
        "command": "grep -c 'Connector' generated/api/index.ts",
        "expected": "^[1-9][0-9]*$",
        "status": "passed|failed"
      },
      {
        "check": "Producer interfaces generated",
        "command": "grep -c 'Api' generated/api/index.ts",
        "expected": "^[1-9][0-9]*$",
        "status": "passed|failed"
      }
    ]
  }
}
```

## Success Criteria

- TypeScript interfaces are generated successfully
- Connector interface is properly generated
- Producer interfaces exist for all API operations  
- Bundled specification and manifest files are created
- Generated code in `generated/` directory has no compilation errors

**Note**: Transpilation errors in `src/` implementation files do not indicate task failure, only interface naming mismatches to be resolved in subsequent tasks.