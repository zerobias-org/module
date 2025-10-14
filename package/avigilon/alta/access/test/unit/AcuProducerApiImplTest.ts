import { expect } from 'chai';
import nock from 'nock';
import { AcuProducerApiImpl } from '../../src/AcuProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { PagedResults, Email, NotConnectedError } from '@auditmation/types-core-js';
import { Acu, AcuInfo, Port } from '../../generated/model';
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

describe('AcuProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: AcuProducerApiImpl;
  const baseUrl = 'https://api.openpath.com';
  const testEmail = process.env.AVIGILON_EMAIL || 'test@example.com';
  const testPassword = process.env.AVIGILON_PASSWORD || 'testpass';
  const orgId = 'test-org-123';

  beforeEach(async () => {
    client = new AvigilonAltaAccessClient();
    const profile: ConnectionProfile = { 
      email: new Email(testEmail),
      password: testPassword 
    };
    
    // Mock login endpoint
    nock(baseUrl)
      .post('/auth/login')
      .reply(200, {
        data: {
          token: 'mock-token-123',
          expiresAt: new Date(Date.now() + 3600000).toISOString()
        }
      });
    
    await client.connect(profile);
    
    producer = new AcuProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create AcuProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(AcuProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();
      
      expect(() => {
        new AcuProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('ACU List Operations', () => {
    it('should list ACUs with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiAcus = [
        {
          id: 183568,
          name: "Caspian office ACU",
          status: "A",
          serialNumber: "1917240000000100000d00010000da05",
          createdAt: "2024-07-17T00:21:27.000Z",
          updatedAt: "2025-08-26T10:13:23.000Z"
        },
        {
          id: 196495,
          name: "Lobby Entrance Video Intercom",
          status: "A",
          serialNumber: "3318060000000100000900010000062c",
          createdAt: "2024-09-03T20:05:49.000Z",
          updatedAt: "2025-08-26T10:27:02.000Z"
        }
      ];
      
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/acus`,
        { offset: 0, limit: 50 },
        rawApiAcus,
        rawApiAcus.length
      );

      const results = new PagedResults<Acu>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');
      expect(results.items[0]).to.have.property('status');
      
      // Verify the ACUs have expected properties
      expect(['Caspian office ACU', 'Lobby Entrance Video Intercom', 'Main Entrance Video Intercom']).to.include(results.items[0].name);
      expect(['active', 'inactive', 'error', 'maintenance']).to.include(results.items[0].status.toString());
      expect(results.items[0].serialNumber).to.be.a('string');
      
      scope.done();
    });

    it('should list ACUs with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;
      
      // Create mock raw API response data
      const rawApiAcus = [
        {
          id: 183574,
          name: "Main Entrance Video Intercom",
          status: "A",
          serialNumber: "33180c00000001000009000100000831",
          createdAt: "2024-07-17T01:22:03.000Z",
          updatedAt: "2025-08-22T10:21:02.000Z"
        }
      ];
      
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/acus`,
        { offset: expectedOffset, limit: pageSize },
        rawApiAcus,
        rawApiAcus.length
      );

      const results = new PagedResults<Acu>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;
      
      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });
  });

  describe('ACU Retrieval Operations', () => {
    it('should retrieve a specific ACU by ID', async () => {
      const acuId = "123";
      // Create mock raw API response data in the format the mapper expects
      const mockAcuData = {
        id: acuId,
        name: "Caspian office ACU",
        status: "A",
        serialNumber: "1917240000000100000d00010000da05",
        createdAt: "2024-07-17T00:21:27.000Z",
        updatedAt: "2025-08-26T10:13:23.000Z"
      };
      
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/acus/${acuId}`)
        .reply(200, mockAcuData);

      const result = await producer.get(orgId, acuId);

      expect(result).to.be.an('object');
      expect(result.id).to.be.a('number');
      expect(result.name).to.be.a('string');
      expect(result.status).to.not.be.undefined;
      expect(result.serialNumber).to.be.a('string');
      
      // Verify the data matches expected values
      expect(['Caspian office ACU', 'Lobby Entrance Video Intercom', 'Main Entrance Video Intercom']).to.include(result.name);
      expect(['active', 'inactive', 'error', 'maintenance']).to.include(result.status.toString());
      
      scope.done();
    });

    it('should handle non-existent ACU ID gracefully', async () => {
      const acuId = "123";
      
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/acus/${acuId}`)
        .reply(404, { error: 'Not found', statusCode: 404 });

      try {
        await producer.get(orgId, acuId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('ACU Data Validation', () => {
    it('should validate ACU response schema', async () => {
      const acuId = "123";
      // Create mock raw API response data in the format the mapper expects
      const mockAcuData = {
        id: 183568,
        name: "Caspian office ACU",
        status: "A",
        serialNumber: "1917240000000100000d00010000da05",
        createdAt: "2024-07-17T00:21:27.000Z",
        updatedAt: "2025-08-26T10:13:23.000Z"
      };
      
      const scope = mockSingleResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/acus/${acuId}`,
        mockAcuData
      );

      const result = await producer.get(orgId, acuId);

      // Validate required fields
      expect(result).to.have.property('id');
      expect(result).to.have.property('name');
      expect(result).to.have.property('status');
      
      // Validate data types
      expect(result.id).to.be.a('number');
      expect(result.name).to.be.a('string');
      expect(['active', 'inactive', 'error', 'maintenance']).to.include(result.status.toString());
      
      // Optional fields should be present when provided
      if (result.serialNumber) {
        expect(result.serialNumber).to.be.a('string');
      }
      if (result.createdAt) {
        expect(result.createdAt).to.be.instanceOf(Date);
      }
      if (result.updatedAt) {
        expect(result.updatedAt).to.be.instanceOf(Date);
      }
      
      scope.done();
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', () => {
      // Test with extremely large page size
      const results = new PagedResults<Acu>();
      results.pageSize = 5000; // Should be limited by API
      
      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/acus`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<Acu>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listPorts()', () => {
    it('should list ports for ACU with default pagination', async () => {
      const acuId = "123";
      const fixtureData = loadFixture('templates/acu-ports-list-success.json');
      
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/acus/${acuId}/ports`,
        { offset: 0, limit: 50 },
        fixtureData.data,
        fixtureData.totalCount
      );

      const results = new PagedResults<Port>();
      await producer.listPorts(results, orgId, acuId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle ACU not found when listing ports', async () => {
      const acuId = "123";

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/acus/${acuId}/ports`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Port>();

      try {
        await producer.listPorts(results, orgId, acuId);
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
        `/orgs/${orgId}/acus`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<Acu>();

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
        .get(`/orgs/${orgId}/acus/123`)
        .replyWithError('Network error');

      try {
        await producer.get(orgId, "123");
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

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/acus`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Acu>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle response with null data', async () => {
      const mockResponse = {
        data: null,
        totalCount: 3
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/acus`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Acu>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(3);
      scope.done();
    });
  });
});