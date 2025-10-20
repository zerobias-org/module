import { describe, it, beforeEach, afterEach } from 'mocha';
import { expect } from 'chai';
import nock from 'nock';
import { Email } from '@auditmation/types-core-js';
import { AuthProducerApiImpl } from '../../src/AuthProducerApiImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { mockAuthenticatedRequest, cleanNock } from '../utils/nock-helpers';

describe('AuthProducerApiImpl', () => {
  let authProducer: AuthProducerApiImpl;
  let client: AvigilonAltaAccessClient;

  const baseUrl = 'https://helium.prod.openpath.com';
  const mockToken = 'mock-token-123';

  beforeEach(async () => {
    client = new AvigilonAltaAccessClient();
    authProducer = new AuthProducerApiImpl(client);

    const profile: ConnectionProfile = {
      email: new Email('test@example.com'),
      password: 'password',
    };

    // Mock login endpoint
    nock(baseUrl)
      .post('/auth/login')
      .reply(200, {
        data: {
          token: mockToken,
          expiresAt: new Date(Date.now() + 3600000).toISOString(),
        },
      });

    await client.connect(profile);
  });

  afterEach(() => {
    cleanNock();
  });

  describe('getTokenProperties', () => {
    it('should retrieve token properties successfully', async () => {
      const mockTokenProperties = {
        identityId: 30053017,
        createdAt: '2025-07-01T10:00:00.000Z',
        expiresAt: '2025-07-15T10:00:00.000Z',
        tokenType: 'Bearer',
        jti: 'token-id-123',
        iat: 1719828000,
        exp: 1721044800,
        tokenScopeList: [
          {
            org: { id: 1067 },
            scope: ['read:users', 'read:groups'],
          },
        ],
      };

      const scope = mockAuthenticatedRequest(baseUrl, mockToken)
        .get(`/auth/accessTokens/${mockToken}`)
        .reply(200, { data: mockTokenProperties });

      const result = await authProducer.getTokenProperties();

      expect(result.organizationId).to.equal('1067');
      expect(result.identityId).to.equal('30053017');
      expect(result.issuedAt).to.be.instanceOf(Date);
      expect(result.expiresAt).to.be.instanceOf(Date);
      expect(result.scope).to.deep.equal(['read:users', 'read:groups']);
      expect(result.tokenType).to.equal('Bearer');
      expect(result.jti).to.equal('token-id-123');
      expect(result.iat).to.equal(1719828000);
      expect(result.exp).to.equal(1721044800);

      scope.done();
    });

    it('should throw error when no access token available', async () => {
      const disconnectedClient = new AvigilonAltaAccessClient();
      const disconnectedAuthProducer = new AuthProducerApiImpl(disconnectedClient);

      try {
        await disconnectedAuthProducer.getTokenProperties();
        expect.fail('Should have thrown an error');
      } catch (error: unknown) {
        expect((error as Error).message).to.include('Not connected');
      }
    });

    it('should handle API errors properly', async () => {
      const scope = mockAuthenticatedRequest(baseUrl, mockToken)
        .get(`/auth/accessTokens/${mockToken}`)
        .reply(401, { error: 'Unauthorized' });

      try {
        await authProducer.getTokenProperties();
        expect.fail('Should have thrown an error');
      } catch (error: unknown) {
        expect((error as Error).message).to.include('Invalid credentials');
      }

      scope.done();
    });
  });
});
