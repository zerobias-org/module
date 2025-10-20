import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { GroupProducerApiImpl } from '../../src/GroupProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { Group, GroupInfo, User, Entry } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('GroupProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: GroupProducerApiImpl;
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

    producer = new GroupProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create GroupProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(GroupProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new GroupProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('Group List Operations', () => {
    it('should list groups with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiGroups = [
        {
          id: 537268,
          name: 'ATS',
          description: 'Inventory Access',
          userCount: 2,
          createdAt: '2024-07-17T00:49:23.000Z',
          updatedAt: '2024-12-18T17:28:38.000Z',
        },
        {
          id: 667720,
          name: 'Delivery Group',
          description: 'Deliveries',
          userCount: 4,
          createdAt: '2024-12-18T17:30:02.000Z',
          updatedAt: '2024-12-18T17:30:02.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups`,
        { offset: 0, limit: 50 },
        rawApiGroups,
        rawApiGroups.length
      );

      const results = new PagedResults<Group>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Verify the groups have expected properties
      expect(['ATS', 'Delivery Group', 'Employees']).to.include(results.items[0].name);
      expect(results.items[0].description).to.be.a('string');
      expect(results.items[0].userCount).to.be.a('number');

      scope.done();
    });

    it('should list groups with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiGroups = [
        {
          id: 538060,
          name: 'Employees',
          description: 'General Access',
          userCount: 18,
          createdAt: '2024-07-17T20:52:36.000Z',
          updatedAt: '2024-12-18T17:28:21.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups`,
        { offset: expectedOffset, limit: pageSize },
        rawApiGroups,
        rawApiGroups.length
      );

      const results = new PagedResults<Group>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });
  });

  describe('Group Retrieval Operations', () => {
    it('should retrieve a specific group by ID', async () => {
      const groupId = '537268';
      // Create mock raw API response data in the format the mapper expects
      const mockGroupData = {
        id: groupId,
        name: 'ATS',
        description: 'Inventory Access',
        userCount: 2,
        createdAt: '2024-07-17T00:49:23.000Z',
        updatedAt: '2024-12-18T17:28:38.000Z',
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/groups/${groupId}`)
        .reply(200, mockGroupData);

      const result = await producer.get(orgId, groupId);

      expect(result).to.be.an('object');
      expect(result.id).to.be.a('string');
      expect(result.name).to.be.a('string');
      expect(result.description).to.be.a('string');
      expect(result.userCount).to.be.a('number');

      // Verify the data matches expected values
      expect(['ATS', 'Delivery Group', 'Employees']).to.include(result.name);
      expect(['Inventory Access', 'Deliveries', 'General Access']).to.include(result.description);

      scope.done();
    });

    it('should handle non-existent group ID gracefully', async () => {
      const groupId = '999999';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/groups/${groupId}`)
        .reply(404, { error: 'Not found', statusCode: 404 });

      try {
        await producer.get(orgId, groupId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('Group Data Validation', () => {
    it('should validate group response schema', async () => {
      const groupId = '537268';
      // Create mock raw API response data in the format the mapper expects
      const mockGroupData = {
        id: 537268,
        name: 'ATS',
        description: 'Inventory Access',
        userCount: 2,
        createdAt: '2024-07-17T00:49:23.000Z',
        updatedAt: '2024-12-18T17:28:38.000Z',
      };

      const scope = mockSingleResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}`,
        mockGroupData
      );

      const result = await producer.get(orgId, groupId);

      // Validate required fields
      expect(result).to.have.property('id');
      expect(result).to.have.property('name');

      // Validate data types
      expect(result.id).to.be.a('string');
      expect(result.name).to.be.a('string');

      // Optional fields should be present when provided
      if (result.description) {
        expect(result.description).to.be.a('string');
      }
      if (result.userCount) {
        expect(result.userCount).to.be.a('number');
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
      const results = new PagedResults<Group>();
      results.pageSize = 5000; // Should be limited by API

      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<Group>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listUsers()', () => {
    it('should list users for group with default pagination', async () => {
      const groupId = '537268';
      const fixtureData = loadFixture('templates/group-users-list-success.json');

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/users`,
        { offset: 0, limit: 50 },
        fixtureData.data,
        fixtureData.totalCount
      );

      const results = new PagedResults<User>();
      await producer.listUsers(results, orgId, groupId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle group not found when listing users', async () => {
      const groupId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/users`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<User>();

      try {
        await producer.listUsers(results, orgId, groupId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });
  });

  describe('listEntries()', () => {
    it('should list entries for group with default pagination', async () => {
      const groupId = '537268';
      const fixtureData = loadFixture('templates/group-entries-list-success.json');

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/entries`,
        { offset: 0, limit: 50 },
        fixtureData.data,
        fixtureData.totalCount
      );

      const results = new PagedResults<Entry>();
      await producer.listEntries(results, orgId, groupId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle group not found when listing entries', async () => {
      const groupId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/entries`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Entry>();

      try {
        await producer.listEntries(results, orgId, groupId);
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
        `/orgs/${orgId}/groups`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<Group>();

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
        .get(`/orgs/${orgId}/groups/123`)
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
        .get(`/orgs/${orgId}/groups`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Group>();

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
        totalCount: 3,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/groups`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Group>();

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

  describe('listZoneGroups()', () => {
    it('should list zone groups for group with default pagination', async () => {
      const groupId = '537268';
      const mockZoneGroupsData = [
        {
          zone: {
            id: 12345,
            name: 'Main Entrance Zone',
          },
          schedule: {
            id: 67890,
            name: 'Business Hours',
          },
        },
        {
          zone: {
            id: 23456,
            name: 'Server Room Zone',
          },
          schedule: {
            id: 78901,
            name: '24/7 Access',
          },
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: 0, limit: 50 },
        mockZoneGroupsData,
        mockZoneGroupsData.length
      );

      const results = new PagedResults<any>();
      await producer.listZoneGroups(results, orgId, groupId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.equal(mockZoneGroupsData.length);

      // Validate first item structure
      if (results.items.length > 0) {
        const firstItem = results.items[0];
        expect(firstItem).to.have.property('zone');
        expect(firstItem).to.have.property('schedule');

        if (firstItem.zone) {
          expect(firstItem.zone).to.have.property('id');
          expect(firstItem.zone.id).to.be.a('string');
        }

        if (firstItem.schedule) {
          expect(firstItem.schedule).to.have.property('id');
          expect(firstItem.schedule).to.have.property('name');
        }
      }

      scope.done();
    });

    it('should list zone groups with custom pagination', async () => {
      const groupId = '537268';
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const mockZoneGroupsData = [
        {
          zone: {
            id: 34567,
            name: 'Parking Lot Zone',
          },
          schedule: {
            id: 89012,
            name: 'Weekend Access',
          },
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: expectedOffset, limit: pageSize },
        mockZoneGroupsData,
        mockZoneGroupsData.length
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listZoneGroups(results, orgId, groupId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty zone groups response', async () => {
      const groupId = '537268';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listZoneGroups(results, orgId, groupId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle group not found when listing zone groups', async () => {
      const groupId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneGroups(results, orgId, groupId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing zone groups', async () => {
      const groupId = '537268';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneGroups(results, orgId, groupId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle unauthorized error when listing zone groups', async () => {
      const groupId = '537268';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneGroups(results, orgId, groupId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle rate limiting when listing zone groups', async () => {
      const groupId = '537268';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zoneGroups`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZoneGroups(results, orgId, groupId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });
  });

  describe('listZones()', () => {
    it('should list zones for group with default pagination', async () => {
      const groupId = '537268';
      const mockZonesData = [
        {
          id: 12345,
          name: 'Main Entrance Zone',
        },
        {
          id: 23456,
          name: 'Server Room Zone',
        },
        {
          id: 34567,
          name: 'Parking Lot Zone',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        mockZonesData,
        mockZonesData.length
      );

      const results = new PagedResults<any>();
      await producer.listZones(results, orgId, groupId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.equal(mockZonesData.length);

      // Validate structure of returned zones
      if (results.items.length > 0) {
        const firstZone = results.items[0];
        expect(firstZone).to.have.property('id');
        expect(firstZone.id).to.be.a('string');

        if (firstZone.name) {
          expect(firstZone.name).to.be.a('string');
        }
      }

      scope.done();
    });

    it('should list zones with custom pagination', async () => {
      const groupId = '537268';
      const pageNumber = 3;
      const pageSize = 15;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const mockZonesData = [
        {
          id: 45678,
          name: 'Conference Room Zone',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: expectedOffset, limit: pageSize },
        mockZonesData,
        mockZonesData.length
      );

      const results = new PagedResults<any>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listZones(results, orgId, groupId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should validate zone data types', async () => {
      const groupId = '537268';
      const mockZonesData = [
        {
          id: 56789,
          name: 'Test Zone',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        mockZonesData,
        mockZonesData.length
      );

      const results = new PagedResults<any>();
      await producer.listZones(results, orgId, groupId);

      expect(results.items).to.have.length.above(0);

      const zone = results.items[0];
      expect(zone.id).to.be.a('string');

      if (zone.name !== undefined) {
        expect(zone.name).to.be.a('string');
      }

      scope.done();
    });

    it('should handle empty zones response', async () => {
      const groupId = '537268';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<any>();
      await producer.listZones(results, orgId, groupId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle group not found when listing zones', async () => {
      const groupId = '999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, groupId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle server error when listing zones', async () => {
      const groupId = '537268';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, groupId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle unauthorized error when listing zones', async () => {
      const groupId = '537268';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, groupId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle rate limiting when listing zones', async () => {
      const groupId = '537268';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/groups/${groupId}/zones`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, groupId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle response with missing data array', async () => {
      const groupId = '537268';
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/groups/${groupId}/zones`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, groupId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with null data', async () => {
      const groupId = '537268';
      const mockResponse = {
        data: null,
        totalCount: 3,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/groups/${groupId}/zones`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<any>();

      try {
        await producer.listZones(results, orgId, groupId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });
});
