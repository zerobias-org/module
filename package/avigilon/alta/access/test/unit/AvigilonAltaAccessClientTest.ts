import { expect } from 'chai';
import nock from 'nock';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { 
  InvalidCredentialsError,
  UnexpectedError,
  URL
} from '@auditmation/types-core-js';
import { cleanNock } from '../utils/nock-helpers';

describe('AvigilonAltaAccessClient', () => {
  let client: AvigilonAltaAccessClient;
  const testToken = 'test-api-token-abc123';

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
      it('should connect with valid API token', async () => {
        const profile: ConnectionProfile = {
          apiToken: testToken
        };

        await client.connect(profile);
        
        expect(await client.isConnected()).to.be.true;
      });

      it('should connect with custom URL', async () => {
        const customUrl = 'https://custom-api.openpath.com';
        const profile: ConnectionProfile = {
          apiToken: testToken,
          url: new URL(customUrl)
        };

        await client.connect(profile);
        
        expect(await client.isConnected()).to.be.true;
      });

      it('should handle missing credentials', async () => {
        const profile: ConnectionProfile = {
          apiToken: '' // Empty token
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
          apiToken: undefined as any
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
          apiToken: testToken
        };

        await client.connect(profile);
        
        expect(await client.isConnected()).to.be.true;
      });
    });

    describe('disconnect()', () => {
      it('should disconnect successfully', async () => {
        const profile: ConnectionProfile = {
          apiToken: testToken
        };

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
        apiToken: testToken
      };

      await client.connect(profile);
      
      const httpClient = client.getHttpClient();
      expect(httpClient).to.not.be.null;
      expect(httpClient).to.have.property('defaults');
      expect((httpClient.defaults.headers as any).Authorization).to.equal(`Bearer ${testToken}`);
    });

    it('should throw error when getting HTTP client while not connected', () => {
      try {
        client.getHttpClient();
        expect.fail('Expected UnexpectedError to be thrown');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }
    });
  });

  describe('Error Handling Integration', () => {
    beforeEach(async () => {
      const profile: ConnectionProfile = {
        apiToken: testToken
      };
      await client.connect(profile);
    });

    it('should handle 401 Unauthorized errors', async () => {
      const scope = nock('https://api.openpath.com')
        .matchHeader('authorization', `Bearer ${testToken}`)
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
      const scope = nock('https://api.openpath.com')
        .matchHeader('authorization', `Bearer ${testToken}`)
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
      const scope = nock('https://api.openpath.com')
        .matchHeader('authorization', `Bearer ${testToken}`)
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