import { expect } from 'chai';
import nock from 'nock';
import {
  PagedResults, Email, NotConnectedError,
  InvalidCredentialsError,
  NoSuchObjectError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { RoleProducerApiImpl } from '../../src/RoleProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { Role, RoleInfo, RoleUser } from '../../generated/model';
import {
  mockAuthenticatedRequest,
  mockPaginatedResponse,
  mockSingleResponse,
  mockErrorResponse,
  loadFixture,
  cleanNock
} from '../utils/nock-helpers';

describe('RoleProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: RoleProducerApiImpl;
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

    producer = new RoleProducerApiImpl(client);
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor', () => {
    it('should create RoleProducerApiImpl instance', () => {
      expect(producer).to.be.instanceOf(RoleProducerApiImpl);
    });

    it('should throw error if client not connected', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();

      expect(() => {
        new RoleProducerApiImpl(disconnectedClient);
      }).to.throw(NotConnectedError);
    });
  });

  describe('listRoles()', () => {
    it('should list roles with default pagination', async () => {
      // Create mock raw API response data in the format the mapper expects
      const rawApiRoles = [
        {
          id: 12345,
          name: 'Administrator',
          description: 'Full system access',
          isEditable: false,
          isSiteSpecific: false,
          isMfaRequired: true,
          userCount: 5,
          sites: [],
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-01-15T10:00:00.000Z',
        },
        {
          id: 12346,
          name: 'Site Manager',
          description: 'Site level access',
          isEditable: true,
          isSiteSpecific: true,
          isMfaRequired: false,
          userCount: 12,
          sites: [{ id: 100, name: 'Main Building' }],
          createdAt: '2024-02-20T14:30:00.000Z',
          updatedAt: '2024-02-20T14:30:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: 0, limit: 50 },
        rawApiRoles,
        rawApiRoles.length
      );

      const results = new PagedResults<RoleInfo>();
      await producer.listRoles(results, orgId);

      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');

      // Verify the roles have expected properties
      expect(['Administrator', 'Site Manager', 'Operator']).to.include(results.items[0].name);
      expect(results.items[0].description).to.be.a('string');

      scope.done();
    });

    it('should list roles with custom pagination', async () => {
      const pageNumber = 2;
      const pageSize = 10;
      const expectedOffset = (pageNumber - 1) * pageSize;

      // Create mock raw API response data
      const rawApiRoles = [
        {
          id: 12347,
          name: 'Operator',
          description: 'Read-only access',
          isEditable: true,
          isSiteSpecific: false,
          isMfaRequired: false,
          userCount: 25,
          sites: [],
          createdAt: '2024-03-01T09:00:00.000Z',
          updatedAt: '2024-03-01T09:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: expectedOffset, limit: pageSize },
        rawApiRoles,
        rawApiRoles.length
      );

      const results = new PagedResults<RoleInfo>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listRoles(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle empty roles list', async () => {
      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<RoleInfo>();
      await producer.listRoles(results, orgId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle invalid response format', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { invalidFormat: 'no data array' });

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });
  });

  describe('listRoleUsers()', () => {
    it('should list users for role with default pagination', async () => {
      const roleId = '12345';
      const rawApiUsers = [
        {
          id: 100001,
          status: 'active',
          identity: {
            id: 'identity-100001',
            email: 'user1@example.com',
            firstName: 'John',
            lastName: 'Doe',
            mobilePhone: '+1-555-0100',
          },
          assignedAt: '2024-01-10T12:00:00.000Z',
        },
        {
          id: 100002,
          status: 'active',
          identity: {
            id: 'identity-100002',
            email: 'user2@example.com',
            firstName: 'Jane',
            lastName: 'Smith',
            mobilePhone: '+1-555-0101',
          },
          assignedAt: '2024-01-11T14:30:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: 0, limit: 50 },
        rawApiUsers,
        rawApiUsers.length
      );

      const results = new PagedResults<RoleUser>();
      await producer.listRoleUsers(results, orgId, roleId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);

      // Verify user properties
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('status');
      expect(results.items[0].identity).to.have.property('email');
      expect(results.items[0].identity).to.have.property('firstName');

      scope.done();
    });

    it('should list role users with custom pagination', async () => {
      const roleId = '12345';
      const pageNumber = 3;
      const pageSize = 5;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiUsers = [
        {
          id: 100010,
          status: 'inactive',
          identity: {
            id: 'identity-100010',
            email: 'user10@example.com',
            firstName: 'Bob',
            lastName: 'Johnson',
            mobilePhone: '+1-555-0110',
          },
          assignedAt: '2024-02-05T10:15:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: expectedOffset, limit: pageSize },
        rawApiUsers,
        rawApiUsers.length
      );

      const results = new PagedResults<RoleUser>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listRoleUsers(results, orgId, roleId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle role with no users', async () => {
      const roleId = '12345';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<RoleUser>();
      await producer.listRoleUsers(results, orgId, roleId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle role not found when listing users', async () => {
      const roleId = '999999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<RoleUser>();

      try {
        await producer.listRoleUsers(results, orgId, roleId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle invalid response format for role users', async () => {
      const roleId = '12345';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles/${roleId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { invalidFormat: 'no data array' });

      const results = new PagedResults<RoleUser>();

      try {
        await producer.listRoleUsers(results, orgId, roleId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });
  });

  describe('listUserRoles()', () => {
    it('should list roles for user with default pagination', async () => {
      const userId = '100001';
      const rawApiRoles = [
        {
          id: 12345,
          name: 'Administrator',
          description: 'Full system access',
          permissions: ['read:all', 'write:all', 'delete:all'],
          assignedAt: '2024-01-05T09:00:00.000Z',
        },
        {
          id: 12346,
          name: 'Site Manager',
          description: 'Site level access',
          permissions: ['read:sites', 'write:sites'],
          assignedAt: '2024-01-10T11:30:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        rawApiRoles,
        rawApiRoles.length
      );

      const results = new PagedResults<Role>();
      await producer.listUserRoles(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length.above(0);
      expect(results.count).to.be.above(0);

      // Verify role properties
      expect(results.items[0]).to.have.property('id');
      expect(results.items[0]).to.have.property('name');
      expect(['Administrator', 'Site Manager', 'Operator']).to.include(results.items[0].name);

      scope.done();
    });

    it('should list user roles with custom pagination', async () => {
      const userId = '100001';
      const pageNumber = 2;
      const pageSize = 3;
      const expectedOffset = (pageNumber - 1) * pageSize;

      const rawApiRoles = [
        {
          id: 12347,
          name: 'Operator',
          description: 'Read-only access',
          permissions: ['read:all'],
          assignedAt: '2024-02-15T13:45:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: expectedOffset, limit: pageSize },
        rawApiRoles,
        rawApiRoles.length
      );

      const results = new PagedResults<Role>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listUserRoles(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.count).to.be.a('number');
      scope.done();
    });

    it('should handle user with no roles', async () => {
      const userId = '100001';

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        [],
        0
      );

      const results = new PagedResults<Role>();
      await producer.listUserRoles(results, orgId, userId);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(0);
      expect(results.count).to.equal(0);
      scope.done();
    });

    it('should handle user not found when listing roles', async () => {
      const userId = '999999';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        404
      );

      const results = new PagedResults<Role>();

      try {
        await producer.listUserRoles(results, orgId, userId);
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NoSuchObjectError);
      }

      scope.done();
    });

    it('should handle invalid response format for user roles', async () => {
      const userId = '100001';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users/${userId}/roles`)
        .query({ offset: 0, limit: 50 })
        .reply(200, { invalidFormat: 'no data array' });

      const results = new PagedResults<Role>();

      try {
        await producer.listUserRoles(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
        expect((error as any).message).to.include('Invalid response format');
      }

      scope.done();
    });
  });

  describe('Data Validation', () => {
    it('should validate role response schema', async () => {
      const rawApiRoles = [
        {
          id: 12345,
          name: 'Administrator',
          description: 'Full system access',
          isEditable: false,
          isSiteSpecific: false,
          isMfaRequired: true,
          userCount: 5,
          sites: [],
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-01-15T10:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: 0, limit: 50 },
        rawApiRoles,
        rawApiRoles.length
      );

      const results = new PagedResults<RoleInfo>();
      await producer.listRoles(results, orgId);

      const role = results.items[0];

      // Validate required fields
      expect(role).to.have.property('id');
      expect(role).to.have.property('name');

      // Validate data types
      expect(role.id).to.be.a('string');
      expect(role.name).to.be.a('string');

      // Optional fields should be present when provided
      if (role.description) {
        expect(role.description).to.be.a('string');
      }
      if (role.userCount !== undefined) {
        expect(role.userCount).to.be.a('number');
      }
      if (role.createdAt) {
        expect(role.createdAt).to.be.instanceOf(Date);
      }
      if (role.updatedAt) {
        expect(role.updatedAt).to.be.instanceOf(Date);
      }

      scope.done();
    });

    it('should validate role user response schema', async () => {
      const roleId = '12345';
      const rawApiUsers = [
        {
          id: 100001,
          status: 'active',
          identity: {
            id: 'identity-100001',
            email: 'user1@example.com',
            firstName: 'John',
            lastName: 'Doe',
            mobilePhone: '+1-555-0100',
          },
          assignedAt: '2024-01-10T12:00:00.000Z',
        },
      ];

      const scope = mockPaginatedResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: 0, limit: 50 },
        rawApiUsers,
        rawApiUsers.length
      );

      const results = new PagedResults<RoleUser>();
      await producer.listRoleUsers(results, orgId, roleId);

      const user = results.items[0];

      // Validate required fields
      expect(user).to.have.property('id');
      expect(user.id).to.be.a('string');

      // Validate identity
      if (user.identity) {
        expect(user.identity).to.have.property('email');
        expect(user.identity).to.have.property('firstName');
      }

      // Validate optional fields
      if (user.status) {
        expect(user.status).to.be.a('string');
      }
      if (user.assignedAt) {
        expect(user.assignedAt).to.be.instanceOf(Date);
      }

      scope.done();
    });
  });

  describe('Error Handling and Edge Cases', () => {
    it('should handle invalid pagination parameters', () => {
      // Test with extremely large page size
      const results = new PagedResults<RoleInfo>();
      results.pageSize = 5000; // Should be limited by API to 1000

      // This should not throw an error, but should handle gracefully
      expect(() => results.pageSize = 5000).to.not.throw();
    });

    it('should handle API rate limiting for listRoles', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle API rate limiting for listRoleUsers', async () => {
      const roleId = '12345';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<RoleUser>();

      try {
        await producer.listRoleUsers(results, orgId, roleId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle API rate limiting for listUserRoles', async () => {
      const userId = '100001';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        429
      );

      const results = new PagedResults<Role>();

      try {
        await producer.listUserRoles(results, orgId, userId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should propagate server errors for listRoles', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should propagate server errors for listRoleUsers', async () => {
      const roleId = '12345';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles/${roleId}/users`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<RoleUser>();

      try {
        await producer.listRoleUsers(results, orgId, roleId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should propagate server errors for listUserRoles', async () => {
      const userId = '100001';

      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/users/${userId}/roles`,
        { offset: 0, limit: 50 },
        500
      );

      const results = new PagedResults<Role>();

      try {
        await producer.listUserRoles(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors for listRoles', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors for listRoleUsers', async () => {
      const roleId = '12345';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles/${roleId}/users`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<RoleUser>();

      try {
        await producer.listRoleUsers(results, orgId, roleId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors for listUserRoles', async () => {
      const userId = '100001';

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users/${userId}/roles`)
        .query({ offset: 0, limit: 50 })
        .replyWithError('Network error');

      const results = new PagedResults<Role>();

      try {
        await producer.listUserRoles(results, orgId, userId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle unauthorized errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/roles`,
        { offset: 0, limit: 50 },
        401
      );

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });
  });

  describe('Response Data Mapping', () => {
    it('should handle response with missing data array for roles', async () => {
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with null data for roles', async () => {
      const mockResponse = {
        data: null,
        totalCount: 3,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<RoleInfo>();

      try {
        await producer.listRoles(results, orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with missing data array for role users', async () => {
      const roleId = '12345';
      const mockResponse = {
        totalCount: 0,
        // Missing 'data' array
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles/${roleId}/users`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<RoleUser>();

      try {
        await producer.listRoleUsers(results, orgId, roleId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle response with null data for user roles', async () => {
      const userId = '100001';
      const mockResponse = {
        data: null,
        totalCount: 2,
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/users/${userId}/roles`)
        .query({ offset: 0, limit: 50 })
        .reply(200, mockResponse);

      const results = new PagedResults<Role>();

      try {
        await producer.listUserRoles(results, orgId, userId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });

  describe('Pagination Boundary Conditions', () => {
    it('should limit page size to 1000 for listRoles', async () => {
      const mockRoles = [
        {
          id: 12345,
          name: 'Test Role',
          description: 'Test',
          isEditable: true,
          isSiteSpecific: false,
          isMfaRequired: false,
          userCount: 0,
          sites: [],
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      // Request with page size > 1000 should be limited to 1000
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: 0, limit: 1000 })
        .reply(200, { data: mockRoles, totalCount: 1 });

      const results = new PagedResults<RoleInfo>();
      results.pageNumber = 1;
      results.pageSize = 5000; // Should be capped at 1000

      await producer.listRoles(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should handle minimum page size of 1', async () => {
      const mockRoles = [
        {
          id: 12345,
          name: 'Test Role',
          description: 'Test',
          isEditable: true,
          isSiteSpecific: false,
          isMfaRequired: false,
          userCount: 0,
          sites: [],
          createdAt: '2024-01-01T00:00:00.000Z',
          updatedAt: '2024-01-01T00:00:00.000Z',
        },
      ];

      // Request with page size of 1 (minimum allowed)
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: 0, limit: 1 })
        .reply(200, { data: mockRoles, totalCount: 1 });

      const results = new PagedResults<RoleInfo>();
      results.pageNumber = 1;
      results.pageSize = 1; // Minimum valid page size

      await producer.listRoles(results, orgId);

      expect(results.items).to.be.an('array');
      scope.done();
    });

    it('should calculate offset correctly for high page numbers', async () => {
      const pageNumber = 100;
      const pageSize = 50;
      const expectedOffset = (pageNumber - 1) * pageSize; // 4950

      const mockRoles = [];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/roles`)
        .query({ offset: expectedOffset, limit: pageSize })
        .reply(200, { data: mockRoles, totalCount: 0 });

      const results = new PagedResults<RoleInfo>();
      results.pageNumber = pageNumber;
      results.pageSize = pageSize;

      await producer.listRoles(results, orgId);

      expect(results.items).to.have.length(0);
      scope.done();
    });
  });
});
