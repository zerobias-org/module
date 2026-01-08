import { expect } from 'chai';
import { describe, it, before } from 'mocha';
import { getLogger } from '@auditmation/util-logger';
import { prepareTestConnection, testConfig, saveFixture, getTestCredentials } from './Common';
import { BitbucketImpl } from '../../src';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('Bitbucket Cloud - Connection Tests', function () {
  this.timeout(testConfig.timeout);

  let bitbucket: BitbucketImpl;

  before(async function () {
    const creds = getTestCredentials();
    if (!creds) {
      logger.warn('Skipping connection tests - no credentials');
      this.skip();
      return;
    }

    bitbucket = await prepareTestConnection();
  });

  describe('Authentication and Connection', () => {
    it('should successfully connect to Bitbucket Cloud API', async () => {
      logger.debug('Connection established in before hook');

      const isConnected = await bitbucket.isConnected();
      logger.debug('bitbucket.isConnected()', JSON.stringify(isConnected, null, 2));

      expect(isConnected).to.be.true;

      await saveFixture('connection-success.json', {
        connected: true,
        timestamp: new Date().toISOString(),
      });
    });

    it('should verify connection status', async () => {
      expect(bitbucket).to.not.be.undefined;

      const connectionStatus = await bitbucket.isConnected();
      logger.debug('bitbucket.isConnected()', JSON.stringify(connectionStatus, null, 2));

      expect(connectionStatus).to.be.a('boolean');
      expect(connectionStatus).to.be.true;
    });

    it('should provide module metadata', async () => {
      expect(bitbucket).to.not.be.undefined;

      const metadata = await bitbucket.metadata();
      logger.debug('bitbucket.metadata()', JSON.stringify(metadata, null, 2));

      expect(metadata).to.not.be.null;
      expect(metadata).to.not.be.undefined;

      await saveFixture('connection-metadata.json', metadata);
    });

    it('should provide operation support information', async () => {
      expect(bitbucket).to.not.be.undefined;

      const operations = ['user.getCurrentUser', 'repository.listRepositories'];
      const supportResults: Record<string, unknown> = {};

      for (const operation of operations) {
        const support = await bitbucket.isSupported(operation);
        logger.debug(`bitbucket.isSupported('${operation}')`, JSON.stringify(support, null, 2));
        supportResults[operation] = support;

        expect(support).to.not.be.null;
        expect(support).to.not.be.undefined;
      }

      await saveFixture('operation-support.json', supportResults);
    });
  });

  describe('User API', () => {
    it('should get current authenticated user', async () => {
      const userApi = bitbucket.getUserApi();
      const user = await userApi.getCurrentUser();

      logger.debug('getCurrentUser()', JSON.stringify(user, null, 2));

      expect(user).to.not.be.undefined;
      expect(user.uuid).to.be.a('string');
      expect(user.accountId).to.be.a('string');

      await saveFixture('current-user.json', user);
    });
  });

  describe('Repository API', () => {
    it('should list repositories in workspace', async function () {
      const creds = getTestCredentials();
      if (!creds) {
        this.skip();
        return;
      }

      const repoApi = bitbucket.getRepositoryApi();
      const repositories = await repoApi.listRepositories(creds.workspace);

      logger.debug('listRepositories()', JSON.stringify(repositories.slice(0, 3), null, 2));

      expect(repositories).to.be.an('array');
      // May be empty if workspace has no repos
      if (repositories.length > 0) {
        const repo = repositories[0];
        expect(repo.uuid).to.be.a('string');
        expect(repo.name).to.be.a('string');
        expect(repo.fullName).to.be.a('string');
      }

      await saveFixture('repositories-list.json', repositories.slice(0, 5));
    });
  });

  describe('Disconnection', () => {
    it('should handle disconnection gracefully', async () => {
      expect(bitbucket).to.not.be.undefined;

      await bitbucket.disconnect();
      logger.debug('bitbucket.disconnect() completed');

      const isConnectedAfterDisconnect = await bitbucket.isConnected();
      logger.debug('bitbucket.isConnected() after disconnect', JSON.stringify(isConnectedAfterDisconnect, null, 2));

      expect(isConnectedAfterDisconnect).to.be.false;
    });
  });
});
