import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { CredentialProducerApiImpl } from '../../src/CredentialProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { CredentialType, OrgCredential, CredentialAction, CredentialActionType, CardFormat } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('CredentialProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: CredentialProducerApiImpl;
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

    producer = new CredentialProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create CredentialProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(CredentialProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new CredentialProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('listCardFormats()', () => {
    it('should list card formats with default pagination', async () => {
      const rawApiCardFormats = [
        {
          id: 1,
          name: '26-bit Wiegand',
          bitLength: 26,
          facilityCodeStart: 2,
          facilityCodeEnd: 9,
          cardNumberStart: 10,
          cardNumberEnd: 24,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
        {
          id: 2,
          name: '34-bit Wiegand',
          bitLength: 34,
          facilityCodeStart: 2,
          facilityCodeEnd: 17,
          cardNumberStart: 18,
          cardNumberEnd: 33,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: 0, limit: 50 },
        rawApiCardFormats,
        rawApiCardFormats.length
      );

      const results = new PagedResults<CardFormat>();
      await producer.listCardFormats(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      scope.done();
    });

    it('should list card formats with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiCardFormats = [
        {
          id: 3,
          name: '37-bit Wiegand',
          bitLength: 37,
          facilityCodeStart: 2,
          facilityCodeEnd: 18,
          cardNumberStart: 19,
          cardNumberEnd: 36,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: expectedOffset, limit: pageSize },
        rawApiCardFormats,
        rawApiCardFormats.length
      );

      const results = new PagedResults<CardFormat>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listCardFormats(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty card formats list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<CardFormat>();
      await producer.listCardFormats(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<CardFormat>();

      try {
        await producer.listCardFormats(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle server errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<CardFormat>();

      try {
        await producer.listCardFormats(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with missing data array', async () => {
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/cardFormats`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<CardFormat>();

      try {
        await producer.listCardFormats(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with null data', async () => {
      const mockResponse = {
        data: null,
        totalCount: 3,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/cardFormats`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<CardFormat>();

      try {
        await producer.listCardFormats(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });
  });

  describe('listCredentialActions()', () => {
    it('should list credential actions with default pagination', async () => {
      const credentialId = 'cred-123';
      const rawApiActions = [
        {
          id: 1001,
          credentialId,
          actionType: 'activate',
          performedBy: 'admin@example.com',
          performedAt: '2024-01-15T10:30:00.000Z',
          details: 'Credential activated',
          createdAt: '2024-01-15T10:30:00.000Z',
        },
        {
          id: 1002,
          credentialId,
          actionType: 'suspend',
          performedBy: 'admin@example.com',
          performedAt: '2024-01-20T14:45:00.000Z',
          details: 'Credential suspended',
          createdAt: '2024-01-20T14:45:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials/${credentialId}/credentialActions`,
        { offset: 0, limit: 50 },
        rawApiActions,
        rawApiActions.length
      );

      const results = new PagedResults<CredentialAction>();
      await producer.listCredentialActions(results, orgId, credentialId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('credentialId');

      scope.done();
    });

    it('should list credential actions with custom pagination', async () => {
      const credentialId = 'cred-456';
      const pageNumber = 2;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiActions = [
        {
          id: 1003,
          credentialId,
          actionType: 'revoke',
          performedBy: 'admin@example.com',
          performedAt: '2024-01-25T09:00:00.000Z',
          details: 'Credential revoked',
          createdAt: '2024-01-25T09:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials/${credentialId}/credentialActions`,
        { offset: expectedOffset, limit: pageSize },
        rawApiActions,
        rawApiActions.length
      );

      const results = new PagedResults<CredentialAction>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listCredentialActions(results, orgId, credentialId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle credential not found when listing actions', async () => {
      const credentialId = '999999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials/${credentialId}/credentialActions`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<CredentialAction>();

      try {
        await producer.listCredentialActions(results, orgId, credentialId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle empty credential actions list', async () => {
      const credentialId = 'cred-empty';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials/${credentialId}/credentialActions`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<CredentialAction>();
      await producer.listCredentialActions(results, orgId, credentialId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle unauthorized access', async () => {
      const credentialId = 'cred-unauthorized';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials/${credentialId}/credentialActions`,
        { offset: 0, limit: 50 },
        401,
        'Unauthorized'
      );

      const results = new PagedResults<CredentialAction>();

      try {
        await producer.listCredentialActions(results, orgId, credentialId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });
  });

  describe('listCredentialActionTypes()', () => {
    it('should list credential action types with default pagination', async () => {
      const rawApiActionTypes = [
        {
          id: 1,
          name: 'activate',
          description: 'Activate a credential',
          category: 'lifecycle',
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
        {
          id: 2,
          name: 'suspend',
          description: 'Suspend a credential',
          category: 'lifecycle',
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
        {
          id: 3,
          name: 'revoke',
          description: 'Revoke a credential',
          category: 'lifecycle',
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialActionTypes`,
        { offset: 0, limit: 50 },
        rawApiActionTypes,
        rawApiActionTypes.length
      );

      const results = new PagedResults<CredentialActionType>();
      await producer.listCredentialActionTypes(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      scope.done();
    });

    it('should list credential action types with custom pagination', async () => {
      const pageNumber = 3;
      const pageSize = 15;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiActionTypes = [
        {
          id: 4,
          name: 'renew',
          description: 'Renew a credential',
          category: 'lifecycle',
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialActionTypes`,
        { offset: expectedOffset, limit: pageSize },
        rawApiActionTypes,
        rawApiActionTypes.length
      );

      const results = new PagedResults<CredentialActionType>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listCredentialActionTypes(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty action types list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialActionTypes`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<CredentialActionType>();
      await producer.listCredentialActionTypes(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle server errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialActionTypes`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<CredentialActionType>();

      try {
        await producer.listCredentialActionTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/credentialActionTypes`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<CredentialActionType>();

      try {
        await producer.listCredentialActionTypes(results, orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listCredentialTypes()', () => {
    it('should list credential types with default pagination', async () => {
      const rawApiCredentialTypes = [
        {
          id: 10,
          name: 'Access Card',
          description: 'Physical access card',
          format: 'card',
          validityDays: 365,
          isReusable: true,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
        {
          id: 11,
          name: 'Mobile Key',
          description: 'Mobile phone credential',
          format: 'mobile',
          validityDays: 180,
          isReusable: true,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialTypes`,
        { offset: 0, limit: 50 },
        rawApiCredentialTypes,
        rawApiCredentialTypes.length
      );

      const results = new PagedResults<CredentialType>();
      await producer.listCredentialTypes(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      scope.done();
    });

    it('should list credential types with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiCredentialTypes = [
        {
          id: 12,
          name: 'Temporary Badge',
          description: 'Temporary visitor badge',
          format: 'card',
          validityDays: 1,
          isReusable: false,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialTypes`,
        { offset: expectedOffset, limit: pageSize },
        rawApiCredentialTypes,
        rawApiCredentialTypes.length
      );

      const results = new PagedResults<CredentialType>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listCredentialTypes(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty credential types list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialTypes`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<CredentialType>();
      await producer.listCredentialTypes(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should validate credential type response schema', async () => {
      const rawApiCredentialTypes = [
        {
          id: 13,
          name: 'Biometric',
          description: 'Biometric credential',
          format: 'biometric',
          validityDays: 730,
          isReusable: true,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialTypes`,
        { offset: 0, limit: 50 },
        rawApiCredentialTypes,
        rawApiCredentialTypes.length
      );

      const results = new PagedResults<CredentialType>();
      await producer.listCredentialTypes(results, orgId);

      expect(results.items).to.have.length(1);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');
      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].name).to.be.a('string');

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialTypes`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<CredentialType>();

      try {
        await producer.listCredentialTypes(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listOrgCredentials()', () => {
    it('should list organization credentials with default pagination', async () => {
      const rawApiOrgCredentials = [
        {
          id: 5001,
          userId: 22509982,
          credentialTypeId: 10,
          number: '12345678',
          status: 'active',
          issuedAt: '2024-01-10T08:00:00.000Z',
          expiresAt: '2025-01-10T08:00:00.000Z',
          createdAt: '2024-01-10T08:00:00.000Z',
          updatedAt: '2024-01-10T08:00:00.000Z',
        },
        {
          id: 5002,
          userId: 20032735,
          credentialTypeId: 11,
          number: '87654321',
          status: 'active',
          issuedAt: '2024-02-15T10:00:00.000Z',
          expiresAt: '2025-02-15T10:00:00.000Z',
          createdAt: '2024-02-15T10:00:00.000Z',
          updatedAt: '2024-02-15T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials`,
        { offset: 0, limit: 50 },
        rawApiOrgCredentials,
        rawApiOrgCredentials.length
      );

      const results = new PagedResults<OrgCredential>();
      await producer.listOrgCredentials(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('userId');

      scope.done();
    });

    it('should list organization credentials with custom pagination', async () => {
      const pageNumber = 4;
      const pageSize = 25;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiOrgCredentials = [
        {
          id: 5003,
          userId: 19710706,
          credentialTypeId: 12,
          number: 'TEMP-001',
          status: 'expired',
          issuedAt: '2024-03-01T09:00:00.000Z',
          expiresAt: '2024-03-02T09:00:00.000Z',
          createdAt: '2024-03-01T09:00:00.000Z',
          updatedAt: '2024-03-02T09:01:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials`,
        { offset: expectedOffset, limit: pageSize },
        rawApiOrgCredentials,
        rawApiOrgCredentials.length
      );

      const results = new PagedResults<OrgCredential>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listOrgCredentials(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty credentials list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<OrgCredential>();
      await producer.listOrgCredentials(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should validate organization credential response schema', async () => {
      const rawApiOrgCredentials = [
        {
          id: 5004,
          userId: 30053017,
          credentialTypeId: 13,
          number: 'BIO-789',
          status: 'active',
          issuedAt: '2024-04-20T12:00:00.000Z',
          expiresAt: '2026-04-20T12:00:00.000Z',
          createdAt: '2024-04-20T12:00:00.000Z',
          updatedAt: '2024-04-20T12:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials`,
        { offset: 0, limit: 50 },
        rawApiOrgCredentials,
        rawApiOrgCredentials.length
      );

      const results = new PagedResults<OrgCredential>();
      await producer.listOrgCredentials(results, orgId);

      expect(results.items).to.have.length(1);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('userId');
      expect(results.items[0].id).to.be.a('string');
      // userId can be string or number depending on API response
      expect(results.items[0].userId).to.not.be.undefined;

      scope.done();
    });

    it('should handle server errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<OrgCredential>();

      try {
        await producer.listOrgCredentials(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/credentials`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<OrgCredential>();

      try {
        await producer.listOrgCredentials(results, orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with missing data array', async () => {
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/credentials`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<OrgCredential>();

      try {
        await producer.listOrgCredentials(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with null data', async () => {
      const mockResponse = {
        data: null,
        totalCount: 10,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/credentials`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<OrgCredential>();

      try {
        await producer.listOrgCredentials(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });
  });

  describe('Pagination Edge Cases', () => {
    it('should handle invalid pagination parameters', () => {
      // Test with extremely large page size
      const results = new PagedResults<CardFormat>();
      results.pageSize = 5000; // Should be limited by API

      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should clamp pageSize to maximum allowed (1000)', async () => {
      const rawApiCardFormats = [
        {
          id: 1,
          name: 'Standard',
          bitLength: 26,
          facilityCodeStart: 2,
          facilityCodeEnd: 9,
          cardNumberStart: 10,
          cardNumberEnd: 24,
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: 0, limit: 1000 }, // API should clamp to 1000
        rawApiCardFormats,
        rawApiCardFormats.length
      );

      const results = new PagedResults<CardFormat>();
      results.pageNumber = 1;
      results.pageSize = 10000; // Request larger than max

      await producer.listCardFormats(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle minimum pageSize of 1', async () => {
      const rawApiCredentials = [
        {
          id: 5001,
          userId: 22509982,
          credentialTypeId: 10,
          number: '12345678',
          status: 'active',
          issuedAt: '2024-01-10T08:00:00.000Z',
          expiresAt: '2025-01-10T08:00:00.000Z',
          createdAt: '2024-01-10T08:00:00.000Z',
          updatedAt: '2024-01-10T08:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentials`,
        { offset: 0, limit: 1 },
        rawApiCredentials,
        rawApiCredentials.length
      );

      const results = new PagedResults<OrgCredential>();
      results.pageNumber = 1;
      results.pageSize = 1;

      await producer.listOrgCredentials(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });
  });

  describe('Error Handling Consistency', () => {
    it('should propagate 401 errors as InvalidCredentialsError', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialTypes`,
        { offset: 0, limit: 50 },
        401,
        'Invalid token'
      );

      const results = new PagedResults<CredentialType>();

      try {
        await producer.listCredentialTypes(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should propagate 404 errors as NoSuchObjectError', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/cardFormats`,
        { offset: 0, limit: 50 },
        404,
        'Organization not found'
      );

      const results = new PagedResults<CardFormat>();

      try {
        await producer.listCardFormats(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should propagate 500 errors as UnexpectedError', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/credentialActionTypes`,
        { offset: 0, limit: 50 },
        500,
        'Internal server error'
      );

      const results = new PagedResults<CredentialActionType>();

      try {
        await producer.listCredentialActionTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });
});
