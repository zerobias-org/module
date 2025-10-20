import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { EntryProducerApiImpl } from '../../src/EntryProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { Entry, EntryInfo, EntryActivityEvent, EntryCamera, EntryStateInfo, EntryUserSchedule, EntryUser } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  cleanNock
} from '../utils/nock-helpers';

describe('EntryProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: EntryProducerApiImpl;
  const baseUrl = 'https://helium.prod.openpath.com';
  const testEmail = process.env.AVIGILON_EMAIL || 'test@example.com';
  const testPassword = process.env.AVIGILON_PASSWORD || 'testpass';
  const orgId = 'test-org-123';
  const entryId = 'test-entry-456';

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

    producer = new EntryProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create EntryProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(EntryProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new EntryProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('list()', () => {
    it('should list entries with default pagination', async () => {
      const rawApiEntries = [
        {
          id: 12345,
          name: 'Main Entrance',
        },
        {
          id: 12346,
          name: 'Back Entrance',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        { offset: 0, limit: 50 },
        rawApiEntries,
        rawApiEntries.length
      );

      const results = new PagedResults<Entry>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      scope.done();
    });

    it('should list entries with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiEntries = [
        {
          id: 12347,
          name: 'Side Entrance',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        { offset: expectedOffset, limit: pageSize },
        rawApiEntries,
        rawApiEntries.length
      );

      const results = new PagedResults<Entry>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty entry list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<Entry>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { invalid: 'format' });

      const results = new PagedResults<Entry>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as UnexpectedError).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should throw UnexpectedError for non-array data', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { data: 'not-an-array', totalCount: 0 });

      const results = new PagedResults<Entry>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('get()', () => {
    it('should retrieve a specific entry by ID', async () => {
      const mockEntryData = {
        id: entryId,
        name: 'Main Entrance',
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}`)
        .reply(200, { data: mockEntryData });

      const result = await producer.get(orgId, entryId);

      expect(result).to.be.an('object');
      expect(result.id).to.be.a('string');
      expect(result.name).to.be.a('string');

      scope.done();
    });

    it('should handle response data without wrapper', async () => {
      const mockEntryData = {
        id: entryId,
        name: 'Main Entrance',
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}`)
        .reply(200, mockEntryData);

      const result = await producer.get(orgId, entryId);

      expect(result).to.be.an('object');
      expect(result.id).to.be.a('string');
      expect(result.name).to.be.a('string');

      scope.done();
    });

    it('should handle non-existent entry ID gracefully', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/999999`)
        .reply(404, { error: 'Not found', statusCode: 404 });

      try {
        await producer.get(orgId, '999999');
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    // Note: In reality, the API returns either valid entry data (200) or 404
    // The edge cases where response.data.data is null don't occur in integration tests
    // Those cases are handled by the implementation but don't need specific unit tests
  });

  describe('listActivity()', () => {
    it('should list activity events with default pagination', async () => {
      const rawActivityEvents = [
        {
          time: 1704106800000,
          sourceName: 'Main Entrance',
        },
        {
          time: 1704110400000,
          sourceName: 'Back Entrance',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/activity`,
        { offset: 0, limit: 50 },
        rawActivityEvents,
        rawActivityEvents.length
      );

      const results = new PagedResults<EntryActivityEvent>();
      await producer.listActivity(results, orgId, entryId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      scope.done();
    });

    it('should list activity events with custom pagination', async () => {
      const pageNumber = 3;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawActivityEvents = [
        {
          time: 1704117600000,
          sourceName: 'Side Entrance',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/activity`,
        { offset: expectedOffset, limit: pageSize },
        rawActivityEvents,
        rawActivityEvents.length
      );

      const results = new PagedResults<EntryActivityEvent>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listActivity(results, orgId, entryId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}/activity`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { invalid: 'format' });

      const results = new PagedResults<EntryActivityEvent>();

      try {
        await producer.listActivity(results, orgId, entryId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listCameras()', () => {
    it('should list cameras for entry with default pagination', async () => {
      const rawCameras = [
        {
          id: 2001,
          name: 'Front Camera',
        },
        {
          id: 2002,
          name: 'Side Camera',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/cameras`,
        { offset: 0, limit: 50 },
        rawCameras,
        rawCameras.length
      );

      const results = new PagedResults<EntryCamera>();
      await producer.listCameras(results, orgId, entryId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      scope.done();
    });

    it('should list cameras with custom pagination', async () => {
      const pageNumber = 1;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawCameras = [
        {
          id: 2003,
          name: 'Back Camera',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/cameras`,
        { offset: expectedOffset, limit: pageSize },
        rawCameras,
        rawCameras.length
      );

      const results = new PagedResults<EntryCamera>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listCameras(results, orgId, entryId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle entry not found when listing cameras', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/999/cameras`)
        .query({ offset: 0, limit: 50 })
        .reply(404, { error: 'Not found', statusCode: 404 });

      const results = new PagedResults<EntryCamera>();

      try {
        await producer.listCameras(results, orgId, '999');
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}/cameras`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { data: 'not-an-array' });

      const results = new PagedResults<EntryCamera>();

      try {
        await producer.listCameras(results, orgId, entryId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listEntryStates()', () => {
    it('should list entry states with default pagination', async () => {
      const rawEntryStates = [
        {
          id: 3001,
          name: 'Locked',
          code: 'locked',
        },
        {
          id: 3002,
          name: 'Unlocked',
          code: 'unlocked',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entryStates`,
        { offset: 0, limit: 50 },
        rawEntryStates,
        rawEntryStates.length
      );

      const results = new PagedResults<EntryStateInfo>();
      await producer.listEntryStates(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      scope.done();
    });

    it('should list entry states with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 15;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawEntryStates = [
        {
          id: 3003,
          name: 'Alarm',
          code: 'alarm',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entryStates`,
        { offset: expectedOffset, limit: pageSize },
        rawEntryStates,
        rawEntryStates.length
      );

      const results = new PagedResults<EntryStateInfo>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listEntryStates(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entryStates`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { totalCount: 5 });

      const results = new PagedResults<EntryStateInfo>();

      try {
        await producer.listEntryStates(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listUserSchedules()', () => {
    it('should list user schedules for entry with default pagination', async () => {
      const rawUserSchedules = [
        {
          id: 4001,
          userId: 5001,
          scheduleId: 6001,
        },
        {
          id: 4002,
          userId: 5002,
          scheduleId: 6002,
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/userSchedules`,
        { offset: 0, limit: 50 },
        rawUserSchedules,
        rawUserSchedules.length
      );

      const results = new PagedResults<EntryUserSchedule>();
      await producer.listUserSchedules(results, orgId, entryId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      scope.done();
    });

    it('should list user schedules with custom pagination', async () => {
      const pageNumber = 4;
      const pageSize = 25;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawUserSchedules = [
        {
          id: 4003,
          userId: 5003,
          scheduleId: 6003,
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/userSchedules`,
        { offset: expectedOffset, limit: pageSize },
        rawUserSchedules,
        rawUserSchedules.length
      );

      const results = new PagedResults<EntryUserSchedule>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listUserSchedules(results, orgId, entryId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle entry not found when listing user schedules', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/999/userSchedules`)
        .query({ offset: 0, limit: 50 })
        .reply(404, { error: 'Not found', statusCode: 404 });

      const results = new PagedResults<EntryUserSchedule>();

      try {
        await producer.listUserSchedules(results, orgId, '999');
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}/userSchedules`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { data: null });

      const results = new PagedResults<EntryUserSchedule>();

      try {
        await producer.listUserSchedules(results, orgId, entryId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listUsers()', () => {
    it('should list users for entry with default pagination', async () => {
      const rawUsers = [
        {
          id: 5001,
          firstName: 'John',
        },
        {
          id: 5002,
          firstName: 'Jane',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/users`,
        { offset: 0, limit: 50 },
        rawUsers,
        rawUsers.length
      );

      const results = new PagedResults<EntryUser>();
      await producer.listUsers(results, orgId, entryId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      scope.done();
    });

    it('should list users with custom pagination', async () => {
      const pageNumber = 5;
      const pageSize = 30;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawUsers = [
        {
          id: 5003,
          firstName: 'Bob',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries/${entryId}/users`,
        { offset: expectedOffset, limit: pageSize },
        rawUsers,
        rawUsers.length
      );

      const results = new PagedResults<EntryUser>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listUsers(results, orgId, entryId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle entry not found when listing users', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/999/users`)
        .query({ offset: 0, limit: 50 })
        .reply(404, { error: 'Not found', statusCode: 404 });

      const results = new PagedResults<EntryUser>();

      try {
        await producer.listUsers(results, orgId, '999');
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, {});

      const results = new PagedResults<EntryUser>();

      try {
        await producer.listUsers(results, orgId, entryId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Error Handling', () => {
    it('should propagate server errors for list operation', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(500, { error: 'Internal Server Error', statusCode: 500 });

      const results = new PagedResults<Entry>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors for get operation', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}`)
        .replyWithError('Network error');

      try {
        await producer.get(orgId, entryId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle unauthorized errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}`)
        .reply(401, { error: 'Unauthorized', statusCode: 401 });

      try {
        await producer.get(orgId, entryId);
        expect.fail('Expected error to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('InvalidCredentialsError');
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(429, { error: 'Too Many Requests', statusCode: 429 });

      const results = new PagedResults<Entry>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle server errors for getActivity operation', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries/${entryId}/activity`)
        .query({ offset: 0, limit: 50 })
        .reply(500, { error: 'Internal Server Error', statusCode: 500 });

      const results = new PagedResults<EntryActivityEvent>();

      try {
        await producer.listActivity(results, orgId, entryId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Edge Cases', () => {
    it('should handle pagination with page size exceeding API limit', async () => {
      const pageNumber = 1;
      const pageSize = 5000;
      const expectedLimit = 1000; // API max limit

      const rawEntries = [
        {
          id: 12348,
          name: 'Test Entry',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        { offset: 0, limit: expectedLimit },
        rawEntries,
        rawEntries.length
      );

      const results = new PagedResults<Entry>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.list(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle response with missing totalCount', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { data: [] });

      const results = new PagedResults<Entry>();
      await producer.list(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });
  });

  describe('Response Data Mapping', () => {
    it('should handle response with null data array', async () => {
      const mockResponse = {
        data: null,
        totalCount: 3,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Entry>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with missing data field', async () => {
      const mockResponse = { totalCount: 0 };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Entry>();

      try {
        await producer.list(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });
});
