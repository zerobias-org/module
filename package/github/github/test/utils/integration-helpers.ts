import * as fs from 'fs';
import * as path from 'path';
import { sanitizeResponse } from '../integration/Common';

/**
 * Save integration test response to fixtures directory
 * @param testName Name of the test (used for filename)
 * @param response Response data to save
 * @param fixtureSubDir Optional subdirectory within fixtures/integration
 */
export function saveResponseFixture(testName: string, response: any, fixtureSubDir = ''): void {
  try {
    const fixturesDir = path.join(__dirname, '../fixtures/integration', fixtureSubDir);
    
    // Ensure fixtures directory exists
    if (!fs.existsSync(fixturesDir)) {
      fs.mkdirSync(fixturesDir, { recursive: true });
    }
    
    // Sanitize response before saving
    const sanitizedResponse = sanitizeResponse(response);
    
    // Create filename from test name
    const filename = `${testName.replace(/\s+/g, '-').toLowerCase()}.json`;
    const filepath = path.join(fixturesDir, filename);
    
    // Save to file
    fs.writeFileSync(filepath, JSON.stringify(sanitizedResponse, null, 2));
    
    console.log(`Fixture saved: ${filepath}`);
  } catch (error) {
    console.warn(`Failed to save fixture for test "${testName}":`, error);
  }
}

/**
 * Load integration test fixture from fixtures directory
 * @param testName Name of the test (used for filename)
 * @param fixtureSubDir Optional subdirectory within fixtures/integration
 * @returns Parsed fixture data or null if not found
 */
export function loadResponseFixture(testName: string, fixtureSubDir = ''): any | null {
  try {
    const fixturesDir = path.join(__dirname, '../fixtures/integration', fixtureSubDir);
    const filename = `${testName.replace(/\s+/g, '-').toLowerCase()}.json`;
    const filepath = path.join(fixturesDir, filename);
    
    if (!fs.existsSync(filepath)) {
      return null;
    }
    
    const content = fs.readFileSync(filepath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.warn(`Failed to load fixture for test "${testName}":`, error);
    return null;
  }
}

/**
 * Validate environment variables for integration tests
 * @param requiredVars Array of required environment variable names
 * @throws Error if any required variables are missing
 */
export function validateEnvironmentVariables(requiredVars: string[]): void {
  const missing = requiredVars.filter(varName => !process.env[varName]);
  
  if (missing.length > 0) {
    throw new Error(
      `Missing required environment variables for integration tests: ${missing.join(', ')}\n` +
      'Please check your .env file and ensure all required variables are set.'
    );
  }
}

/**
 * Retry a function with exponential backoff
 * @param fn Function to retry
 * @param maxRetries Maximum number of retries
 * @param initialDelay Initial delay in milliseconds
 * @returns Promise that resolves with the function result
 */
export async function retryWithBackoff<T>(
  fn: () => Promise<T>,
  maxRetries = 3,
  initialDelay = 1000
): Promise<T> {
  let lastError: Error;
  
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error as Error;
      
      if (attempt === maxRetries) {
        throw lastError;
      }
      
      const delay = initialDelay * Math.pow(2, attempt);
      console.log(`Attempt ${attempt + 1} failed, retrying in ${delay}ms...`);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
  
  throw lastError!;
}

/**
 * Wait for a specified amount of time
 * @param ms Milliseconds to wait
 */
export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Create a test timeout wrapper that fails gracefully
 * @param timeoutMs Timeout in milliseconds
 * @param operation Operation to perform
 * @returns Promise that resolves with the operation result or rejects on timeout
 */
export function withTimeout<T>(timeoutMs: number, operation: () => Promise<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    const timeoutHandle = setTimeout(() => {
      reject(new Error(`Operation timed out after ${timeoutMs}ms`));
    }, timeoutMs);
    
    operation()
      .then(result => {
        clearTimeout(timeoutHandle);
        resolve(result);
      })
      .catch(error => {
        clearTimeout(timeoutHandle);
        reject(error);
      });
  });
}

/**
 * Check if the current environment is configured for integration testing
 * @returns true if integration test environment is properly configured
 */
export function isIntegrationTestEnvironment(): boolean {
  return !!(
    process.env.GITHUB_API_TOKEN &&
    process.env.NODE_ENV === 'test'
  );
}

/**
 * Get configuration for integration tests from environment variables
 * @returns Configuration object
 */
export function getIntegrationTestConfig() {
  return {
    apiToken: process.env.GITHUB_API_TOKEN,
    baseUrl: process.env.GITHUB_BASE_URL || 'https://api.github.com',
    timeout: parseInt(process.env.INTEGRATION_TIMEOUT || '30000'),
    logLevel: process.env.LOG_LEVEL || 'info',
    retryAttempts: parseInt(process.env.INTEGRATION_RETRY_ATTEMPTS || '3'),
    retryDelay: parseInt(process.env.INTEGRATION_RETRY_DELAY || '1000')
  };
}