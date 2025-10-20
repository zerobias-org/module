import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { ZoneProducerApiImpl } from '../../src/ZoneProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { Zone, ZoneShare, ZoneZoneUser } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('ZoneProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: ZoneProducerApiImpl;
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

    producer = new ZoneProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create ZoneProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(ZoneProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new ZoneProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('Zone List Operations', () => {
    it('should list zones with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiZones = [
        {
          id: 12345,
          name: 'Building A - Main Entrance',
          description: 'Primary access zone for Building A',
          opal: true,
          entryCount: 5,
          offlineEntryCount: 1,
          userCount: 25,
          groupCount: 3,
          org: {
            id: 1067,
            name: 'Test Organization',
          },
          site: {
            id: 5678,
            name: 'Main Campus',
          },
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-03-20T14:30:00.000Z',
        },
        {
          id: 12346,
          name: 'Building B - Secure Lab',
          description: 'Restricted access zone',
          opal: false,
          entryCount: 2,
          offlineEntryCount: 0,
          userCount: 8,
          groupCount: 1,
          org: {
            id: 1067,
            name: 'Test Organization',
          },
          site: {
            id: 5678,
            name: 'Main Campus',
          },
          createdAt: '2024-02-10T08:00:00.000Z',
          updatedAt: '2024-03-18T16:45:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        rawApiZones,
        rawApiZones.length
      );

      const results = new PagedResults<Zone>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Verify the zones have expected properties
      expect(results.items[0].name).to.be.a('string');
      expect(results.items[0].id).to.be.a('string');
      if (results.items[0].description) {
        expect(results.items[0].description).to.be.a('string');
      }
      if (results.items[0].entryCount !== undefined) {
        expect(results.items[0].entryCount).to.be.a('number');
      }
      if (results.items[0].userCount !== undefined) {
        expect(results.items[0].userCount).to.be.a('number');
      }

      scope.done();
    });

    it('should list zones with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiZones = [
        {
          id: 12347,
          name: 'Warehouse Zone',
          description: 'Storage area access',
          opal: true,
          entryCount: 3,
          userCount: 12,
          groupCount: 2,
          org: {
            id: 1067,
            name: 'Test Organization',
          },
          createdAt: '2024-03-01T09:00:00.000Z',
          updatedAt: '2024-03-15T11:20:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: expectedOffset, limit: pageSize },
        rawApiZones,
        rawApiZones.length
      );

      const results = new PagedResults<Zone>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle pagination with limit clamping', async () => {
      const pageNumber = 1;
      const pageSize = 5000; // Exceeds max of 1000
      const expectedLimit = 1000; // Should be clamped to max

      const rawApiZones = [
        {
          id: 12348,
          name: 'Test Zone',
          org: {
            id: 1067,
            name: 'Test Organization',
          },
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: expectedLimit },
        rawApiZones,
        1
      );

      const results = new PagedResults<Zone>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle empty zone list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<Zone>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });
  });

  describe('Zone Data Validation', () => {
    it('should validate zone response schema', async () => {
      // Create mock raw API response data in the format the mapper expects
      const mockZoneData = {
        id: 12345,
        name: 'Test Zone',
        description: 'Test Description',
        opal: true,
        entryCount: 5,
        offlineEntryCount: 1,
        userCount: 20,
        groupCount: 3,
        org: {
          id: 1067,
          name: 'Test Organization',
        },
        site: {
          id: 5678,
          name: 'Test Site',
        },
        createdAt: '2024-01-15T10:00:00.000Z',
        updatedAt: '2024-03-20T14:30:00.000Z',
      };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        [mockZoneData],
        1
      );

      const results = new PagedResults<Zone>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(1);
      const zone = results.items[0];

      // Validate required fields
      expect(zone).to.have.property('id');
      expect(zone).to.have.property('name');

      // Validate data types
      expect(zone.id).to.be.a('string');
      expect(zone.name).to.be.a('string');

      // Optional fields should have correct types when present
      if (zone.description) {
        expect(zone.description).to.be.a('string');
      }
      if (zone.opal !== undefined) {
        expect(zone.opal).to.be.a('boolean');
      }
      if (zone.entryCount !== undefined) {
        expect(zone.entryCount).to.be.a('number');
      }
      if (zone.userCount !== undefined) {
        expect(zone.userCount).to.be.a('number');
      }
      if (zone.createdAt) {
        expect(zone.createdAt).to.be.instanceOf(Date);
      }
      if (zone.updatedAt) {
        expect(zone.updatedAt).to.be.instanceOf(Date);
      }

      scope.done();
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid response format - missing data array', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/zones`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          totalCount: 0,
          // Missing 'data' array
        });

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle invalid response format - null data', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/zones`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: null,
          totalCount: 5,
        });

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle invalid response format - non-array data', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/zones`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: 'not an array',
          totalCount: 1,
        });

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle 401 unauthorized error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle 404 not found error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should propagate server errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<Zone>();

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
        .get(`/orgs/${orgId}/zones`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<Zone>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listZoneShares()', () => {
    const zoneId = '12345';

    it('should list zone shares with default pagination', async () => {
      const rawApiZoneShares = [
        {
          id: 98765,
          zoneId: '12345',
          sharedWithOrgId: '2000',
          sharedWithOrg: {
            id: 2000,
            name: 'Partner Organization',
          },
          createdAt: '2024-01-20T12:00:00.000Z',
          updatedAt: '2024-02-15T14:00:00.000Z',
        },
        {
          id: 98766,
          zoneId: '12345',
          sharedWithOrgId: '2001',
          sharedWithOrg: {
            id: 2001,
            name: 'External Vendor',
          },
          createdAt: '2024-02-01T09:00:00.000Z',
          updatedAt: '2024-02-20T16:30:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneShares`,
        { offset: 0, limit: 50 },
        rawApiZoneShares,
        rawApiZoneShares.length
      );

      const results = new PagedResults<ZoneShare>();
      await producer.listZoneShares(results, orgId, zoneId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);

      const share = results.items[0];
      expect(share).to.have.property('id');
      expect(share.id).to.be.a('string');

      if (share.zoneId) {
        expect(share.zoneId).to.be.a('string');
      }
      if (share.sharedWithOrgId) {
        expect(share.sharedWithOrgId).to.be.a('string');
      }
      if (share.createdAt) {
        expect(share.createdAt).to.be.instanceOf(Date);
      }

      scope.done();
    });

    it('should list zone shares with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiZoneShares = [
        {
          id: 98767,
          zoneId: '12345',
          sharedWithOrgId: '2002',
          createdAt: '2024-03-01T10:00:00.000Z',
          updatedAt: '2024-03-10T12:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneShares`,
        { offset: expectedOffset, limit: pageSize },
        rawApiZoneShares,
        1
      );

      const results = new PagedResults<ZoneShare>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listZoneShares(results, orgId, zoneId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty zone shares list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneShares`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<ZoneShare>();
      await producer.listZoneShares(results, orgId, zoneId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle zone not found when listing shares', async () => {
      const invalidZoneId = '999999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${invalidZoneId}/zoneShares`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<ZoneShare>();

      try {
        await producer.listZoneShares(results, orgId, invalidZoneId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle invalid response format for zone shares', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/zones/${zoneId}/zoneShares`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          totalCount: 1,
          // Missing 'data' array
        });

      const results = new PagedResults<ZoneShare>();

      try {
        await producer.listZoneShares(results, orgId, zoneId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle rate limiting for zone shares', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneShares`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<ZoneShare>();

      try {
        await producer.listZoneShares(results, orgId, zoneId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle server errors for zone shares', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneShares`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<ZoneShare>();

      try {
        await producer.listZoneShares(results, orgId, zoneId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listZoneUsers()', () => {
    const zoneId = '12345';

    it('should list zone users with default pagination', async () => {
      const rawApiZoneUsers = [
        {
          user: {
            id: 22509982,
            status: 'a',
            identity: {
              id: 'identity-22509982',
              email: 'user1@example.com',
              firstName: 'John',
              lastName: 'Doe',
              mobilePhone: '+1-555-0123',
            },
          },
          schedule: {
            id: 5001,
            name: 'Business Hours',
            type: 'standard',
          },
        },
        {
          user: {
            id: 22509983,
            status: 'a',
            identity: {
              id: 'identity-22509983',
              email: 'user2@example.com',
              firstName: 'Jane',
              lastName: 'Smith',
              mobilePhone: '+1-555-0124',
            },
          },
          schedule: {
            id: 5002,
            name: '24/7 Access',
            type: 'always',
          },
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        rawApiZoneUsers,
        rawApiZoneUsers.length
      );

      const results = new PagedResults<ZoneZoneUser>();
      await producer.listZoneUsers(results, orgId, zoneId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);

      const zoneUser = results.items[0];
      expect(zoneUser).to.have.property('user');

      if (zoneUser.user) {
        expect(zoneUser.user).to.have.property('id');
        expect(zoneUser.user.id).to.be.a('string');
      }

      scope.done();
    });

    it('should list zone users with custom pagination', async () => {
      const pageNumber = 3;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiZoneUsers = [
        {
          user: {
            id: 22509984,
            status: 'i',
            identity: {
              id: 'identity-22509984',
              email: 'user3@example.com',
              firstName: 'Bob',
              lastName: 'Johnson',
            },
          },
          schedule: {
            id: 5003,
            name: 'Weekend Only',
          },
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: expectedOffset, limit: pageSize },
        rawApiZoneUsers,
        1
      );

      const results = new PagedResults<ZoneZoneUser>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listZoneUsers(results, orgId, zoneId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty zone users list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<ZoneZoneUser>();
      await producer.listZoneUsers(results, orgId, zoneId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle zone not found when listing users', async () => {
      const invalidZoneId = '999999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${invalidZoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<ZoneZoneUser>();

      try {
        await producer.listZoneUsers(results, orgId, invalidZoneId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should validate zone user response schema', async () => {
      const rawApiZoneUsers = [
        {
          user: {
            id: 22509985,
            status: 'a',
            identity: {
              id: 'identity-22509985',
              email: 'test@example.com',
              firstName: 'Test',
              lastName: 'User',
              mobilePhone: '+1-555-9999',
            },
          },
          schedule: {
            id: 5004,
            name: 'Custom Schedule',
            type: 'custom',
          },
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        rawApiZoneUsers,
        1
      );

      const results = new PagedResults<ZoneZoneUser>();
      await producer.listZoneUsers(results, orgId, zoneId);

      expect(results.items).to.have.length(1);
      const zoneUser = results.items[0];

      // Validate structure
      expect(zoneUser).to.have.property('user');

      if (zoneUser.user) {
        expect(zoneUser.user).to.have.property('id');
        expect(zoneUser.user.id).to.be.a('string');

        if (zoneUser.user.status) {
          expect(zoneUser.user.status).to.be.a('string');
        }

        if (zoneUser.user.identity) {
          expect(zoneUser.user.identity).to.have.property('id');
        }
      }

      if (zoneUser.schedule) {
        expect(zoneUser.schedule).to.have.property('id');
      }

      scope.done();
    });

    it('should handle invalid response format for zone users', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/zones/${zoneId}/zoneUsers`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {
          data: 'invalid',
          totalCount: 1,
        });

      const results = new PagedResults<ZoneZoneUser>();

      try {
        await producer.listZoneUsers(results, orgId, zoneId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle rate limiting for zone users', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<ZoneZoneUser>();

      try {
        await producer.listZoneUsers(results, orgId, zoneId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle unauthorized errors for zone users', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<ZoneZoneUser>();

      try {
        await producer.listZoneUsers(results, orgId, zoneId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle server errors for zone users', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones/${zoneId}/zoneUsers`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<ZoneZoneUser>();

      try {
        await producer.listZoneUsers(results, orgId, zoneId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors for zone users', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/zones/${zoneId}/zoneUsers`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network timeout');

      const results = new PagedResults<ZoneZoneUser>();

      try {
        await producer.listZoneUsers(results, orgId, zoneId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Pagination Edge Cases', () => {
    it('should handle pagination with minimal page size', async () => {
      const pageNumber = 1;
      const pageSize = 1; // Minimum allowed

      const rawApiZones = [
        {
          id: 12349,
          name: 'Single Zone',
          org: { id: 1067, name: 'Test Org' },
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 1 },
        rawApiZones,
        1
      );

      const results = new PagedResults<Zone>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle pagination without pageNumber and pageSize', async () => {
      const rawApiZones = [
        {
          id: 12350,
          name: 'Default Pagination Zone',
          org: { id: 1067, name: 'Test Org' },
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      // When no pageNumber/pageSize are set, implementation defaults to offset=0
      // PagedResults has default pageSize of 50 which gets sent
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/zones`,
        { offset: 0, limit: 50 },
        rawApiZones,
        1
      );

      const results = new PagedResults<Zone>();
      // Don't set pageNumber or pageSize

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(1);
      expect(results.count).to.equal(1);
      scope.done();
    });
  });
});
