import * as dotenv from 'dotenv';
import { expect } from 'chai';
import fs from 'fs';
import path from 'path';
import { getLogger } from '@auditmation/util-logger';
import { Email, URL, UUID, IpAddress } from '@auditmation/types-core-js';
import { newAccess, AccessImpl } from '../../src';
import { ConnectionProfile } from '../../generated/model';

// Load environment variables
dotenv.config();

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

// Environment variables for Avigilon Alta Access API
const EMAIL = process.env.AVIGILON_EMAIL;
const PASSWORD = process.env.AVIGILON_PASSWORD;
const TOTP_CODE = process.env.AVIGILON_TOTP_CODE;
const ORGANIZATION_ID = process.env.AVIGILON_ORG_ID;

if (!ORGANIZATION_ID) {
  throw new Error('AVIGILON_ORG_ID must be set in .env file');
}

function sanitizeObject(obj: unknown): unknown {
  if (Array.isArray(obj)) {
    return obj.map(sanitizeObject);
  }
  if (obj && typeof obj === 'object') {
    const result: Record<string, unknown> = {};
    const sensitiveFields = ['api_key', 'token', 'password', 'secret', 'email', 'phone'];

    for (const [key, value] of Object.entries(obj)) {
      const lowerKey = key.toLowerCase();
      if (sensitiveFields.some((field) => lowerKey.includes(field))) {
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

export function sanitizeResponse(data: unknown): unknown {
  if (!data) return data;
  const sanitized = JSON.parse(JSON.stringify(data));
  return sanitizeObject(sanitized);
}

export function debugLog(operation: string, params: unknown, response?: unknown): void {
  if (process.env.LOG_LEVEL === 'debug') {
    logger.debug(`Operation: ${operation}`);
    if (params) {
      logger.debug(`Params: ${JSON.stringify(params, null, 2)}`);
    }
    if (response) {
      const sanitized = sanitizeResponse(response);
      logger.debug(`Response: ${JSON.stringify(sanitized, null, 2)}`);
    }
  }
}

export async function prepareApi(): Promise<AccessImpl> {
  const access = newAccess();

  if (!EMAIL || !PASSWORD) {
    throw new Error(
      'Integration tests require AVIGILON_EMAIL and AVIGILON_PASSWORD environment variables. '
      + 'Check .env file or copy from .env.example'
    );
  }

  const profile = new ConnectionProfile(new Email(EMAIL), PASSWORD, TOTP_CODE);
  logger.debug('Connecting to Avigilon Alta Access API', { email: EMAIL });

  try {
    await access.connect(profile);
    logger.debug('Successfully connected to Avigilon Alta Access API');
  } catch (error) {
    logger.error('Failed to connect to Avigilon Alta Access API', error);
    throw error;
  }

  return access;
}

export function hasCredentials(): boolean {
  return !!(EMAIL && PASSWORD);
}

export async function saveFixture(filename: string, data: unknown): Promise<void> {
  if (process.env.SAVE_FIXTURES !== 'true') {
    return;
  }

  const sanitized = sanitizeResponse(data);
  const fixtureDir = path.join(__dirname, '../fixtures/integration');

  if (!fs.existsSync(fixtureDir)) {
    fs.mkdirSync(fixtureDir, { recursive: true });
  }

  const filepath = path.join(fixtureDir, filename);
  fs.writeFileSync(filepath, JSON.stringify(sanitized, null, 2));
  logger.debug(`Saved fixture: ${filename}`);
}

export const validateCoreTypes = {
  isEmail: (value: unknown) => expect(value).to.be.instanceof(Email),
  isURL: (value: unknown) => expect(value).to.be.instanceof(URL),
  isUUID: (value: unknown) => expect(value).to.be.instanceof(UUID),
  isIpAddress: (value: unknown) => expect(value).to.be.instanceof(IpAddress),
  isDate: (value: unknown) => expect(value).to.be.instanceof(Date),
};

export const testConfig = {
  timeout: parseInt(process.env.INTEGRATION_TIMEOUT || '30000', 10),
  retries: parseInt(process.env.INTEGRATION_RETRIES || '2', 10),
  delay: parseInt(process.env.INTEGRATION_DELAY || '1000', 10),
  organizationId: ORGANIZATION_ID,
};
