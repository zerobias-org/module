# Task 01: Product Discovery and Setup

## Overview

This task discovers the target product, sets up the working environment, and extracts basic product information. Task 02 will handle the comprehensive API analysis and operation mapping.

## Input Requirements

- User-provided module identifier: `{vendor}-{module}` or `{vendor}-{suite}-{module}`
- **Complete initial user prompt** - Store the full user request for context preservation across tasks
- User prompt will be analyzed for resource intent in Task 02 to determine actual operations needed

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
- `productPackage`: Discovered product package name (e.g., "@auditlogic/product-github-github")
- `modulePackage`: Generated module package name (always `@zerobias-org/module-{module-identifier}` format)
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

