# Task 04: Scaffold Module with Yeoman Generator

## Overview

This task creates the initial module structure using the @auditmation/hub-module Yeoman generator. It sets up the complete directory structure, generates configuration files, and prepares the module for development.

## Input Requirements

- Task 03 output file: `.claude/.localmemory/{action}-{module-identifier}/task-03-output.json`
- Task 02 output file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json`
- All prerequisites must be verified as passing from Task 03

## Process Steps

### 1. Extract Module Components

Extract vendor, suite, and service components from the module package name:

1. **Parse module package name**:
   - Format: `@zerobias-org/module-{vendor}-{suite}-{service}` or `@zerobias-org/module-{vendor}-{service}`
   - Extract components using pattern matching
   - Store components for directory structure creation

### 2. Navigate to Working Directory

1. **Set working directory**:
   - Default: `/Users/ctamas/code/module` (or use provided working_directory)
   - Verify directory exists and is accessible
   - Create directory if it doesn't exist

### 3. Run Yeoman Generator

Execute the @auditmation/hub-module generator with all required parameters:

1. **Generator command**:
   ```bash
   yo @auditmation/hub-module \
     --productPackage '${product_package}' \
     --modulePackage '${module_package}' \
     --packageVersion '${package_version}' \
     --description '${description}' \
     --repository '${repository}' \
     --author '${author}'
   ```
   
   **Note**: The generator package is `@auditmation/generator-hub-module` but the command uses `@auditmation/hub-module`

2. **Parameters from Task 02 output**:
   - `product_package`: From Task 02 output
   - `module_package`: From Task 02 output  
   - `package_version`: "0.0.0" (initial version)
   - `description`: Service name from Task 02 (use service name like 'Ec2', 'Jira' rather than longer descriptive text)
   - `repository`: "{repository_url}"
   - `author`: "team@zerobias.org"

### 4. Verify Generated Structure

Check that all expected files and directories were created:

1. **Required directories**:
   - `package/{vendor}/{suite}/{service}/` or `package/{vendor}/{service}/`

2. **Required files**:
   - `package.json` - Module metadata and scripts
   - `api.yml` - OpenAPI specification template
   - `src/index.ts` - Main module entry point
   - `src/{ServiceName}Impl.ts` - Main implementation class
   - `test/unit/{ServiceName}Impl.ts` - Unit test file

### 5. Sync Metadata to OpenAPI Spec

Ensure OpenAPI specification has correct title and version:

1. **Run sync command**:
   ```bash
   cd package/{vendor}/{suite}/{service}
   npm run sync-meta
   ```

2. **Verify OpenAPI spec**:
   - Check that `api.yml` contains `title` and `version` fields
   - Verify x-product-infos reference is present

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-04-output.json`

```json
{
  "scaffolding": {
    "status": "completed|failed",
    "timestamp": "${iso_timestamp}",
    "modulePath": "package/${vendor}/${suite}/${service}",
    "components": {
      "vendor": "${vendor}",
      "suite": "${suite}",
      "service": "${service}"
    },
    "generatedFiles": {
      "packageJson": {
        "path": "package/{vendor}/{suite}/{service}/package.json",
        "status": "created|failed",
        "validation": "moduleId_present|missing"
      },
      "apiSpec": {
        "path": "package/{vendor}/{suite}/{service}/api.yml",
        "status": "created|failed",
        "validation": "product_reference_present|missing"
      },
      "sourceFiles": {
        "mainEntry": "package/{vendor}/{suite}/{service}/src/index.ts",
        "implementation": "package/{vendor}/{suite}/{service}/src/{ServiceName}Impl.ts",
        "status": "created|failed"
      },
      "testFiles": {
        "unitTests": "package/{vendor}/{suite}/{service}/test/unit/{ServiceName}Impl.ts",
        "status": "created|failed"
      }
    },
    "metadataSync": {
      "status": "completed|failed",
      "apiSpecValidation": "title_version_present|missing"
    },
    "validations": [
      {
        "check": "Module directory structure exists",
        "command": "ls -la package/${vendor}/${suite}/${service}",
        "expected": "package.json.*api.yml.*src",
        "status": "passed|failed"
      },
      {
        "check": "package.json has correct metadata",
        "command": "cat package/${vendor}/${suite}/${service}/package.json | jq '.name, .moduleId, .author'",
        "expected": "${module_package}",
        "status": "passed|failed"
      },
      {
        "check": "api.yml has product reference",
        "command": "grep -q 'x-product-infos' package/${vendor}/${suite}/${service}/api.yml",
        "expected_exit_code": 0,
        "status": "passed|failed"
      },
      {
        "check": "api.yml has title and version fields",
        "command": "grep -E 'title:|version:' package/${vendor}/${suite}/${service}/api.yml | wc -l",
        "expected": "2",
        "status": "passed|failed"
      }
    ]
  }
}
```

## Error Conditions

- **Task 03 prerequisites failed**: Stop execution, prerequisites must pass first
- **Task 02 output not found**: Stop execution, Task 02 must be completed first
- **Yeoman generator not found**: Stop execution, install @auditmation/hub-module globally
- **Generator execution failed**: Stop execution, check generator installation and parameters
- **Required files not generated**: Stop execution, verify generator completed successfully
- **Directory already exists**: Stop execution, choose different module name or remove existing directory

## Success Criteria

- Module directory structure created successfully
- All required files generated with correct content:
  - `package.json` contains moduleId and correct metadata
  - `api.yml` contains x-product-infos reference and title/version fields
  - Source files exist with proper exports
  - Test files exist with basic test structure
- Configuration links created successfully
- Metadata sync completed successfully
- All validation checks pass

## Failure Handling

If scaffolding fails:
1. Record all failure details in the output file
2. Set overall status to "failed"
3. Clean up any partially created files:
   ```bash
   rm -rf package/${vendor}/${suite}/${service}
   ```
4. Provide clear error messages and remediation steps
5. Stop task execution and wait for user to resolve issues

## Common Error Solutions

- **yo: command not found**: Install Yeoman globally: `npm install -g yo`
- **Generator @auditmation/hub-module not found**: Install generator: `npm install -g @auditmation/hub-module`
- **Permission denied**: Check write permissions on target directory
- **Directory already exists**: Choose different module name or remove existing directory
