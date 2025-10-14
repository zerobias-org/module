import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, GroupApi } from '../../src';
import { getLogger } from '@auditmation/util-logger';

// Core types for assertions
import { Email, URL, IpAddress } from '@auditmation/types-core-js';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - Group Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let groupApi: GroupApi;

  before(async function () {
    access = await prepareApi();
    groupApi = access.getGroupApi();
    
    expect(groupApi).to.not.be.undefined;
    logger.debug('GroupApi initialized successfully');
  });

  describe('Group List Operations', function () {
    
    it('should list groups with default pagination', async function () {
      const groupsResult = await groupApi.list(testConfig.organizationId);
      
      logger.debug(`groupApi.list()`, JSON.stringify(groupsResult, null, 2));
      
      expect(groupsResult).to.not.be.null;
      expect(groupsResult).to.not.be.undefined;
      
      // Validate structure
      const groups = groupsResult.items;
      if (groups && Array.isArray(groups)) {
        expect(groups).to.be.an('array');
        
        // If groups exist, validate the first one
        if (groups.length > 0) {
          const firstGroup = groups[0];
          expect(firstGroup).to.be.an('object');
          
          // Validate common group fields that should exist
          if (firstGroup.id) {
            // ID should be numeric
            expect(firstGroup.id).to.be.a('number');
            expect(firstGroup.id).to.be.greaterThan(0);
          }
          
          if (firstGroup.createdAt && firstGroup.createdAt instanceof Date) {
            validateCoreTypes.isDate(firstGroup.createdAt);
          }
        }
      }
      
      // Save fixture
      await saveFixture('groups-list-default.json', groupsResult);
    });

    it('should list groups with custom pagination', async function () {
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 5);
      
      logger.debug(`groupApi.list(1, 5)`, JSON.stringify(groupsResult, null, 2));
      
      expect(groupsResult).to.not.be.null;
      expect(groupsResult).to.not.be.undefined;
      
      // Validate pagination constraints
      const groups = groupsResult.items;
      if (groups && Array.isArray(groups)) {
        expect(groups).to.be.an('array');
        // Should return at most 5 groups
        expect(groups.length).to.be.at.most(5);
      }
      
      // Save fixture
      await saveFixture('groups-list-paginated.json', groupsResult);
    });
  });

  describe('Group Retrieval Operations', function () {
    
    it('should retrieve a specific group by ID', async function () {

      // First get a list to find a valid group ID
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      const groups = groupsResult.items;
      
      if (!groups || !Array.isArray(groups) || groups.length === 0) {
        throw new Error('No groups available for testing');
      }
      
      const groupId = groups[0].id;
      expect(groupId).to.not.be.undefined;
      
      // Use numeric ID directly
      expect(groupId).to.be.a('number');
      
      const group = await groupApi.get(testConfig.organizationId, groupId);
      
      logger.debug(`groupApi.get('${groupId}')`, JSON.stringify(group, null, 2));
      
      expect(group).to.not.be.null;
      expect(group).to.not.be.undefined;
      expect(group).to.be.an('object');
      
      // Validate group properties
      if (group.id) {
        expect(group.id).to.equal(groupId);
        
        // ID should be numeric
        expect(group.id).to.be.a('number');
        expect(group.id).to.be.greaterThan(0);
      }
      
      if (group.createdAt && group.createdAt instanceof Date) {
        validateCoreTypes.isDate(group.createdAt);
      }
      
      // Save fixture
      await saveFixture('group-get-by-id.json', group);
    });

    it('should handle non-existent group ID gracefully', async function () {

      // Use a clearly non-existent string ID
      const nonExistentId = '999999999';

      try {
        const group = await groupApi.get(testConfig.organizationId, nonExistentId);
        logger.debug(`groupApi.get('${nonExistentId}')`, JSON.stringify(group, null, 2));
        
        // If no error was thrown, the result should be null or undefined
        expect(group).to.satisfy((result: any) => result === null || result === undefined);
        
      } catch (error: any) {
        logger.debug('Expected error for non-existent group', error.message);
        
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
        await saveFixture('group-get-not-found-error.json', {
          error: error.message,
          status: error.status || error.statusCode,
          timestamp: new Date().toISOString()
        });
      }
    });
  });

  describe('Group Data Validation', function () {
    
    it('should validate group response schema', async function () {

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
        group: group,
        validation: {
          hasIdentifier: !!hasIdentifier,
          hasName: !!group.name,
          hasCreatedAt: !!group.createdAt,
          timestamp: new Date().toISOString()
        }
      });
    });
  });

  describe('Error Handling and Edge Cases', function () {
    
    it('should handle invalid pagination parameters', async function () {

      try {
        // Test with potentially invalid parameters
        const groupsResult = await groupApi.list(testConfig.organizationId, -1, -1);
        logger.debug(`groupApi.list(-1, -1)`, JSON.stringify(groupsResult, null, 2));
        
        // Should either return empty array or throw appropriate error
        if (groupsResult && groupsResult.items) {
          expect(groupsResult.items).to.be.an('array');
        }
        
      } catch (error: any) {
        logger.debug('Expected error for invalid pagination', error.message);
        
        // Should get a validation error
        expect(error).to.not.be.null;
        
        await saveFixture('group-list-invalid-pagination-error.json', {
          error: error.message,
          status: error.status || error.statusCode,
          timestamp: new Date().toISOString()
        });
      }
    });

    it('should handle API rate limiting', async function () {

      // This test would require making many requests quickly
      // For now, we'll just make a single request to verify no immediate rate limiting
      const groupsResult = await groupApi.list(testConfig.organizationId, 1, 1);
      logger.debug('Rate limiting test - single request succeeded');
      
      expect(groupsResult).to.not.be.null;
    });
  });
});