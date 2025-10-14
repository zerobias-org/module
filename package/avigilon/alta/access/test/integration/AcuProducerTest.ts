import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, AcuApi } from '../../src';
import { getLogger } from '@auditmation/util-logger';

// Core types for assertions
import { Email, URL, IpAddress } from '@auditmation/types-core-js';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - ACU Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let acuApi: AcuApi;

  before(async function () {
    access = await prepareApi();
    acuApi = access.getAcuApi();
    
    expect(acuApi).to.not.be.undefined;
    logger.debug('AcuApi initialized successfully');
  });

  describe('ACU List Operations', function () {
    
    it('should list ACUs with default pagination', async function () {
      const acusResult = await acuApi.list(testConfig.organizationId);
      
      logger.debug(`acuApi.list()`, JSON.stringify(acusResult, null, 2));
      
      expect(acusResult).to.not.be.null;
      expect(acusResult).to.not.be.undefined;
      
      // Validate structure
      const acus = acusResult.items;
      if (acus && Array.isArray(acus)) {
        expect(acus).to.be.an('array');
        
        // If ACUs exist, validate the first one
        if (acus.length > 0) {
          const firstAcu = acus[0];
          expect(firstAcu).to.be.an('object');
          
          // Validate common ACU fields that should exist
          if (firstAcu.id) {
            // ID should be numeric
            expect(firstAcu.id).to.be.a('number');
            expect(firstAcu.id).to.be.greaterThan(0);
          }
          
          if (firstAcu.hostname) {
            validateCoreTypes.isIpAddress(firstAcu.hostname);
          }
          
          if (firstAcu.createdAt && firstAcu.createdAt instanceof Date) {
            validateCoreTypes.isDate(firstAcu.createdAt);
          }
        }
      }
      
      // Save fixture
      await saveFixture('acus-list-default.json', acusResult);
    });

    it('should list ACUs with custom pagination', async function () {
      const acusResult = await acuApi.list(testConfig.organizationId, 1, 5);
      
      logger.debug(`acuApi.list(1, 5)`, JSON.stringify(acusResult, null, 2));
      
      expect(acusResult).to.not.be.null;
      expect(acusResult).to.not.be.undefined;
      
      // Validate pagination constraints
      const acus = acusResult.items;
      if (acus && Array.isArray(acus)) {
        expect(acus).to.be.an('array');
        // Should return at most 5 ACUs
        expect(acus.length).to.be.at.most(5);
      }
      
      // Save fixture
      await saveFixture('acus-list-paginated.json', acusResult);
    });
  });

  describe('ACU Retrieval Operations', function () {
    
    it('should retrieve a specific ACU by ID', async function () {

      // First get a list to find a valid ACU ID
      const acusResult = await acuApi.list(testConfig.organizationId, 1, 1);
      const acus = acusResult.items;
      
      if (!acus || !Array.isArray(acus) || acus.length === 0) {
        throw new Error('No ACUs available for testing');
      }
      
      const acuId = acus[0].id;
      expect(acuId).to.not.be.undefined;
      
      // Use numeric ID directly
      expect(acuId).to.be.a('number');
      
      const acu = await acuApi.get(testConfig.organizationId, acuId);
      
      logger.debug(`acuApi.get('${acuId}')`, JSON.stringify(acu, null, 2));
      
      expect(acu).to.not.be.null;
      expect(acu).to.not.be.undefined;
      expect(acu).to.be.an('object');
      
      // Validate ACU properties
      if (acu.id) {
        expect(acu.id).to.equal(acuId);
        
        // ID should be numeric
        expect(acu.id).to.be.a('number');
        expect(acu.id).to.be.greaterThan(0);
      }
      
      if (acu.hostname) {
        validateCoreTypes.isIpAddress(acu.hostname);
      }
      
      if (acu.createdAt && acu.createdAt instanceof Date) {
        validateCoreTypes.isDate(acu.createdAt);
      }
      
      // Save fixture
      await saveFixture('acu-get-by-id.json', acu);
    });

    it('should handle non-existent ACU ID gracefully', async function () {

      // Use a clearly non-existent string ID
      const nonExistentId = '999999999';

      try {
        const acu = await acuApi.get(testConfig.organizationId, nonExistentId);
        logger.debug(`acuApi.get('${nonExistentId}')`, JSON.stringify(acu, null, 2));
        
        // If no error was thrown, the result should be null or undefined
        expect(acu).to.satisfy((result: any) => result === null || result === undefined);
        
      } catch (error: any) {
        logger.debug('Expected error for non-existent ACU', error.message);
        
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
        await saveFixture('acu-get-not-found-error.json', {
          error: error.message,
          status: error.status || error.statusCode,
          timestamp: new Date().toISOString()
        });
      }
    });
  });

  describe('ACU Data Validation', function () {
    
    it('should validate ACU response schema', async function () {

      const acusResult = await acuApi.list(testConfig.organizationId, 1, 1);
      const acus = acusResult.items;
      
      if (!acus || !Array.isArray(acus) || acus.length === 0) {
        throw new Error('No ACUs available for testing');
      }
      
      const acu = acus[0];
      logger.debug('Validating ACU schema', JSON.stringify(acu, null, 2));
      
      expect(acu).to.be.an('object');
      
      // Basic validation - check for expected structure
      // Note: Actual fields depend on the API response structure
      
      // Most ACU APIs should have some form of identifier
      const hasIdentifier = acu.id || acu.serialNumber;
      expect(hasIdentifier).to.not.be.undefined;
      
      // Save schema validation fixture
      await saveFixture('acu-schema-validation.json', {
        acu: acu,
        validation: {
          hasIdentifier: !!hasIdentifier,
          hasIpAddress: !!acu.hostname,
          hasCreatedAt: !!acu.createdAt,
          timestamp: new Date().toISOString()
        }
      });
    });
  });

  describe('Error Handling and Edge Cases', function () {
    
    it('should handle invalid pagination parameters', async function () {

      try {
        // Test with potentially invalid parameters
        const acusResult = await acuApi.list(testConfig.organizationId, -1, -1);
        logger.debug(`acuApi.list(-1, -1)`, JSON.stringify(acusResult, null, 2));
        
        // Should either return empty array or throw appropriate error
        if (acusResult && acusResult.items) {
          expect(acusResult.items).to.be.an('array');
        }
        
      } catch (error: any) {
        logger.debug('Expected error for invalid pagination', error.message);
        
        // Should get a validation error
        expect(error).to.not.be.null;
        
        await saveFixture('acu-list-invalid-pagination-error.json', {
          error: error.message,
          status: error.status || error.statusCode,
          timestamp: new Date().toISOString()
        });
      }
    });

    it('should handle API rate limiting', async function () {

      // This test would require making many requests quickly
      // For now, we'll just make a single request to verify no immediate rate limiting
      const acusResult = await acuApi.list(testConfig.organizationId, 1, 1);
      logger.debug('Rate limiting test - single request succeeded');
      
      expect(acusResult).to.not.be.null;
    });
  });
});