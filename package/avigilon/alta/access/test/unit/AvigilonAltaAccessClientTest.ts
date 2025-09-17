import { expect } from 'chai';
import nock from 'nock';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { 
  InvalidCredentialsError,
  UnexpectedError,
  NotConnectedError,
  Email
} from '@auditmation/types-core-js';
import { cleanNock } from '../utils/nock-helpers';

describe('AvigilonAltaAccessClient', () => {
  let client: AvigilonAltaAccessClient;
  const baseUrl = 'https://helium.prod.openpath.com';
  const testEmail = process.env.AVIGILON_EMAIL || 'test@example.com';
  const testPassword = process.env.AVIGILON_PASSWORD || 'testpass123';

  beforeEach(() => {
    client = new AvigilonAltaAccessClient();
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor and Initialization', () => {
    it('should create AvigilonAltaAccessClient instance', () => {
      expect(client).to.be.instanceOf(AvigilonAltaAccessClient);
    });

    it('should initialize in disconnected state', async () => {
      expect(await client.isConnected()).to.be.false;
    });
  });

  describe('Connection Management', () => {
    describe('connect()', () => {
      it('should connect with valid credentials', async () => {
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
        
        expect(await client.isConnected()).to.be.true;
      });

      it('should return connection state after successful connect', async () => {
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

        const connectionState = await client.connect(profile);
        
        expect(connectionState.accessToken).to.equal('mock-token-123');
        expect(connectionState.expiresIn).to.be.a('number');
        expect(await client.isConnected()).to.be.true;
      });

      it('should handle missing credentials', async () => {
        const profile: ConnectionProfile = {
          email: new Email('invalid@example.com'),
          password: ''  // Empty password
        };

        try {
          await client.connect(profile);
          expect.fail('Expected InvalidCredentialsError to be thrown');
        } catch (error) {
          expect(error).to.be.instanceOf(InvalidCredentialsError);
        }
      });

      it('should handle undefined API token', async () => {
        const profile: ConnectionProfile = {
          email: new Email(testEmail),
          password: undefined as any
        };

        try {
          await client.connect(profile);
          expect.fail('Expected InvalidCredentialsError to be thrown');
        } catch (error) {
          expect(error).to.be.instanceOf(InvalidCredentialsError);
        }
      });
    });

    describe('isConnected()', () => {
      it('should return false when not connected', async () => {
        expect(await client.isConnected()).to.be.false;
      });

      it('should return true when connected', async () => {
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
        
        expect(await client.isConnected()).to.be.true;
      });
    });

    describe('disconnect()', () => {
      it('should disconnect successfully', async () => {
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
        expect(await client.isConnected()).to.be.true;

        await client.disconnect();
        expect(await client.isConnected()).to.be.false;
      });

      it('should handle disconnect when not connected', async () => {
        await client.disconnect();
        expect(await client.isConnected()).to.be.false;
      });
    });
  });

  describe('HTTP Client Access', () => {
    it('should provide HTTP client when connected', async () => {
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
      
      const httpClient = client.getHttpClient();
      expect(httpClient).to.not.be.null;
      expect(httpClient).to.have.property('defaults');
      expect((httpClient.defaults.headers as any).Authorization).to.equal(`Bearer mock-token-123`);
    });

    it('should throw error when getting HTTP client while not connected', () => {
      try {
        client.getHttpClient();
        expect.fail('Expected NotConnectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NotConnectedError);
      }
    });
  });

  describe('Token Refresh', () => {
    it('should refresh access token successfully', async () => {
      const profile: ConnectionProfile = {
        email: new Email(testEmail),
        password: testPassword
      };

      // Mock login endpoint
      nock(baseUrl)
        .post('/auth/login')
        .reply(200, {
          data: {
            token: 'initial-token-123',
            expiresAt: new Date(Date.now() + 3600000).toISOString()
          }
        });

      await client.connect(profile);
      
      // Mock refresh endpoint
      const newExpiresAt = new Date(Date.now() + 7200000).toISOString();
      nock(baseUrl)
        .post('/auth/accessTokens/refresh')
        .reply(200, {
          data: {
            token: 'refreshed-token-456',
            expiresAt: newExpiresAt
          }
        });

      const newConnectionState = await client.refresh();
      
      expect(newConnectionState.accessToken).to.equal('refreshed-token-456');
      expect(newConnectionState.expiresIn).to.be.a('number');
      expect(newConnectionState.expiresIn).to.be.greaterThan(7000); // Should be around 7200 seconds
      
      // Verify the HTTP client has the new token
      const httpClient = client.getHttpClient();
      expect((httpClient.defaults.headers as any).Authorization).to.equal(`Bearer refreshed-token-456`);
    });

    it('should throw error when refreshing without connection', async () => {
      try {
        await client.refresh();
        expect.fail('Expected NotConnectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(NotConnectedError);
      }
    });
  });

  describe('Error Handling Integration', () => {
    beforeEach(async () => {
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
    });

    it('should handle 401 Unauthorized errors', async () => {
      const scope = nock(baseUrl)
        .matchHeader('authorization', `Bearer mock-token-123`)
        .get('/test-endpoint')
        .reply(401, { error: 'Unauthorized', statusCode: 401 });

      const httpClient = client.getHttpClient();

      try {
        await httpClient.get('/test-endpoint');
        expect.fail('Expected InvalidCredentialsError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }

      scope.done();
    });

    it('should handle 404 Not Found errors', async () => {
      const scope = nock(baseUrl)
        .matchHeader('authorization', `Bearer mock-token-123`)
        .get('/nonexistent-endpoint')
        .reply(404, { error: 'Not found', statusCode: 404 });

      const httpClient = client.getHttpClient();

      try {
        await httpClient.get('/nonexistent-endpoint');
        expect.fail('Expected NoSuchObjectError to be thrown');
      } catch (error) {
        expect((error as any).constructor.name).to.equal('NoSuchObjectError');
      }

      scope.done();
    });

    it('should handle 500 Internal Server errors', async () => {
      const scope = nock(baseUrl)
        .matchHeader('authorization', `Bearer mock-token-123`)
        .get('/error-endpoint')
        .reply(500, { error: 'Internal server error', statusCode: 500 });

      const httpClient = client.getHttpClient();

      try {
        await httpClient.get('/error-endpoint');
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }

      scope.done();
    });
  });
});