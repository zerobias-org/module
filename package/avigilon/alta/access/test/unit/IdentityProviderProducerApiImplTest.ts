import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  NoSuchObjectError,
  UnexpectedError,
  InvalidCredentialsError
} from '@auditmation/types-core-js';
import { IdentityProviderProducerApiImpl } from '../../src/IdentityProviderProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import {
  IdentityProvider,
  IdentityProviderType,
  IdentityProviderTypeInfo,
  IdentityProviderGroup,
  IdentityProviderGroupRelation
} from '../../generated/model';
import {
  mockAuthenticatedRequest,
  cleanNock
} from '../utils/nock-helpers';

describe('IdentityProviderProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: IdentityProviderProducerApiImpl;
  const baseUrl = 'https://helium.prod.openpath.com';
  const testEmail = process.env.AVIGILON_EMAIL || 'test@example.com';
  const testPassword = process.env.AVIGILON_PASSWORD || 'testpass';
  const orgId = 'test-org-123';

  beforeEach(async () => {
    client = new AvigilonAltaAccessClient();
    const profile: ConnectionProfile = {
      email: new Email(testEmail),
      password: testPassword,
    };

    // Mock login endpoint
    nock(baseUrl)
      .post('/auth/login')
      .reply(200, {
        data: {
          token: 'mock-token-123',
          expiresAt: new Date(Date.now() + 3600000).toISOString(),
        },
      });

    await client.connect(profile);

    producer = new IdentityProviderProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create IdentityProviderProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(IdentityProviderProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new IdentityProviderProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('listIdentityProviders()', () => {
    it('should list identity providers with default pagination', async () => {
      const rawApiProviders = [
        {
          id: 'idp-1',
          orgId,
          identityProviderType: {
            id: '1',
            name: 'Azure AD',
            code: 'azure',
          },
          isSyncUsersEnabled: true,
          isMobileCredentialEnabled: false,
          isSsoEnabled: true,
          isMobileSsoEnabled: false,
          status: 'active',
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-02T00:00:00.000Z',
        },
        {
          id: 'idp-2',
          orgId,
          identityProviderType: {
            id: '2',
            name: 'Okta',
            code: 'okta',
          },
          isSyncUsersEnabled: false,
          isSsoEnabled: false,
          status: 'active',
          createdAt: '2024-01-05T00:00:00.000Z',
          updatedAt: '2024-01-06T00:00:00.000Z',
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: rawApiProviders,
          totalCount: rawApiProviders.length,
        });

      const results = new PagedResults<IdentityProvider>();
      await producer.listIdentityProviders(results, orgId);

      expect(results.items).to.have.length(2);
      expect(results.count).to.equal(2);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('orgId');
      expect(results.items[0].identityProviderType).to.have.property('name');

      scope.done();
    });

    it('should list identity providers with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiProviders = [
        {
          id: 'idp-3',
          orgId,
          identityProviderType: {
            id: '3',
            name: 'Google',
            code: 'google',
          },
          status: 'active',
          createdAt: '2024-01-10T00:00:00.000Z',
          updatedAt: '2024-01-11T00:00:00.000Z',
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, {
          data: rawApiProviders,
          totalCount: rawApiProviders.length,
        });

      const results = new PagedResults<IdentityProvider>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listIdentityProviders(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty list response', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: [],
          totalCount: 0,
        });

      const results = new PagedResults<IdentityProvider>();
      await producer.listIdentityProviders(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should throw UnexpectedError when response data is not an array', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: 'invalid-data',
          totalCount: 1,
        });

      const results = new PagedResults<IdentityProvider>();

      try {
        await producer.listIdentityProviders(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(429, {
          error: 'Rate limit exceeded',
          statusCode: 429,
        });

      const results = new PagedResults<IdentityProvider>();

      try {
        await producer.listIdentityProviders(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listIdentityProviderTypes()', () => {
    it('should list identity provider types with default pagination', async () => {
      const rawApiTypes = [
        {
          id: '1',
          name: 'Azure AD',
          code: 'azure',
          featureCode: 'azure_sso',
          supportsIdpInitiatedSso: true,
          authStrategyTypes: [
            { id: 'auth-1', name: 'SAML', code: 'saml' },
          ],
        },
        {
          id: '2',
          name: 'Okta',
          code: 'okta',
          featureCode: 'okta_sso',
          supportsIdpInitiatedSso: false,
          authStrategyTypes: [
            { id: 'auth-2', name: 'OAuth', code: 'oauth' },
          ],
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: rawApiTypes,
          totalCount: rawApiTypes.length,
        });

      const results = new PagedResults<IdentityProviderType>();
      await producer.listIdentityProviderTypes(results, orgId);

      expect(results.items).to.have.length(2);
      expect(results.count).to.equal(2);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');
      expect(results.items[0]).to.have.property('code');
      expect(results.items[0].id).to.be.a('string');

      scope.done();
    });

    it('should list identity provider types with custom pagination', async () => {
      const pageNumber = 3;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiTypes = [
        {
          id: '3',
          name: 'Google',
          code: 'google',
          featureCode: 'google_sso',
          supportsIdpInitiatedSso: true,
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, {
          data: rawApiTypes,
          totalCount: rawApiTypes.length,
        });

      const results = new PagedResults<IdentityProviderType>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listIdentityProviderTypes(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle missing optional fields', async () => {
      const rawApiTypes = [
        {
          id: '4',
          name: 'Custom IDP',
          code: 'custom',
          // Missing optional fields: featureCode, supportsIdpInitiatedSso, authStrategyTypes
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: rawApiTypes,
          totalCount: rawApiTypes.length,
        });

      const results = new PagedResults<IdentityProviderType>();
      await producer.listIdentityProviderTypes(results, orgId);

      expect(results.items).to.have.length(1);
      expect(results.items[0].id).to.equal('4');
      expect(results.items[0].name).to.equal('Custom IDP');
      expect(results.items[0].code).to.equal('custom');

      scope.done();
    });
  });

  describe('getIdentityProviderType()', () => {
    it('should retrieve a specific identity provider type by ID', async () => {
      const typeId = '1';
      const mockTypeData = {
        id: typeId,
        name: 'Azure AD',
        code: 'azure',
        featureCode: 'azure_sso',
        supportsIdpInitiatedSso: true,
        authStrategyTypes: [
          { id: 'auth-1', name: 'SAML', code: 'saml' },
        ],
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes/${typeId}`)
        .reply(200, { data: mockTypeData });

      const result = await producer.getIdentityProviderType(orgId, typeId);

      expect(result).to.be.an('object');
      expect(result.id).to.equal('1');
      expect(result.name).to.equal('Azure AD');
      expect(result.code).to.equal('azure');
      expect(result.featureCode).to.equal('azure_sso');
      expect(result.supportsIdpInitiatedSso).to.be.true;

      scope.done();
    });

    it('should handle response without data wrapper', async () => {
      const typeId = '2';
      const mockTypeData = {
        id: typeId,
        name: 'Okta',
        code: 'okta',
        featureCode: 'okta_sso',
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes/${typeId}`)
        .reply(200, mockTypeData);

      const result = await producer.getIdentityProviderType(orgId, typeId);

      expect(result).to.be.an('object');
      expect(result.id).to.equal('2');
      expect(result.name).to.equal('Okta');

      scope.done();
    });

    it('should handle 404 response', async () => {
      const typeId = '999';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes/${typeId}`)
        .reply(404, { error: 'Not found', statusCode: 404 });

      try {
        await producer.getIdentityProviderType(orgId, typeId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('listIdentityProviderGroups()', () => {
    it('should list identity provider groups with default pagination', async () => {
      const identityProviderId = 'idp-1';
      const rawApiGroups = [
        {
          idpGroupUniqueIdentifier: 'group-unique-1',
          name: 'Engineering Team',
          description: 'Engineering department access',
          email: 'engineering@example.com',
        },
        {
          idpGroupUniqueIdentifier: 'group-unique-2',
          name: 'Sales Team',
          description: 'Sales department access',
          email: 'sales@example.com',
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groups`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: rawApiGroups,
          totalCount: rawApiGroups.length,
        });

      const results = new PagedResults<IdentityProviderGroup>();
      await producer.listIdentityProviderGroups(results, orgId, identityProviderId);

      expect(results.items).to.have.length(2);
      expect(results.count).to.equal(2);
      expect(results.items[0]).to.have.property('name');
      expect(results.items[0].name).to.equal('Engineering Team');

      scope.done();
    });

    it('should list identity provider groups with custom pagination', async () => {
      const identityProviderId = 'idp-2';
      const pageNumber = 2;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiGroups = [
        {
          idpGroupUniqueIdentifier: 'group-unique-3',
          name: 'Marketing Team',
          description: 'Marketing department',
          email: 'marketing@example.com',
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groups`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, {
          data: rawApiGroups,
          totalCount: rawApiGroups.length,
        });

      const results = new PagedResults<IdentityProviderGroup>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listIdentityProviderGroups(results, orgId, identityProviderId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle groups with missing optional fields', async () => {
      const identityProviderId = 'idp-3';
      const rawApiGroups = [
        {
          name: 'Minimal Group',
          // Missing optional fields: idpGroupUniqueIdentifier, description, email
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groups`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: rawApiGroups,
          totalCount: rawApiGroups.length,
        });

      const results = new PagedResults<IdentityProviderGroup>();
      await producer.listIdentityProviderGroups(results, orgId, identityProviderId);

      expect(results.items).to.have.length(1);
      expect(results.items[0].name).to.equal('Minimal Group');

      scope.done();
    });

    it('should handle identity provider not found', async () => {
      const identityProviderId = '999';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groups`)
        .query({ offset: 0, limit: 50 })
        .reply(404, { error: 'Not found', statusCode: 404 });

      const results = new PagedResults<IdentityProviderGroup>();

      try {
        await producer.listIdentityProviderGroups(results, orgId, identityProviderId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('getIdentityProviderGroupRelations()', () => {
    it('should list identity provider group relations with default pagination', async () => {
      const identityProviderId = 'idp-1';
      const rawApiRelations = [
        {
          idpGroupUniqueIdentifier: 'relation-unique-1',
          identityProviderGroup: {
            idpGroupUniqueIdentifier: 'group-1',
            name: 'IDP Group 1',
            description: 'First IDP group',
            email: 'group1@example.com',
          },
          groupId: 'local-group-1',
          group: {
            id: 'local-group-1',
            name: 'Local Group 1',
            description: 'First local group',
          },
        },
        {
          idpGroupUniqueIdentifier: 'relation-unique-2',
          identityProviderGroup: {
            idpGroupUniqueIdentifier: 'group-2',
            name: 'IDP Group 2',
            email: 'group2@example.com',
          },
          groupId: 'local-group-2',
          group: {
            id: 'local-group-2',
            name: 'Local Group 2',
          },
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groupRelations`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: rawApiRelations,
          totalCount: rawApiRelations.length,
        });

      const results = new PagedResults<IdentityProviderGroupRelation>();
      await producer.getIdentityProviderGroupRelations(results, orgId, identityProviderId);

      expect(results.items).to.have.length(2);
      expect(results.count).to.equal(2);
      expect(results.items[0]).to.have.property('idpGroupUniqueIdentifier');
      expect(results.items[0].identityProviderGroup).to.have.property('name');

      scope.done();
    });

    it('should list identity provider group relations with custom pagination', async () => {
      const identityProviderId = 'idp-2';
      const pageNumber = 3;
      const pageSize = 15;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiRelations = [
        {
          idpGroupUniqueIdentifier: 'relation-unique-3',
          groupId: 'local-group-3',
        },
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groupRelations`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, {
          data: rawApiRelations,
          totalCount: rawApiRelations.length,
        });

      const results = new PagedResults<IdentityProviderGroupRelation>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.getIdentityProviderGroupRelations(results, orgId, identityProviderId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty relations list', async () => {
      const identityProviderId = 'idp-empty';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groupRelations`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: [],
          totalCount: 0,
        });

      const results = new PagedResults<IdentityProviderGroupRelation>();
      await producer.getIdentityProviderGroupRelations(results, orgId, identityProviderId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle identity provider not found when getting relations', async () => {
      const identityProviderId = '999';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groupRelations`)
        .query({ offset: 0, limit: 50 })
        .reply(404, { error: 'Not found', statusCode: 404 });

      const results = new PagedResults<IdentityProviderGroupRelation>();

      try {
        await producer.getIdentityProviderGroupRelations(results, orgId, identityProviderId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('Error Handling', () => {
    it('should propagate server errors for listIdentityProviders', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(500, {
          error: 'Internal Server Error',
          statusCode: 500,
        });

      const results = new PagedResults<IdentityProvider>();

      try {
        await producer.listIdentityProviders(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should propagate server errors for listIdentityProviderTypes', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes`)
        .query({ offset: 0, limit: 50 })
        .reply(500, {
          error: 'Internal Server Error',
          statusCode: 500,
        });

      const results = new PagedResults<IdentityProviderType>();

      try {
        await producer.listIdentityProviderTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes/123`)
        .replyWithError('Network error');

      try {
        await producer.getIdentityProviderType(orgId, '123');
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle unauthorized errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(401, {
          error: 'Unauthorized',
          statusCode: 401,
        });

      const results = new PagedResults<IdentityProvider>();

      try {
        await producer.listIdentityProviders(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });
  });

  describe('Response Data Validation', () => {
    it('should throw UnexpectedError for missing data array in listIdentityProviders', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          totalCount: 0,
          // Missing 'data' array
        });

      const results = new PagedResults<IdentityProvider>();

      try {
        await producer.listIdentityProviders(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should throw UnexpectedError for null data in listIdentityProviderTypes', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviderTypes`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: null,
          totalCount: 5,
        });

      const results = new PagedResults<IdentityProviderType>();

      try {
        await producer.listIdentityProviderTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle empty data array for listIdentityProviderGroups', async () => {
      const identityProviderId = 'idp-empty';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders/${identityProviderId}/groups`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: [],
          totalCount: 0,
        });

      const results = new PagedResults<IdentityProviderGroup>();
      await producer.listIdentityProviderGroups(results, orgId, identityProviderId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });
  });

  describe('Pagination Edge Cases', () => {
    it('should handle very large page size by capping to 1000', async () => {
      const pageNumber = 1;
      const pageSize = 5000; // Should be capped to 1000
      const expectedOffset = 0;
      const expectedLimit = 1000;

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: expectedOffset, limit: expectedLimit })
        .reply(200, {
          data: [],
          totalCount: 0,
        });

      const results = new PagedResults<IdentityProvider>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listIdentityProviders(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should calculate offset correctly for middle pages', async () => {
      const pageNumber = 5;
      const pageSize = 25;
      const expectedOffset = (pageNumber - 1) * pageSize; // 100

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/identityProviders`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, {
          data: [],
          totalCount: 0,
        });

      const results = new PagedResults<IdentityProvider>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listIdentityProviders(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });
  });
});
