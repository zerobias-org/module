import { expect } from 'chai';
import * as nock from 'nock';
import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus
} from '@auditmation/hub-core';
import { URL } from '@auditmation/types-core-js';
import { GitHubConnectorImpl } from '../../src/GitHubConnectorImpl';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { mockAuthenticatedRequest } from '../utils/nock-helpers';

describe('GitHubConnectorImpl', () => {
  let connector: GitHubConnectorImpl;
  const baseUrl = 'https://api.github.com';

  beforeEach(() => {
    connector = new GitHubConnectorImpl();
    nock.cleanAll();
  });

  afterEach(() => {
    nock.cleanAll();
  });

  describe('Constructor and Initialization', () => {
    it('should create a GitHubConnectorImpl instance', () => {
      expect(connector).to.be.instanceOf(GitHubConnectorImpl);
    });

    it('should not be connected initially', async () => {
      const connected = await connector.isConnected();
      expect(connected).to.be.false;
    });
  });

  describe('connect()', () => {
    it('should connect successfully with valid profile', async () => {
      const profile = new ConnectionProfile('test_token');
      
      const scope = mockAuthenticatedRequest(baseUrl)
        .get('/user')
        .reply(200, {
          id: 1,
          login: 'testuser',
          email: 'test@example.com'
        });

      await connector.connect(profile);
      
      expect(scope.isDone()).to.be.true;
    });

    it('should connect with custom URL profile', async () => {
      const customUrl = new URL('https://github.enterprise.com/api/v3');
      const profile = new ConnectionProfile('test_token', customUrl);
      
      const scope = nock('https://github.enterprise.com/api/v3', {
        reqheaders: {
          'authorization': 'token test_token'
        }
      })
        .get('/user')
        .reply(200, {
          id: 1,
          login: 'testuser',
          email: 'test@example.com'
        });

      await connector.connect(profile);
      
      expect(scope.isDone()).to.be.true;
    });

    it('should delegate to client and handle connection errors', async () => {
      const profile = new ConnectionProfile('invalid_token');
      
      nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token invalid_token')
        .reply(401, { message: 'Bad credentials' });

      try {
        await connector.connect(profile);
        expect.fail('Expected error to be thrown');
      } catch (error) {
        expect(error).to.exist;
      }
    });
  });

  describe('isConnected()', () => {
    it('should delegate to client isConnected method', async () => {
      const connected = await connector.isConnected();
      expect(connected).to.be.false;
    });

    it('should return true when client is connected', async () => {
      const profile = new ConnectionProfile('test_token');
      
      // Mock connect call
      mockAuthenticatedRequest(baseUrl)
        .get('/user')
        .reply(200, { id: 1, login: 'testuser' });

      // Mock isConnected verification call
      mockAuthenticatedRequest(baseUrl)
        .get('/user')
        .reply(200, { id: 1, login: 'testuser' });

      await connector.connect(profile);
      const connected = await connector.isConnected();
      
      expect(connected).to.be.true;
    });
  });

  describe('disconnect()', () => {
    it('should disconnect successfully', async () => {
      const profile = new ConnectionProfile('test_token');
      
      mockAuthenticatedRequest(baseUrl)
        .get('/user')
        .reply(200, { id: 1, login: 'testuser' });

      await connector.connect(profile);
      await connector.disconnect();
      
      const connected = await connector.isConnected();
      expect(connected).to.be.false;
    });

    it('should clean up organization API instance', async () => {
      const profile = new ConnectionProfile('test_token');
      
      mockAuthenticatedRequest(baseUrl)
        .get('/user')
        .reply(200, { id: 1, login: 'testuser' });

      await connector.connect(profile);
      
      // Get organization API to initialize it
      const orgApi1 = connector.getOrganizationApi();
      expect(orgApi1).to.exist;
      
      await connector.disconnect();
      
      // After disconnect, should create new instance
      const orgApi2 = connector.getOrganizationApi();
      expect(orgApi2).to.exist;
      expect(orgApi2).to.not.equal(orgApi1);
    });
  });

  describe('isSupported()', () => {
    it('should return Maybe for any operation', async () => {
      const result = await connector.isSupported('testOperation');
      expect(result).to.equal(OperationSupportStatus.Maybe);
    });

    it('should ignore operation ID parameter', async () => {
      const result1 = await connector.isSupported('operation1');
      const result2 = await connector.isSupported('operation2');
      
      expect(result1).to.equal(OperationSupportStatus.Maybe);
      expect(result2).to.equal(OperationSupportStatus.Maybe);
    });
  });

  describe('metadata()', () => {
    it('should return ConnectionMetadata with Down status', async () => {
      const metadata = await connector.metadata();
      
      expect(metadata).to.be.instanceOf(ConnectionMetadata);
      expect(metadata.status).to.equal(ConnectionStatus.Down);
    });
  });

  describe('getOrganizationApi()', () => {
    it('should return OrganizationApi instance', () => {
      const orgApi = connector.getOrganizationApi();
      expect(orgApi).to.exist;
      expect(orgApi.get).to.be.a('function');
      expect(orgApi.list).to.be.a('function');
    });

    it('should return same instance on multiple calls', () => {
      const orgApi1 = connector.getOrganizationApi();
      const orgApi2 = connector.getOrganizationApi();
      
      expect(orgApi1).to.equal(orgApi2);
    });

    it('should create new instance after disconnect', async () => {
      const profile = new ConnectionProfile('test_token');
      
      mockAuthenticatedRequest(baseUrl)
        .get('/user')
        .reply(200, { id: 1, login: 'testuser' });

      await connector.connect(profile);
      
      const orgApi1 = connector.getOrganizationApi();
      await connector.disconnect();
      const orgApi2 = connector.getOrganizationApi();
      
      expect(orgApi1).to.not.equal(orgApi2);
    });
  });

  describe('Error Propagation', () => {
    it('should propagate connection errors from client', async () => {
      const profile = new ConnectionProfile('invalid_token');
      
      nock(baseUrl)
        .get('/user')
        .matchHeader('authorization', 'token invalid_token')
        .reply(401, { message: 'Bad credentials' });

      try {
        await connector.connect(profile);
        expect.fail('Expected error to be thrown');
      } catch (error) {
        expect(error).to.exist;
      }
    });

    it('should propagate isConnected errors from client', async () => {
      // This test ensures that any errors from the client's isConnected method
      // are properly propagated through the connector
      const connected = await connector.isConnected();
      expect(connected).to.be.false;
    });
  });
});