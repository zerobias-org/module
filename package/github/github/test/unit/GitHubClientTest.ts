import { expect } from 'chai';
import * as nock from 'nock';
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
  URL
} from '@auditmation/types-core-js';
import { GitHubClient } from '../../src/GitHubClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { mockAuthenticatedRequest } from '../utils/nock-helpers';

describe('GitHubClient', () => {
  let client: GitHubClient;
  const baseUrl = 'https://api.github.com';

  beforeEach(() => {
    client = new GitHubClient();
    nock.cleanAll();
  });

  afterEach(() => {
    nock.cleanAll();
  });

  describe('Constructor and Initialization', () => {
    it('should create a GitHubClient instance', () => {
      expect(client).to.be.instanceOf(GitHubClient);
    });

    it('should not be connected initially', async () => {
      const connected = await client.isConnected();
      expect(connected).to.be.false;
    });
  });

  describe('connect()', () => {
    it('should connect successfully with valid credentials', async () => {
      const profile = new ConnectionProfile('test_token');
      
      const scope = nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token test_token')
        .reply(200, {
          id: 1,
          login: 'testuser',
          email: 'test@example.com'
        });

      await client.connect(profile);
      
      expect(scope.isDone()).to.be.true;
    });

    it('should handle 401 authentication error', async () => {
      const profile = new ConnectionProfile('invalid_token');
      
      nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token invalid_token')
        .reply(401, { message: 'Bad credentials' });

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
      const connected = await client.isConnected();
      expect(connected).to.be.false;
    });

    it('should return true when connected successfully', async () => {
      const profile = new ConnectionProfile('test_token');
      
      // Mock the connect call
      nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token test_token')
        .reply(200, { id: 1, login: 'testuser' });

      // Mock the isConnected verification call  
      nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token test_token')
        .reply(200, { id: 1, login: 'testuser' });

      await client.connect(profile);
      const connected = await client.isConnected();
      
      expect(connected).to.be.true;
    });
  });

  describe('getHttpClient()', () => {
    it('should return Octokit instance when connected', async () => {
      const profile = new ConnectionProfile('test_token');
      
      nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token test_token')
        .reply(200, { id: 1, login: 'testuser' });

      await client.connect(profile);
      const httpClient = client.getHttpClient();
      
      expect(httpClient).to.exist;
      expect(httpClient.rest).to.exist;
    });

    it('should throw error when not connected', () => {
      expect(() => client.getHttpClient())
        .to.throw(UnexpectedError, 'Client not connected. Call connect() first.');
    });
  });
});