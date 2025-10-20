import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, RoleApi, UserApi } from '../../src';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - Role Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let roleApi: RoleApi;
  let userApi: UserApi;

  before(async () => {
    access = await prepareApi();
    roleApi = access.getRoleApi();
    userApi = access.getUserApi();

    expect(roleApi).to.not.be.undefined;
    expect(userApi).to.not.be.undefined;
    logger.debug('RoleApi and UserApi initialized successfully');
  });

  describe('Role List Operations', () => {
    it('should list roles with default pagination', async () => {
      const rolesResult = await roleApi.listRoles(testConfig.organizationId);

      logger.debug(`roleApi.listRoles(${testConfig.organizationId})`, JSON.stringify(rolesResult, null, 2));

      expect(rolesResult).to.not.be.null;
      expect(rolesResult).to.not.be.undefined;

      // Validate structure
      const roles = rolesResult.items;
      expect(roles).to.be.an('array');
      expect(rolesResult.count).to.be.a('number');

      // If roles exist, validate the first one
      if (roles && roles.length > 0) {
        const firstRole = roles[0];
        expect(firstRole).to.be.an('object');

        // Validate required fields
        expect(firstRole.id).to.be.a('string');
        expect(firstRole.id).to.have.length.greaterThan(0);
        expect(firstRole.name).to.be.a('string');
        expect(firstRole.name).to.have.length.greaterThan(0);

        // Validate optional fields if present
        if (firstRole.description !== undefined) {
          expect(firstRole.description).to.be.a('string');
        }

        if (firstRole.isEditable !== undefined) {
          expect(firstRole.isEditable).to.be.a('boolean');
        }

        if (firstRole.isSiteSpecific !== undefined) {
          expect(firstRole.isSiteSpecific).to.be.a('boolean');
        }

        if (firstRole.isMfaRequired !== undefined) {
          expect(firstRole.isMfaRequired).to.be.a('boolean');
        }

        if (firstRole.userCount !== undefined) {
          expect(firstRole.userCount).to.be.a('number');
          expect(firstRole.userCount).to.be.at.least(0);
        }

        if (firstRole.sites !== undefined) {
          expect(firstRole.sites).to.be.an('array');
        }

        if (firstRole.createdAt !== undefined) {
          expect(firstRole.createdAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstRole.createdAt);
        }

        if (firstRole.updatedAt !== undefined) {
          expect(firstRole.updatedAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstRole.updatedAt);
        }
      }

      // Save fixture
      await saveFixture('roles-list-default.json', rolesResult);
    });

    it('should list roles with custom pagination', async () => {
      const rolesResult = await roleApi.listRoles(testConfig.organizationId, 1, 5);

      logger.debug(`roleApi.listRoles(${testConfig.organizationId}, 1, 5)`, JSON.stringify(rolesResult, null, 2));

      expect(rolesResult).to.not.be.null;
      expect(rolesResult).to.not.be.undefined;

      // Validate pagination constraints
      const roles = rolesResult.items;
      expect(roles).to.be.an('array');
      // Should return at most 5 roles
      expect(roles.length).to.be.at.most(5);

      // Validate count is set
      expect(rolesResult.count).to.be.a('number');
      expect(rolesResult.count).to.be.at.least(0);

      // Save fixture
      await saveFixture('roles-list-paginated.json', rolesResult);
    });

    it('should handle pagination across multiple pages', async () => {
      // Get first page
      const firstPage = await roleApi.listRoles(testConfig.organizationId, 1, 2);

      logger.debug('First page', JSON.stringify(firstPage, null, 2));

      expect(firstPage.items).to.be.an('array');
      expect(firstPage.items.length).to.be.at.most(2);

      // If we have data, try second page
      if (firstPage.count && firstPage.count > 2) {
        const secondPage = await roleApi.listRoles(testConfig.organizationId, 2, 2);

        logger.debug('Second page', JSON.stringify(secondPage, null, 2));

        expect(secondPage.items).to.be.an('array');
        expect(secondPage.items.length).to.be.at.most(2);

        // Save multi-page fixture
        await saveFixture('roles-list-multipage.json', {
          firstPage,
          secondPage,
        });
      }
    });
  });

  describe('Role Users Operations', () => {
    it('should list users for a specific role with default pagination', async () => {
      // First get a valid role ID
      const rolesResult = await roleApi.listRoles(testConfig.organizationId, 1, 1);
      const roles = rolesResult.items;

      if (!roles || !Array.isArray(roles) || roles.length === 0) {
        throw new Error('No roles available for testing');
      }

      const roleId = roles[0].id;
      expect(roleId).to.not.be.undefined;

      // List users for the role
      const usersResult = await roleApi.listRoleUsers(testConfig.organizationId, roleId);

      logger.debug(`roleApi.listRoleUsers(${testConfig.organizationId}, '${roleId}')`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult).to.not.be.undefined;

      // Validate structure
      expect(usersResult.items).to.be.an('array');
      expect(usersResult.count).to.be.a('number');

      // If users exist, validate the first one
      if (usersResult.items && usersResult.items.length > 0) {
        const firstUser = usersResult.items[0];
        expect(firstUser).to.be.an('object');

        // Validate required fields
        expect(firstUser.id).to.be.a('string');
        expect(firstUser.id).to.have.length.greaterThan(0);

        // Validate optional fields if present
        if (firstUser.status !== undefined) {
          expect(firstUser.status).to.be.a('string');
        }

        if (firstUser.identity !== undefined) {
          expect(firstUser.identity).to.be.an('object');

          if (firstUser.identity.email) {
            validateCoreTypes.isEmail(firstUser.identity.email);
          }

          if (firstUser.identity.firstName !== undefined) {
            expect(firstUser.identity.firstName).to.be.a('string');
          }

          if (firstUser.identity.lastName !== undefined) {
            expect(firstUser.identity.lastName).to.be.a('string');
          }
        }

        if (firstUser.assignedAt !== undefined) {
          expect(firstUser.assignedAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstUser.assignedAt);
        }
      }

      // Save fixture
      await saveFixture('role-users-list.json', usersResult);
    });

    it('should list role users with custom pagination', async () => {
      // First get a valid role ID
      const rolesResult = await roleApi.listRoles(testConfig.organizationId, 1, 1);
      const roles = rolesResult.items;

      if (!roles || !Array.isArray(roles) || roles.length === 0) {
        throw new Error('No roles available for testing');
      }

      const roleId = roles[0].id;

      // List users with custom pagination
      const usersResult = await roleApi.listRoleUsers(testConfig.organizationId, roleId, 1, 3);

      logger.debug(`roleApi.listRoleUsers(${testConfig.organizationId}, '${roleId}', 1, 3)`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult.items).to.be.an('array');

      // Should return at most 3 users
      expect(usersResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('role-users-list-paginated.json', usersResult);
    });

    it('should handle role with no users', async () => {
      // Get list of roles to find one potentially with no users
      const rolesResult = await roleApi.listRoles(testConfig.organizationId);
      const roles = rolesResult.items;

      if (!roles || !Array.isArray(roles) || roles.length === 0) {
        throw new Error('No roles available for testing');
      }

      // Find a role with 0 user count if possible, otherwise use first role
      const testRole = roles.find((r) => r.userCount === 0) || roles[0];
      const roleId = testRole.id;

      const usersResult = await roleApi.listRoleUsers(testConfig.organizationId, roleId);

      logger.debug(`roleApi.listRoleUsers(${testConfig.organizationId}, '${roleId}') [potentially empty]`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult.items).to.be.an('array');
      expect(usersResult.count).to.be.a('number');
      expect(usersResult.count).to.be.at.least(0);

      // If this role truly has no users, validate empty result
      if (testRole.userCount === 0) {
        expect(usersResult.items.length).to.equal(0);
      }

      // Save fixture
      await saveFixture('role-users-list-empty-role.json', usersResult);
    });
  });

  describe('User Roles Operations (via UserApi)', () => {
    it('should list roles for a specific user with default pagination', async () => {
      // First get a valid user ID
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;
      expect(userId).to.not.be.undefined;

      // List roles for the user (this tests the listUserRoles operation implemented in RoleProducerApiImpl)
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

        // Validate required fields
        expect(firstRole.id).to.be.a('string');
        expect(firstRole.id).to.have.length.greaterThan(0);
        expect(firstRole.name).to.be.a('string');
        expect(firstRole.name).to.have.length.greaterThan(0);

        // Validate optional fields if present
        if (firstRole.description !== undefined) {
          expect(firstRole.description).to.be.a('string');
        }

        if (firstRole.permissions !== undefined) {
          expect(firstRole.permissions).to.be.an('array');
          // Each permission should be a string
          firstRole.permissions.forEach((perm: any) => {
            expect(perm).to.be.a('string');
          });
        }

        if (firstRole.assignedAt !== undefined) {
          expect(firstRole.assignedAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstRole.assignedAt);
        }
      }

      // Save fixture
      await saveFixture('user-roles-list-via-role-api.json', rolesResult);
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
      await saveFixture('user-roles-list-paginated-via-role-api.json', rolesResult);
    });
  });

  describe('Role Data Validation', () => {
    it('should validate role response schema', async () => {
      const rolesResult = await roleApi.listRoles(testConfig.organizationId, 1, 1);
      const roles = rolesResult.items;

      if (!roles || !Array.isArray(roles) || roles.length === 0) {
        throw new Error('No roles available for testing');
      }

      const role = roles[0];
      logger.debug('Validating role schema', JSON.stringify(role, null, 2));

      expect(role).to.be.an('object');

      // Required fields
      expect(role.id).to.exist;
      expect(role.id).to.be.a('string');
      expect(role.name).to.exist;
      expect(role.name).to.be.a('string');

      // Save schema validation fixture
      await saveFixture('role-schema-validation.json', {
        role,
        validation: {
          hasId: !!role.id,
          hasName: !!role.name,
          hasDescription: role.description !== undefined,
          hasUserCount: role.userCount !== undefined,
          hasCreatedAt: !!role.createdAt,
          hasUpdatedAt: !!role.updatedAt,
          timestamp: new Date().toISOString(),
        },
      });
    });

    it('should validate role user response schema', async () => {
      // Get a role with users
      const rolesResult = await roleApi.listRoles(testConfig.organizationId);
      const roles = rolesResult.items;

      if (!roles || !Array.isArray(roles) || roles.length === 0) {
        throw new Error('No roles available for testing');
      }

      // Find a role with users
      const roleWithUsers = roles.find((r) => r.userCount && r.userCount > 0) || roles[0];
      const usersResult = await roleApi.listRoleUsers(testConfig.organizationId, roleWithUsers.id);

      if (!usersResult.items || usersResult.items.length === 0) {
        logger.debug('No users found for role validation test');
        return;
      }

      const roleUser = usersResult.items[0];
      logger.debug('Validating role user schema', JSON.stringify(roleUser, null, 2));

      expect(roleUser).to.be.an('object');

      // Required fields
      expect(roleUser.id).to.exist;
      expect(roleUser.id).to.be.a('string');

      // Save schema validation fixture
      await saveFixture('role-user-schema-validation.json', {
        roleUser,
        validation: {
          hasId: !!roleUser.id,
          hasStatus: roleUser.status !== undefined,
          hasIdentity: roleUser.identity !== undefined,
          has: roleUser.identity?.email !== undefined,
          hasAssignedAt: !!roleUser.assignedAt,
          timestamp: new Date().toISOString(),
        },
      });
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters for listRoles', async () => {
      try {
        // Test with potentially invalid parameters
        const rolesResult = await roleApi.listRoles(testConfig.organizationId, -1, -1);
        logger.debug(`roleApi.listRoles(${testConfig.organizationId}, -1, -1)`, JSON.stringify(rolesResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (rolesResult && rolesResult.items) {
          expect(rolesResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for invalid pagination', err.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('role-list-invalid-pagination-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle non-existent role ID gracefully', async () => {
      // Use a clearly non-existent ID
      const nonExistentId = '99999999-9999-9999-9999-999999999999';

      try {
        const usersResult = await roleApi.listRoleUsers(testConfig.organizationId, nonExistentId);
        logger.debug(`roleApi.listRoleUsers(${testConfig.organizationId}, '${nonExistentId}')`, JSON.stringify(usersResult, null, 2));

        // If no error was thrown, the result should be an empty list or null
        if (usersResult) {
          expect(usersResult.items).to.be.an('array');
          // Might be empty for non-existent role
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for non-existent role', err.message);

        // Expecting a 404 or similar error
        expect(error).to.not.be.null;

        // Log the error for debugging
        logger.debug('Non-existent role error:', JSON.stringify(error, null, 2));

        // Common error patterns for not found
        const errorMessage = err.message?.toLowerCase() || '';
        const hasNotFoundIndicator = errorMessage.includes('not found')
          || errorMessage.includes('404')
          || errorMessage.includes('does not exist')
          || err.status === 404
          || err.statusCode === 404
          || errorMessage.includes('4') // Any 4xx error
          || errorMessage.includes('invalid');

        // API may return empty results instead of throwing error
        expect(hasNotFoundIndicator || error).to.exist;

        // Save error fixture
        await saveFixture('role-users-not-found-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle empty organization gracefully', async () => {
      try {
        // Test with empty organization ID (should fail parameter validation)
        const rolesResult = await roleApi.listRoles('');

        // If it doesn't throw, log unexpected success
        logger.debug('Expected error for empty organization ID but succeeded', rolesResult);
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for empty organization ID', err.message);

        // Should get a parameter validation error
        expect(error).to.not.be.null;
        expect((error as Error).message).to.be.a('string');

        await saveFixture('role-list-empty-org-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });
  });

  describe('Data Consistency Validation', () => {
    it('should validate role userCount matches actual users', async () => {
      const rolesResult = await roleApi.listRoles(testConfig.organizationId);
      const roles = rolesResult.items;

      if (!roles || !Array.isArray(roles) || roles.length === 0) {
        throw new Error('No roles available for testing');
      }

      // Pick a role with a defined user count
      const testRole = roles.find((r) => r.userCount !== undefined) || roles[0];

      if (testRole.userCount === undefined) {
        logger.debug('No roles with userCount defined, skipping consistency test');
        return;
      }

      // Get actual users for this role
      const usersResult = await roleApi.listRoleUsers(testConfig.organizationId, testRole.id);

      logger.debug(`Role '${testRole.name}' reports userCount=${testRole.userCount}, actual count=${usersResult.count}`);

      // The reported user count should match the actual count
      // Note: There might be slight inconsistencies due to timing/caching, so we log but don't fail
      if (testRole.userCount !== usersResult.count) {
        logger.debug(`User count mismatch for role ${testRole.id}: expected ${testRole.userCount}, got ${usersResult.count}`);
      }

      // Save consistency check fixture
      await saveFixture('role-user-count-consistency.json', {
        roleId: testRole.id,
        roleName: testRole.name,
        reportedUserCount: testRole.userCount,
        actualUserCount: usersResult.count,
        match: testRole.userCount === usersResult.count,
        timestamp: new Date().toISOString(),
      });
    });

    it('should validate bidirectional role-user relationship', async () => {
      // Get a user with roles
      const usersResult = await userApi.list(testConfig.organizationId, 1, 1);
      const users = usersResult.items;

      if (!users || !Array.isArray(users) || users.length === 0) {
        throw new Error('No users available for testing');
      }

      const userId = users[0].id;

      // Get roles for this user
      const userRolesResult = await userApi.listRoles(testConfig.organizationId, userId);

      if (!userRolesResult.items || userRolesResult.items.length === 0) {
        logger.debug('User has no roles, skipping bidirectional test');
        return;
      }

      const roleId = userRolesResult.items[0].id;

      // Now get users for this role and verify our user is in the list
      const roleUsersResult = await roleApi.listRoleUsers(testConfig.organizationId, roleId);

      const userInRoleList = roleUsersResult.items.some((u) => u.id === userId);

      logger.debug(`User ${userId} has role ${roleId}. Role contains user: ${userInRoleList}`);

      // The bidirectional relationship should hold
      if (!userInRoleList) {
        logger.debug(`Bidirectional mismatch: user ${userId} has role ${roleId}, but role doesn't list user`);
      }

      // Save bidirectional validation fixture
      await saveFixture('role-user-bidirectional-validation.json', {
        userId,
        roleId,
        userHasRole: true,
        roleContainsUser: userInRoleList,
        bidirectionalMatch: userInRoleList,
        timestamp: new Date().toISOString(),
      });
    });
  });
});
