# GitHub Module User Guide

## Obtaining Credentials

### Where to Find Your Credentials

1. Navigate to GitHub.com and sign in to your account <!-- Screenshot suggestion: GitHub homepage login -->
2. Click your profile picture in the top-right corner
3. Select "Settings" from the dropdown menu <!-- Screenshot suggestion: User dropdown menu -->
4. Scroll down in the left sidebar and click "Developer settings" <!-- Screenshot suggestion: Settings sidebar with Developer settings highlighted -->
5. Click "Personal access tokens" > "Tokens (classic)" <!-- Screenshot suggestion: Developer settings page with PAT section -->
6. Click "Generate new token" > "Generate new token (classic)" <!-- Screenshot suggestion: Token generation options -->
7. Provide a description for your token (e.g., "Module Access")
8. Select the required scopes for your use case:
   - For organization access: `read:org` scope
   - For additional permissions, select scopes as needed <!-- Screenshot suggestion: Scopes selection interface -->
9. Click "Generate token" at the bottom
10. Copy the generated token immediately (it won't be shown again) <!-- Screenshot suggestion: Generated token display with copy button -->

### Connection Profile Mapping

| Service Credential | Connection Profile Field | Required |
|-------------------|-------------------------|----------|
| Personal Access Token | `apiToken` | Yes |
| GitHub Enterprise Server URL | `url` | No |

**Notes:**
- The `apiToken` field accepts your personal access token from step 10 above
- The `url` field is only needed for GitHub Enterprise Server installations (leave empty for GitHub.com)
- For GitHub.com, the API automatically uses `https://api.github.com` as the base URL

---

📋 **Important**: This guide contains auto-generated instructions. Interface changes may occur - please verify each step matches what you see on screen.