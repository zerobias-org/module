# Task 01: Product Discovery and Setup

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task discovers the target product, sets up the working environment, and extracts basic product information. Task 02 will handle the comprehensive API analysis and operation mapping.

## Input Requirements

- User request specifying the target product/service for module creation
- **Complete initial user prompt** - Store the full user request for context preservation across tasks
- User prompt will be analyzed for resource intent in Task 02 to determine actual operations needed

## Module Identifier Definition

**Module Identifier Derivation**: When a product package is identified (e.g., `@scope/product-xyz`), the **module identifier** is `xyz` (the part after `product-`).

**Examples**:
- Product: `@scope/product-vendor-suite-service` → Module Identifier: `vendor-suite-service`
- Product: `@scope/product-vendor-service` → Module Identifier: `vendor-service`  
- Product: `@scope/product-company-platform` → Module Identifier: `company-platform`

**Memory Folder Structure**: `.claude/.localmemory/{action}-{module-identifier}/`
- Examples: 
  - `.claude/.localmemory/create-vendor-suite-service/`
  - `.claude/.localmemory/create-vendor-service/`

## Process Steps

### 1. Product Discovery

1. **List product bundle dependencies**:
   - Run `npm view @zerobias-org/product-bundle --json` to get dependencies (not nested) of `@zerobias-org/product-bundle`
   - Run `npm view @auditlogic/product-bundle --json` to get dependencies (not nested) of `@auditlogic/product-bundle`

2. **Identify matching product**:
   - Products follow format: `@scope/product-{vendor}{-suite?}-{product}`
   - Examples: `@auditlogic/product-amazon-aws-iam`, `@auditlogic/product-github-github`
   - Match case-insensitively by product component mainly
   - If multiple matches exist (e.g., `gcp-iam` and `aws-iam` for "iam" request): **STOP** and ask user to clarify which product to use
   - If product not found: **STOP** and wait for user action
   - Store product package name for next steps

### 2. Environment Setup

1. **Create memory folder**:
   - Create directory structure: `.claude/.localmemory/{action}-{module-identifier}/_work/`
   - Where `{module-identifier}` is derived from the identified product package (see Module Identifier Definition above)

2. **Install product package temporarily**:
   - Navigate to work directory
   - Run `npm init -y && npm install {product-package}`

3. **Extract basic product information**:
   - Read `node_modules/{product-package}/index.yml` (priority 1)
   - Use `yq` to extract the "Product" property from `node_modules/{product-package}/catalog.yml` (priority 2, if index.yml not available)
   - Extract basic information: product name, description, base URL
   - Store basic information for Task 02

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json`

```json
{
  "status": "completed|failed|error",
  "productPackage": "${product_package}",
  "modulePackage": "${module_package}",
  "serviceName": "${service_name}",
  "description": "${service_description}",
  "baseUrl": "${base_url}",
  "mainDocumentationUrl": "${main_documentation_url}",
  "initialUserPrompt": "${complete_user_request}"
}
```

Where:
- `productPackage`: Discovered product package name (e.g., "@auditlogic/product-vendor-service")
- `modulePackage`: Generated module package name (replace product package scope with `@zerobias-org` and change `product-` prefix to `module-`, e.g., `@auditlogic/product-xyz` becomes `@zerobias-org/module-xyz`)
- `serviceName`: Human-readable service name extracted from product files
- `description`: Service description from product files
- `baseUrl`: Primary service URL extracted from product files
- `mainDocumentationUrl`: Primary documentation URL for the service
- `initialUserPrompt`: Complete original user request for context preservation (Task 02 will analyze this to determine actual operations)

## Error Conditions

- **Product not found**: Stop execution, report missing product to user
- **Multiple product matches**: Stop execution, ask user to clarify which product to use (e.g., `gcp-iam` vs `aws-iam`)
- **Product package installation fails**: Stop execution, report installation issue

## Success Criteria

- Product successfully identified and matched
- Working environment set up with temp directory and product package installed
- Basic product information extracted and stored for Task 02

