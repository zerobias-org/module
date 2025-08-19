import * as dotenv from 'dotenv';
import { expect } from 'chai';
import { newAvigilonAltaAccess, AccessImpl } from '../../src';
import { ConnectionProfile } from '../../generated/model';
import { getLogger } from '@auditmation/util-logger';

// Core types for assertions
import { Email, URL, UUID, IpAddress } from '@auditmation/types-core-js';

// Load environment variables
dotenv.config();

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

// Environment variables for Avigilon Alta Access API
const API_KEY = process.env.API_TOKEN || process.env.AVIGILON_API_KEY;
const BASE_URL = process.env.AVIGILON_BASE_URL || 'https://api.openpath.com';
const ORGANIZATION_ID = process.env.AVIGILON_ORG_ID || 'test-org-123';

/**
 * Creates and configures an Avigilon Alta Access module instance for testing
 * @returns Promise<AccessImpl> - Configured module instance
 */
export async function prepareApi(): Promise<AccessImpl> {
  const access = newAvigilonAltaAccess();
  
  if (!API_KEY) {
    throw new Error('Integration tests require API_TOKEN or AVIGILON_API_KEY environment variable. Check .env file or copy from .env.example');
  }

  // Create connection profile for Avigilon Alta Access
  const baseURL = new URL(BASE_URL);
  const profile = new ConnectionProfile(API_KEY, baseURL);

  logger.debug('Connecting to Avigilon Alta Access API', { baseUrl: BASE_URL });
  
  try {
    await access.connect(profile);
    logger.debug('Successfully connected to Avigilon Alta Access API');
  } catch (error) {
    logger.error('Failed to connect to Avigilon Alta Access API', error);
    throw error;
  }

  return access;
}

/**
 * Checks if integration test credentials are available
 * @returns boolean - True if credentials are available
 */
export function hasCredentials(): boolean {
  return !!(API_KEY);
}

/**
 * Sanitizes sensitive data from API responses for fixture storage
 * @param data - Raw API response data
 * @returns Sanitized data safe for storage
 */
export function sanitizeResponse(data: any): any {
  if (!data) return data;
  
  const sanitized = JSON.parse(JSON.stringify(data));
  
  // Remove or mask sensitive fields
  const sensitiveFields = ['api_key', 'token', 'password', 'secret', 'email', 'phone'];
  
  function sanitizeObject(obj: any): any {
    if (Array.isArray(obj)) {
      return obj.map(sanitizeObject);
    } else if (obj && typeof obj === 'object') {
      const result: any = {};
      for (const [key, value] of Object.entries(obj)) {
        const lowerKey = key.toLowerCase();
        if (sensitiveFields.some(field => lowerKey.includes(field))) {
          if (lowerKey.includes('email')) {
            result[key] = 'user@example.com';
          } else if (lowerKey.includes('phone')) {
            result[key] = '+1-555-0123';
          } else {
            result[key] = '[REDACTED]';
          }
        } else {
          result[key] = sanitizeObject(value);
        }
      }
      return result;
    }
    return obj;
  }
  
  return sanitizeObject(sanitized);
}

/**
 * Saves sanitized API response as fixture file
 * @param filename - Name of the fixture file
 * @param data - API response data to save
 */
export async function saveFixture(filename: string, data: any): Promise<void> {
  const fs = require('fs');
  const path = require('path');
  
  const sanitized = sanitizeResponse(data);
  const fixtureDir = path.join(__dirname, '../fixtures/integration');
  
  // Ensure directory exists
  if (!fs.existsSync(fixtureDir)) {
    fs.mkdirSync(fixtureDir, { recursive: true });
  }
  
  const filepath = path.join(fixtureDir, filename);
  fs.writeFileSync(filepath, JSON.stringify(sanitized, null, 2));
  
  logger.debug(`Saved fixture: ${filename}`);
}

/**
 * Test helper to validate core type instances
 */
export const validateCoreTypes = {
  isEmail: (value: any) => expect(value).to.be.instanceof(Email),
  isURL: (value: any) => expect(value).to.be.instanceof(URL),
  isUUID: (value: any) => expect(value).to.be.instanceof(UUID),
  isIpAddress: (value: any) => expect(value).to.be.instanceof(IpAddress),
  isDate: (value: any) => expect(value).to.be.instanceof(Date)
};

/**
 * Common test configuration
 */
export const testConfig = {
  timeout: parseInt(process.env.INTEGRATION_TIMEOUT || '30000'),
  retries: parseInt(process.env.INTEGRATION_RETRIES || '2'),
  delay: parseInt(process.env.INTEGRATION_DELAY || '1000'),
  organizationId: ORGANIZATION_ID
};

