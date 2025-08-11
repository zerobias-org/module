# Task 10: Create User Guide Documentation

## Overview

Create a concise USER_GUIDE.md that focuses exclusively on two things:
1. Where to find credentials in the service
2. Which credential field maps to which connection profile parameter

Keep it short, clear, and to the point. No extensive documentation - just the essential mapping between service credentials and connection profile fields.

## 🚨 Critical Rules

- **EXACT CONNECTIONPROFILE MAPPING** - Show exactly which service credential goes to which connectionProfile field
- **FIELD-BY-FIELD ACCURACY** - Every connectionProfile field must have a clear source credential identified
- **CONCISE ONLY** - No lengthy explanations - just credential location and field mapping
- **NO EXTRAS** - No troubleshooting, security practices, or additional sections beyond credential mapping
- **GENERIC EXAMPLES** - Never use "Zborg", "GitHub", or other specific product names in examples - use generic terms like "Module Access", "testuser", "{service_name}"

## Input Requirements

### Essential
- Task 02 output: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (connection profile)
- `module_path` - Path to the module directory
- `service_name` - Service name in proper case

## Expected Outputs

### Files Created
- `USER_GUIDE.md` - Concise credential location and connection profile field mapping

### Memory Output  
- `.claude/.localmemory/{action}-{module-identifier}/task-10-output.json` - Task completion status

## Implementation

### Step 1: Analyze Connection Profile

**Load Task 02 Output:**
- Read Task 02 output to get the exact connectionProfile field structure
- Identify ALL fields that need credentials from the service
- Note the field hierarchy and types

### Step 2: Research Credential Locations

**Find Credential Sources:**
- Research where each required credential is obtained in the service UI
- Document the exact navigation path to find each credential
- Map each service credential to its corresponding connectionProfile field
- Include hidden HTML comments at the end of relevant steps suggesting where screenshots would be helpful (not visible in rendered markdown, positioned to avoid breaking list formatting)

### Step 3: Create Simple Mapping Guide

**Document Structure:**
```markdown
# {Service Name} Module User Guide

## Obtaining Credentials

### Where to Find Your Credentials
[Brief navigation instructions for each credential type with inline screenshot suggestions]

1. Step with navigation instruction <!-- Screenshot suggestion: Specific UI element or page -->
2. Another step with action <!-- Screenshot suggestion: Relevant UI screenshot -->

Note: Place HTML comments at the end of relevant steps to avoid breaking markdown list formatting

### Connection Profile Mapping
[Table showing: Service Credential → Connection Profile Field]

---

📋 **Important**: This guide contains auto-generated instructions. Interface changes may occur - please verify each step matches what you see on screen.
```

## Success Criteria

- [ ] **🚨 CRITICAL**: Every connectionProfile field from Task 02 has a clear source credential identified
- [ ] **🚨 CRITICAL**: All connectionProfile field names match Task 02 exactly
- [ ] USER_GUIDE.md created with concise credential location and mapping instructions
- [ ] Simple table/mapping showing which service credential goes to which connectionProfile field
- [ ] Brief navigation instructions for finding each required credential in the service
- [ ] Option 2 AI warning with proper formatting  
- [ ] Hidden HTML comments suggesting where screenshots would be helpful for visual clarity

## Template Structure

The USER_GUIDE.md should be concise with only:

1. **Where to Find Your Credentials** - Brief navigation for each credential type
2. **Connection Profile Mapping** - Simple table: Service Credential → ConnectionProfile Field

---

**Note**: Keep the guide short and focused only on credential acquisition and field mapping. Include hidden HTML comments suggesting where screenshots would be helpful, but don't include actual screenshots or additional sections. Include user-facing AI-generated warning that will be removed after human validation.