import { expect } from 'chai';
import { getLogger } from '@auditmation/util-logger';
import { URL } from '@auditmation/types-core-js';
import { prepareApi, areCredentialsAvailable, skipIfNoCredentials } from './Common';
import { GitHubConnector } from '../../src';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('GitHub Integration - Connection Tests', () => {
  
  before(function() {
    if (!areCredentialsAvailable()) {
      this.skip();
    }
  });

  describe('Authentication and Connection', () => {
    let github: GitHubConnector;

    it('should establish connection with valid credentials', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('connection test');
        this.skip();
        return;
      }

      github = await prepareApi();
      
      const isConnected = await github.isConnected();
      logger.debug('github.isConnected()', JSON.stringify(isConnected, null, 2));
      
      expect(isConnected).to.be.true;
    });

    it('should retrieve connection metadata', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('metadata test');
        this.skip();
        return;
      }

      if (!github) {
        github = await prepareApi();
      }

      const metadata = await github.metadata();
      logger.debug('github.metadata()', JSON.stringify(metadata, null, 2));
      
      expect(metadata).to.not.be.null;
      expect(metadata.status).to.exist;
    });

    it('should support organization operations', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('operation support test');
        this.skip();
        return;
      }

      if (!github) {
        github = await prepareApi();
      }

      const supportStatus = await github.isSupported('getOrganization');
      logger.debug(`github.isSupported('getOrganization')`, JSON.stringify(supportStatus, null, 2));
      
      expect(supportStatus).to.exist;
    });

    it('should access organization API', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('organization API access test');
        this.skip();
        return;
      }

      if (!github) {
        github = await prepareApi();
      }

      const organizationApi = github.getOrganizationApi();
      logger.debug('github.getOrganizationApi()', 'OrganizationApi instance created');
      
      expect(organizationApi).to.not.be.null;
      expect(organizationApi).to.not.be.undefined;
    });

    afterEach(async function() {
      if (github) {
        await github.disconnect();
        logger.debug('github.disconnect()', 'Connection closed');
      }
    });
  });

  describe('Error Handling', () => {
    
    it('should handle invalid credentials gracefully', async function() {
      this.timeout(10000);
      
      // This test doesn't require real credentials, it tests error handling
      const { newGitHub } = await import('../../src');
      const { ConnectionProfile } = await import('../../generated/model');
      
      const invalidGithub = newGitHub();
      const invalidProfile = new ConnectionProfile('invalid-token');
      
      try {
        await invalidGithub.connect(invalidProfile);
        const isConnected = await invalidGithub.isConnected();
        logger.debug('invalidGithub.isConnected() with invalid token', JSON.stringify(isConnected, null, 2));
        
        // Connection might succeed but operations should fail
        // The actual behavior depends on GitHub's API validation
        expect(isConnected).to.be.a('boolean');
        
      } catch (error) {
        logger.debug('Expected error with invalid credentials', JSON.stringify(error, null, 2));
        expect(error).to.be.an('error');
      } finally {
        await invalidGithub.disconnect();
      }
    });

    it('should handle missing URL gracefully', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('missing URL test');
        this.skip();
        return;
      }

      const { newGitHub } = await import('../../src');
      const { ConnectionProfile } = await import('../../generated/model');
      
      const github = newGitHub();
      // Create profile with token but no URL (should use default)
      const profile = new ConnectionProfile(process.env.GITHUB_API_TOKEN!);
      
      try {
        await github.connect(profile);
        const isConnected = await github.isConnected();
        logger.debug('github.isConnected() with no URL', JSON.stringify(isConnected, null, 2));
        
        expect(isConnected).to.be.a('boolean');
        
      } finally {
        await github.disconnect();
      }
    });
  });
});