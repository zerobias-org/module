import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { UserProducerApiImpl } from '../../src/UserProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { User, UserInfo, Role, Site } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('UserProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: UserProducerApiImpl;
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

    producer = new UserProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create UserProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(UserProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new UserProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('User List Operations', () => {
    it('should list users with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiUsers = [
        {
          id: 22509982,
          identity: {
            id: 'identity-22509982',
            email: 'user@example.com',
            firstName: 'Delivery',
            lastName: null,
            mobilePhone: '+1-555-0123',
          },
          status: 'a', // API returns single letter codes
          createdAt: '2024-12-18T17:27:10.000Z',
          updatedAt: '2024-12-18T17:27:10.000Z',
        },
        {
          id: 20032735,
          identity: {
            id: 'identity-20032735',
            email: 'user@example.com',
            firstName: 'Luis',
            lastName: 'Alvarado',
            mobilePhone: '+1-555-0123',
          },
          status: 'i',
          createdAt: '2024-08-27T17:22:39.000Z',
          updatedAt: '2024-10-14T13:40:57.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users`,
        { offset: 0, limit: 50 },
        rawApiUsers,
        rawApiUsers.length
      );

      const results = new PagedResults<User>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0].identity).to.have.property('email');
      expect(results.items[0].identity).to.have.property('firstName');

      // Verify anonymized data is used - email could be string or Email object
      const emailStr = typeof results.items[0].identity?.email === 'string'
        ? results.items[0].identity?.email
        : results.items[0].identity?.email?.toString() || '';
      expect(emailStr).to.include('@');
      expect(['example.com', 'testcorp.com', 'demo.org']).to.include(emailStr.split('@')[1]);
      expect(['Jane', 'Alice', 'John', 'Bob', 'Carol', 'David', 'Delivery', 'Luis']).to.include(results.items[0].identity?.firstName);

      scope.done();
    });

    it('should list users with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiUsers = [
        {
          id: 19710706,
          identity: {
            id: 'identity-19710706',
            email: 'user@example.com',
            firstName: 'Jonathan',
            lastName: 'Bates',
            mobilePhone: '+1-555-0123',
          },
          status: 'a',
          createdAt: '2024-08-14T14:56:23.000Z',
          updatedAt: '2024-08-17T10:26:50.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users`,
        { offset: expectedOffset, limit: pageSize },
        rawApiUsers,
        rawApiUsers.length
      );

      const results = new PagedResults<User>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });
  });

  describe('User Retrieval Operations', () => {
    it('should retrieve a specific user by ID', async () => {
      const userId = '123';
      // Create mock raw API response data in the format the mapper expects
      const mockUserData = {
        id: userId,
        identity: {
          id: `identity-${userId}`,
          email: 'user@example.com',
          firstName: 'Jane',
          lastName: 'Doe',
          mobilePhone: '+1-555-0123',
        },
        status: 'a',
        createdAt: '2024-12-18T17:27:10.000Z',
        updatedAt: '2024-12-18T17:27:10.000Z',
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users/${userId}`)
        .reply(200, mockUserData);

      const result = await producer.get(orgId, userId);

      expect(result).to.be.an('object');
      expect(result.id).to.be.a('string');
      expect(result.identity?.firstName).to.be.a('string');
      expect(result.identity?.lastName).to.be.a('string');
      expect(result.identity?.email).to.not.be.undefined;

      // Verify anonymized data - email could be string or Email object
      const emailStr = typeof result.identity?.email === 'string'
        ? result.identity?.email
        : result.identity?.email?.toString() || '';
      expect(['example.com', 'testcorp.com', 'demo.org']).to.include(emailStr.split('@')[1]);
      expect(['Jane', 'Alice', 'John', 'Bob', 'Carol', 'David', 'Delivery', 'Luis']).to.include(result.identity?.firstName);

      scope.done();
    });

    it('should handle non-existent user ID gracefully', async () => {
      const userId = '999999';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users/${userId}`)
        .reply(404, { error: 'Not found', statusCode: 404 });

      try {
        await producer.get(orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('User Data Validation', () => {
    it('should validate user response schema', async () => {
      const userId = '123';
      // Create mock raw API response data in the format the mapper expects
      const mockUserData = {
        id: 22509982,
        identity: {
          id: 'identity-22509982',
          email: 'user@example.com',
          firstName: 'Delivery',
          lastName: null,
          mobilePhone: '+1-555-0123',
        },
        status: 'a',
        createdAt: '2024-12-18T17:27:10.000Z',
        updatedAt: '2024-12-18T17:27:10.000Z',
      };

      const scope = mockSingleResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}`,
        mockUserData
      );

      const result = await producer.get(orgId, userId);

      // Validate required fields
      expect(result).to.have.property('id');
      expect(result.identity).to.have.property('firstName');
      expect(result.identity).to.have.property('lastName');
      expect(result.identity).to.have.property('email');

      // Validate data types
      expect(result.id).to.be.a('string');
      expect(result.identity?.firstName).to.be.a('string');
      expect(result.identity?.email).to.not.be.undefined;

      // Ensure anonymized data - email could be string or Email object
      const emailStr = typeof result.identity?.email === 'string'
        ? result.identity?.email
        : result.identity?.email?.toString() || '';
      expect(emailStr).to.match(/@(example\.com|testcorp\.com|demo\.org)$/);

      scope.done();
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', () => {
      // Test with extremely large page size
      const results = new PagedResults<User>();
      results.pageSize = 5000; // Should be limited by API

      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<User>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listRoles()', () => {
    it('should list roles for user with default pagination', async () => {
      const userId = '123';
      const fixtureData = loadFixture('templates/user-roles-list-success.json');

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        fixtureData.data,
        fixtureData.totalCount
      );

      const results = new PagedResults<Role>();
      await producer.listRoles(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle user not found when listing roles', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Role>();

      try {
        await producer.listRoles(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('listSites()', () => {
    it('should list sites for user with default pagination', async () => {
      const userId = '123';
      const fixtureData = loadFixture('templates/user-sites-list-success.json');

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/sites`,
        { offset: 0, limit: 50 },
        fixtureData.data,
        fixtureData.totalCount
      );

      const results = new PagedResults<Site>();
      await producer.listSites(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle user not found when listing sites', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/sites`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Site>();

      try {
        await producer.listSites(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('Error Handling', () => {
    it('should propagate server errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<User>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users/123`)
        .replyWithError('Network error');

      try {
        await producer.get(orgId, '123');
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Response Data Mapping', () => {
    it('should handle response with missing data array', async () => {
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<User>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format: expected data array');
      }

      scope.done();
    });

    it('should handle response with null data', async () => {
      const mockResponse = {
        data: null,
        totalCount: 5,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<User>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format: expected data array');
      }

      scope.done();
    });
  });

  describe('listZones()', () => {
    it('should list zones for user with default pagination', async () => {
      const userId = '123';
      const mockZones = [
        {
          id: 'zone-1',
          name: 'Main Zone',
          siteId: 'site-123',
          description: 'Primary access zone',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zones`,
        { offset: 0, limit: 50 },
        mockZones,
        mockZones.length
      );

      const results = new PagedResults<any>();
      await producer.listZones(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list zones with custom pagination', async () => {
      const userId = '123';
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockZones = [{ id: 'zone-1', name: 'Zone 1' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zones`,
        { offset: expectedOffset, limit: pageSize },
        mockZones,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listZones(results, orgId, userId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty zones list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zones`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listZones(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing zones', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zones`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing zones', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zones`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listMfaCredentials()', () => {
    it('should list MFA credentials for user with default pagination', async () => {
      const userId = '123';
      const mockCredentials = [
        {
          id: 'mfa-1',
          type: 'totp',
          name: 'Authenticator App',
          createdAt: '2024-01-01T00:00:00Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/mfaCredentials`,
        { offset: 0, limit: 50 },
        mockCredentials,
        mockCredentials.length
      );

      const results = new PagedResults<any>();
      await producer.listMfaCredentials(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty MFA credentials list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/mfaCredentials`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listMfaCredentials(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing MFA credentials', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/mfaCredentials`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listMfaCredentials(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle unauthorized access when listing MFA credentials', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/mfaCredentials`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<any>();

      try {
        await producer.listMfaCredentials(results, orgId, userId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });
  });

  describe('listPictures()', () => {
    it('should list pictures for user with default pagination', async () => {
      const userId = '123';
      const mockPictures = [
        {
          id: 'pic-1',
          url: 'https://example.com/pic1.jpg',
          createdAt: '2024-01-01T00:00:00Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/userPictures`,
        { offset: 0, limit: 50 },
        mockPictures,
        mockPictures.length
      );

      const results = new PagedResults<any>();
      await producer.listPictures(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list pictures with custom pagination', async () => {
      const userId = '123';
      const pageNumber = 3;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockPictures = [{ id: 'pic-1', url: 'https://example.com/pic1.jpg' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/userPictures`,
        { offset: expectedOffset, limit: pageSize },
        mockPictures,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listPictures(results, orgId, userId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty pictures list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/userPictures`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listPictures(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing pictures', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/userPictures`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listPictures(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle rate limiting when listing pictures', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/userPictures`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<any>();

      try {
        await producer.listPictures(results, orgId, userId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listGroups()', () => {
    it('should list groups for user with default pagination', async () => {
      const userId = '123';
      const mockGroups = [
        {
          id: 'group-1',
          name: 'Administrators',
          description: 'Admin group',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/groups`,
        { offset: 0, limit: 50 },
        mockGroups,
        mockGroups.length
      );

      const results = new PagedResults<any>();
      await producer.listGroups(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list groups with custom pagination', async () => {
      const userId = '123';
      const pageNumber = 2;
      const pageSize = 25;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockGroups = [{ id: 'group-1', name: 'Group 1' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/groups`,
        { offset: expectedOffset, limit: pageSize },
        mockGroups,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listGroups(results, orgId, userId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty groups list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/groups`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listGroups(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing groups', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/groups`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listGroups(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing groups', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/groups`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listGroups(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listEntries()', () => {
    it('should list entries for user with default pagination', async () => {
      const userId = '123';
      const mockEntries = [
        {
          id: 'entry-1',
          name: 'Main Entrance',
          accessLevel: 'full',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/entries`,
        { offset: 0, limit: 50 },
        mockEntries,
        mockEntries.length
      );

      const results = new PagedResults<any>();
      await producer.listEntries(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty entries list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/entries`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listEntries(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing entries', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/entries`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listEntries(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle unauthorized access when listing entries', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/entries`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<any>();

      try {
        await producer.listEntries(results, orgId, userId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });
  });

  describe('listActivity()', () => {
    it('should get activity for user with default pagination', async () => {
      const userId = '123';
      const mockActivity = [
        {
          id: 'activity-1',
          type: 'access',
          time: '2024-01-01T12:00:00Z',
          sourceName: 'Main Door',
          details: 'Door access granted',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/activity`,
        { offset: 0, limit: 50 },
        mockActivity,
        mockActivity.length
      );

      const results = new PagedResults<any>();
      await producer.listActivity(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should get activity with custom pagination', async () => {
      const userId = '123';
      const pageNumber = 2;
      const pageSize = 100;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockActivity = [{ id: 'activity-1', type: 'access', time: '2024-01-01T12:00:00Z', sourceName: 'Main Door' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/activity`,
        { offset: expectedOffset, limit: pageSize },
        mockActivity,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listActivity(results, orgId, userId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty activity list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/activity`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listActivity(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when getting activity', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/activity`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listActivity(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when getting activity', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/activity`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listActivity(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle rate limiting when getting activity', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/activity`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<any>();

      try {
        await producer.listActivity(results, orgId, userId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listOrgIdentities()', () => {
    it('should list org identities with default pagination', async () => {
      const mockIdentities = [
        {
          id: 'identity-1',
          email: 'user@example.com',
          firstName: 'John',
          lastName: 'Doe',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgIdentities`,
        { offset: 0, limit: 50 },
        mockIdentities,
        mockIdentities.length
      );

      const results = new PagedResults<any>();
      await producer.listOrgIdentities(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list org identities with custom pagination', async () => {
      const pageNumber = 3;
      const pageSize = 15;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockIdentities = [{ id: 'identity-1', email: 'user@example.com' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgIdentities`,
        { offset: expectedOffset, limit: pageSize },
        mockIdentities,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listOrgIdentities(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty org identities list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgIdentities`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listOrgIdentities(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle org not found when listing identities', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgIdentities`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listOrgIdentities(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing org identities', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgIdentities`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listOrgIdentities(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listOrgPictures()', () => {
    it('should list org pictures with default pagination', async () => {
      const mockPictures = [
        {
          id: 'pic-1',
          url: 'https://example.com/org-pic1.jpg',
          createdAt: '2024-01-01T00:00:00Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgPictures`,
        { offset: 0, limit: 50 },
        mockPictures,
        mockPictures.length
      );

      const results = new PagedResults<any>();
      await producer.listOrgPictures(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty org pictures list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgPictures`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listOrgPictures(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle org not found when listing pictures', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgPictures`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listOrgPictures(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle unauthorized access when listing org pictures', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/orgPictures`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<any>();

      try {
        await producer.listOrgPictures(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });
  });

  describe('listSharedUsers()', () => {
    it('should list shared users with default pagination', async () => {
      const mockSharedUsers = [
        {
          id: 'shared-1',
          userId: 'user-123',
          sharedWithOrgId: 'org-456',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sharedUsers`,
        { offset: 0, limit: 50 },
        mockSharedUsers,
        mockSharedUsers.length
      );

      const results = new PagedResults<any>();
      await producer.listSharedUsers(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list shared users with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 30;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockSharedUsers = [{ id: 'shared-1', userId: 'user-123' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sharedUsers`,
        { offset: expectedOffset, limit: pageSize },
        mockSharedUsers,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listSharedUsers(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty shared users list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sharedUsers`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listSharedUsers(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle org not found when listing shared users', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sharedUsers`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listSharedUsers(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing shared users', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sharedUsers`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listSharedUsers(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle rate limiting when listing shared users', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sharedUsers`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<any>();

      try {
        await producer.listSharedUsers(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listCredentials()', () => {
    it('should list credentials for user with default pagination', async () => {
      const userId = '123';
      const mockCredentials = [
        {
          id: 'cred-1',
          type: 'card',
          value: '****1234',
          status: 'active',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/credentials`,
        { offset: 0, limit: 50 },
        mockCredentials,
        mockCredentials.length
      );

      const results = new PagedResults<any>();
      await producer.listCredentials(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list credentials with custom pagination', async () => {
      const userId = '123';
      const pageNumber = 2;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockCredentials = [{ id: 'cred-1', type: 'card' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/credentials`,
        { offset: expectedOffset, limit: pageSize },
        mockCredentials,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listCredentials(results, orgId, userId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty credentials list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/credentials`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listCredentials(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing credentials', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/credentials`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listCredentials(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle unauthorized access when listing credentials', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/credentials`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<any>();

      try {
        await producer.listCredentials(results, orgId, userId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle server error when listing credentials', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/credentials`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listCredentials(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listZoneUsers()', () => {
    it('should list zone users for user with default pagination', async () => {
      const userId = '123';
      const mockZoneUsers = [
        {
          id: 'zoneuser-1',
          zoneId: 'zone-123',
          userId: 'user-123',
          accessLevel: 'full',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zoneUsers`,
        { offset: 0, limit: 50 },
        mockZoneUsers,
        mockZoneUsers.length
      );

      const results = new PagedResults<any>();
      await producer.listZoneUsers(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list zone users with custom pagination', async () => {
      const userId = '123';
      const pageNumber = 2;
      const pageSize = 15;
      const expectedOffset = (pageNumber - 1) * pageSize;
      const mockZoneUsers = [{ id: 'zoneuser-1', zoneId: 'zone-123' }];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zoneUsers`,
        { offset: expectedOffset, limit: pageSize },
        mockZoneUsers,
        1
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      await producer.listZoneUsers(results, orgId, userId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty zone users list', async () => {
      const userId = '123';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zoneUsers`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listZoneUsers(results, orgId, userId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing zone users', async () => {
      const userId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zoneUsers`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneUsers(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing zone users', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zoneUsers`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneUsers(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle rate limiting when listing zone users', async () => {
      const userId = '123';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/zoneUsers`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneUsers(results, orgId, userId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });
});
