import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { ScheduleProducerApiImpl } from '../../src/ScheduleProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { Schedule, ScheduleType, ScheduleEvent } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('ScheduleProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: ScheduleProducerApiImpl;
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

    producer = new ScheduleProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create ScheduleProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(ScheduleProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new ScheduleProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('listSchedules()', () => {
    it('should list schedules with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiSchedules = [
        {
          id: 100001,
          name: 'Business Hours',
          description: 'Monday to Friday 9-5',
          scheduleTypeId: 1,
          timeZone: 'America/New_York',
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-01-15T10:00:00.000Z',
        },
        {
          id: 100002,
          name: 'Weekend Access',
          description: 'Saturday and Sunday',
          scheduleTypeId: 2,
          timeZone: 'America/New_York',
          createdAt: '2024-01-16T10:00:00.000Z',
          updatedAt: '2024-01-16T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        rawApiSchedules,
        rawApiSchedules.length
      );

      const results = new PagedResults<Schedule>();
      await producer.listSchedules(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Verify the schedules have expected properties
      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].name).to.be.a('string');
      expect(['Business Hours', 'Weekend Access']).to.include(results.items[0].name);

      scope.done();
    });

    it('should list schedules with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiSchedules = [
        {
          id: 100003,
          name: 'Night Shift',
          description: 'Overnight access',
          scheduleTypeId: 3,
          timeZone: 'America/New_York',
          createdAt: '2024-01-17T10:00:00.000Z',
          updatedAt: '2024-01-17T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: expectedOffset, limit: pageSize },
        rawApiSchedules,
        rawApiSchedules.length
      );

      const results = new PagedResults<Schedule>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listSchedules(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty schedule list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<Schedule>();
      await producer.listSchedules(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { invalid: 'response' });

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle 404 error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle 500 server error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle 401 unauthorized error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should limit page size to 1000', async () => {
      const pageNumber = 1;
      const pageSize = 5000; // Exceeds max
      const expectedLimit = 1000; // Should be capped

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: expectedLimit },
        [],
        0
      );

      const results = new PagedResults<Schedule>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listSchedules(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });
  });

  describe('listScheduleTypes()', () => {
    it('should list schedule types with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiScheduleTypes = [
        {
          id: 1,
          name: 'Regular',
          description: 'Standard schedule type',
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-01-15T10:00:00.000Z',
        },
        {
          id: 2,
          name: 'Holiday',
          description: 'Holiday schedule type',
          createdAt: '2024-01-16T10:00:00.000Z',
          updatedAt: '2024-01-16T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        rawApiScheduleTypes,
        rawApiScheduleTypes.length
      );

      const results = new PagedResults<ScheduleType>();
      await producer.listScheduleTypes(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Verify the schedule types have expected properties
      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].name).to.be.a('string');
      expect(['Regular', 'Holiday']).to.include(results.items[0].name);

      scope.done();
    });

    it('should list schedule types with custom pagination', async () => {
      const pageNumber = 3;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiScheduleTypes = [
        {
          id: 3,
          name: 'Special Event',
          description: 'Special event schedule type',
          createdAt: '2024-01-17T10:00:00.000Z',
          updatedAt: '2024-01-17T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: expectedOffset, limit: pageSize },
        rawApiScheduleTypes,
        rawApiScheduleTypes.length
      );

      const results = new PagedResults<ScheduleType>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listScheduleTypes(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty schedule type list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<ScheduleType>();
      await producer.listScheduleTypes(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/scheduleTypes`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { notData: [] });

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle 404 error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle 500 server error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle 401 unauthorized error', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/scheduleTypes`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Connection timeout');

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('listScheduleEvents()', () => {
    it('should list schedule events with default pagination', async () => {
      const scheduleId = 'schedule-123';

      // Create mock raw API response data in the format the mapper expects
      const rawApiScheduleEvents = [
        {
          id: 1001,
          scheduleId,
          startTime: '09:00:00',
          endTime: '17:00:00',
          dayOfWeek: 1,
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-01-15T10:00:00.000Z',
        },
        {
          id: 1002,
          scheduleId,
          startTime: '09:00:00',
          endTime: '17:00:00',
          dayOfWeek: 2,
          createdAt: '2024-01-16T10:00:00.000Z',
          updatedAt: '2024-01-16T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        rawApiScheduleEvents,
        rawApiScheduleEvents.length
      );

      const results = new PagedResults<ScheduleEvent>();
      await producer.listScheduleEvents(results, orgId, scheduleId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('startTime');
      expect(results.items[0]).to.have.property('endTime');

      // Verify the schedule events have expected properties
      expect(results.items[0].id).to.be.a('string');
      // startTime and endTime are mapped to Date objects by the mapper
      expect(results.items[0].startTime).to.be.instanceOf(Date);
      expect(results.items[0].endTime).to.be.instanceOf(Date);

      scope.done();
    });

    it('should list schedule events with custom pagination', async () => {
      const scheduleId = 'schedule-456';
      const pageNumber = 2;
      const pageSize = 20;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiScheduleEvents = [
        {
          id: 1003,
          scheduleId,
          startTime: '18:00:00',
          endTime: '22:00:00',
          dayOfWeek: 5,
          createdAt: '2024-01-17T10:00:00.000Z',
          updatedAt: '2024-01-17T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: expectedOffset, limit: pageSize },
        rawApiScheduleEvents,
        rawApiScheduleEvents.length
      );

      const results = new PagedResults<ScheduleEvent>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listScheduleEvents(results, orgId, scheduleId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty schedule events list', async () => {
      const scheduleId = 'schedule-empty';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<ScheduleEvent>();
      await producer.listScheduleEvents(results, orgId, scheduleId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should throw UnexpectedError for invalid response format', async () => {
      const scheduleId = 'schedule-789';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules/${scheduleId}/events`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { events: [] });

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as Error).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle schedule not found (404)', async () => {
      const scheduleId = 'nonexistent';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scheduleId = 'schedule-rate-limit';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle 500 server error', async () => {
      const scheduleId = 'schedule-server-error';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle 401 unauthorized error', async () => {
      const scheduleId = 'schedule-unauthorized';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scheduleId = 'schedule-network-error';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules/${scheduleId}/events`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network failure');

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Response Data Mapping', () => {
    it('should handle response with missing data array for schedules', async () => {
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with null data for schedules', async () => {
      const mockResponse = {
        data: null,
        totalCount: 3,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Schedule>();

      try {
        await producer.listSchedules(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with missing data array for schedule types', async () => {
      const mockResponse = { totalCount: 0 };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/scheduleTypes`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<ScheduleType>();

      try {
        await producer.listScheduleTypes(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with null data for schedule events', async () => {
      const scheduleId = 'schedule-123';
      const mockResponse = {
        data: null,
        totalCount: 5,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/schedules/${scheduleId}/events`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<ScheduleEvent>();

      try {
        await producer.listScheduleEvents(results, orgId, scheduleId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Data Validation', () => {
    it('should validate schedule response schema', async () => {
      const mockScheduleData = {
        id: 100001,
        name: 'Business Hours',
        description: 'Monday to Friday 9-5',
        scheduleTypeId: 1,
        timeZone: 'America/New_York',
        createdAt: '2024-01-15T10:00:00.000Z',
        updatedAt: '2024-01-15T10:00:00.000Z',
      };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules`,
        { offset: 0, limit: 50 },
        [mockScheduleData],
        1
      );

      const results = new PagedResults<Schedule>();
      await producer.listSchedules(results, orgId);

      // Validate required fields
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Validate data types
      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].name).to.be.a('string');

      // Optional fields should be present when provided
      if (results.items[0].description) {
        expect(results.items[0].description).to.be.a('string');
      }
      if (results.items[0].createdAt) {
        expect(results.items[0].createdAt).to.be.instanceOf(Date);
      }
      if (results.items[0].updatedAt) {
        expect(results.items[0].updatedAt).to.be.instanceOf(Date);
      }

      scope.done();
    });

    it('should validate schedule type response schema', async () => {
      const mockScheduleTypeData = {
        id: 1,
        name: 'Regular',
        description: 'Standard schedule type',
        createdAt: '2024-01-15T10:00:00.000Z',
        updatedAt: '2024-01-15T10:00:00.000Z',
      };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/scheduleTypes`,
        { offset: 0, limit: 50 },
        [mockScheduleTypeData],
        1
      );

      const results = new PagedResults<ScheduleType>();
      await producer.listScheduleTypes(results, orgId);

      // Validate required fields
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Validate data types
      expect(results.items[0].id).to.be.a('string');
      expect(results.items[0].name).to.be.a('string');

      scope.done();
    });

    it('should validate schedule event response schema', async () => {
      const scheduleId = 'schedule-123';
      const mockScheduleEventData = {
        id: 1001,
        scheduleId,
        startTime: '09:00:00',
        endTime: '17:00:00',
        dayOfWeek: 1,
        createdAt: '2024-01-15T10:00:00.000Z',
        updatedAt: '2024-01-15T10:00:00.000Z',
      };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/schedules/${scheduleId}/events`,
        { offset: 0, limit: 50 },
        [mockScheduleEventData],
        1
      );

      const results = new PagedResults<ScheduleEvent>();
      await producer.listScheduleEvents(results, orgId, scheduleId);

      // Validate required fields
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('startTime');
      expect(results.items[0]).to.have.property('endTime');

      // Validate data types
      expect(results.items[0].id).to.be.a('string');
      // startTime and endTime are mapped to Date objects by the mapper
      expect(results.items[0].startTime).to.be.instanceOf(Date);
      expect(results.items[0].endTime).to.be.instanceOf(Date);

      scope.done();
    });
  });
});
