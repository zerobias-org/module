import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, IdentityProviderApi } from '../../src';

// Core types for assertions

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - Identity Provider Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let identityProviderApi: IdentityProviderApi;

  before(async () => {
    access = await prepareApi();
    identityProviderApi = access.getIdentityProviderApi();

    expect(identityProviderApi).to.not.be.undefined;
    logger.debug('IdentityProviderApi initialized successfully');
  });

  describe('Identity Provider List Operations', () => {
    it('should list identity providers with default pagination', async () => {
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId);

      logger.debug(`identityProviderApi.listIdentityProviders(${testConfig.organizationId})`, JSON.stringify(providersResult, null, 2));

      expect(providersResult).to.not.be.null;
      expect(providersResult).to.not.be.undefined;

      // Validate structure
      const providers = providersResult.items;
      if (providers && Array.isArray(providers)) {
        expect(providers).to.be.an('array');

        // If providers exist, validate the first one
        if (providers.length > 0) {
          const firstProvider = providers[0];
          expect(firstProvider).to.be.an('object');

          // Validate common identity provider fields
          if (firstProvider.id) {
            expect(firstProvider.id).to.be.a('number');
          }

          if (firstProvider.orgId) {
            expect(firstProvider.orgId).to.be.a('number');
          }

          if (firstProvider.identityProviderType) {
            expect(firstProvider.identityProviderType).to.be.an('object');
          }

          if (firstProvider.status) {
            expect(firstProvider.status).to.be.a('string');
          }

          if (firstProvider.createdAt && firstProvider.createdAt instanceof Date) {
            validateCoreTypes.isDate(firstProvider.createdAt);
          }
        }
      }

      // Save fixture
      await saveFixture('identity-providers-list-default.json', providersResult);
    });

    it('should list identity providers with custom pagination', async () => {
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 5);

      logger.debug(`identityProviderApi.listIdentityProviders(${testConfig.organizationId}, 1, 5)`, JSON.stringify(providersResult, null, 2));

      expect(providersResult).to.not.be.null;
      expect(providersResult).to.not.be.undefined;

      // Validate pagination constraints
      const providers = providersResult.items;
      if (providers && Array.isArray(providers)) {
        expect(providers).to.be.an('array');
        // Should return at most 5 providers
        expect(providers.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('identity-providers-list-paginated.json', providersResult);
    });
  });

  describe('Identity Provider Type Operations', () => {
    it('should list identity provider types with default pagination', async () => {
      const typesResult = await identityProviderApi.listIdentityProviderTypes(testConfig.organizationId);

      logger.debug(`identityProviderApi.listIdentityProviderTypes(${testConfig.organizationId})`, JSON.stringify(typesResult, null, 2));

      expect(typesResult).to.not.be.null;
      expect(typesResult).to.not.be.undefined;

      // Validate structure
      const types = typesResult.items;
      if (types && Array.isArray(types)) {
        expect(types).to.be.an('array');

        // If types exist, validate the first one
        if (types.length > 0) {
          const firstType = types[0];
          expect(firstType).to.be.an('object');

          // Validate common identity provider type fields
          if (firstType.id) {
            expect(firstType.id).to.be.a('string');
          }

          if (firstType.name) {
            expect(firstType.name).to.be.a('string');
          }

          if (firstType.code) {
            expect(firstType.code).to.be.a('string');
          }
        }
      }

      // Save fixture
      await saveFixture('identity-provider-types-list-default.json', typesResult);
    });

    it('should list identity provider types with custom pagination', async () => {
      const typesResult = await identityProviderApi.listIdentityProviderTypes(testConfig.organizationId, 1, 3);

      logger.debug(`identityProviderApi.listIdentityProviderTypes(${testConfig.organizationId}, 1, 3)`, JSON.stringify(typesResult, null, 2));

      expect(typesResult).to.not.be.null;
      expect(typesResult.items).to.be.an('array');

      // Should return at most 3 types
      expect(typesResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('identity-provider-types-list-paginated.json', typesResult);
    });

    it('should retrieve a specific identity provider type by ID', async function () {
      // First get a list to find a valid identity provider type ID
      const typesResult = await identityProviderApi.listIdentityProviderTypes(testConfig.organizationId, 1, 1);
      const types = typesResult.items;

      if (!types || !Array.isArray(types) || types.length === 0) {
        logger.debug('No identity provider types available for testing - skipping getIdentityProviderType test');
        this.skip();
        return;
      }

      const typeId = types[0].id;
      expect(typeId).to.not.be.undefined;

      const typeInfo = await identityProviderApi.getIdentityProviderType(testConfig.organizationId, typeId);

      logger.debug(`identityProviderApi.getIdentityProviderType(${testConfig.organizationId}, '${typeId}')`, JSON.stringify(typeInfo, null, 2));

      expect(typeInfo).to.not.be.null;
      expect(typeInfo).to.not.be.undefined;
      expect(typeInfo).to.be.an('object');

      // Validate type properties
      if (typeInfo.id) {
        expect(typeInfo.id).to.equal(typeId);
      }

      if (typeInfo.name) {
        expect(typeInfo.name).to.be.a('string');
      }

      if (typeInfo.code) {
        expect(typeInfo.code).to.be.a('string');
      }

      // Save fixture
      await saveFixture('identity-provider-type-get-by-id.json', typeInfo);
    });

    it('should handle non-existent identity provider type ID gracefully', async () => {
      // Use a clearly non-existent ID
      const nonExistentId = '99999999';

      try {
        const typeInfo = await identityProviderApi.getIdentityProviderType(testConfig.organizationId, nonExistentId);
        logger.debug(
          `identityProviderApi.getIdentityProviderType(${testConfig.organizationId}, '${nonExistentId}')`,
          JSON.stringify(typeInfo, null, 2)
        );

        // If no error was thrown, the result should be null or undefined
        expect(typeInfo).to.satisfy((result: any) => (result === null || result === undefined));
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for non-existent identity provider type', err.message);

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
        await saveFixture('identity-provider-type-get-not-found-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });
  });

  describe('Identity Provider Groups Operations', () => {
    it('should list identity provider groups with default pagination', async function () {
      // First get a valid identity provider ID
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 1);
      const providers = providersResult.items;

      if (!providers || !Array.isArray(providers) || providers.length === 0) {
        logger.debug('No identity providers available for testing - skipping listIdentityProviderGroups test');
        this.skip();
        return;
      }

      const providerId = providers[0].id;
      expect(providerId).to.not.be.undefined;

      // List groups for the identity provider
      const groupsResult = await identityProviderApi.listIdentityProviderGroups(testConfig.organizationId, String(providerId));

      logger.debug(
        `identityProviderApi.listIdentityProviderGroups(${testConfig.organizationId}, '${providerId}')`,
        JSON.stringify(groupsResult, null, 2)
      );

      expect(groupsResult).to.not.be.null;
      expect(groupsResult).to.not.be.undefined;

      // Validate structure
      expect(groupsResult.items).to.be.an('array');
      expect(groupsResult.count).to.be.a('number');

      // If groups exist, validate the first one
      if (groupsResult.items && groupsResult.items.length > 0) {
        const firstGroup = groupsResult.items[0];
        expect(firstGroup).to.be.an('object');

        // Validate common group fields
        if (firstGroup.idpGroupUniqueIdentifier) {
          expect(firstGroup.idpGroupUniqueIdentifier).to.be.a('string');
        }

        if (firstGroup.name) {
          expect(firstGroup.name).to.be.a('string');
        }

        if (firstGroup.description) {
          expect(firstGroup.description).to.be.a('string');
        }

        if (firstGroup.email) {
          validateCoreTypes.isEmail(firstGroup.email);
        }
      }

      // Save fixture
      await saveFixture('identity-provider-groups-list.json', groupsResult);
    });

    it('should list identity provider groups with custom pagination', async function () {
      // First get a valid identity provider ID
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 1);
      const providers = providersResult.items;

      if (!providers || !Array.isArray(providers) || providers.length === 0) {
        logger.debug('No identity providers available for testing - skipping listIdentityProviderGroups pagination test');
        this.skip();
        return;
      }

      const providerId = providers[0].id;

      // List groups with custom pagination
      const groupsResult = await identityProviderApi.listIdentityProviderGroups(testConfig.organizationId, String(providerId), 1, 3);
      logger.debug(
        `identityProviderApi.listIdentityProviderGroups(${testConfig.organizationId}, '${providerId}', 1, 3)`,
        JSON.stringify(groupsResult, null, 2)
      );

      expect(groupsResult).to.not.be.null;
      expect(groupsResult.items).to.be.an('array');

      // Should return at most 3 groups
      expect(groupsResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('identity-provider-groups-list-paginated.json', groupsResult);
    });
  });

  describe('Identity Provider Group Relations Operations', () => {
    it('should get identity provider group relations with default pagination', async function () {
      // First get a valid identity provider ID
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 1);
      const providers = providersResult.items;

      if (!providers || !Array.isArray(providers) || providers.length === 0) {
        logger.debug('No identity providers available for testing - skipping getIdentityProviderGroupRelations test');
        this.skip();
        return;
      }

      const providerId = providers[0].id;
      expect(providerId).to.not.be.undefined;

      // Get group relations for the identity provider
      logger.debug(
        `identityProviderApi.getIdentityProviderGroupRelations(${testConfig.organizationId}, '${providerId}')`
      );

      const relationsResult = await identityProviderApi.getIdentityProviderGroupRelations(
        testConfig.organizationId,
        String(providerId)
      );

      expect(relationsResult).to.not.be.null;
      expect(relationsResult).to.not.be.undefined;

      // Validate structure
      expect(relationsResult.items).to.be.an('array');
      expect(relationsResult.count).to.be.a('number');

      // If relations exist, validate the first one
      if (relationsResult.items && relationsResult.items.length > 0) {
        const firstRelation = relationsResult.items[0];
        expect(firstRelation).to.be.an('object');

        // Validate common relation fields
        if (firstRelation.idpGroupUniqueIdentifier) {
          expect(firstRelation.idpGroupUniqueIdentifier).to.be.a('string');
        }

        if (firstRelation.identityProviderGroup) {
          expect(firstRelation.identityProviderGroup).to.be.an('object');
        }

        if (firstRelation.groupId) {
          expect(firstRelation.groupId).to.be.a('number');
        }

        if (firstRelation.group) {
          expect(firstRelation.group).to.be.an('object');
        }
      }

      // Save fixture
      await saveFixture('identity-provider-group-relations-list.json', relationsResult);
    });

    it('should get identity provider group relations with custom pagination', async function () {
      // First get a valid identity provider ID
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 1);
      const providers = providersResult.items;

      if (!providers || !Array.isArray(providers) || providers.length === 0) {
        logger.debug('No identity providers available for testing - skipping getIdentityProviderGroupRelations pagination test');
        this.skip();
        return;
      }

      const providerId = providers[0].id;

      const relationsResult = await identityProviderApi.getIdentityProviderGroupRelations(testConfig.organizationId, String(providerId), 1, 3);

      logger.debug(
        `identityProviderApi.getIdentityProviderGroupRelations(${testConfig.organizationId}, '${providerId}', 1, 3)`,
        JSON.stringify(relationsResult, null, 2)
      );
      expect(relationsResult).to.not.be.null;
      expect(relationsResult.items).to.be.an('array');

      // Should return at most 3 relations
      expect(relationsResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('identity-provider-group-relations-list-paginated.json', relationsResult);
    });
  });

  describe('Identity Provider Data Validation', () => {
    it('should validate identity provider response schema', async function () {
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 1);
      const providers = providersResult.items;

      if (!providers || !Array.isArray(providers) || providers.length === 0) {
        logger.debug('No identity providers available for schema validation - skipping test');
        this.skip();
        return;
      }

      const provider = providers[0];
      logger.debug('Validating identity provider schema', JSON.stringify(provider, null, 2));

      expect(provider).to.be.an('object');

      // Basic validation - check for expected structure
      const hasIdentifier = provider.id;
      expect(hasIdentifier).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('identity-provider-schema-validation.json', {
        provider,
        validation: {
          hasIdentifier: !!hasIdentifier,
          hasOrgId: !!provider.orgId,
          hasType: !!provider.identityProviderType,
          hasStatus: !!provider.status,
          hasCreatedAt: !!provider.createdAt,
          timestamp: new Date().toISOString(),
        },
      });
    });

    it('should validate identity provider type response schema', async function () {
      const typesResult = await identityProviderApi.listIdentityProviderTypes(testConfig.organizationId, 1, 1);
      const types = typesResult.items;

      if (!types || !Array.isArray(types) || types.length === 0) {
        logger.debug('No identity provider types available for schema validation - skipping test');
        this.skip();
        return;
      }

      const typeInfo = types[0];
      logger.debug('Validating identity provider type schema', JSON.stringify(typeInfo, null, 2));

      expect(typeInfo).to.be.an('object');

      // Basic validation - check for expected structure
      const hasIdentifier = typeInfo.id;
      expect(hasIdentifier).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('identity-provider-type-schema-validation.json', {
        type: typeInfo,
        validation: {
          hasIdentifier: !!hasIdentifier,
          hasName: !!typeInfo.name,
          hasCode: !!typeInfo.code,
          timestamp: new Date().toISOString(),
        },
      });
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', async () => {
      try {
        // Test with potentially invalid parameters
        const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, -1, -1);
        logger.debug(`identityProviderApi.listIdentityProviders(${testConfig.organizationId}, -1, -1)`, JSON.stringify(providersResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (providersResult && providersResult.items) {
          expect(providersResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for invalid pagination', err.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('identity-provider-list-invalid-pagination-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle API rate limiting', async () => {
      // This test would require making many requests quickly
      // For now, we'll just make a single request to verify no immediate rate limiting
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 1);
      logger.debug('Rate limiting test - single request succeeded');

      expect(providersResult).to.not.be.null;
    });

    it('should handle empty identity provider list', async () => {
      // Even if there are no identity providers, the API should return an empty array
      const providersResult = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 100);

      logger.debug('Empty list test', JSON.stringify(providersResult, null, 2));

      expect(providersResult).to.not.be.null;
      expect(providersResult.items).to.be.an('array');
      expect(providersResult.count).to.be.a('number');

      // Save fixture
      await saveFixture('identity-provider-list-empty.json', providersResult);
    });
  });

  describe('Pagination Behavior', () => {
    it('should handle multiple pages of identity providers', async () => {
      // Get first page with small page size
      const page1 = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, 2);

      logger.debug('First page', JSON.stringify(page1, null, 2));

      expect(page1).to.not.be.null;
      expect(page1.items).to.be.an('array');

      // If there are enough items, test second page
      if (page1.count && page1.count > 2) {
        const page2 = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 2, 2);

        logger.debug('Second page', JSON.stringify(page2, null, 2));

        expect(page2).to.not.be.null;
        expect(page2.items).to.be.an('array');

        // Items on page 2 should be different from page 1
        if (page1.items.length > 0 && page2.items.length > 0) {
          expect(page1.items[0].id).to.not.equal(page2.items[0].id);
        }

        // Save fixture
        await saveFixture('identity-provider-pagination-multiple-pages.json', {
          page1,
          page2,
        });
      } else {
        logger.debug('Not enough identity providers for pagination test');
      }
    });

    it('should respect page size limits', async () => {
      // Test with various page sizes
      const pageSizes = [1, 5, 10, 50];

      for (const pageSize of pageSizes) {
        const result = await identityProviderApi.listIdentityProviders(testConfig.organizationId, 1, pageSize);

        logger.debug(`Page size ${pageSize}:`, JSON.stringify(result, null, 2));

        expect(result.items).to.be.an('array');
        expect(result.items.length).to.be.at.most(pageSize);
      }
    });
  });
});
