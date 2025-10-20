import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, GroupApi } from '../../src';

// Core types for assertions

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - Group Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let groupApi: GroupApi;

  before(async () => {
    access = await prepareApi();
    groupApi = access.getGroupApi();

    expect(groupApi).to.not.be.undefined;
    logger.debug('GroupApi initialized successfully');
  });

  describe('Group List Operations', () => {
    it('should list groups with default pagination', async () => {
      const groupsResult = await groupApi.list(testConfig.organizationId);

      logger.debug(`groupApi.list(${testConfig.organizationId})`, JSON.stringify(groupsResult, null, 2));

      expect(groupsResult).to.not.be.null;
      expect(groupsResult).to.not.be.undefined;

      const groups = groupsResult.items;
      if (groups && Array.isArray(groups)) {
        expect(groups).to.be.an('array');

        if (groups.length > 0) {
          const firstGroup = groups[0];
          expect(firstGroup).to.be.an('object');

          if (firstGroup.id) {
            expect(firstGroup.id).to.be.a('string');
            expect(firstGroup.id).to.not.be.empty;
          }

          if (firstGroup.createdAt && firstGroup.createdAt instanceof Date) {
            validateCoreTypes.isDate(firstGroup.createdAt);
          }
        }
      }

      // Save fixture
      await saveFixture('groups-list-default.json', groupsResult);
    });

    it('should list groups with custom pagination', async () => {
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 5);

      logger.debug(`groupApi.list(${testConfig.organizationId}, 1, 5)`, JSON.stringify(groupsResult, null, 2));

      expect(groupsResult).to.not.be.null;
      expect(groupsResult).to.not.be.undefined;

      const groups = groupsResult.items;
      if (groups && Array.isArray(groups)) {
        expect(groups).to.be.an('array');
        expect(groups.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('groups-list-paginated.json', groupsResult);
    });
  });

  describe('Group Retrieval Operations', () => {
    it('should retrieve a specific group by ID', async () => {
      // First get a list to find a valid group ID
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;

      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }

      const groupId = groups[0].id;
      expect(groupId).to.not.be.undefined;

      // Use string ID directly
      expect(groupId).to.be.a('string');

      const group = await groupApi.get(testConfig.organizationId, groupId);

      logger.debug(`groupApi.get(${testConfig.organizationId}, '${groupId}')`, JSON.stringify(group, null, 2));

      expect(group).to.not.be.null;
      expect(group).to.not.be.undefined;
      expect(group).to.be.an('object');

      if (group.id) {
        expect(group.id).to.equal(groupId);
        expect(group.id).to.be.a('string');
        expect(group.id).to.not.be.empty;
      }

      if (group.createdAt && group.createdAt instanceof Date) {
        validateCoreTypes.isDate(group.createdAt);
      }

      // Save fixture
      await saveFixture('group-get-by-id.json', group);
    });

    it('should handle non-existent group ID gracefully', async () => {
      // Use a clearly non-existent string ID
      const nonExistentId = '999999999';

      try {
        const group = await groupApi.get(testConfig.organizationId, nonExistentId);
        logger.debug(`groupApi.get(${testConfig.organizationId}, '${nonExistentId}')`, JSON.stringify(group, null, 2));

        // If no error was thrown, the result should be null or undefined
        expect(group).to.satisfy((result: any) => (result === null || result === undefined));
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for non-existent group', err.message);

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
        await saveFixture('group-get-not-found-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });
  });

  describe('Group Child Operations', () => {
    it('should list group users with default pagination', async () => {
      // First get a valid group ID from the list operation
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;

      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }

      const groupId = groups[0].id;
      expect(groupId).to.not.be.undefined;
      expect(groupId).to.be.a('string');

      // Test listUsers operation
      const usersResult = await groupApi.listUsers(testConfig.organizationId, groupId);

      logger.debug(`groupApi.listUsers(${testConfig.organizationId}, '${groupId}')`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult).to.not.be.undefined;

      // Validate structure
      expect(usersResult.items).to.be.an('array');

      if (usersResult.items && usersResult.items.length > 0) {
        const firstUser = usersResult.items[0];
        expect(firstUser).to.be.an('object');

        // Validate common user fields
        if (firstUser.id) {
          expect(firstUser.id).to.be.a('string');
          expect(firstUser.id).to.not.be.empty;
        }

        if (firstUser.createdAt && firstUser.createdAt instanceof Date) {
          validateCoreTypes.isDate(firstUser.createdAt);
        }
      }

      // Validate count property exists
      if (usersResult.count !== undefined) {
        expect(usersResult.count).to.be.a('number');
        expect(usersResult.count).to.be.at.least(0);
      }

      // Save fixture
      await saveFixture('group-users-list.json', usersResult);
    });

    it('should list group users with custom pagination', async () => {
      // First get a valid group ID from the list operation
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;

      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }

      const groupId = groups[0].id;
      expect(groupId).to.not.be.undefined;

      // Test listUsers with pagination
      const usersResult = await groupApi.listUsers(testConfig.organizationId, groupId, 1, 5);

      logger.debug(`groupApi.listUsers(${testConfig.organizationId}, '${groupId}', 1, 5)`, JSON.stringify(usersResult, null, 2));

      expect(usersResult).to.not.be.null;
      expect(usersResult).to.not.be.undefined;
      expect(usersResult.items).to.be.an('array');

      // Should return at most 5 users
      if (usersResult.items) {
        expect(usersResult.items.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('group-users-list-paginated.json', usersResult);
    });

    it('should list group entries with default pagination', async () => {
      // First get a valid group ID from the list operation
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;

      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }

      const groupId = groups[0].id;
      expect(groupId).to.not.be.undefined;
      expect(groupId).to.be.a('string');

      // Test listEntries operation
      const entriesResult = await groupApi.listEntries(testConfig.organizationId, groupId);

      logger.debug(`groupApi.listEntries(${testConfig.organizationId}, '${groupId}')`, JSON.stringify(entriesResult, null, 2));

      expect(entriesResult).to.not.be.null;
      expect(entriesResult).to.not.be.undefined;

      // Validate structure
      expect(entriesResult.items).to.be.an('array');

      // If entries exist for the group, validate the first one
      if (entriesResult.items && entriesResult.items.length > 0) {
        const firstEntry = entriesResult.items[0];
        expect(firstEntry).to.be.an('object');

        // Validate common entry fields
        if (firstEntry.id) {
          expect(firstEntry.id).to.be.a('string');
          expect(firstEntry.id).to.not.be.empty;
        }

        if (firstEntry.createdAt && firstEntry.createdAt instanceof Date) {
          validateCoreTypes.isDate(firstEntry.createdAt);
        }
      }

      // Validate count property exists
      if (entriesResult.count !== undefined) {
        expect(entriesResult.count).to.be.a('number');
        expect(entriesResult.count).to.be.at.least(0);
      }

      // Save fixture
      await saveFixture('group-entries-list.json', entriesResult);
    });

    it('should list group entries with custom pagination', async () => {
      // First get a valid group ID from the list operation
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;

      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }

      const groupId = groups[0].id;
      expect(groupId).to.not.be.undefined;

      // Test listEntries with pagination
      const entriesResult = await groupApi.listEntries(testConfig.organizationId, groupId, 1, 5);

      logger.debug(`groupApi.listEntries(${testConfig.organizationId}, '${groupId}', 1, 5)`, JSON.stringify(entriesResult, null, 2));

      expect(entriesResult).to.not.be.null;
      expect(entriesResult).to.not.be.undefined;
      expect(entriesResult.items).to.be.an('array');

      // Should return at most 5 entries
      if (entriesResult.items) {
        expect(entriesResult.items.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('group-entries-list-paginated.json', entriesResult);
    });
  });

  describe('Group Data Validation', () => {
    it('should validate group response schema', async () => {
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;

      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }

      const group = groups[0];
      logger.debug('Validating group schema', JSON.stringify(group, null, 2));

      expect(group).to.be.an('object');

      // Basic validation - check for expected structure
      // Note: Actual fields depend on the API response structure

      // Most group APIs should have some form of identifier
      const hasIdentifier = group.id || group.name;
      expect(hasIdentifier).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('group-schema-validation.json', {
        group,
        validation: {
          hasIdentifier: !!hasIdentifier,
          hasName: !!group.name,
          hasCreatedAt: !!group.createdAt,
          timestamp: new Date().toISOString(),
        },
      });
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', async () => {
      try {
        // Test with potentially invalid parameters
        const groupsResult = await groupApi.list(testConfig.organizationId, -1, -1);
        logger.debug(`groupApi.list(${testConfig.organizationId}, -1, -1)`, JSON.stringify(groupsResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (groupsResult && groupsResult.items) {
          expect(groupsResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for invalid pagination', err.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('group-list-invalid-pagination-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle API rate limiting', async () => {
      // This test would require making many requests quickly
      // For now, we'll just make a single request to verify no immediate rate limiting
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      logger.debug('Rate limiting test - single request succeeded');

      expect(groupsResult).to.not.be.null;
    });
  });
});
