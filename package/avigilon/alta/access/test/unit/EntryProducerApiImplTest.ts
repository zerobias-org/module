import { expect } from 'chai';
import nock from 'nock';
import { EntryProducerApiImpl } from '../../src/EntryProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { EntryDetails } from '../../generated/model';
import {
  Email,
  NotConnectedError,
  UnexpectedError
} from '@auditmation/types-core-js';
import {
  mockAuthenticatedRequest,
  mockErrorResponse,
  cleanNock
} from '../utils/nock-helpers';

describe('EntryProducerApiImpl', () => {
  let client: AvigilonAltaAccessClient;
  let producer: EntryProducerApiImpl;
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

  describe('Entry List Operations', () => {
    it('should list entries and return flat array', async () => {
      // Create mock raw API response data
      const rawApiEntries = [
        {
          id: 1001,
          name: 'Main Entrance',
          opal: 'E1-001',
          pincode: null,
          isPincodeEnabled: false,
          color: '#FF5733',
          isMusterPoint: false,
          notes: 'Primary building entrance',
          externalUuid: null,
          isReaderless: false,
          createdAt: '2024-01-15T10:00:00.000Z',
          updatedAt: '2024-01-15T10:00:00.000Z',
          zone: {
            id: 100,
            name: 'Building A',
            site: {
              id: 10,
              name: 'Main Campus'
            }
          },
          acu: {
            id: 500,
            name: 'ACU-001',
            isGatewayMode: false
          },
          entryState: {
            id: 1,
            name: 'Active'
          },
          schedule: {
            id: 200,
            name: 'Business Hours'
          },
          cameras: [
            {
              id: 300,
              name: 'Camera-001'
            }
          ]
        },
        {
          id: 1002,
          name: 'Side Door',
          opal: 'E1-002',
          pincode: '1234',
          isPincodeEnabled: true,
          color: '#33FF57',
          isMusterPoint: true,
          notes: null,
          externalUuid: 'ext-uuid-123',
          isReaderless: true,
          createdAt: '2024-02-20T14:30:00.000Z',
          updatedAt: '2024-02-20T14:30:00.000Z'
        }
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: rawApiEntries,
          meta: { pagination: { offset: 0, limit: 50 } },
          totalCount: 2
        });

      const results = await producer.list(orgId);

      // Verify we get a flat array, not wrapped
      expect(results).to.be.an('array');
      expect(results).to.not.have.property('data');
      expect(results).to.not.have.property('meta');
      expect(results).to.not.have.property('totalCount');

      // Verify we have entries
      expect(results).to.have.length(2);

      // Verify first entry structure
      expect(results[0]).to.have.property('id');
      expect(results[0]).to.have.property('name');
      expect(results[0].id).to.be.a('number');
      expect(results[0].name).to.be.a('string');
      expect(results[0].id).to.equal(1001);
      expect(results[0].name).to.equal('Main Entrance');

      scope.done();
    });

    it('should handle empty entry list', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: [],
          totalCount: 0
        });

      const results = await producer.list(orgId);

      expect(results).to.be.an('array');
      expect(results).to.have.length(0);
      scope.done();
    });
  });

  describe('Entry Data Validation', () => {
    it('should validate entry response schema with all fields', async () => {
      const mockEntryData = {
        id: 1001,
        name: 'Main Entrance',
        opal: 'E1-001',
        pincode: null,
        isPincodeEnabled: false,
        color: '#FF5733',
        isMusterPoint: false,
        notes: 'Primary entrance',
        externalUuid: null,
        isReaderless: false,
        effectiveLocationRestriction: {
          type: 'ALLOW_ALL'
        },
        org: {
          id: 123
        },
        shadow: {
          state: 'active'
        },
        createdAt: '2024-01-15T10:00:00.000Z',
        updatedAt: '2024-01-15T10:00:00.000Z',
        wirelessLock: null,
        zone: {
          id: 100,
          name: 'Building A',
          site: {
            id: 10,
            name: 'Main Campus'
          }
        },
        acu: {
          id: 500,
          name: 'ACU-001',
          isGatewayMode: false
        },
        entryState: {
          id: 1,
          name: 'Active'
        },
        schedule: {
          id: 200,
          name: 'Business Hours'
        },
        cameras: [
          {
            id: 300,
            name: 'Camera-001'
          }
        ]
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: [mockEntryData],
          totalCount: 1
        });

      const results = await producer.list(orgId);

      expect(results).to.have.length(1);
      const entry = results[0];

      // Validate required fields
      expect(entry).to.have.property('id');
      expect(entry).to.have.property('name');

      // Validate data types
      expect(entry.id).to.be.a('number');
      expect(entry.name).to.be.a('string');

      // Validate optional fields if present
      if (entry.opal) expect(entry.opal).to.be.a('string');
      if (entry.pincode !== undefined) {
        expect(entry.pincode).to.satisfy((val: any) => val === null || typeof val === 'string');
      }
      if (entry.isPincodeEnabled !== undefined) expect(entry.isPincodeEnabled).to.be.a('boolean');
      if (entry.color) expect(entry.color).to.be.a('string');
      if (entry.isMusterPoint !== undefined) expect(entry.isMusterPoint).to.be.a('boolean');
      if (entry.isReaderless !== undefined) expect(entry.isReaderless).to.be.a('boolean');
      if (entry.createdAt) expect(entry.createdAt).to.be.instanceOf(Date);
      if (entry.updatedAt) expect(entry.updatedAt).to.be.instanceOf(Date);

      // Validate nested objects
      if (entry.zone) {
        expect(entry.zone).to.have.property('id');
        expect(entry.zone).to.have.property('name');
        expect(entry.zone.id).to.be.a('number');
        expect(entry.zone.name).to.be.a('string');
      }

      if (entry.acu) {
        expect(entry.acu).to.have.property('id');
        expect(entry.acu).to.have.property('name');
        expect(entry.acu.id).to.be.a('number');
        expect(entry.acu.name).to.be.a('string');
      }

      if (entry.cameras) {
        expect(entry.cameras).to.be.an('array');
        entry.cameras.forEach(camera => {
          expect(camera).to.have.property('id');
          expect(camera).to.have.property('name');
        });
      }

      scope.done();
    });

    it('should validate entry with minimal fields', async () => {
      const mockMinimalEntry = {
        id: 1001,
        name: 'Simple Door'
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: [mockMinimalEntry],
          totalCount: 1
        });

      const results = await producer.list(orgId);

      expect(results).to.have.length(1);
      expect(results[0].id).to.equal(1001);
      expect(results[0].name).to.equal('Simple Door');

      scope.done();
    });

    it('should handle nullable and optional fields correctly', async () => {
      const mockEntry = {
        id: 1001,
        name: 'Test Entry',
        pincode: null,
        notes: null,
        externalUuid: null,
        wirelessLock: null
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: [mockEntry],
          totalCount: 1
        });

      const results = await producer.list(orgId);

      expect(results).to.have.length(1);
      const entry = results[0];

      // These fields can be null
      expect(entry.pincode).to.satisfy((val: any) => val === null || val === undefined || typeof val === 'string');
      expect(entry.notes).to.satisfy((val: any) => val === null || val === undefined || typeof val === 'string');
      expect(entry.externalUuid).to.satisfy((val: any) => val === null || val === undefined || typeof val === 'string');
      expect(entry.wirelessLock).to.satisfy((val: any) => val === null || val === undefined || typeof val === 'object');

      scope.done();
    });
  });

  describe('Response Flattening', () => {
    it('should flatten response and drop metadata', async () => {
      const rawApiEntries = [
        { id: 1, name: 'Entry 1' },
        { id: 2, name: 'Entry 2' }
      ];

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: rawApiEntries,
          meta: {
            pagination: { offset: 0, limit: 50 },
            additionalInfo: 'some metadata'
          },
          totalCount: 2,
          filteredCount: 2
        });

      const results = await producer.list(orgId);

      // Verify we get a flat array, not wrapped
      expect(results).to.be.an('array');
      expect(results).to.not.have.property('data');
      expect(results).to.not.have.property('meta');
      expect(results).to.not.have.property('totalCount');
      expect(results).to.not.have.property('filteredCount');

      // Verify array contents
      expect(results).to.have.length(2);

      scope.done();
    });

    it('should handle response with missing data array', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          totalCount: 0
          // Missing 'data' array
        });

      const results = await producer.list(orgId);

      expect(results).to.be.an('array');
      expect(results).to.have.length(0);
      scope.done();
    });

    it('should handle response with null data', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: null,
          totalCount: 0
        });

      const results = await producer.list(orgId);

      expect(results).to.be.an('array');
      expect(results).to.have.length(0);
      scope.done();
    });
  });

  describe('Error Handling', () => {
    it('should handle API errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        {},
        500
      );

      try {
        await producer.list(orgId);
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle network errors', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .replyWithError('Network error');

      try {
        await producer.list(orgId);
        expect.fail('Expected network error to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });

    it('should handle API rate limiting', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        {},
        429
      );

      try {
        await producer.list(orgId);
        expect.fail('Expected RateLimitExceededError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('RateLimitExceededError');
      }

      scope.done();
    });

    it('should handle unauthorized errors', async () => {
      const scope = mockErrorResponse(
        mockAuthenticatedRequest(baseUrl, 'mock-token-123'),
        'GET',
        `/orgs/${orgId}/entries`,
        {},
        401
      );

      try {
        await producer.list(orgId);
        expect.fail('Expected error to be thrown');
      } catch (error) {
        // Should throw some kind of authentication error
        expect(error).to.exist;
      }

      scope.done();
    });
  });

  describe('Date Mapping', () => {
    it('should map ISO date strings to Date objects', async () => {
      const mockEntry = {
        id: 1001,
        name: 'Test Entry',
        createdAt: '2024-01-15T10:00:00.000Z',
        updatedAt: '2024-02-20T15:30:00.000Z'
      };

      const scope = mockAuthenticatedRequest(baseUrl, 'mock-token-123')
        .get(`/orgs/${orgId}/entries`)
        .reply(200, {
          data: [mockEntry],
          totalCount: 1
        });

      const results = await producer.list(orgId);

      expect(results).to.have.length(1);
      const entry = results[0];

      if (entry.createdAt) {
        expect(entry.createdAt).to.be.instanceOf(Date);
        expect(entry.createdAt.toISOString()).to.equal('2024-01-15T10:00:00.000Z');
      }

      if (entry.updatedAt) {
        expect(entry.updatedAt).to.be.instanceOf(Date);
        expect(entry.updatedAt.toISOString()).to.equal('2024-02-20T15:30:00.000Z');
      }

      scope.done();
    });
  });
});
