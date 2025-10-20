import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, CredentialApi } from '../../src';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - Credential Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let credentialApi: CredentialApi;

  before(async () => {
    access = await prepareApi();
    credentialApi = access.getCredentialApi();

    expect(credentialApi).to.not.be.undefined;
    logger.debug('CredentialApi initialized successfully');
  });

  describe('Card Formats Operations', () => {
    it('should list card formats with default pagination', async () => {
      const cardFormatsResult = await credentialApi.listCardFormats(testConfig.organizationId);

      logger.debug(`credentialApi.listCardFormats(${testConfig.organizationId})`, JSON.stringify(cardFormatsResult, null, 2));

      expect(cardFormatsResult).to.not.be.null;
      expect(cardFormatsResult).to.not.be.undefined;

      // Validate structure
      const cardFormats = cardFormatsResult.items;
      if (cardFormats && Array.isArray(cardFormats)) {
        expect(cardFormats).to.be.an('array');

        // If card formats exist, validate the first one
        if (cardFormats.length > 0) {
          const firstCardFormat = cardFormats[0];
          expect(firstCardFormat).to.be.an('object');

          // Validate required fields
          expect(firstCardFormat.id).to.be.a('string');
          expect(firstCardFormat.id).to.have.length.greaterThan(0);

          // Validate optional fields if present
          if (firstCardFormat.name) {
            expect(firstCardFormat.name).to.be.a('string');
          }

          if (firstCardFormat.code) {
            expect(firstCardFormat.code).to.be.a('string');
          }

          if (firstCardFormat.description) {
            // description can be either string or object
            expect(firstCardFormat.description).to.satisfy((desc: any) => typeof desc === 'string' || typeof desc === 'object');
          }

          if (firstCardFormat.bitLength !== undefined) {
            expect(firstCardFormat.bitLength).to.be.a('number');
            expect(firstCardFormat.bitLength).to.be.greaterThan(0);
          }

          if (firstCardFormat.facilityCodeStart !== undefined) {
            expect(firstCardFormat.facilityCodeStart).to.be.a('number');
          }

          if (firstCardFormat.facilityCodeLength !== undefined) {
            expect(firstCardFormat.facilityCodeLength).to.be.a('number');
          }

          if (firstCardFormat.cardNumberStart !== undefined) {
            expect(firstCardFormat.cardNumberStart).to.be.a('number');
          }

          if (firstCardFormat.cardNumberLength !== undefined) {
            expect(firstCardFormat.cardNumberLength).to.be.a('number');
          }
        }
      }

      // Save fixture
      await saveFixture('card-formats-list-default.json', cardFormatsResult);
    });

    it('should list card formats with custom pagination', async () => {
      const cardFormatsResult = await credentialApi.listCardFormats(testConfig.organizationId, 1, 5);

      logger.debug(`credentialApi.listCardFormats(${testConfig.organizationId}, 1, 5)`, JSON.stringify(cardFormatsResult, null, 2));

      expect(cardFormatsResult).to.not.be.null;
      expect(cardFormatsResult).to.not.be.undefined;

      // Validate pagination constraints
      const cardFormats = cardFormatsResult.items;
      if (cardFormats && Array.isArray(cardFormats)) {
        expect(cardFormats).to.be.an('array');
        // Should return at most 5 card formats
        expect(cardFormats.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('card-formats-list-paginated.json', cardFormatsResult);
    });
  });

  describe('Credential Types Operations', () => {
    it('should list credential types with default pagination', async () => {
      const credentialTypesResult = await credentialApi.listCredentialTypes(testConfig.organizationId);

      logger.debug(`credentialApi.listCredentialTypes(${testConfig.organizationId})`, JSON.stringify(credentialTypesResult, null, 2));

      expect(credentialTypesResult).to.not.be.null;
      expect(credentialTypesResult).to.not.be.undefined;

      // Validate structure
      const credentialTypes = credentialTypesResult.items;
      if (credentialTypes && Array.isArray(credentialTypes)) {
        expect(credentialTypes).to.be.an('array');

        // If credential types exist, validate the first one
        if (credentialTypes.length > 0) {
          const firstCredentialType = credentialTypes[0];
          expect(firstCredentialType).to.be.an('object');

          // Validate required fields
          expect(firstCredentialType.id).to.be.a('string');
          expect(firstCredentialType.id).to.have.length.greaterThan(0);

          // Validate optional fields if present
          if (firstCredentialType.name) {
            expect(firstCredentialType.name).to.be.a('string');
          }

          if (firstCredentialType.description) {
            expect(firstCredentialType.description).to.be.a('string');
          }

          if (firstCredentialType.modelName) {
            expect(firstCredentialType.modelName).to.be.a('string');
          }
        }
      }

      // Save fixture
      await saveFixture('credential-types-list-default.json', credentialTypesResult);
    });

    it('should list credential types with custom pagination', async () => {
      const credentialTypesResult = await credentialApi.listCredentialTypes(testConfig.organizationId, 1, 3);

      logger.debug(`credentialApi.listCredentialTypes(${testConfig.organizationId}, 1, 3)`, JSON.stringify(credentialTypesResult, null, 2));

      expect(credentialTypesResult).to.not.be.null;
      expect(credentialTypesResult).to.not.be.undefined;

      // Validate pagination constraints
      const credentialTypes = credentialTypesResult.items;
      if (credentialTypes && Array.isArray(credentialTypes)) {
        expect(credentialTypes).to.be.an('array');
        // Should return at most 3 credential types
        expect(credentialTypes.length).to.be.at.most(3);
      }

      // Save fixture
      await saveFixture('credential-types-list-paginated.json', credentialTypesResult);
    });
  });

  describe('Credential Action Types Operations', () => {
    it('should list credential action types with default pagination', async () => {
      const actionTypesResult = await credentialApi.listCredentialActionTypes(testConfig.organizationId);

      logger.debug(`credentialApi.listCredentialActionTypes(${testConfig.organizationId})`, JSON.stringify(actionTypesResult, null, 2));

      expect(actionTypesResult).to.not.be.null;
      expect(actionTypesResult).to.not.be.undefined;

      // Validate structure
      const actionTypes = actionTypesResult.items;
      if (actionTypes && Array.isArray(actionTypes)) {
        expect(actionTypes).to.be.an('array');

        // If action types exist, validate the first one
        if (actionTypes.length > 0) {
          const firstActionType = actionTypes[0];
          expect(firstActionType).to.be.an('object');

          // Validate required fields
          expect(firstActionType.id).to.be.a('string');
          expect(firstActionType.id).to.have.length.greaterThan(0);

          // Validate optional fields if present
          if (firstActionType.name) {
            expect(firstActionType.name).to.be.a('string');
          }

          if (firstActionType.description) {
            expect(firstActionType.description).to.be.a('string');
          }
        }
      }

      // Save fixture
      await saveFixture('credential-action-types-list-default.json', actionTypesResult);
    });

    it('should list credential action types with custom pagination', async () => {
      const actionTypesResult = await credentialApi.listCredentialActionTypes(testConfig.organizationId, 1, 3);

      logger.debug(`credentialApi.listCredentialActionTypes(${testConfig.organizationId}, 1, 3)`, JSON.stringify(actionTypesResult, null, 2));

      expect(actionTypesResult).to.not.be.null;
      expect(actionTypesResult).to.not.be.undefined;

      // Validate pagination constraints
      const actionTypes = actionTypesResult.items;
      if (actionTypes && Array.isArray(actionTypes)) {
        expect(actionTypes).to.be.an('array');
        // Should return at most 3 action types
        expect(actionTypes.length).to.be.at.most(3);
      }

      // Save fixture
      await saveFixture('credential-action-types-list-paginated.json', actionTypesResult);
    });
  });

  describe('Organization Credentials Operations', () => {
    it('should list organization credentials with default pagination', async () => {
      const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId);

      logger.debug(`credentialApi.listOrgCredentials(${testConfig.organizationId})`, JSON.stringify(credentialsResult, null, 2));

      expect(credentialsResult).to.not.be.null;
      expect(credentialsResult).to.not.be.undefined;

      // Validate structure
      const credentials = credentialsResult.items;
      if (credentials && Array.isArray(credentials)) {
        expect(credentials).to.be.an('array');

        // If credentials exist, validate the first one
        if (credentials.length > 0) {
          const firstCredential = credentials[0];
          expect(firstCredential).to.be.an('object');

          // Validate required fields
          expect(firstCredential.id).to.be.a('string');
          expect(firstCredential.id).to.have.length.greaterThan(0);

          // Validate optional fields if present
          if (firstCredential.userId) {
            expect(firstCredential.userId).to.be.a('string');
          }

          if (firstCredential.user) {
            expect(firstCredential.user).to.be.an('object');
          }

          if (firstCredential.credentialType) {
            expect(firstCredential.credentialType).to.be.an('object');
            expect(firstCredential.credentialType.id).to.be.a('string');
          }

          if (firstCredential.startDate) {
            expect(firstCredential.startDate).to.be.instanceOf(Date);
          }

          if (firstCredential.endDate) {
            expect(firstCredential.endDate).to.be.instanceOf(Date);
          }

          if (firstCredential.mobile) {
            expect(firstCredential.mobile).to.be.an('object');
          }

          if (firstCredential.card) {
            expect(firstCredential.card).to.be.an('object');
          }

          if (firstCredential.createdAt) {
            expect(firstCredential.createdAt).to.be.instanceOf(Date);
            validateCoreTypes.isDate(firstCredential.createdAt);
          }

          if (firstCredential.updatedAt) {
            expect(firstCredential.updatedAt).to.be.instanceOf(Date);
            validateCoreTypes.isDate(firstCredential.updatedAt);
          }
        }
      }

      // Save fixture
      await saveFixture('org-credentials-list-default.json', credentialsResult);
    });

    it('should list organization credentials with custom pagination', async () => {
      const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId, 1, 5);

      logger.debug(`credentialApi.listOrgCredentials(${testConfig.organizationId}, 1, 5)`, JSON.stringify(credentialsResult, null, 2));

      expect(credentialsResult).to.not.be.null;
      expect(credentialsResult).to.not.be.undefined;

      // Validate pagination constraints
      const credentials = credentialsResult.items;
      if (credentials && Array.isArray(credentials)) {
        expect(credentials).to.be.an('array');
        // Should return at most 5 credentials
        expect(credentials.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('org-credentials-list-paginated.json', credentialsResult);
    });
  });

  describe('Credential Actions Operations', () => {
    it('should list credential actions with default pagination', async function () {
      // First get a valid credential ID from the org credentials list
      const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId, 1, 1);
      const credentials = credentialsResult.items;

      if (!credentials || !Array.isArray(credentials) || credentials.length === 0) {
        logger.warn('No credentials available for testing credential actions');
        this.skip();
        return;
      }

      const credentialId = credentials[0].id;
      expect(credentialId).to.not.be.undefined;

      // List actions for the credential
      const actionsResult = await credentialApi.listCredentialActions(
        testConfig.organizationId,
        credentialId
      );

      logger.debug(`credentialApi.listCredentialActions(${testConfig.organizationId}, '${credentialId}')`, JSON.stringify(actionsResult, null, 2));

      expect(actionsResult).to.not.be.null;
      expect(actionsResult).to.not.be.undefined;

      // Validate structure
      expect(actionsResult.items).to.be.an('array');
      expect(actionsResult.count).to.be.a('number');

      // If actions exist, validate the first one
      if (actionsResult.items && actionsResult.items.length > 0) {
        const firstAction = actionsResult.items[0];
        expect(firstAction).to.be.an('object');

        // Validate required fields
        expect(firstAction.id).to.be.a('string');
        expect(firstAction.id).to.have.length.greaterThan(0);

        // Validate optional fields if present
        if (firstAction.credentialId) {
          expect(firstAction.credentialId).to.be.a('string');
        }

        if (firstAction.credentialActionType) {
          expect(firstAction.credentialActionType).to.be.an('object');
          expect(firstAction.credentialActionType.id).to.be.a('string');
        }

        if (firstAction.performedBy) {
          expect(firstAction.performedBy).to.be.an('object');
        }

        if (firstAction.performedAt) {
          expect(firstAction.performedAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstAction.performedAt);
        }

        if (firstAction.details) {
          expect(firstAction.details).to.be.an('object');
        }
      }

      // Save fixture
      await saveFixture('credential-actions-list-default.json', actionsResult);
    });

    it('should list credential actions with custom pagination', async function () {
      // First get a valid credential ID
      const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId, 1, 1);
      const credentials = credentialsResult.items;

      if (!credentials || !Array.isArray(credentials) || credentials.length === 0) {
        logger.warn('No credentials available for testing credential actions');
        this.skip();
        return;
      }

      const credentialId = credentials[0].id;

      // List actions with custom pagination
      const actionsResult = await credentialApi.listCredentialActions(
        testConfig.organizationId,
        credentialId,
        1,
        3
      );

      logger.debug(
        `credentialApi.listCredentialActions(${testConfig.organizationId}, '${credentialId}', 1, 3)`,
        JSON.stringify(actionsResult, null, 2)
      );

      expect(actionsResult).to.not.be.null;
      expect(actionsResult.items).to.be.an('array');

      // Should return at most 3 actions
      expect(actionsResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('credential-actions-list-paginated.json', actionsResult);
    });
  });

  describe('Data Validation and Mapping', () => {
    it('should validate credential response schema and mapping', async function () {
      const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId, 1, 1);
      const credentials = credentialsResult.items;

      if (!credentials || !Array.isArray(credentials) || credentials.length === 0) {
        logger.warn('No credentials available for schema validation');
        this.skip();
        return;
      }

      const credential = credentials[0];
      logger.debug('Validating credential schema', JSON.stringify(credential, null, 2));

      expect(credential).to.be.an('object');

      // Validate that required fields are present and properly mapped
      expect(credential.id).to.be.a('string');
      expect(credential.id).to.have.length.greaterThan(0);

      // Save schema validation fixture
      await saveFixture('credential-schema-validation.json', {
        credential,
        validation: {
          hasId: !!credential.id,
          hasUserId: !!credential.userId,
          hasCredentialType: !!credential.credentialType,
          hasCreatedAt: !!credential.createdAt,
          hasUpdatedAt: !!credential.updatedAt,
          timestamp: new Date().toISOString(),
        },
      });
    });

    it('should validate card format response schema and mapping', async function () {
      const cardFormatsResult = await credentialApi.listCardFormats(testConfig.organizationId, 1, 1);
      const cardFormats = cardFormatsResult.items;

      if (!cardFormats || !Array.isArray(cardFormats) || cardFormats.length === 0) {
        logger.warn('No card formats available for schema validation');
        this.skip();
        return;
      }

      const cardFormat = cardFormats[0];
      logger.debug('Validating card format schema', JSON.stringify(cardFormat, null, 2));

      expect(cardFormat).to.be.an('object');
      expect(cardFormat.id).to.be.a('string');

      // Save schema validation fixture
      await saveFixture('card-format-schema-validation.json', {
        cardFormat,
        validation: {
          hasId: !!cardFormat.id,
          hasName: !!cardFormat.name,
          hasBitLength: cardFormat.bitLength !== undefined,
          timestamp: new Date().toISOString(),
        },
      });
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', async () => {
      try {
        // Test with potentially invalid parameters
        const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId, -1, -1);
        logger.debug(`credentialApi.listOrgCredentials(${testConfig.organizationId}, -1, -1)`, JSON.stringify(credentialsResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (credentialsResult && credentialsResult.items) {
          expect(credentialsResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for invalid pagination', err.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('credential-list-invalid-pagination-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle non-existent credential ID for actions', async () => {
      // Use a clearly non-existent ID
      const nonExistentId = '99999999';

      try {
        const actionsResult = await credentialApi.listCredentialActions(
          testConfig.organizationId,
          nonExistentId
        );
        logger.debug(`credentialApi.listCredentialActions(${testConfig.organizationId}, '${nonExistentId}')`, JSON.stringify(actionsResult, null, 2));

        // If no error was thrown, should return empty array or specific result
        if (actionsResult && actionsResult.items) {
          expect(actionsResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for non-existent credential', err.message);

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
        await saveFixture('credential-actions-not-found-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle empty organization credentials list gracefully', async () => {
      const credentialsResult = await credentialApi.listOrgCredentials(testConfig.organizationId);

      logger.debug('Testing empty credentials list handling');

      expect(credentialsResult).to.not.be.null;
      expect(credentialsResult.items).to.be.an('array');

      // Count should be a number (0 or positive)
      expect(credentialsResult.count).to.be.a('number');
      expect(credentialsResult.count).to.be.at.least(0);
    });
  });

  describe('Pagination Functionality', () => {
    it('should properly handle pagination metadata for card formats', async () => {
      const page1Result = await credentialApi.listCardFormats(testConfig.organizationId, 1, 2);

      logger.debug('Pagination metadata test', JSON.stringify({
        count: page1Result.count,
        itemsLength: page1Result.items.length,
        pageNumber: page1Result.pageNumber,
        pageSize: page1Result.pageSize,
      }, null, 2));

      expect(page1Result.count).to.be.a('number');
      expect(page1Result.pageNumber).to.equal(1);
      expect(page1Result.pageSize).to.equal(2);

      if (page1Result.count !== undefined && page1Result.count > 2) {
        // If there are more than 2 items, test second page
        const page2Result = await credentialApi.listCardFormats(testConfig.organizationId, 2, 2);

        expect(page2Result.pageNumber).to.equal(2);
        expect(page2Result.pageSize).to.equal(2);

        // Save pagination test fixture
        await saveFixture('card-formats-pagination-test.json', {
          page1: page1Result,
          page2: page2Result,
        });
      }
    });

    it('should properly handle pagination metadata for credentials', async () => {
      const page1Result = await credentialApi.listOrgCredentials(testConfig.organizationId, 1, 2);

      logger.debug('Credentials pagination metadata test', JSON.stringify({
        count: page1Result.count,
        itemsLength: page1Result.items.length,
        pageNumber: page1Result.pageNumber,
        pageSize: page1Result.pageSize,
      }, null, 2));

      expect(page1Result.count).to.be.a('number');
      expect(page1Result.pageNumber).to.equal(1);
      expect(page1Result.pageSize).to.equal(2);

      if (page1Result.count !== undefined && page1Result.count > 2) {
        // If there are more than 2 items, test second page
        const page2Result = await credentialApi.listOrgCredentials(testConfig.organizationId, 2, 2);

        expect(page2Result.pageNumber).to.equal(2);
        expect(page2Result.pageSize).to.equal(2);

        // Items on page 2 should be different from page 1
        if (page1Result.items.length > 0 && page2Result.items.length > 0) {
          expect(page1Result.items[0].id).to.not.equal(page2Result.items[0].id);
        }

        // Save pagination test fixture
        await saveFixture('credentials-pagination-test.json', {
          page1: page1Result,
          page2: page2Result,
        });
      }
    });
  });
});
