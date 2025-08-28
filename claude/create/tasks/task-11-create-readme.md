# Task 10: Create Module README Documentation

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP - CONTEXT CLEARING**: 
- **IGNORE all previous conversation context** - This task runs in isolation
- **CLEAR mental context** - Treat this as a fresh start with no prior assumptions
- **REQUEST**: User should run `/clear` or `/compact` command before starting this task for optimal performance

**🚨 MANDATORY SECOND STEP**: Read and understand the original user intent:

1. **Read initial user prompt**:
   - Load `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json`
   - Extract and review the `initialUserPrompt` field
   - Understand the original goal, scope, and specific user requirements

2. **Goal alignment verification**:
   - Ensure all README decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

3. **Context preservation**:
   - Reference the original prompt when determining README focus and examples
   - Ensure the README addresses the user's actual needs and use cases

## Task Overview

Create a concise, focused README.md file that provides essential information without overwhelming detail. Focus on practical information users actually need: installation, quick start, basic usage, and key links.

## 🚨 Critical Rules

- **KEEP IT SHORT** - Maximum ~150 lines, focus on essentials only
- **NO VERBOSE SECTIONS** - Remove detailed API reference, extensive examples, development setup
- **ESSENTIAL ONLY** - Include: description, installation, quick start, basic usage, documentation links, license
- **SINGLE EXAMPLE** - One working code example in Quick Start, no more
- **LINK TO DETAILS** - Reference USER_GUIDE.md for detailed instructions
- **NO VERSIONING** - Avoid version numbers (publish workflow handles this)
- **NO SUPPORT/RELATED** - Remove support and related projects sections
- **PUBLISHING AWARE** - Note that API operations/models are auto-appended during publish
- **GENERIC EXAMPLES** - Never use "Zborg", "GitHub", or other specific product names in examples - use generic terms and placeholders like "{service_name}", "testuser"

## Input Requirements

### Essential
- Task 01 output: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json` (API discovery)
- Task 02 output: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (operation mapping)
- Task 04 output: `.claude/.localmemory/{action}-{module-identifier}/task-04-output.json` (module structure)
- Task 06 output: `.claude/.localmemory/{action}-{module-identifier}/task-06-output.json` (implementation details)
- Task 08 output: `.claude/.localmemory/{action}-{module-identifier}/task-08-output.json` (testing information)
- Where `{module-identifier}` is the product identifier derived from the identified product package (e.g., `vendor-suite-service` from `@scope/product-vendor-suite-service`, or `vendor-service` from `@scope/product-vendor-service`)
- `module_path` - Path to the module directory
- `vendor_name` - Service vendor name
- `service_name` - Service name in proper case
- `module_version` - Module version from package.json

### Optional
- `api_documentation_url` - Link to official API documentation
- `license_type` - License type (default: MIT)
- `repository_url` - Git repository URL
- `author_info` - Author/maintainer information

## Expected Outputs

### Files Created
- `README.md` - Comprehensive module documentation

### Memory Output
- `.claude/.localmemory/{action}-{module-identifier}/task-10-output.json` - Task completion status

## Implementation

### Step 1: Analyze Module Structure and Capabilities

**Load Task Outputs:**
- Read Task 02 to understand available operations and producers
- Read Task 06 to understand implementation architecture
- Read Task 08 to understand testing structure and capabilities
- Examine package.json for dependencies and scripts

**Capability Analysis:**
- List all producers and their operations
- Identify supported authentication methods
- Document error handling capabilities
- Note any special features or limitations

### Step 2: Create README Structure

**Concise README Sections:**
```markdown
# {Module Name}

[Brief description - 1-2 sentences]

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.

## Installation

[npm install command and basic requirements]

## Quick Start

[Single working example - 15-20 lines max]

See [User Guide](USER_GUIDE.md) for detailed setup instructions.

## Usage

[Basic connection and operation examples - minimal detail]

---

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
```

### Step 3: Write Project Header and Description

**Project Header:**
- Module name (no version number - publish workflow handles versioning)
- 1-2 sentence description of what the module does
- No badges or decorative elements

**Example Header:**
```markdown
# @zerobias-org/module-{vendor}{-suite?}-{product}

A TypeScript client library for integrating with the {Service Name} API. Provides type-safe operations for {brief description of main capabilities}.

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.
```

### Step 4: Write Installation Instructions

**Installation Section:**
- npm install command only
- Basic requirements

**Example:**
```markdown
## Installation

```bash
npm install @zerobias-org/module-{vendor}{-suite?}-{product}
```

Requires Node.js 18+ and a {Service Name} account with API access.
```

### Step 5: Create Quick Start Guide

**Quick Start Section:**
- Single working example (15-20 lines max)
- Basic connection and one operation
- Reference to user guide

**Example:**
```markdown
## Quick Start

```typescript
import { new{ServiceName} } from '@zerobias-org/module-{vendor}{-suite?}-{product}';

const client = new{ServiceName}();

await client.connect({
  apiToken: process.env.{SERVICE}_API_TOKEN
});

const result = await client.get{ExampleResource}Api().{simpleOperation}();
console.log(result);

await client.disconnect();
```

See [User Guide](USER_GUIDE.md) for detailed setup instructions.
```

### Step 6: Create Basic Usage Section

**Usage Section:**
- Brief connection example
- List available producers/APIs
- Keep concise without additional references

**Example:**
```markdown
## Usage

```typescript
const client = new{ServiceName}();
await client.connect({ apiToken: 'your-token' });

// Available APIs
const {exampleApi} = client.get{ExampleResource}Api();
await {exampleApi}.{operation}();
```
```

### Step 7: Final Review

**Final Structure Check:**
- Ensure all sections are concise and focused
- Verify User Guide links are properly placed
- Confirm publishing workflow note is included

Note: No separate Documentation section needed - User Guide links are embedded throughout.

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-10-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "${iso_timestamp}",
  "readme": {
    "created": true,
    "sectionsIncluded": ["${list_of_sections}"],
    "codeExamplesCount": "${code_examples_count}",
    "userGuideLinksCount": "${user_guide_links_count}"
  },
  "files": {
    "readme": "${module_path}/README.md"
  }
}
```

## Success Criteria

- [ ] README.md created with concise, focused documentation (~150 lines max)
- [ ] Clear project description (1-2 sentences)
- [ ] Simple installation instructions
- [ ] Single working Quick Start example
- [ ] Basic usage section with API overview
- [ ] Early link to User Guide for credential setup 
- [ ] Links to User Guide for detailed instructions
- [ ] Option 2 AI warning with proper formatting
- [ ] Note about auto-generated API operations/models during publish
- [ ] No license, version numbers, support sections, or related projects
- [ ] No excessive detail, badges, or verbose sections
- [ ] Code example validated and working

## Documentation Quality Standards

The README should serve as:
- **Quick overview** for developers evaluating the module
- **Fast start** with minimal working example
- **Navigation hub** linking to detailed documentation
- **Professional but concise** presentation

---

**Note**: Keep README under 150 lines. Detailed information belongs in USER_GUIDE.md. The publish workflow will automatically append API operations and data models from the OpenAPI specification.