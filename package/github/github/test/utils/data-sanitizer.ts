export function sanitizeRecordedData(recording: any): any {
  const sanitized = JSON.parse(JSON.stringify(recording));
  
  // Sanitize headers
  if (sanitized.reqheaders) {
    sanitized.reqheaders = sanitizeHeaders(sanitized.reqheaders);
  }
  
  if (sanitized.headers) {
    sanitized.headers = sanitizeHeaders(sanitized.headers);
  }
  
  // Sanitize request body
  if (sanitized.body) {
    sanitized.body = sanitizeObject(sanitized.body);
  }
  
  // Sanitize response body
  if (sanitized.response) {
    sanitized.response = sanitizeObject(sanitized.response);
  }
  
  return sanitized;
}

function sanitizeHeaders(headers: Record<string, any>): Record<string, any> {
  const sanitized = { ...headers };
  
  // Remove or sanitize authentication headers
  if (sanitized.authorization) {
    if (sanitized.authorization.startsWith('token ')) {
      sanitized.authorization = 'token test_token_sanitized';
    } else if (sanitized.authorization.startsWith('Bearer ')) {
      sanitized.authorization = 'Bearer test_bearer_token_sanitized';
    }
  }
  
  // Remove sensitive headers
  delete sanitized['x-ratelimit-reset'];
  delete sanitized['x-oauth-scopes'];
  delete sanitized['x-accepted-oauth-scopes'];
  
  return sanitized;
}

function sanitizeObject(obj: any): any {
  if (obj === null || obj === undefined) {
    return obj;
  }
  
  if (Array.isArray(obj)) {
    return obj.map(item => sanitizeObject(item));
  }
  
  if (typeof obj !== 'object') {
    return sanitizeValue(obj);
  }
  
  const sanitized: Record<string, any> = {};
  
  for (const [key, value] of Object.entries(obj)) {
    if (isSensitiveField(key)) {
      sanitized[key] = sanitizeValue(value, key);
    } else {
      sanitized[key] = sanitizeObject(value);
    }
  }
  
  return sanitized;
}

function sanitizeValue(value: any, fieldName?: string): any {
  if (typeof value !== 'string') {
    return value;
  }
  
  // Sanitize email addresses
  if (isEmailAddress(value)) {
    return 'test@example.com';
  }
  
  // Sanitize GitHub tokens
  if (value.startsWith('ghp_') || value.startsWith('github_pat_')) {
    return 'test_github_token_sanitized';
  }
  
  // Sanitize URLs with personal info
  if (isUrl(value) && containsPersonalInfo(value)) {
    return sanitizeUrl(value);
  }
  
  // Sanitize based on field name
  if (fieldName) {
    if (['token', 'access_token', 'refresh_token', 'api_key'].includes(fieldName.toLowerCase())) {
      return 'test_token_sanitized';
    }
    
    if (['email', 'email_address'].includes(fieldName.toLowerCase())) {
      return 'test@example.com';
    }
    
    if (['phone', 'phone_number', 'mobile'].includes(fieldName.toLowerCase())) {
      return '+1-555-0123';
    }
  }
  
  return value;
}

function isSensitiveField(fieldName: string): boolean {
  const sensitiveFields = [
    'token', 'access_token', 'refresh_token', 'api_key', 'password',
    'email', 'email_address', 'phone', 'phone_number', 'mobile',
    'ssn', 'social_security_number', 'credit_card', 'cc_number',
    'private_key', 'secret', 'auth', 'authorization'
  ];
  
  return sensitiveFields.some(field => 
    fieldName.toLowerCase().includes(field)
  );
}

function isEmailAddress(value: string): boolean {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(value);
}

function isUrl(value: string): boolean {
  try {
    new URL(value);
    return true;
  } catch {
    return false;
  }
}

function containsPersonalInfo(url: string): boolean {
  // Check if URL contains patterns that might include personal information
  const personalPatterns = [
    /@[\w.-]+/,  // @ symbols (usernames)
    /\/users\/[^\/]+/,  // user paths
    /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/  // emails in URLs
  ];
  
  return personalPatterns.some(pattern => pattern.test(url));
}

function sanitizeUrl(url: string): string {
  try {
    const parsed = new URL(url);
    
    // Replace username in path
    if (parsed.pathname.includes('/users/')) {
      parsed.pathname = parsed.pathname.replace(/\/users\/[^\/]+/, '/users/testuser');
    }
    
    // Remove query parameters that might contain personal info
    const sensitiveParams = ['email', 'user', 'username', 'token'];
    sensitiveParams.forEach(param => {
      if (parsed.searchParams.has(param)) {
        parsed.searchParams.set(param, 'sanitized');
      }
    });
    
    return parsed.toString();
  } catch {
    return url;
  }
}