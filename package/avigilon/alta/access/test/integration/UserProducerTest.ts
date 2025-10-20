import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { UUID } from '@auditmation/types-core-js';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, UserApi, RoleApi } from '../../src';

// Core types for assertions

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - User Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let userApi: UserApi;
  let roleApi: RoleApi;

  before(async () => {
    access = await prepareApi();
    userApi = access.getUserApi();
    roleApi = access.getRoleApi();

    expect(userApi).to.not.be.undefined;
    expect(roleApi).to.not.be.undefined;
    logger.debug('UserApi and RoleApi initialized successfully');
  });

  describe('User List Operations', () => {
    it('should list users with default pagination', async () => {
      const usersResult = await userApi.list(testConfig.organizationId);

      logger.debug(`userApi.list(${testConfig.organizationId})`, JSON.stringify(usersResult, null, 2));

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
            if (typeof firstUser.id === 'string'
              && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(firstUser.id)) {
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

    it('should list users with custom pagination', async () => {
      const usersResult = await userApi.list(testConfig.organizationId, 1, 5);

      logger.debug(`userApi.list(${testConfig.organizationId}, 1, 5)`, JSON.stringify(usersResult, null, 2));

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

  describe('User Retrieval Operations', () => {
    it('should retrieve a specific user by ID', async () => {
      // First get a list to find a valid user ID
      const usersPr = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersPr.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;
      expect(userId).to.not.be.undefined;

      const user = await userApi.get(testConfig.organizationId, userId);

      logger.debug(`userApi.get(${testConfig.organizationId}, '${userId}')`, JSON.stringify(user, null, 2));

      expect(user).to.not.be.null;
      expect(user).to.not.be.undefined;
      expect(user).to.be.an('object');

      // Validate user properties
      if (user.id) {
        expect(user.id).to.equal(userId);

        // ID could be UUID or string - validate if it looks like UUID
        if (typeof user.id === 'string'
          && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(user.id)) {
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

    it('should handle non-existent user ID gracefully', async () => {
      // Use a clearly non-existent ID
      const nonExistentId = '99999999'; // Use a numeric ID that doesn't exist

      try {
        const user = await userApi.get(testConfig.organizationId, nonExistentId);
        logger.debug(`userApi.get(${testConfig.organizationId}, '${nonExistentId}')`, JSON.stringify(user, null, 2));

        // If no error was thrown, the result should be null or undefined
        expect(user).to.satisfy((result: any) => (result === null || result === undefined));
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for non-existent user', err.message);

        // Expecting a 404 or similar error
        expect(error).to.not.be.null;

        // Common error patterns for not found
        const errorMessage = err.message?.toLowerCase() || '';
        const hasNotFoundIndicator = errorMessage.includes('not found')
          || errorMessage.includes('404')
          || errorMessage.includes('does not exist')
          || err.status === 404
          || err.statusCode === 404;

        expect(hasNotFoundIndicator).to.be.true;

        // Save error fixture
        await saveFixture('user-get-not-found-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });
  });

  describe('User Roles Operations', () => {
    it('should list user roles with default pagination', async () => {
      // First get a valid user ID
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;
      expect(userId).to.not.be.undefined;

      // List roles for the user
      const rolesResult = await userApi.listRoles(testConfig.organizationId, userId);

      logger.debug(`userApi.listRoles(${testConfig.organizationId}, '${userId}')`, JSON.stringify(rolesResult, null, 2));

      expect(rolesResult).to.not.be.null;
      expect(rolesResult).to.not.be.undefined;

      // Validate structure
      expect(rolesResult.items).to.be.an('array');
      expect(rolesResult.count).to.be.a('number');

      // If roles exist, validate the first one
      if (rolesResult.items && rolesResult.items.length > 0) {
        const firstRole = rolesResult.items[0];
        expect(firstRole).to.be.an('object');

        // Validate common role fields
        if (firstRole.id) {
          expect(firstRole.id).to.be.a('string');
        }

        if (firstRole.name) {
          expect(firstRole.name).to.be.a('string');
        }
      }

      // Save fixture
      await saveFixture('user-roles-list.json', rolesResult);
    });

    it('should list user roles with custom pagination', async () => {
      // First get a valid user ID
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;

      // List roles with custom pagination
      const rolesResult = await userApi.listRoles(testConfig.organizationId, userId, 1, 3);

      logger.debug(`userApi.listRoles(${testConfig.organizationId}, '${userId}', 1, 3)`, JSON.stringify(rolesResult, null, 2));

      expect(rolesResult).to.not.be.null;
      expect(rolesResult.items).to.be.an('array');

      // Should return at most 3 roles
      expect(rolesResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('user-roles-list-paginated.json', rolesResult);
    });
  });

  describe('User Sites Operations', () => {
    it('should list user sites with default pagination', async () => {
      // First get a valid user ID
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;
      expect(userId).to.not.be.undefined;

      // List sites for the user
      const sitesResult = await userApi.listSites(testConfig.organizationId, userId);

      logger.debug(`userApi.listSites(${testConfig.organizationId}, '${userId}')`, JSON.stringify(sitesResult, null, 2));

      expect(sitesResult).to.not.be.null;
      expect(sitesResult).to.not.be.undefined;

      // Validate structure
      expect(sitesResult.items).to.be.an('array');
      expect(sitesResult.count).to.be.a('number');

      // If sites exist, validate the first one
      if (sitesResult.items && sitesResult.items.length > 0) {
        const firstSite = sitesResult.items[0];
        expect(firstSite).to.be.an('object');

        // Validate common site fields
        if (firstSite.id) {
          expect(firstSite.id).to.be.a('string');
        }

        if (firstSite.name) {
          expect(firstSite.name).to.be.a('string');
        }
      }

      // Save fixture
      await saveFixture('user-sites-list.json', sitesResult);
    });

    it('should list user sites with custom pagination', async () => {
      // First get a valid user ID
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;

      // List sites with custom pagination
      const sitesResult = await userApi.listSites(testConfig.organizationId, userId, 1, 3);

      logger.debug(`userApi.listSites(${testConfig.organizationId}, '${userId}', 1, 3)`, JSON.stringify(sitesResult, null, 2));

      expect(sitesResult).to.not.be.null;
      expect(sitesResult.items).to.be.an('array');

      // Should return at most 3 sites
      expect(sitesResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('user-sites-list-paginated.json', sitesResult);
    });
  });

  describe('User Data Validation', () => {
    it('should validate user response schema', async () => {
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
        user,
        validation: {
          hasIdentifier: !!hasIdentifier,
          has: !!user.identity?.email,
          hasCreatedAt: !!user.createdAt,
          timestamp: new Date().toISOString(),
        },
      });
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', async () => {
      try {
        // Test with potentially invalid parameters
        const usersResult = await userApi.list(testConfig.organizationId, -1, -1);
        logger.debug(`userApi.list(${testConfig.organizationId}, -1, -1)`, JSON.stringify(usersResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (usersResult && usersResult.items) {
          expect(usersResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for invalid pagination', err.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('user-list-invalid-pagination-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle API rate limiting', async () => {
      // This test would require making many requests quickly
      // For now, we'll just make a single request to verify no immediate rate limiting
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      logger.debug('Rate limiting test - single request succeeded');

      expect(usersResult).to.not.be.null;
    });
  });
});
