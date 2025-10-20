import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { prepareApi, testConfig, saveFixture, validateCoreTypes } from './Common';
import { AccessImpl, ScheduleApi } from '../../src';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Avigilon Alta Access - Schedule Producer Tests', function () {
  this.timeout(testConfig.timeout);

  let access: AccessImpl;
  let scheduleApi: ScheduleApi;

  before(async () => {
    access = await prepareApi();
    scheduleApi = access.getScheduleApi();

    expect(scheduleApi).to.not.be.undefined;
    logger.debug('ScheduleApi initialized successfully');
  });

  describe('Schedule List Operations', () => {
    it('should list schedules with default pagination', async () => {
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId);

      logger.debug(`scheduleApi.listSchedules(${testConfig.organizationId})`, JSON.stringify(schedulesResult, null, 2));

      expect(schedulesResult).to.not.be.null;
      expect(schedulesResult).to.not.be.undefined;

      // Validate structure
      const schedules = schedulesResult.items;
      if (schedules && Array.isArray(schedules)) {
        expect(schedules).to.be.an('array');

        // If schedules exist, validate the first one
        if (schedules.length > 0) {
          const firstSchedule = schedules[0];
          expect(firstSchedule).to.be.an('object');

          // Validate required schedule fields
          expect(firstSchedule.id).to.be.a('string');
          expect(firstSchedule.name).to.be.a('string');

          // Validate optional fields if present
          if (firstSchedule.description) {
            expect(firstSchedule.description).to.be.a('string');
          }

          if (firstSchedule.scheduleType) {
            expect(firstSchedule.scheduleType).to.be.an('object');
            expect(firstSchedule.scheduleType.id).to.be.a('string');
          }

          if (firstSchedule.isActive !== undefined) {
            expect(firstSchedule.isActive).to.be.a('boolean');
          }

          if (firstSchedule.createdAt) {
            expect(firstSchedule.createdAt).to.be.instanceOf(Date);
            validateCoreTypes.isDate(firstSchedule.createdAt);
          }

          if (firstSchedule.updatedAt) {
            expect(firstSchedule.updatedAt).to.be.instanceOf(Date);
            validateCoreTypes.isDate(firstSchedule.updatedAt);
          }
        }
      }

      // Validate pagination metadata
      expect(schedulesResult.count).to.be.a('number');
      expect(schedulesResult.count).to.be.at.least(0);

      // Save fixture
      await saveFixture('schedules-list-default.json', schedulesResult);
    });

    it('should list schedules with custom pagination', async () => {
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1, 5);

      logger.debug(`scheduleApi.listSchedules(${testConfig.organizationId}, 1, 5)`, JSON.stringify(schedulesResult, null, 2));

      expect(schedulesResult).to.not.be.null;
      expect(schedulesResult).to.not.be.undefined;

      // Validate pagination constraints
      const schedules = schedulesResult.items;
      if (schedules && Array.isArray(schedules)) {
        expect(schedules).to.be.an('array');
        // Should return at most 5 schedules
        expect(schedules.length).to.be.at.most(5);
      }

      // Save fixture
      await saveFixture('schedules-list-paginated.json', schedulesResult);
    });

    it('should handle empty schedule list', async () => {
      // Request with large page number to potentially get empty results
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1000, 10);

      logger.debug(`scheduleApi.listSchedules(${testConfig.organizationId}, 1000, 10)`, JSON.stringify(schedulesResult, null, 2));

      expect(schedulesResult).to.not.be.null;
      expect(schedulesResult.items).to.be.an('array');

      // Save fixture
      await saveFixture('schedules-list-empty-page.json', schedulesResult);
    });
  });

  describe('Schedule Type List Operations', () => {
    it('should list schedule types with default pagination', async () => {
      const typesResult = await scheduleApi.listScheduleTypes(testConfig.organizationId);

      logger.debug(`scheduleApi.listScheduleTypes(${testConfig.organizationId})`, JSON.stringify(typesResult, null, 2));

      expect(typesResult).to.not.be.null;
      expect(typesResult).to.not.be.undefined;

      // Validate structure
      const types = typesResult.items;
      if (types && Array.isArray(types)) {
        expect(types).to.be.an('array');

        // If types exist, validate the first one
        if (types.length > 0) {
          const firstType = types[0];
          expect(firstType).to.be.an('object');

          // Validate required schedule type fields
          expect(firstType.id).to.be.a('string');

          // Validate optional fields if present
          if (firstType.name) {
            expect(firstType.name).to.be.a('string');
          }

          if (firstType.code) {
            expect(firstType.code).to.be.a('string');
          }

          if (firstType.description) {
            expect(firstType.description).to.be.a('string');
          }
        }
      }

      // Validate pagination metadata
      expect(typesResult.count).to.be.a('number');
      expect(typesResult.count).to.be.at.least(0);

      // Save fixture
      await saveFixture('schedule-types-list-default.json', typesResult);
    });

    it('should list schedule types with custom pagination', async () => {
      const typesResult = await scheduleApi.listScheduleTypes(testConfig.organizationId, 1, 3);

      logger.debug(`scheduleApi.listScheduleTypes(${testConfig.organizationId}, 1, 3)`, JSON.stringify(typesResult, null, 2));

      expect(typesResult).to.not.be.null;
      expect(typesResult.items).to.be.an('array');

      // Should return at most 3 types
      expect(typesResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('schedule-types-list-paginated.json', typesResult);
    });
  });

  describe('Schedule Event List Operations', () => {
    it('should list schedule events for a specific schedule', async function () {
      // First get a schedule to test with
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1, 1);
      const schedules = schedulesResult.items;

      if (!schedules || !Array.isArray(schedules) || schedules.length === 0) {
        logger.warn('No schedules available for testing schedule events - skipping test');
        this.skip();
        return;
      }

      const scheduleId = schedules[0].id;
      expect(scheduleId).to.not.be.undefined;

      // List events for the schedule
      const eventsResult = await scheduleApi.listScheduleEvents(
        testConfig.organizationId,
        scheduleId
      );

      logger.debug(`scheduleApi.listScheduleEvents(${testConfig.organizationId}, '${scheduleId}')`, JSON.stringify(eventsResult, null, 2));

      expect(eventsResult).to.not.be.null;
      expect(eventsResult).to.not.be.undefined;

      // Validate structure
      expect(eventsResult.items).to.be.an('array');
      expect(eventsResult.count).to.be.a('number');

      // If events exist, validate the first one
      if (eventsResult.items && eventsResult.items.length > 0) {
        const firstEvent = eventsResult.items[0];
        expect(firstEvent).to.be.an('object');

        // Validate required event fields
        expect(firstEvent.id).to.be.a('string');

        // Validate optional fields if present
        if (firstEvent.scheduleId) {
          expect(firstEvent.scheduleId).to.be.a('string');
        }

        if (firstEvent.startTime) {
          expect(firstEvent.startTime).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstEvent.startTime);
        }

        if (firstEvent.endTime) {
          expect(firstEvent.endTime).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstEvent.endTime);
        }

        if (firstEvent.daysOfWeek) {
          expect(firstEvent.daysOfWeek).to.be.an('array');
          // Validate each day is an EnumValue
          firstEvent.daysOfWeek.forEach((day) => {
            expect(day).to.have.property('value');
            expect(day).to.have.property('name');
          });
        }

        if (firstEvent.isRecurring !== undefined) {
          expect(firstEvent.isRecurring).to.be.a('boolean');
        }

        if (firstEvent.createdAt) {
          expect(firstEvent.createdAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstEvent.createdAt);
        }

        if (firstEvent.updatedAt) {
          expect(firstEvent.updatedAt).to.be.instanceOf(Date);
          validateCoreTypes.isDate(firstEvent.updatedAt);
        }
      }

      // Save fixture
      await saveFixture('schedule-events-list.json', eventsResult);
    });

    it('should list schedule events with custom pagination', async function () {
      // First get a schedule to test with
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1, 1);
      const schedules = schedulesResult.items;

      if (!schedules || !Array.isArray(schedules) || schedules.length === 0) {
        logger.warn('No schedules available for testing schedule events pagination - skipping test');
        this.skip();
        return;
      }

      const scheduleId = schedules[0].id;

      // List events with custom pagination
      const eventsResult = await scheduleApi.listScheduleEvents(
        testConfig.organizationId,
        scheduleId,
        1,
        3
      );

      logger.debug(`scheduleApi.listScheduleEvents(${testConfig.organizationId}, '${scheduleId}', 1, 3)`, JSON.stringify(eventsResult, null, 2));

      expect(eventsResult).to.not.be.null;
      expect(eventsResult.items).to.be.an('array');

      // Should return at most 3 events
      expect(eventsResult.items.length).to.be.at.most(3);

      // Save fixture
      await saveFixture('schedule-events-list-paginated.json', eventsResult);
    });

    it('should handle schedule with no events', async function () {
      // First get a schedule to test with
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1, 10);
      const schedules = schedulesResult.items;

      if (!schedules || !Array.isArray(schedules) || schedules.length === 0) {
        logger.warn('No schedules available for testing - skipping test');
        this.skip();
        return;
      }

      // Try to find a schedule or use the first one
      const scheduleId = schedules[0].id;

      const eventsResult = await scheduleApi.listScheduleEvents(
        testConfig.organizationId,
        scheduleId
      );

      logger.debug(
        `scheduleApi.listScheduleEvents(${testConfig.organizationId}, '${scheduleId}') - checking for empty`,
        JSON.stringify(eventsResult, null, 2)
      );

      expect(eventsResult).to.not.be.null;
      expect(eventsResult.items).to.be.an('array');

      // Save fixture (may or may not be empty)
      await saveFixture('schedule-events-potentially-empty.json', eventsResult);
    });
  });

  describe('Schedule Data Validation', () => {
    it('should validate schedule response schema', async function () {
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1, 1);
      const schedules = schedulesResult.items;

      if (!schedules || !Array.isArray(schedules) || schedules.length === 0) {
        logger.warn('No schedules available for schema validation - skipping test');
        this.skip();
        return;
      }

      const schedule = schedules[0];
      logger.debug('Validating schedule schema', JSON.stringify(schedule, null, 2));

      expect(schedule).to.be.an('object');

      // Required fields
      expect(schedule.id).to.not.be.undefined;
      expect(schedule.name).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('schedule-schema-validation.json', {
        schedule,
        validation: {
          hasId: !!schedule.id,
          hasName: !!schedule.name,
          hasDescription: !!schedule.description,
          hasScheduleType: !!schedule.scheduleType,
          hasIsActive: schedule.isActive !== undefined,
          hasCreatedAt: !!schedule.createdAt,
          hasUpdatedAt: !!schedule.updatedAt,
          timestamp: new Date().toISOString(),
        },
      });
    });

    it('should validate schedule type response schema', async function () {
      const typesResult = await scheduleApi.listScheduleTypes(testConfig.organizationId, 1, 1);
      const types = typesResult.items;

      if (!types || !Array.isArray(types) || types.length === 0) {
        logger.warn('No schedule types available for schema validation - skipping test');
        this.skip();
        return;
      }

      const scheduleType = types[0];
      logger.debug('Validating schedule type schema', JSON.stringify(scheduleType, null, 2));

      expect(scheduleType).to.be.an('object');

      // Required fields
      expect(scheduleType.id).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('schedule-type-schema-validation.json', {
        scheduleType,
        validation: {
          hasId: !!scheduleType.id,
          hasName: !!scheduleType.name,
          hasCode: !!scheduleType.code,
          hasDescription: !!scheduleType.description,
          timestamp: new Date().toISOString(),
        },
      });
    });

    it('should validate schedule event response schema', async function () {
      // First get a schedule to test with
      const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, 1, 1);
      const schedules = schedulesResult.items;

      if (!schedules || !Array.isArray(schedules) || schedules.length === 0) {
        logger.warn('No schedules available for event schema validation - skipping test');
        this.skip();
        return;
      }

      const scheduleId = schedules[0].id;
      const eventsResult = await scheduleApi.listScheduleEvents(testConfig.organizationId, scheduleId, 1, 1);
      const events = eventsResult.items;

      if (!events || !Array.isArray(events) || events.length === 0) {
        logger.warn('No schedule events available for schema validation - skipping test');
        this.skip();
        return;
      }

      const event = events[0];
      logger.debug('Validating schedule event schema', JSON.stringify(event, null, 2));

      expect(event).to.be.an('object');

      // Required fields
      expect(event.id).to.not.be.undefined;

      // Save schema validation fixture
      await saveFixture('schedule-event-schema-validation.json', {
        event,
        validation: {
          hasId: !!event.id,
          hasScheduleId: !!event.scheduleId,
          hasStartTime: !!event.startTime,
          hasEndTime: !!event.endTime,
          hasDaysOfWeek: !!event.daysOfWeek,
          hasIsRecurring: event.isRecurring !== undefined,
          hasCreatedAt: !!event.createdAt,
          hasUpdatedAt: !!event.updatedAt,
          timestamp: new Date().toISOString(),
        },
      });
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters for schedules', async () => {
      try {
        // Test with potentially invalid parameters
        const schedulesResult = await scheduleApi.listSchedules(testConfig.organizationId, -1, -1);
        logger.debug(`scheduleApi.listSchedules(${testConfig.organizationId}, -1, -1)`, JSON.stringify(schedulesResult, null, 2));

        // Should either return empty array or throw appropriate error
        if (schedulesResult && schedulesResult.items) {
          expect(schedulesResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for invalid pagination', err.message);

        // Should get a validation error
        expect(error).to.not.be.null;

        await saveFixture('schedules-list-invalid-pagination-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle invalid schedule ID for events', async () => {
      const nonExistentScheduleId = '99999999';

      try {
        const eventsResult = await scheduleApi.listScheduleEvents(
          testConfig.organizationId,
          nonExistentScheduleId
        );
        logger.debug(
          `scheduleApi.listScheduleEvents(${testConfig.organizationId}, '${nonExistentScheduleId}')`,
          JSON.stringify(eventsResult, null, 2)
        );

        // If no error was thrown, the result should be empty or null
        if (eventsResult) {
          expect(eventsResult.items).to.be.an('array');
        }
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for non-existent schedule', err.message);

        // Expecting a 404 or similar error
        expect(error).to.not.be.null;

        // Common error patterns for not found
        const errorMessage = err.message?.toLowerCase() || '';
        const hasNotFoundIndicator = errorMessage.includes('not found')
          || errorMessage.includes('404')
          || errorMessage.includes('does not exist')
          || err.status === 404
          || err.statusCode === 404;

        expect(hasNotFoundIndicator).to.be.true;

        // Save error fixture
        await saveFixture('schedule-events-not-found-error.json', {
          error: err.message,
          status: err.status || err.statusCode,
          timestamp: new Date().toISOString(),
        });
      }
    });

    it('should handle empty organization ID gracefully', async () => {
      try {
        await scheduleApi.listSchedules('');

        // Should not reach here
        expect.fail('Should have thrown ParameterRequiredError');
      } catch (error: unknown) {
        const err = error as any;
        logger.debug('Expected error for empty organizationId', err.message);

        expect(error).to.not.be.null;
        expect((error as Error).message).to.include('organizationId');

        await saveFixture('schedules-empty-org-id-error.json', {
          error: err.message,
          timestamp: new Date().toISOString(),
        });
      }
    });
  });

  describe('Pagination Functionality', () => {
    it('should paginate through multiple pages of schedules', async () => {
      const pageSize = 2;
      let currentPage = 1;
      let totalFetched = 0;
      const maxPages = 3; // Limit to avoid excessive API calls

      for (let i = 0; i < maxPages; i += 1) {
        const schedulesResult = await scheduleApi.listSchedules(
          testConfig.organizationId,
          currentPage,
          pageSize
        );

        logger.debug(`Page ${currentPage}:`, JSON.stringify(schedulesResult, null, 2));

        expect(schedulesResult.items).to.be.an('array');

        if (schedulesResult.items.length === 0) {
          // No more results
          logger.debug('No more schedules to fetch');
          break;
        }

        totalFetched += schedulesResult.items.length;
        currentPage++;

        // Validate pagination is working
        expect(schedulesResult.items.length).to.be.at.most(pageSize);
      }

      logger.debug(`Total schedules fetched: ${totalFetched} across ${currentPage - 1} pages`);

      // Save pagination summary
      await saveFixture('schedules-pagination-summary.json', {
        totalFetched,
        pagesFetched: currentPage - 1,
        pageSize,
        timestamp: new Date().toISOString(),
      });
    });

    it('should paginate through multiple pages of schedule types', async () => {
      const pageSize = 2;
      let currentPage = 1;
      let totalFetched = 0;
      const maxPages = 3;

      for (let i = 0; i < maxPages; i += 1) {
        const typesResult = await scheduleApi.listScheduleTypes(
          testConfig.organizationId,
          currentPage,
          pageSize
        );

        logger.debug(`Page ${currentPage}:`, JSON.stringify(typesResult, null, 2));

        expect(typesResult.items).to.be.an('array');

        if (typesResult.items.length === 0) {
          logger.debug('No more schedule types to fetch');
          break;
        }

        totalFetched += typesResult.items.length;
        currentPage++;

        expect(typesResult.items.length).to.be.at.most(pageSize);
      }

      logger.debug(`Total schedule types fetched: ${totalFetched} across ${currentPage - 1} pages`);

      await saveFixture('schedule-types-pagination-summary.json', {
        totalFetched,
        pagesFetched: currentPage - 1,
        pageSize,
        timestamp: new Date().toISOString(),
      });
    });
  });
});
