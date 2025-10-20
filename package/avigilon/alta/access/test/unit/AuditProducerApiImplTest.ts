import { expect } from 'chai';
import nock from 'nock';
import { PagedResults, Email, UnexpectedError } from '@auditmation/types-core-js';
import { AuditProducerApiImpl } from '../../src/AuditProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { AuditLogEntry } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockErrorResponse,
  cleanNock
} from '../utils/nock-helpers';

describe('AuditProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: AuditProducerApiImpl;
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

    producer = new AuditProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create AuditProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(AuditProducerApiImpl);
    });
  });

  describe('Audit Log List Operations', () => {
    it('should list audit logs with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiAuditLogs = [
        {
          timestamp: 1704067200,
          timestampIso: '2024-01-01T00:00:00.000Z',
          action: 'user.created',
          category: 'user_management',
          actorId: '12345',
          actorName: 'Admin User',
          actorEmail: 'admin@example.com',
          targetId: '67890',
          targetType: 'user',
          targetName: 'New User',
          details: { role: 'member' },
          ipAddress: '192.168.1.100',
          userAgent: 'Mozilla/5.0',
        },
        {
          timestamp: 1704070800,
          timestampIso: '2024-01-01T01:00:00.000Z',
          action: 'group.updated',
          category: 'group_management',
          actorId: '12345',
          actorName: 'Admin User',
          actorEmail: 'admin@example.com',
          targetId: '11111',
          targetType: 'group',
          targetName: 'Access Group',
          details: { change: 'permissions' },
          ipAddress: '192.168.1.100',
          userAgent: 'Mozilla/5.0',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        rawApiAuditLogs,
        rawApiAuditLogs.length
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('timestamp');
      expect(results.items[0]).to.have.property('action');
      expect(results.items[0]).to.have.property('category');
      expect(results.items[0].timestamp).to.be.a('number');
      expect(results.items[0].action).to.be.a('string');

      scope.done();
    });

    it('should list audit logs with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiAuditLogs = [
        {
          timestamp: 1704074400,
          timestampIso: '2024-01-01T02:00:00.000Z',
          action: 'entry.opened',
          category: 'access_control',
          actorId: '54321',
          actorName: 'User Smith',
          actorEmail: 'user@example.com',
          targetId: '99999',
          targetType: 'entry',
          targetName: 'Main Door',
          details: { method: 'badge' },
          ipAddress: '192.168.1.101',
          userAgent: 'Mozilla/5.0',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: expectedOffset, limit: pageSize },
        rawApiAuditLogs,
        rawApiAuditLogs.length
      );

      const results = new PagedResults<AuditLogEntry>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should list audit logs with filter parameter', async () => {
      const filter = 'action:user.created';

      const rawApiAuditLogs = [
        {
          timestamp: 1704067200,
          timestampIso: '2024-01-01T00:00:00.000Z',
          action: 'user.created',
          category: 'user_management',
          actorId: '12345',
          actorName: 'Admin User',
          actorEmail: 'admin@example.com',
          targetId: '67890',
          targetType: 'user',
          targetName: 'New User',
          details: { role: 'member' },
          ipAddress: '192.168.1.100',
          userAgent: 'Mozilla/5.0',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50, filter },
        rawApiAuditLogs,
        rawApiAuditLogs.length
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId, filter);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      expect(results.items[0].action).to.equal('user.created');
      scope.done();
    });

    it('should list audit logs with options parameter', async () => {
      const options = 'includeDetails:true';

      const rawApiAuditLogs = [
        {
          timestamp: 1704067200,
          timestampIso: '2024-01-01T00:00:00.000Z',
          action: 'user.created',
          category: 'user_management',
          actorId: '12345',
          actorName: 'Admin User',
          actorEmail: 'admin@example.com',
          targetId: '67890',
          targetType: 'user',
          targetName: 'New User',
          details: { role: 'member', permissions: ['read', 'write'] },
          ipAddress: '192.168.1.100',
          userAgent: 'Mozilla/5.0',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50, options },
        rawApiAuditLogs,
        rawApiAuditLogs.length
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId, undefined, options);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      expect(results.items[0].details).to.be.an('object');
      scope.done();
    });

    it('should list audit logs with both filter and options parameters', async () => {
      const filter = 'category:access_control';
      const options = 'sortBy:timestamp';

      const rawApiAuditLogs = [
        {
          timestamp: 1704074400,
          timestampIso: '2024-01-01T02:00:00.000Z',
          action: 'entry.opened',
          category: 'access_control',
          actorId: '54321',
          actorName: 'User Smith',
          actorEmail: 'user@example.com',
          targetId: '99999',
          targetType: 'entry',
          targetName: 'Main Door',
          details: { method: 'badge' },
          ipAddress: '192.168.1.101',
          userAgent: 'Mozilla/5.0',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50, filter, options },
        rawApiAuditLogs,
        rawApiAuditLogs.length
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId, filter, options);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      expect(results.items[0].category).to.equal('access_control');
      scope.done();
    });

    it('should handle empty audit log results', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });
  });

  describe('Audit Log Data Validation', () => {
    it('should validate audit log response schema', async () => {
      const rawApiAuditLog = {
        timestamp: 1704067200,
        timestampIso: '2024-01-01T00:00:00.000Z',
        action: 'user.created',
        category: 'user_management',
        actorId: '12345',
        actorName: 'Admin User',
        actorEmail: 'admin@example.com',
        targetId: '67890',
        targetType: 'user',
        targetName: 'New User',
        details: { role: 'member' },
        ipAddress: '192.168.1.100',
        userAgent: 'Mozilla/5.0',
      };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        [rawApiAuditLog],
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      const result = results.items[0];

      // Validate required fields
      expect(result).to.have.property('timestamp');
      expect(result.timestamp).to.be.a('number');

      // Validate optional fields when present
      if (result.timestampIso) {
        // timestampIso might be a string or Date depending on mapper implementation
        expect(result.timestampIso).to.satisfy((val: any) => val instanceof Date || typeof val === 'string');
      }
      if (result.action) {
        expect(result.action).to.be.a('string');
      }
      if (result.category) {
        expect(result.category).to.be.a('string');
      }
      if (result.actorId) {
        expect(result.actorId).to.be.a('string');
      }
      if (result.actorName) {
        expect(result.actorName).to.be.a('string');
      }
      if (result.actorEmail) {
        expect(result.actorEmail).to.not.be.undefined;
      }
      if (result.targetId) {
        expect(result.targetId).to.be.a('string');
      }
      if (result.targetType) {
        expect(result.targetType).to.be.a('string');
      }
      if (result.targetName) {
        expect(result.targetName).to.be.a('string');
      }
      if (result.details) {
        expect(result.details).to.be.an('object');
      }
      if (result.ipAddress) {
        expect(result.ipAddress).to.be.a('string');
      }
      if (result.userAgent) {
        expect(result.userAgent).to.be.a('string');
      }

      scope.done();
    });

    it('should handle audit log with minimal fields (only timestamp)', async () => {
      const rawApiAuditLog = { timestamp: 1704067200 };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        [rawApiAuditLog],
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length(1);
      expect(results.items[0].timestamp).to.equal(1704067200);
      scope.done();
    });

    it('should handle audit log with all optional fields populated', async () => {
      const rawApiAuditLog = {
        timestamp: 1704067200,
        timestampIso: '2024-01-01T00:00:00.000Z',
        action: 'user.created',
        category: 'user_management',
        actorId: '12345',
        actorName: 'Admin User',
        actorEmail: 'admin@example.com',
        targetId: '67890',
        targetType: 'user',
        targetName: 'New User',
        details: { role: 'member', department: 'IT', level: 'senior' },
        ipAddress: '192.168.1.100',
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      };

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        [rawApiAuditLog],
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      const result = results.items[0];
      expect(result.timestamp).to.equal(1704067200);
      expect(result.action).to.equal('user.created');
      expect(result.category).to.equal('user_management');
      expect(result.actorId).to.equal('12345');
      expect(result.actorName).to.equal('Admin User');
      expect(result.targetId).to.equal('67890');
      expect(result.targetType).to.equal('user');
      expect(result.targetName).to.equal('New User');
      expect(result.details).to.be.an('object');
      expect(result.ipAddress).to.equal('192.168.1.100');
      expect(result.userAgent).to.include('Mozilla');
      scope.done();
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', () => {
      // Test with extremely large page size
      const results = new PagedResults<AuditLogEntry>();
      results.pageSize = 5000; // Should be limited by API

      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should enforce maximum page size of 1000', async () => {
      const rawApiAuditLogs = [
        {
          timestamp: 1704067200,
          action: 'user.created',
        },
      ];

      // When page size exceeds 1000, it should be capped at 1000
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 1000 }, // Should be capped at 1000
        rawApiAuditLogs,
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      results.pageSize = 2000; // Try to set larger than max

      await producer.listAuditLogs(results, orgId);
      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should enforce minimum page size of 1', async () => {
      const rawApiAuditLogs = [
        {
          timestamp: 1704067200,
          action: 'user.created',
        },
      ];

      // PagedResults validates that pageSize must be >= 1, so setting to 1 is minimum
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 1 },
        rawApiAuditLogs,
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      results.pageSize = 1; // Set to minimum valid value

      await producer.listAuditLogs(results, orgId);
      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle API rate limiting (429)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        429,
        'Rate limit exceeded'
      );

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle unauthorized error (401)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        401,
        'Invalid credentials'
      );

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('InvalidCredentialsError');
      }

      scope.done();
    });

    it('should handle forbidden error (403)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        403,
        'Forbidden'
      );

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected UnauthorizedError to be thrown');
      } catch (error) {
        // API returns UnauthorizedError for 403
        expect((error as any).constructor.name).to.equal('UnauthorizedError');
      }

      scope.done();
    });

    it('should handle server error (500)', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        500,
        'Internal server error'
      );

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/reports/auditLogs/ui`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
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
        .get(`/orgs/${orgId}/reports/auditLogs/ui`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with null data', async () => {
      const mockResponse = {
        data: null,
        totalCount: 5,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/reports/auditLogs/ui`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should handle response with invalid data type (not array)', async () => {
      const mockResponse = {
        data: 'not-an-array',
        totalCount: 1,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/reports/auditLogs/ui`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<AuditLogEntry>();

      try {
        await producer.listAuditLogs(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });

    it('should set count to 0 when totalCount is missing', async () => {
      const rawApiAuditLogs = [
        {
          timestamp: 1704067200,
          action: 'user.created',
        },
      ];

      const mockResponse = {
        data: rawApiAuditLogs,
        // Missing totalCount
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/reports/auditLogs/ui`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length(1);
      expect(results.count).to.equal(0); // Should default to 0 when totalCount is missing
      scope.done();
    });
  });

  describe('Pagination Offset Calculation', () => {
    it('should calculate correct offset for page 1', async () => {
      const rawApiAuditLogs = [
        { timestamp: 1704067200, action: 'user.created' },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 }, // Page 1: (1-1) * 50 = 0
        rawApiAuditLogs,
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      results.pageNumber = 1;
      results.pageSize = 50;

      await producer.listAuditLogs(results, orgId);
      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should calculate correct offset for page 3', async () => {
      const rawApiAuditLogs = [
        { timestamp: 1704067200, action: 'user.created' },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 40, limit: 20 }, // Page 3: (3-1) * 20 = 40
        rawApiAuditLogs,
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      results.pageNumber = 3;
      results.pageSize = 20;

      await producer.listAuditLogs(results, orgId);
      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should use offset 0 when no pagination is specified', async () => {
      const rawApiAuditLogs = [
        { timestamp: 1704067200, action: 'user.created' },
      ];

      // PagedResults has defaults: pageNumber=1, pageSize=50
      // So even without explicitly setting them, offset=0 and limit=50 are used
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 }, // Defaults from PagedResults
        rawApiAuditLogs,
        1
      );

      const results = new PagedResults<AuditLogEntry>();
      // PagedResults has default values: pageNumber=1, pageSize=50

      await producer.listAuditLogs(results, orgId);
      expect(results.items).to.be.an('array');
      scope.done();
    });
  });

  describe('Audit Log Action Types', () => {
    it('should handle user management actions', async () => {
      const rawApiAuditLogs = [
        { timestamp: 1704067200, action: 'user.created', category: 'user_management' },
        { timestamp: 1704067201, action: 'user.updated', category: 'user_management' },
        { timestamp: 1704067202, action: 'user.deleted', category: 'user_management' },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        rawApiAuditLogs,
        3
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length(3);
      expect(results.items.every((item) => item.category === 'user_management')).to.be.true;
      scope.done();
    });

    it('should handle access control actions', async () => {
      const rawApiAuditLogs = [
        { timestamp: 1704067200, action: 'entry.opened', category: 'access_control' },
        { timestamp: 1704067201, action: 'entry.denied', category: 'access_control' },
        { timestamp: 1704067202, action: 'entry.forced', category: 'access_control' },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        rawApiAuditLogs,
        3
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length(3);
      expect(results.items.every((item) => item.category === 'access_control')).to.be.true;
      scope.done();
    });

    it('should handle authentication actions', async () => {
      const rawApiAuditLogs = [
        { timestamp: 1704067200, action: 'auth.login', category: 'authentication' },
        { timestamp: 1704067201, action: 'auth.logout', category: 'authentication' },
        { timestamp: 1704067202, action: 'auth.failed', category: 'authentication' },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/reports/auditLogs/ui`,
        { offset: 0, limit: 50 },
        rawApiAuditLogs,
        3
      );

      const results = new PagedResults<AuditLogEntry>();
      await producer.listAuditLogs(results, orgId);

      expect(results.items).to.have.length(3);
      expect(results.items.every((item) => item.category === 'authentication')).to.be.true;
      scope.done();
    });
  });
});
