# Avigilon Alta Access Module User Guide

## Obtaining Credentials

### Method 1: Dashboard Access Token (Recommended)

1. **Login to Dashboard** - Navigate to https://access.alta.avigilon.com/ and log in with your administrator account <!-- Screenshot suggestion: Avigilon Alta login page -->

2. **Verify User Permissions** - Ensure your user account has the following settings:
   - Portal access is selected (enabled) <!-- Screenshot suggestion: User settings page showing Portal access toggle -->
   - User role includes "Super Admin" permissions for API functionality <!-- Screenshot suggestion: User roles configuration page -->

3. **Access API Settings** - Navigate to the API configuration section in the dashboard (typically found in Administration or Settings) <!-- Screenshot suggestion: Dashboard navigation menu highlighting API or Administration section -->

4. **Generate or Retrieve Access Token** - Locate your API access token in the API settings or credentials section <!-- Screenshot suggestion: API credentials page showing access token field -->

5. **Note Your API Base URL** - The service uses `https://api.openpath.com` as the base URL for all API calls

### Method 2: Login API with Email/Password

Alternatively, you can authenticate programmatically using the login API:

1. **Prepare Credentials** - Gather your Avigilon Alta account credentials:
   - Email address (username)
   - Password
   - MFA code (if multi-factor authentication is enabled)

2. **Make Login Request** - Use the login endpoint to obtain an access token:
   ```bash
   curl -X POST https://api.openpath.com/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "email": "your-email@domain.com",
       "password": "your-password",
       "mfaCode": "123456"
     }'
   ```

3. **Extract Access Token** - The response will contain an access token for API authentication

4. **Use Token for API Calls** - Include the token in subsequent API requests

⚠️ **Security Note**: Store credentials securely and consider using dashboard-generated tokens for production environments.

### Connection Profile Mapping

| Service Credential | Connection Profile Field | Description |
|-------------------|-------------------------|-------------|
| API Access Token | `apiToken` | Your personal access token, OAuth token, or API key from the dashboard |
| API Base URL | `url` | Service endpoint URL (https://api.openpath.com) |

---

📋 **Important**: This guide contains auto-generated instructions. Interface changes may occur - please verify each step matches what you see on screen.