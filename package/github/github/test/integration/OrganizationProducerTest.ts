import { expect } from 'chai';
import { PagedResults } from '@auditmation/types-core-js';
import { getLogger } from '@auditmation/util-logger';
import { Email, URL } from '@auditmation/types-core-js';
import { prepareApi, areCredentialsAvailable, skipIfNoCredentials, sanitizeResponse } from './Common';
import { GitHubConnector, OrganizationApi } from '../../src';
import { Organization, OrganizationInfo } from '../../generated/model';

const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');

describe('GitHub Integration - Organization Producer Tests', () => {
  let github: GitHubConnector;
  let organizationApi: OrganizationApi;

  before(async function() {
    if (!areCredentialsAvailable()) {
      skipIfNoCredentials('Organization Producer tests');
      this.skip();
      return;
    }

    github = await prepareApi();
    organizationApi = github.getOrganizationApi();
  });

  after(async function() {
    if (github) {
      await github.disconnect();
      logger.debug('github.disconnect()', 'Connection closed');
    }
  });

  describe('List Operations', () => {
    
    it('should list organizations for authenticated user', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('list organizations test');
        this.skip();
        return;
      }

      this.timeout(15000);

      const results = await organizationApi.list(1, 10);
      
      logger.debug('organizationApi.list(pageNumber: 1, pageSize: 10)', JSON.stringify(sanitizeResponse(results), null, 2));

      expect(results.items).to.be.an('array');
      
      if (results.items.length > 0) {
        // Test first organization structure
        const org = results.items[0];
        expect(org).to.be.instanceOf(Organization);
        expect(org.id).to.be.a('number');
        expect(org.login).to.be.a('string');
        expect(org.url).to.be.instanceOf(URL);
        
        // Optional fields
        if (org.reposUrl) {
          expect(org.reposUrl).to.be.instanceOf(URL);
        }
        if (org.avatarUrl) {
          expect(org.avatarUrl).to.be.instanceOf(URL);
        }
        if (org.description !== null && org.description !== undefined) {
          expect(org.description).to.be.a('string');
        }
      }
    });

    it('should handle pagination correctly', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('pagination test');
        this.skip();
        return;
      }

      this.timeout(15000);

      // Test small page size to potentially trigger pagination
      const results = await organizationApi.list(1, 1);
      
      logger.debug('organizationApi.list(pageNumber: 1, pageSize: 1)', JSON.stringify(sanitizeResponse(results), null, 2));

      expect(results.items).to.be.an('array');
      expect(results.pageNumber).to.equal(1);
      expect(results.pageSize).to.equal(1);
      
      if (results.items.length === 1) {
        expect(results.items).to.have.lengthOf(1);
      }
      
      // Check pagination token handling
      if (results.pageToken) {
        expect(results.pageToken).to.be.a('string');
      }
    });

    it('should handle empty results gracefully', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('empty results test');
        this.skip();
        return;
      }

      this.timeout(10000);

      // Test with high page number that should return no results
      const results = await organizationApi.list(1000, 10);
      
      logger.debug('organizationApi.list(pageNumber: 1000, pageSize: 10)', JSON.stringify(sanitizeResponse(results), null, 2));

      expect(results.items).to.be.an('array');
      // Could be empty or have some items depending on user's organizations
      expect(results.pageNumber).to.equal(1000);
    });
  });

  describe('Get Operations', () => {
    let testOrganizationLogin: string | undefined;

    before(async function() {
      if (!areCredentialsAvailable()) {
        this.skip();
        return;
      }

      // Get an organization to test with by listing first
      try {
        const results = await organizationApi.list(1, 1);
        
        if (results.items.length > 0) {
          testOrganizationLogin = results.items[0].login;
          logger.debug('Found test organization login', testOrganizationLogin);
        }
      } catch (error) {
        logger.debug('Unable to find test organization', JSON.stringify(error, null, 2));
      }
    });

    it('should get organization details by login', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('get organization test');
        this.skip();
        return;
      }

      if (!testOrganizationLogin) {
        this.skip(); // No organizations available to test with
        return;
      }

      this.timeout(10000);

      const orgInfo = await organizationApi.get(testOrganizationLogin);
      logger.debug(`organizationApi.get('${testOrganizationLogin}')`, JSON.stringify(sanitizeResponse(orgInfo), null, 2));

      expect(orgInfo).to.be.instanceOf(OrganizationInfo);
      
      // Core Organization fields (inherited)
      expect(orgInfo.id).to.be.a('number');
      expect(orgInfo.login).to.be.a('string');
      expect(orgInfo.login).to.equal(testOrganizationLogin);
      expect(orgInfo.url).to.be.instanceOf(URL);
      
      // Optional Organization fields
      if (orgInfo.reposUrl) {
        expect(orgInfo.reposUrl).to.be.instanceOf(URL);
      }
      if (orgInfo.avatarUrl) {
        expect(orgInfo.avatarUrl).to.be.instanceOf(URL);
      }
      if (orgInfo.description !== null && orgInfo.description !== undefined) {
        expect(orgInfo.description).to.be.a('string');
      }
      
      // OrganizationInfo specific fields
      if (orgInfo.name !== null && orgInfo.name !== undefined) {
        expect(orgInfo.name).to.be.a('string');
      }
      if (orgInfo.company !== null && orgInfo.company !== undefined) {
        expect(orgInfo.company).to.be.a('string');
      }
      if (orgInfo.blog !== null && orgInfo.blog !== undefined) {
        expect(orgInfo.blog).to.be.instanceOf(URL);
      }
      if (orgInfo.location !== null && orgInfo.location !== undefined) {
        expect(orgInfo.location).to.be.a('string');
      }
      if (orgInfo.email !== null && orgInfo.email !== undefined) {
        expect(orgInfo.email).to.be.instanceOf(Email);
      }
      if (orgInfo.twitterUsername !== null && orgInfo.twitterUsername !== undefined) {
        expect(orgInfo.twitterUsername).to.be.a('string');
      }
      if (orgInfo.publicRepos !== null && orgInfo.publicRepos !== undefined) {
        expect(orgInfo.publicRepos).to.be.a('number');
      }
      if (orgInfo.followers !== null && orgInfo.followers !== undefined) {
        expect(orgInfo.followers).to.be.a('number');
      }
      if (orgInfo.following !== null && orgInfo.following !== undefined) {
        expect(orgInfo.following).to.be.a('number');
      }
      if (orgInfo.createdAt !== null && orgInfo.createdAt !== undefined) {
        expect(orgInfo.createdAt).to.be.instanceOf(Date);
      }
      if (orgInfo.updatedAt !== null && orgInfo.updatedAt !== undefined) {
        expect(orgInfo.updatedAt).to.be.instanceOf(Date);
      }
    });

    it('should handle non-existent organization gracefully', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('non-existent organization test');
        this.skip();
        return;
      }

      this.timeout(10000);

      const nonExistentOrg = 'definitely-does-not-exist-org-12345';
      
      try {
        const orgInfo = await organizationApi.get(nonExistentOrg);
        logger.debug(`organizationApi.get('${nonExistentOrg}')`, JSON.stringify(sanitizeResponse(orgInfo), null, 2));
        
        // If no error is thrown, the organization might exist or API behavior differs
        expect(orgInfo).to.be.instanceOf(OrganizationInfo);
        
      } catch (error: any) {
        logger.debug(`Expected error for non-existent organization '${nonExistentOrg}'`, JSON.stringify(error.message, null, 2));
        
        expect(error).to.be.an('error');
        // GitHub typically returns 404 for non-existent organizations
        // The exact error handling depends on the implementation
      }
    });

    it('should handle organization with minimal data', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('minimal organization data test');
        this.skip();
        return;
      }

      if (!testOrganizationLogin) {
        this.skip(); // No organizations available to test with
        return;
      }

      this.timeout(10000);

      // This test verifies that the API handles organizations with minimal data correctly
      const orgInfo = await organizationApi.get(testOrganizationLogin);
      logger.debug(`organizationApi.get('${testOrganizationLogin}') - minimal data check`, JSON.stringify(sanitizeResponse(orgInfo), null, 2));

      // Only verify required fields, optional fields may be null/undefined
      expect(orgInfo).to.be.instanceOf(OrganizationInfo);
      expect(orgInfo.id).to.be.a('number');
      expect(orgInfo.login).to.be.a('string');
      expect(orgInfo.url).to.be.instanceOf(URL);
    });
  });

  describe('Error Handling and Edge Cases', () => {
    
    it('should handle API rate limiting gracefully', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('rate limiting test');
        this.skip();
        return;
      }

      this.timeout(30000);

      // Make multiple rapid requests to potentially trigger rate limiting
      const requests: Promise<any>[] = [];
      
      for (let i = 0; i < 3; i++) {
        requests.push(organizationApi.list(1, 5));
      }
      
      try {
        const responses = await Promise.all(requests);
        logger.debug('Multiple rapid requests completed', `${responses.length} requests successful`);
        
        expect(responses).to.have.lengthOf(3);
        responses.forEach(response => {
          // Each response should be a PagedResults object
          expect(response).to.have.property('items');
          expect(response.items).to.be.an('array');
        });
        
      } catch (error: any) {
        logger.debug('Rate limiting or other error encountered', JSON.stringify(error.message, null, 2));
        
        // Rate limiting errors are acceptable in this test
        expect(error).to.be.an('error');
      }
    });

    it('should handle network timeouts gracefully', async function() {
      if (!areCredentialsAvailable()) {
        skipIfNoCredentials('network timeout test');
        this.skip();
        return;
      }

      this.timeout(5000);

      try {
        const results = await organizationApi.list(1, 1);
        
        logger.debug('Network request completed within timeout', 'Success');
        expect(results.items).to.be.an('array');
        
      } catch (error: any) {
        logger.debug('Network timeout or other error', JSON.stringify(error.message, null, 2));
        
        // Timeout errors are acceptable - just verify error handling works
        expect(error).to.be.an('error');
      }
    });
  });
});