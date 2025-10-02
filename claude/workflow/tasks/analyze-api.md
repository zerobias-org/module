# Task: Analyze API

## Overview
Performs comprehensive analysis of an external API, creating entity relationship diagrams and identifying operations. This can be used before module creation or independently for API research.

## Responsible Personas
- **Product Specialist**: API documentation research and analysis
- **API Architect**: Entity modeling and relationship mapping

## Prerequisites
- API documentation URL or vendor/product name
- Product package installed (optional, helps with context)

## Input
- Vendor/Product name OR API documentation URL
- Optional: Specific focus areas (e.g., "focus on webhook operations")

## Sub-Tasks

### 1. Identify Product Package (Optional)
**Persona**: Product Specialist
**Purpose**: Get product context if available

1. If vendor/product name provided:
   ```bash
   npm view @zerobias-org/product-bundle --json  # Get dependencies
   npm view @auditlogic/product-bundle --json    # Get dependencies
   ```
2. Match product package (case-insensitive)
3. If found, install temporarily for reference
4. If not found, proceed without (analysis still works)

**Output**: `task-01-output.json`

### 2. API Documentation Discovery
**Persona**: Product Specialist
**Purpose**: Find and access API documentation

1. Determine API documentation location:
   - User provided URL → use directly
   - Product package → check index.yml or catalog.yml for docs URL
   - Vendor name → search for official API docs
2. Access and validate documentation availability
3. Identify documentation type (REST, GraphQL, etc.)
4. Note authentication requirements

**Output**: `task-02-output.json`

