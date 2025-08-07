# Task 10: Create User Guide Documentation

## Overview

Create effective, short, and easy-to-read USER_GUIDE.md documentation that provides concise step-by-step instructions for obtaining credentials and setting up the module. Focus on clarity and brevity while maintaining completeness.

## 🚨 Critical Rules

- **EXACT CONNECTIONPROFILE MAPPING** - Credential instructions must match EXACTLY with connectionProfile properties from Task 02
- **FIELD-BY-FIELD ACCURACY** - Every credential field in connectionProfile must have corresponding acquisition instructions
- **PARAMETER NAME CONSISTENCY** - Use identical parameter names and structure as defined in Task 02
- **CONCISE AND CLEAR** - Write in short, easy-to-read format focusing on essential information
- **STEP-BY-STEP** - Break down each process into numbered steps, but keep steps brief
- **MINIMAL VISUALS** - Include only essential screenshot placeholders with concise descriptions
- **CREDENTIAL SECURITY** - Include warnings about credential security and best practices
- **TESTING INSTRUCTIONS** - Provide clear steps to verify connection works
- **PLATFORM-SPECIFIC** - Include instructions for different platforms when applicable
- **TROUBLESHOOTING** - Add common issues and solutions section

## Input Requirements

### Essential
- Task 01 output: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json` (API discovery)
- Task 02 output: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (connection profile)
- Task 04 output: `.claude/.localmemory/{action}-{module-identifier}/task-04-output.json` (module structure)
- `module_path` - Path to the module directory
- `vendor_name` - Service vendor name (e.g., "GitHub", "AWS", "GitLab")
- `service_name` - Service name in proper case

### Optional
- `api_documentation_url` - Link to official API documentation
- `credential_types` - List of supported authentication methods
- `test_endpoints` - Endpoints for testing connection

## Expected Outputs

### Files Created
- `USER_GUIDE.md` - Comprehensive credential setup and connection guide

### Memory Output  
- `.claude/.localmemory/{action}-{module-identifier}/task-10-output.json` - Task completion status

## Implementation

### Step 1: Analyze Connection Requirements

**Load Previous Task Outputs:**
- Read Task 02 output to understand authentication methods and connection parameters
- Identify ALL credential fields defined in the connectionProfile structure
- Determine if multiple authentication methods are supported
- Extract base URLs, API versions, and other connection parameters

**🚨 CRITICAL: ConnectionProfile Field Mapping:**
- **MANDATORY**: Every field in connectionProfile MUST have corresponding user instructions
- **EXACT NAMES**: Use identical field names as defined in Task 02 connectionProfile
- **NESTED STRUCTURE**: Match the exact hierarchy (authentication.token, authentication.type, etc.)
- **DATA TYPES**: Respect field types and validation requirements from connectionProfile
- **OPTIONAL FIELDS**: Clearly mark which connectionProfile fields are optional vs required

**Credential Analysis:**
- Map technical parameter names to user-friendly descriptions while preserving exact field names
- Identify which connectionProfile credentials are mandatory vs optional
- Determine if credentials have different permission levels or scopes
- Note any special requirements (formats, restrictions, expiration) defined in connectionProfile

### Step 2: Create User Guide Structure

**Document Outline:**
```markdown
# {Service Name} Module User Guide

## Table of Contents
1. Prerequisites
2. Obtaining Credentials
3. Setting Up the Module
4. Testing Your Connection
5. Troubleshooting
6. Security Best Practices
7. Additional Resources

## Prerequisites
[System requirements, account requirements]

## Obtaining Credentials
### Method 1: [Primary auth method]
### Method 2: [Secondary auth method if applicable]

## Setting Up the Module
[Environment setup, credential configuration]

## Testing Your Connection
[Step-by-step testing instructions]

## Troubleshooting
[Common issues and solutions]

## Security Best Practices
[Credential security guidelines]

## Additional Resources
[Links to official documentation]
```

### Step 3: Write Credential Acquisition Instructions

**For Each Authentication Method:**

**Step-by-Step Navigation:**
- Start from the main login/dashboard page
- Provide exact navigation path: "Click Settings → API Keys → Generate New Key"
- Include what each page/section looks like
- Describe where to find specific options or buttons

**Screenshot Placeholders:**
For each major step, include:
```markdown
**[SCREENSHOT PLACEHOLDER: {Description}]**
- **File name:** `screenshot-{step-number}-{description}.png`
- **Content:** Screenshot showing {specific UI elements}
- **Annotations:** Highlight {specific buttons/fields} with red circles/arrows
- **Size:** Recommended 800x600 or larger for clarity
```

**Example Credential Steps:**
1. **Login and Navigate**
   - Go to {service_url}
   - Click "Sign In" and log into your account
   - Navigate to Account Settings → API Access
   
2. **Generate API Credentials**
   - Click "Create New API Key" button
   - Enter a descriptive name (e.g., "Zborg Module Access")
   - Select appropriate permissions/scopes
   - Copy the generated key immediately

3. **Record Connection Parameters**
   - Note your account/organization ID
   - Record the API endpoint URL
   - Save any additional required parameters

### Step 4: Create Module Setup Instructions

**Environment Configuration:**
- Show how to create `.env` file
- Map credential parameters to environment variable names EXACTLY as defined in connectionProfile
- Include example values (sanitized/placeholder) matching connectionProfile field types
- Explain the purpose of each parameter using connectionProfile structure

**🚨 CRITICAL: Connection Profile Setup:**
- Show the connectionProfile.yml structure EXACTLY as defined in Task 02
- **FIELD-BY-FIELD MATCH**: Every connectionProfile field must have setup instructions
- **EXACT HIERARCHY**: Preserve nested structure (authentication.type, authentication.token, etc.)
- **PARAMETER VALIDATION**: Include validation rules defined in connectionProfile schema
- Provide examples for different authentication methods supported in connectionProfile
- Include validation steps that verify connectionProfile requirements are met

**Example Section:**
```markdown
## Setting Up Your Environment

