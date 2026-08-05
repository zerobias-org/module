import { expect } from 'chai';
import nock from 'nock';
import {
  InvalidCredentialsError,
  NotConnectedError,
  RateLimitExceededError,
  UnauthorizedError,
  UnexpectedError
} from '@zerobias-org/types-core-js';
import { AzureResourceGraphClient } from '../../src/AzureResourceGraphClient.js';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile.js';
import {
  cleanNock,
  mockTokenSuccess,
  mockTokenFailure,
  mockArgQuery,
  TEST_DIRECTORY_ID,
  TEST_CLIENT_ID,
  TEST_CLIENT_SECRET
} from '../utils/nock-helpers.js';

function testProfile(): ConnectionProfile {
  return {
    client_id: TEST_CLIENT_ID,
    client_secret: TEST_CLIENT_SECRET,
    directoryId: TEST_DIRECTORY_ID,
  } as unknown as ConnectionProfile;
}

describe('AzureResourceGraphClient', () => {
  let client: AzureResourceGraphClient;

  beforeEach(() => {
    client = new AzureResourceGraphClient();
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
    nock.enableNetConnect();
  });

  describe('connect()', () => {
    it('should mint a client-credentials token and report connected', async () => {
      mockTokenSuccess();

      const state = await client.connect(testProfile());

      expect(state.accessToken).to.equal('mock-arm-token');
      expect(state.expiresIn).to.be.a('number');
      expect(state.expiresIn).to.be.greaterThan(0);
      expect(await client.isConnected()).to.be.true;
    });

    it('should throw InvalidCredentialsError when directoryId is missing', async () => {
      const profile = {
        client_id: TEST_CLIENT_ID,
        client_secret: TEST_CLIENT_SECRET,
      } as unknown as ConnectionProfile;

      try {
        await client.connect(profile);
        expect.fail('expected InvalidCredentialsError');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }
    });

    it('should throw InvalidCredentialsError on a rejected token request', async () => {
      mockTokenFailure(401);

      try {
        await client.connect(testProfile());
        expect.fail('expected InvalidCredentialsError');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }
      expect(await client.isConnected()).to.be.false;
    });

    it('should accept camelCase clientId/clientSecret aliases', async () => {
      mockTokenSuccess();

      const profile = {
        clientId: TEST_CLIENT_ID,
        clientSecret: TEST_CLIENT_SECRET,
        directoryId: TEST_DIRECTORY_ID,
      } as unknown as ConnectionProfile;

      const state = await client.connect(profile);
      expect(state.accessToken).to.equal('mock-arm-token');
    });
  });

  describe('disconnect()', () => {
    it('should drop the connection', async () => {
      mockTokenSuccess();
      await client.connect(testProfile());
      expect(await client.isConnected()).to.be.true;

      await client.disconnect();

      expect(await client.isConnected()).to.be.false;
      expect(() => client.getHttpClient()).to.throw(NotConnectedError);
    });
  });

  describe('query()', () => {
    it('should throw NotConnectedError before connect()', async () => {
      try {
        await client.query({ query: 'Resources' });
        expect.fail('expected NotConnectedError');
      } catch (error) {
        expect(error).to.be.instanceOf(NotConnectedError);
      }
    });

    it('should POST the pinned api-version and return the envelope', async () => {
      mockTokenSuccess();
      await client.connect(testProfile());

      mockArgQuery({ totalRecords: 1, count: 1, data: [{ id: 'x', name: 'y', type: 'z' }] });

      const response = await client.query({ query: 'Resources' });

      expect(response.totalRecords).to.equal(1);
      expect(response.data).to.have.length(1);
    });

    it('should default resultFormat to objectArray', async () => {
      mockTokenSuccess();
      await client.connect(testProfile());

      let capturedBody: Record<string, unknown> | undefined;
      nock('https://management.azure.com')
        .post('/providers/Microsoft.ResourceGraph/resources', (body) => {
          capturedBody = body as Record<string, unknown>;
          return true;
        })
        .query({ 'api-version': '2024-04-01' })
        .reply(200, { totalRecords: 0, count: 0, data: [] });

      await client.query({ query: 'Resources' });

      const options = capturedBody?.options as Record<string, unknown>;
      expect(options.resultFormat).to.equal('objectArray');
    });

    it('should map 429 to RateLimitExceededError', async () => {
      mockTokenSuccess();
      await client.connect(testProfile());

      mockArgQuery(
        { error: { code: 'RateLimiting', message: 'Rate limit exceeded' } },
        429,
        { 'x-ms-user-quota-remaining': '0', 'x-ms-user-quota-resets-after': '00:00:05' }
      );

      try {
        await client.query({ query: 'Resources' });
        expect.fail('expected RateLimitExceededError');
      } catch (error) {
        expect(error).to.be.instanceOf(RateLimitExceededError);
      }
    });

    it('should map 403 to UnauthorizedError', async () => {
      mockTokenSuccess();
      await client.connect(testProfile());

      mockArgQuery({ error: { code: 'AccessDenied', message: 'No readable scope' } }, 403);

      try {
        await client.query({ query: 'Resources' });
        expect.fail('expected UnauthorizedError');
      } catch (error) {
        expect(error).to.be.instanceOf(UnauthorizedError);
      }
    });

    it('should throw UnexpectedError on a malformed envelope', async () => {
      mockTokenSuccess();
      await client.connect(testProfile());

      mockArgQuery({ notData: true });

      try {
        await client.query({ query: 'Resources' });
        expect.fail('expected UnexpectedError');
      } catch (error) {
        expect(error).to.be.instanceOf(UnexpectedError);
      }
    });
  });
});
