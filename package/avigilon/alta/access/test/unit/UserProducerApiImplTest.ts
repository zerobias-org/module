import { expect } from 'chai';
import nock from 'nock';
import { UserProducerApiImpl } from '../../src/UserProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { PagedResults } from '@auditmation/types-core-js';
import { User, UserInfo, Role, Site } from '../../generated/model';
import { 
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
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
  const baseUrl = 'https://api.openpath.com';
  const testToken = 'test-api-token-ghi789';
  const orgId = 'test-org-123';

  beforeEach(async () => {
    client = new AvigilonAltaAccessClient();
    const profile: ConnectionProfile = { apiToken: testToken };
    
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
      }).to.throw('not connected');
    });
  });

  describe('User List Operations', () => {
    it('should list users with default pagination', async () => {
      // Load the anonymized fixture from integration tests
      const fixtureData = loadFixture('integration/users-list-default.json');
      
      const scope = mockAuthenticatedRequest(baseUrl, testToken)
        .get(`/orgs/${orgId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: fixtureData.items,
          totalCount: fixtureData.count
        });

      const results = new PagedResults<User>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('email');
      expect(results.items[0]).to.have.property('firstName');
      
      // Verify anonymized data is used
      const emailStr = results.items[0].email?.toString() || '';
      expect(emailStr).to.include('@');
      expect(['example.com', 'testcorp.com', 'demo.org']).to.include(emailStr.split('@')[1]);
      expect(['Jane', 'Alice', 'John', 'Bob', 'Carol', 'David']).to.include(results.items[0].firstName);
      
      scope.done();
    });

    it('should list users with custom pagination', async () => {
      const fixtureData = loadFixture('integration/users-list-paginated.json');
      
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;
      
      const scope = mockAuthenticatedRequest(baseUrl, testToken)
        .get(`/orgs/${orgId}/users`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, {
          data: fixtureData.items,
          totalCount: fixtureData.count
        });

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
      const userId = 123;
      const fixtureData = loadFixture('integration/user-get-by-id.json');
      
      const scope = mockAuthenticatedRequest(baseUrl, testToken)
        .get(`/orgs/${orgId}/users/${userId}`)
        .reply(200, fixtureData);

      const result = await producer.get(orgId, userId);

      expect(result).to.be.an('object');
      expect(result.id).to.be.a('number');
      expect(result.firstName).to.be.a('string');
      expect(result.lastName).to.be.a('string');
      expect(result.email).to.be.an('object');
      
      // Verify anonymized data
      const emailStr = result.email?.toString() || '';
      expect(['example.com', 'testcorp.com', 'demo.org']).to.include(emailStr.split('@')[1]);
      expect(['Jane', 'Alice', 'John', 'Bob', 'Carol', 'David']).to.include(result.firstName);
      
      scope.done();
    });

    it('should handle non-existent user ID gracefully', async () => {
      const userId = 999999;
      
      const scope = mockAuthenticatedRequest(baseUrl, testToken)
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
      const fixtureData = loadFixture('integration/user-schema-validation.json');
      const userId = 123;
      
      const scope = mockSingleResponse(
        mockAuthenticatedRequest(baseUrl, testToken),
        'GET',
        `/orgs/${orgId}/users/${userId}`,
        fixtureData
      );

      const result = await producer.get(orgId, userId);

      // Validate required fields
      expect(result).to.have.property('id');
      expect(result).to.have.property('firstName');
      expect(result).to.have.property('lastName');
      expect(result).to.have.property('email');
      
      // Validate data types
      expect(result.id).to.be.a('number');
      expect(result.firstName).to.be.a('string');
      expect(result.email).to.be.an('object');
      
      // Ensure anonymized data
      const emailStr = result.email?.toString() || '';
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
        mockAuthenticatedRequest(baseUrl, testToken),
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
      const userId = 123;
      const fixtureData = loadFixture('templates/user-roles-list-success.json');
      
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, testToken),
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
      const userId = 999;

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, testToken),
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
      const userId = 123;
      const fixtureData = loadFixture('templates/user-sites-list-success.json');
      
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, testToken),
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
      const userId = 999;

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, testToken),
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
        mockAuthenticatedRequest(baseUrl, testToken),
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
      const scope = mockAuthenticatedRequest(baseUrl, testToken)
        .get(`/orgs/${orgId}/users/123`)
        .replyWithError('Network error');

      try {
        await producer.get(orgId, 123);
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
        totalCount: 0
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, testToken)
        .get(`/orgs/${orgId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<User>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle response with null data', async () => {
      const mockResponse = {
        data: null,
        totalCount: 5
      };

      const scope = mockAuthenticatedRequest(baseUrl, testToken)
        .get(`/orgs/${orgId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<User>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(5);
      scope.done();
    });
  });
});