1. **Create Environment File**
   Create a `.env` file in your project root:
   ```bash
   # {Service Name} Configuration
   {SERVICE}_API_TOKEN=your_api_token_here
   {SERVICE}_BASE_URL=https://api.{service}.com/v1
   {SERVICE}_ORGANIZATION_ID=your_org_id
   ```

2. **Configure Connection Profile**
   Update your `connectionProfile.yml`:
   ```yaml
   {service_name}:
     authentication:
       type: "token"
       token: "${SERVICE_API_TOKEN}"
     base_url: "${SERVICE_BASE_URL}"
     organization_id: "${SERVICE_ORGANIZATION_ID}"
   ```
```

### Step 5: Add Testing Instructions

**Connection Testing:**
- Provide simple test commands or scripts
- Show expected success output
- Explain how to identify connection issues
- Include validation steps for each credential type

**Test Commands:**
```markdown
## Testing Your Connection

1. **Basic Connection Test**
   ```bash
   npm test -- --grep "connection"
   ```

2. **Credential Validation**
   ```bash
   node -e "
   const client = require('./dist/index.js');
   client.connect().then(() => console.log('✓ Connected successfully'));
   "
   ```

3. **API Access Test**
   Test a basic read operation to verify permissions work correctly.
```

### Step 6: Add Troubleshooting Section

**Common Issues:**
- Authentication failures and their causes
- Network connectivity problems
- Permission/scope issues
- Configuration errors

**For Each Issue:**
- Describe the symptom
- Explain the likely cause
- Provide step-by-step solution
- Include relevant error messages to look for

**Example Troubleshooting:**
```markdown
## Troubleshooting

### "Invalid API Token" Error
**Symptoms:** Authentication fails with 401 Unauthorized
**Causes:** 
- Token copied incorrectly (missing characters, extra spaces)
- Token expired or revoked
- Token lacks required permissions

**Solution:**
1. Verify token is copied completely without extra characters
2. Check token expiration date in {service} dashboard
3. Regenerate token if necessary
4. Ensure token has required scopes: [list specific scopes]
```

### Step 7: Add Security Best Practices

**Credential Security:**
- Never commit credentials to version control
- Use environment variables or secure secret management
- Rotate credentials regularly
- Use minimal required permissions
- Monitor credential usage

**Access Management:**
- Create service-specific accounts when possible
- Use time-limited tokens
- Implement proper access controls
- Regular security audits

### Step 8: Include Additional Resources

**Official Documentation Links:**
- API documentation
- Authentication guides
- SDK/client library documentation
- Community resources

**Support Channels:**
- Official support contact
- Community forums
- Issue tracking
- Documentation feedback

### Step 9: Review and Validate

**🚨 CRITICAL: ConnectionProfile Validation:**
- **MANDATORY CHECK**: Verify EVERY connectionProfile field from Task 02 has user instructions
- **FIELD NAME ACCURACY**: Confirm all parameter names match Task 02 connectionProfile exactly
- **STRUCTURE VALIDATION**: Ensure nested hierarchy matches connectionProfile structure
- **TYPE CONSISTENCY**: Validate examples match connectionProfile field types and formats
- **COMPLETENESS**: No connectionProfile field should be missing from user instructions

**Content Review:**
- Verify all steps are accurate and complete
- Check that screenshot placeholders have clear descriptions
- Ensure troubleshooting covers common scenarios
- Validate that security recommendations are current

**User Testing Simulation:**
- Walk through each step as a new user would using connectionProfile requirements
- Identify any gaps or unclear instructions for connectionProfile setup
- Test example commands and configurations against Task 02 connectionProfile
- Verify all links and references work

## Success Criteria

- [ ] **🚨 CRITICAL**: Every connectionProfile field from Task 02 has corresponding user acquisition instructions
- [ ] **🚨 CRITICAL**: All parameter names match Task 02 connectionProfile exactly (field-by-field validation)
- [ ] **🚨 CRITICAL**: ConnectionProfile structure hierarchy preserved in user instructions
- [ ] USER_GUIDE.md created with comprehensive credential setup instructions
- [ ] Step-by-step navigation instructions for obtaining credentials
- [ ] Screenshot placeholders with detailed descriptions for each major step
- [ ] Environment variable and connection profile setup instructions matching Task 02 requirements
- [ ] Connection testing and validation steps using connectionProfile parameters
- [ ] Comprehensive troubleshooting section with common issues
- [ ] Security best practices and credential management guidelines
- [ ] Links to additional resources and official documentation
- [ ] Document reviewed for accuracy and connectionProfile completeness

## Template Structure

The USER_GUIDE.md should follow this structure:

1. **Prerequisites** - Account requirements, system requirements
2. **Obtaining Credentials** - Step-by-step for each auth method
3. **Setting Up the Module** - Environment and connection configuration  
4. **Testing Your Connection** - Validation and test procedures
5. **Troubleshooting** - Common issues and solutions
6. **Security Best Practices** - Credential security guidelines
7. **Additional Resources** - Official documentation and support

---

**Human Review Required**: After creating the user guide, review all instructions for accuracy and completeness. Verify that credential acquisition steps match the current UI of the target service.