### 3. Comprehensive API Analysis
**Persona**: Product Specialist (with AI agent if needed)
**Rules**: [api-specification.md](../../rules/api-specification.md#entity-discovery-checklist)

**Analysis Steps:**
1. **Read API documentation thoroughly**
   - Identify all resource types (entities)
   - Document CRUD operations per resource
   - Note special operations (search, bulk, async)
   - Identify nested resources and relationships

2. **Map Entity Relationships**
   - Primary entities (top-level resources)
   - Child entities (nested resources)
   - Reference relationships (foreign keys)
   - Ownership relationships (parent-child)
   - Many-to-many relationships

3. **Document Operations**
   - List all available endpoints
   - Map to standard operation types (get, list, create, update, delete, search)
   - Identify operation dependencies
   - Note operation complexity (parameters, pagination, etc.)

4. **Identify Data Structures**
   - Request schemas
   - Response schemas
   - Common/shared objects
   - Enums and constants

**Output**: `task-03-output.json`

### 4. Create Entity Relationship Diagram
**Persona**: API Architect
**Rules**: [api-specification.md](../../rules/api-specification.md)

**Diagram Creation:**
1. Create Mermaid entity relationship diagram (ERD)
2. Use standard Mermaid ERD syntax:
   ```mermaid
   erDiagram
       EntityA ||--o{ EntityB : "relationship"
       EntityB }o--|| EntityC : "relationship"

       EntityA {
           string id
           string name
           date createdAt
       }
   ```

3. **Relationship notation:**
   - `||--||` : One to one
   - `||--o{` : One to many
   - `}o--||` : Many to one
   - `}o--o{` : Many to many

4. **Include key attributes:**
   - IDs and primary keys
   - Foreign keys for relationships
   - Important business attributes (name, status, etc.)
   - Timestamps (createdAt, updatedAt)

5. **Organization:**
   - Group related entities
   - Show clear hierarchy
   - Keep diagram readable (split if needed)

**Output**: `api-model.mmd` (Mermaid file)

### 5. Identify Essential Operations
**Persona**: Product Specialist + API Architect
**Rules**: [api-specification.md](../../rules/api-specification.md#operation-discovery-process)

**Operation Prioritization:**
1. **Tier 1 - Essential (Connection validation)**
   - Simple GET operation with no parameters
   - Used to verify connectivity
   - Example: getCurrentUser, getAccount, getOrganization

2. **Tier 2 - Core CRUD**
   - List primary entities
   - Get single entity by ID
   - Create entity
   - Update entity
   - Delete entity

3. **Tier 3 - Advanced**
   - Search operations
   - Bulk operations
   - Nested resource operations
   - Special actions

4. **Document dependencies:**
   - Operations requiring other operations first
   - Required setup operations
   - Authentication/token operations

**Output**: `task-05-output.json`

### 6. Generate Analysis Summary
**Personas**: All
**Purpose**: Create human-readable summary

**Summary Contents:**
1. **API Overview**
   - Vendor/Product name
   - API type (REST, GraphQL, etc.)
   - Base URL pattern
   - Authentication methods

2. **Entity Summary**
   - Total entities identified
   - Primary entities (top-level)
   - Nested entities
   - Key relationships

3. **Operation Summary**
   - Total operations available
   - Operations by type (GET, POST, etc.)
   - Essential operations for minimal module
   - Complex/special operations

4. **Recommendations**
   - Suggested minimal implementation
   - Authentication approach
   - Potential challenges
   - Dependencies to consider

**Output**: `api-analysis-summary.md`

## Outputs

All outputs stored in: `.claude/.localmemory/analyze-api-{vendor}-{product}/`

### Files Created:
- `task-01-output.json` - Product package info (if found)
- `task-02-output.json` - Documentation URLs and access info
- `task-03-output.json` - Detailed API analysis
- `api-model.mmd` - Mermaid entity relationship diagram
- `task-05-output.json` - Prioritized operations list
- `api-analysis-summary.md` - Human-readable summary

### JSON Schema Examples:

**task-03-output.json:**
```json
{
  "entities": [
    {
      "name": "Repository",
      "type": "primary",
      "operations": ["list", "get", "create", "update", "delete"],
      "attributes": ["id", "name", "owner", "description", "private"],
      "relationships": [
        {"entity": "Issue", "type": "one-to-many"},
        {"entity": "PullRequest", "type": "one-to-many"}
      ]
    }
  ],
  "operations": [
    {
      "name": "listRepositories",
      "method": "GET",
      "path": "/repos",
      "entity": "Repository",
      "type": "list",
      "parameters": ["org", "type", "sort"],
      "pagination": true
    }
  ]
}
```

**task-05-output.json:**
```json
{
  "essentialOperation": {
    "name": "getCurrentUser",
    "reason": "Simple GET, no params, validates connection"
  },
  "tier1": ["getCurrentUser"],
  "tier2": ["listRepositories", "getRepository", "createRepository"],
  "tier3": ["searchRepositories", "updateRepository"],
  "dependencies": {
    "createIssue": ["requires repository ID from listRepositories or getRepository"]
  }
}
```

## Usage Examples

### Example 1: Before Module Creation
```
User: "Analyze the GitHub API before I create a module"
→ Execute analyze-api task
→ Review api-model.mmd and summary
→ Decide on minimal implementation
→ Proceed to create-module task
```

### Example 2: Standalone Research
```
User: "I need to understand the Stripe API structure"
→ Execute analyze-api task
→ Generate ERD and summary
→ User reviews without creating module
```

### Example 3: Module Update Planning
```
User: "Analyze webhook operations in the Slack API"
→ Execute analyze-api task with focus on webhooks
→ Identify operations to add
→ Proceed to update-module task
```

## Context Management
- **Estimated Total**: 30-40%
- **Heavy Tasks**: API analysis (20%), Diagram creation (10%)
- **Split Points**: After analysis, before diagram creation

## Decision Points
- No product package found → Continue with URL-only analysis
- Multiple authentication methods → Document all, recommend simplest
- Large API (100+ operations) → Suggest splitting diagram by domain

## Success Criteria
- ✅ Entity relationship diagram created
- ✅ All entities mapped with relationships
- ✅ Operations categorized by tier
- ✅ Essential operation identified
- ✅ Analysis summary complete
- ✅ Recommendations provided

## Integration with Other Tasks

This task feeds into:
- **create-module**: Provides entity model and essential operation
- **update-module**: Identifies operations to add
- **API documentation**: Source for API specification work

This task can be run:
- **Before module work**: Research and planning
- **During module work**: Additional entity discovery
- **Standalone**: Pure research, no module creation
