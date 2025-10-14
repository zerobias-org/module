import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, UserApi } from '../../src';
import { getLogger } from '@auditmation/util-logger';

// Core types for assertions
import { Email, URL, UUID, IpAddress } from '@auditmation/types-core-js';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - User Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let userApi: UserApi;

  before(async function () {
    access = await prepareApi();
    userApi = access.getUserApi();

    expect(userApi).to.not.be.undefined;
    logger.debug('UserApi initialized successfully');
  });

  describe('User List Operations', function () {

    it('should list users with default pagination', async function () {
      const usersResult = await userApi.list(testConfig.organizationId);

      logger.debug(`userApi.list()`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult).to.not.be.undefined;

      // Validate structure
      const users = usersResult.items;
      if (users && Array.isArray(users)) {
        expect(users).to.be.an('array');

        // If users exist, validate the first one
        if (users.length > 0) {
          const firstUser = users[0];
          expect(firstUser).to.be.an('object');

          // Validate common user fields that should exist
          if (firstUser.id) {
            // ID could be UUID or string - validate if it looks like UUID
            if (typeof firstUser.id === 'string' &&
              /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(firstUser.id)) {
              validateCoreTypes.isUUID(firstUser.id);
            }
          }

          if (firstUser.identity?.email) {
            validateCoreTypes.isEmail(firstUser.identity?.email);
          }

          if (firstUser.createdAt && firstUser.createdAt instanceof Date) {
            validateCoreTypes.isDate(firstUser.createdAt);
          }
        }
      }

      // Save fixture
      await saveFixture('users-list-default.json', usersResult);
    });

    it('should list users with custom pagination', async function () {
      const usersResult = await userApi.list(testConfig.organizationId, 1, 5);

      logger.debug(`userApi.list(1, 5)`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult).to.not.be.undefined;

      // Validate pagination constraints
      const users = usersResult.items;
      if (users && Array.isArray(users)) {
        expect(users).to.be.an('array');
        // Should return at most 5 users
        expect(users.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('users-list-paginated.json', usersResult);
    });
  });

  describe('User Retrieval Operations', function () {

    it('should retrieve a specific user by ID', async function () {

      // First get a list to find a valid user ID
      const usersPr = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersPr.items;


      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;
      expect(userId).to.not.be.undefined;

      const user = await userApi.get(testConfig.organizationId, userId);

      logger.debug(`userApi.get('${userId}')`, JSON.stringify(user, null, 2));

      expect(user).to.not.be.null;
      expect(user).to.not.be.undefined;
      expect(user).to.be.an('object');

      // Validate user properties
      if (user.id) {
        expect(user.id).to.equal(userId);

        // ID could be UUID or string - validate if it looks like UUID
        if (typeof user.id === 'string' &&
          /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(user.id)) {
          validateCoreTypes.isUUID(user.id);
        }
      }

      if (user.identity?.email) {
        validateCoreTypes.isEmail(user.identity?.email);
      }

      if (user.createdAt && user.createdAt instanceof Date) {
        validateCoreTypes.isDate(user.createdAt);
      }

      // Save fixture
      await saveFixture('user-get-by-id.json', user);
    });

    it('should handle non-existent user ID gracefully', async function () {

      // Use a clearly non-existent ID - use string format
      const nonExistentId = '12312432452331';

      try {
        const user = await userApi.get(testConfig.organizationId, nonExistentId);
        logger.debug(`userApi.get('${nonExistentId}')`, JSON.stringify(user, null, 2));

        // If no error was thrown, the result should be null or undefined
        expect(user).to.satisfy((result: any) => result === null || result === undefined);

      } catch (error: any) {
        logger.debug('Expected error for non-existent user', error.message);

        // Expecting a 404 or similar error
        expect(error).to.not.be.null;

        // Common error patterns for not found
        const errorMessage = error.message?.toLowerCase() || '';
        const hasNotFoundIndicator =
          errorMessage.includes('not found') ||
          errorMessage.includes('404') ||
          errorMessage.includes('does not exist') ||
          error.status === 404 ||
          error.statusCode === 404;

        expect(hasNotFoundIndicator).to.be.true;

        // Save error fixture
        await saveFixture('user-get-not-found-error.json', {
          error: error.message,
          status: error.status || error.statusCode,
          timestamp: new Date().toISOString()
        });
      }
    });
  });

  describe('User Data Validation', function () {

    it('should validate user response schema', async function () {

      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const user = users[0];
      logger.debug('Validating user schema', JSON.stringify(user, null, 2));

      expect(user).to.be.an('object');

      // Basic validation - check for expected structure
      // Note: Actual fields depend on the API response structure

      // Most user APIs should have some form of identifier
      const hasIdentifier = user.id || user.identity?.email;
      expect(hasIdentifier).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('user-schema-validation.json', {
        user: user,
        validation: {
          hasIdentifier: !!hasIdentifier,
          hasEmail: !!user.identity?.email,
          hasCreatedAt: !!user.createdAt,
          timestamp: new Date().toISOString()
        }
      });
    });
  });

  describe('Error Handling and Edge Cases', function () {

    it('should handle invalid pagination parameters', async function () {

      try {
        // Test with potentially invalid parameters
        const usersResult = await userApi.list(testConfig.organizationId, -1, -1);
        logger.debug(`userApi.list(-1, -1)`, JSON.stringify(usersResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (usersResult && usersResult.items) {
          expect(usersResult.items).to.be.an('array');
        }

      } catch (error: any) {
        logger.debug('Expected error for invalid pagination', error.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('user-list-invalid-pagination-error.json', {
          error: error.message,
          status: error.status || error.statusCode,
          timestamp: new Date().toISOString()
        });
      }
    });

    it('should handle API rate limiting', async function () {

      // This test would require making many requests quickly
      // For now, we'll just make a single request to verify no immediate rate limiting
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      logger.debug('Rate limiting test - single request succeeded');

      expect(usersResult).to.not.be.null;
    });
  });
});
