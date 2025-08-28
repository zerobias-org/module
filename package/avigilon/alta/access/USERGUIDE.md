# Avigilon Alta Access Module User Guide

## Obtaining Credentials

### Where to Find Your Credentials

1. **Login to Dashboard** - Navigate to https://access.alta.avigilon.com/ and log in with your administrator account <!-- Screenshot suggestion: Avigilon Alta login page -->

2. **Verify User Permissions** - Ensure your user account has the following settings:
   - Portal access is selected (enabled) <!-- Screenshot suggestion: User settings page showing Portal access toggle -->
   - User role includes "Super Admin" permissions for API functionality <!-- Screenshot suggestion: User roles configuration page -->

3. **Use Your Login Credentials** - The same email and password you use to log into the dashboard will be used for API authentication <!-- Screenshot suggestion: Login form highlighting email and password fields -->

4. **Multi-Factor Authentication** - If MFA is enabled on your account, you'll need your authenticator app to generate TOTP codes <!-- Screenshot suggestion: MFA setup page or authenticator app -->

### Connection Profile Mapping

| Service Credential | Connection Profile Field | Description |
|-------------------|-------------------------|-------------|
| Dashboard Login Email | `email` | Your Avigilon Alta account email address |
| Dashboard Login Password | `password` | Your Avigilon Alta account password |
| Authenticator App Code | `totpCode` | Time-based One-Time Password (only required if MFA is enabled) |

---

📋 **Important**: This guide contains auto-generated instructions. Interface changes may occur - please verify each step matches what you see on screen.
