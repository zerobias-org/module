import * as dotenv from 'dotenv';
import { URL } from '@auditmation/types-core-js';
import { newGitHub, GitHubConnector } from '../../src';
import { ConnectionProfile } from '../../generated/model';

// Load environment variables
dotenv.config();

/**
 * Prepare and configure the GitHub API client for integration testing
 * @returns Configured GitHubConnector instance
 */
export async function prepareApi(): Promise<GitHubConnector> {
  // Check for required environment variables
  const apiToken = process.env.GITHUB_API_TOKEN;
  
  if (!apiToken) {
    throw new Error(
      'GITHUB_API_TOKEN environment variable is required for integration tests. ' +
      'Please set it in your .env file.'
    );
  }

  // Create connection profile
  const baseUrl = process.env.GITHUB_BASE_URL;
  const url = baseUrl ? new URL(baseUrl) : undefined;
  const connectionProfile = new ConnectionProfile(apiToken, url);

  // Create and connect the client
  const github = newGitHub();
  await github.connect(connectionProfile);

  // Verify connection
  const isConnected = await github.isConnected();
  if (!isConnected) {
    throw new Error('Failed to establish connection to GitHub API');
  }

  return github;
}

/**
 * Check if integration test credentials are available
 * @returns true if credentials are configured
 */
export function areCredentialsAvailable(): boolean {
  return !!process.env.GITHUB_API_TOKEN;
}

/**
 * Skip test if credentials are not available
 * @param testName Name of the test being skipped
 */
export function skipIfNoCredentials(testName: string): void {
  if (!areCredentialsAvailable()) {
    console.log(`Skipping ${testName} - GITHUB_API_TOKEN not configured`);
    return;
  }
}

/**
 * Sanitize sensitive data from test responses
 * @param data Response data to sanitize
 * @returns Sanitized data
 */
export function sanitizeResponse(data: any): any {
  if (!data) return data;

  const sanitized = JSON.parse(JSON.stringify(data));
  
  // Remove or mask sensitive fields
  const sensitiveFields = ['token', 'password', 'secret', 'key', 'apiToken'];
  
  function sanitizeObject(obj: any): void {
    if (typeof obj !== 'object' || obj === null) return;
    
    for (const [key, value] of Object.entries(obj)) {
      if (sensitiveFields.some(field => key.toLowerCase().includes(field.toLowerCase()))) {
        obj[key] = '***SANITIZED***';
      } else if (typeof value === 'object') {
        sanitizeObject(value);
      }
    }
  }
  
  if (Array.isArray(sanitized)) {
    sanitized.forEach(sanitizeObject);
  } else {
    sanitizeObject(sanitized);
  }
  
  return sanitized;
}