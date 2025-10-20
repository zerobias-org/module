import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { SiteProducerApiImpl } from '../../src/SiteProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { Site } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('SiteProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: SiteProducerApiImpl;
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

    producer = new SiteProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create SiteProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(SiteProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new SiteProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('Site List Operations', () => {
    it('should list sites with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiSites = [
        {
          id: 103697,
          name: 'Chicago Office',
          opal: 'CHI-001',
          address: '123 Main St',
          address2: 'Suite 100',
          city: 'Chicago',
          state: 'IL',
          zip: '60601',
          country: 'USA',
          phone: '+1-312-555-0100',
          language: 'en',
          zoneCount: 5,
          userCount: 25,
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-12-18T15:30:00.000Z',
        },
        {
          id: 103698,
          name: 'New York Office',
          opal: 'NYC-001',
          address: '456 Broadway',
          city: 'New York',
          state: 'NY',
          zip: '10013',
          country: 'USA',
          phone: '+1-212-555-0200',
          language: 'en',
          zoneCount: 8,
          userCount: 45,
          createdAt: '2024-02-20T14:30:00.000Z',
          updatedAt: '2024-12-18T16:45:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Verify the sites have expected properties
      expect(['Chicago Office', 'New York Office', 'Los Angeles Office']).to.include(results.items[0].name);
      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].name).to.be.a('string');

      // Verify optional properties exist
      if (results.items[0].address) {
        expect(results.items[0].address).to.be.a('string');
      }
      if (results.items[0].zoneCount) {
        expect(results.items[0].zoneCount).to.be.a('number');
      }
      if (results.items[0].userCount) {
        expect(results.items[0].userCount).to.be.a('number');
      }

      scope.done();
    });

    it('should list sites with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiSites = [
        {
          id: 103699,
          name: 'Los Angeles Office',
          opal: 'LA-001',
          address: '789 Sunset Blvd',
          city: 'Los Angeles',
          state: 'CA',
          zip: '90028',
          country: 'USA',
          phone: '+1-323-555-0300',
          language: 'en',
          zoneCount: 6,
          userCount: 30,
          createdAt: '2024-03-10T09:15:00.000Z',
          updatedAt: '2024-12-18T17:20:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: expectedOffset, limit: pageSize },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle pageSize limit of 1000', async () => {
      const pageNumber = 1;
      const pageSize = 2000; // Request exceeds max
      const expectedLimit = 1000; // Should be capped at 1000
      const expectedOffset = 0;

      const rawApiSites = [
        {
          id: 103700,
          name: 'Miami Office',
          opal: 'MIA-001',
          address: '321 Ocean Drive',
          city: 'Miami',
          state: 'FL',
          zip: '33139',
          country: 'USA',
          language: 'en',
          zoneCount: 4,
          userCount: 20,
          createdAt: '2024-04-05T11:00:00.000Z',
          updatedAt: '2024-12-18T18:10:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: expectedOffset, limit: expectedLimit },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle minimum pageSize of 1', async () => {
      const pageNumber = 1;
      const pageSize = 1; // Minimum page size
      const expectedLimit = 1;
      const expectedOffset = 0;

      const rawApiSites = [
        {
          id: 103701,
          name: 'Seattle Office',
          opal: 'SEA-001',
          address: '555 Pike St',
          city: 'Seattle',
          state: 'WA',
          zip: '98101',
          country: 'USA',
          language: 'en',
          zoneCount: 7,
          userCount: 35,
          createdAt: '2024-05-12T08:45:00.000Z',
          updatedAt: '2024-12-18T19:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: expectedOffset, limit: expectedLimit },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle list without pagination parameters', async () => {
      const rawApiSites = [
        {
          id: 103702,
          name: 'Boston Office',
          opal: 'BOS-001',
          address: '101 Federal St',
          city: 'Boston',
          state: 'MA',
          zip: '02110',
          country: 'USA',
          language: 'en',
          zoneCount: 3,
          userCount: 15,
          createdAt: '2024-06-18T13:20:00.000Z',
          updatedAt: '2024-12-18T20:15:00.000Z',
        },
      ];

      // When no pageNumber/pageSize are set, implementation defaults to offset=0
      // PagedResults has default pageSize of 50 which gets sent
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      // Don't set pageNumber or pageSize - uses PagedResults defaults

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });
  });

  describe('Site Data Validation', () => {
    it('should validate site response schema with all fields', async () => {
      // Create mock raw API response data with all fields
      const rawApiSites = [
        {
          id: 103703,
          name: 'Full Site Data',
          opal: 'FULL-001',
          address: '999 Complete Ave',
          address2: 'Building A',
          city: 'Denver',
          state: 'CO',
          zip: '80202',
          country: 'USA',
          phone: '+1-303-555-0400',
          language: 'en',
          zoneCount: 10,
          userCount: 50,
          createdAt: '2024-07-25T10:30:00.000Z',
          updatedAt: '2024-12-18T21:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      const site = results.items[0];

      // Validate required fields
      expect(site).to.have.property('id');
      expect(site).to.have.property('name');

      // Validate data types for required fields
      expect(site.id).to.be.a('string');
      expect(site.name).to.be.a('string');

      // Validate optional fields when present
      if (site.opal) {
        expect(site.opal).to.be.a('string');
      }
      if (site.address) {
        expect(site.address).to.be.a('string');
      }
      if (site.address2) {
        expect(site.address2).to.be.a('string');
      }
      if (site.city) {
        expect(site.city).to.be.a('string');
      }
      if (site.state) {
        expect(site.state).to.be.a('string');
      }
      if (site.zip) {
        expect(site.zip).to.be.a('string');
      }
      if (site.country) {
        expect(site.country).to.be.a('string');
      }
      if (site.phone) {
        expect(site.phone).to.be.a('string');
      }
      if (site.language) {
        expect(site.language).to.be.a('string');
      }
      if (site.zoneCount !== undefined) {
        expect(site.zoneCount).to.be.a('number');
      }
      if (site.userCount !== undefined) {
        expect(site.userCount).to.be.a('number');
      }
      if (site.createdAt) {
        expect(site.createdAt).to.be.instanceOf(Date);
      }
      if (site.updatedAt) {
        expect(site.updatedAt).to.be.instanceOf(Date);
      }

      scope.done();
    });

    it('should validate site response schema with minimal fields', async () => {
      // Create mock raw API response data with only required fields
      const rawApiSites = [
        {
          id: 103704,
          name: 'Minimal Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      const site = results.items[0];

      // Validate required fields are present
      expect(site).to.have.property('id');
      expect(site).to.have.property('name');
      expect(site.id).to.be.a('string');
      expect(site.name).to.be.a('string');

      scope.done();
    });

    it('should handle dates correctly in site data', async () => {
      const testDate = '2024-12-18T15:30:45.000Z';
      const rawApiSites = [
        {
          id: 103705,
          name: 'Date Test Site',
          createdAt: testDate,
          updatedAt: testDate,
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      const site = results.items[0];

      if (site.createdAt) {
        expect(site.createdAt).to.be.instanceOf(Date);
        expect(site.createdAt.toISOString()).to.equal(testDate);
      }
      if (site.updatedAt) {
        expect(site.updatedAt).to.be.instanceOf(Date);
        expect(site.updatedAt.toISOString()).to.equal(testDate);
      }

      scope.done();
    });

    it('should convert numeric id to string', async () => {
      const rawApiSites = [
        {
          id: 999999, // numeric id
          name: 'Numeric ID Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].id).to.equal('999999');

      scope.done();
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', () => {
      // Test with extremely large page size
      const results = new PagedResults<Site>();
      results.pageSize = 5000; // Should be limited by API

      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should handle API rate limiting (429)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should propagate server errors (500)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle unauthorized errors (401)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        401,
        'Unauthorized'
      );

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/sites`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
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
        .get(`/orgs/${orgId}/sites`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with null data array', async () => {
      const mockResponse = {
        data: null,
        totalCount: 5,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/sites`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with empty data array', async () => {
      const mockResponse = {
        data: [],
        totalCount: 0,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/sites`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);

      scope.done();
    });

    it('should handle response with non-array data', async () => {
      const mockResponse = {
        data: { id: 123, name: 'Not an array' },
        totalCount: 1,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/sites`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Site>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with missing totalCount', async () => {
      const rawApiSites = [
        {
          id: 103706,
          name: 'Missing Count Site',
        },
      ];

      const mockResponse = {
        data: rawApiSites,
        // Missing totalCount
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/sites`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(1);
      expect(results.count).to.equal(0); // Should default to 0

      scope.done();
    });

    it('should map all site fields correctly', async () => {
      const rawApiSites = [
        {
          id: 103707,
          name: 'Complete Mapping Test',
          opal: 'TEST-001',
          address: '123 Test St',
          address2: 'Floor 2',
          city: 'Austin',
          state: 'TX',
          zip: '78701',
          country: 'USA',
          phone: '+1-512-555-0500',
          language: 'en',
          zoneCount: 12,
          userCount: 60,
          createdAt: '2024-08-30T16:45:00.000Z',
          updatedAt: '2024-12-18T22:30:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, orgId);

      const site = results.items[0];

      expect(site.id).to.equal('103707');
      expect(site.name).to.equal('Complete Mapping Test');
      expect(site.opal).to.equal('TEST-001');
      expect(site.address).to.equal('123 Test St');
      expect(site.address2).to.equal('Floor 2');
      expect(site.city).to.equal('Austin');
      expect(site.state).to.equal('TX');
      expect(site.zip).to.equal('78701');
      expect(site.country).to.equal('USA');
      expect(site.phone).to.equal('+1-512-555-0500');
      expect(site.language).to.equal('en');
      expect(site.zoneCount).to.equal(12);
      expect(site.userCount).to.equal(60);
      expect(site.createdAt).to.be.instanceOf(Date);
      expect(site.updatedAt).to.be.instanceOf(Date);

      scope.done();
    });
  });

  describe('Pagination Calculation', () => {
    it('should calculate offset correctly for page 1', async () => {
      const pageNumber = 1;
      const pageSize = 25;
      const expectedOffset = 0;

      const rawApiSites = [
        {
          id: 103708,
          name: 'Page 1 Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: expectedOffset, limit: pageSize },
        rawApiSites,
        100
      );

      const results = new PagedResults<Site>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should calculate offset correctly for page 3', async () => {
      const pageNumber = 3;
      const pageSize = 25;
      const expectedOffset = 50; // (3 - 1) * 25

      const rawApiSites = [
        {
          id: 103709,
          name: 'Page 3 Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: expectedOffset, limit: pageSize },
        rawApiSites,
        100
      );

      const results = new PagedResults<Site>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle large page numbers', async () => {
      const pageNumber = 100;
      const pageSize = 50;
      const expectedOffset = 4950; // (100 - 1) * 50

      const rawApiSites = [
        {
          id: 103710,
          name: 'Large Page Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/sites`,
        { offset: expectedOffset, limit: pageSize },
        rawApiSites,
        5000
      );

      const results = new PagedResults<Site>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });
  });

  describe('Organization ID Handling', () => {
    it('should use correct organization ID in request path', async () => {
      const specificOrgId = '12345';

      const rawApiSites = [
        {
          id: 103711,
          name: 'Org Specific Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${specificOrgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, specificOrgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle special characters in organization ID', async () => {
      const specialOrgId = 'org-with-dashes-123';

      const rawApiSites = [
        {
          id: 103712,
          name: 'Special Org Site',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${specialOrgId}/sites`,
        { offset: 0, limit: 50 },
        rawApiSites,
        rawApiSites.length
      );

      const results = new PagedResults<Site>();
      await producer.list(results, specialOrgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });
  });
});
