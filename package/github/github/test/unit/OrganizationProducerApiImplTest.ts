import { expect } from 'chai';
import * as nock from 'nock';
import { PagedResults, URL, Email } from '@auditmation/types-core-js';
import { OrganizationProducerApiImpl } from '../../src/OrganizationProducerApiImpl';
import { Organization, OrganizationInfo } from '../../generated/model';
import { GitHubClient } from '../../src/GitHubClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { loadFixture } from '../utils/nock-helpers';

describe('OrganizationProducerApiImpl', () => {
  let client: GitHubClient;
  let producer: OrganizationProducerApiImpl;
  const baseUrl = 'https://api.github.com';

  beforeEach(async () => {
    client = new GitHubClient();
    producer = new OrganizationProducerApiImpl(client);
    
    // Connect client for tests
    const profile = new ConnectionProfile('test_token');
    
    nock(baseUrl)
      .get('/user')
      .matchHeader('authorization', 'token test_token')
      .reply(200, { id: 1, login: 'testuser' });
    
    await client.connect(profile);
    // Don't clean nock mocks here - let individual tests clean up
  });

  afterEach(() => {
    nock.cleanAll();
  });

  describe('get()', () => {
    it('should get organization info successfully', async () => {
      const orgData = loadFixture('organization-get-success');
      
      const scope = nock(baseUrl)
        .get('/orgs/testorg')
        .matchHeader('authorization', 'token test_token')
        .reply(200, orgData);

      const result: OrganizationInfo = await producer.get('testorg');

      expect(scope.isDone()).to.be.true;
      expect(result).to.exist;
      expect(result.id).to.equal(12345);
      expect(result.login).to.equal('testorg');
      expect(result.name).to.equal('Test Organization');
      expect(result.url).to.be.instanceof(URL);
      expect(result.url.toString()).to.equal('https://api.github.com/orgs/testorg');
      expect(result.email).to.be.instanceof(Email);
      expect(result.email?.toString()).to.equal('contact@testorg.com');
      expect(result.createdAt).to.be.instanceof(Date);
      expect(result.updatedAt).to.be.instanceof(Date);
    });
  });

  describe('list()', () => {
    it('should list organizations successfully', async () => {
      const orgList = loadFixture('organization-list-success');
      
      const pagedResults = new PagedResults<Organization>();
      pagedResults.pageNumber = 1;
      pagedResults.pageSize = 50;

      const scope = nock(baseUrl)
        .get('/user/orgs')
        .query({ per_page: 50, page: 1 })
        .matchHeader('authorization', 'token test_token')
        .reply(200, orgList, {
          'content-type': 'application/json; charset=utf-8'
        });

      await producer.list(pagedResults);

      expect(scope.isDone()).to.be.true;
      expect(pagedResults.items).to.have.length(2);
      
      const firstOrg = pagedResults.items[0];
      expect(firstOrg.id).to.equal(12345);
      expect(firstOrg.login).to.equal('testorg1');
      expect(firstOrg.url).to.be.instanceof(URL);
      expect(firstOrg.url.toString()).to.equal('https://api.github.com/orgs/testorg1');
      expect(firstOrg.description).to.equal('Test organization for development');
    });

    it('should handle pagination correctly', async () => {
      const orgList = loadFixture('organization-list-success');
      
      const pagedResults = new PagedResults<Organization>();
      pagedResults.pageNumber = 1;
      pagedResults.pageSize = 10;

      const scope = nock(baseUrl)
        .get('/user/orgs')
        .query({ per_page: 10, page: 1 })
        .matchHeader('authorization', 'token test_token')
        .reply(200, orgList, {
          'content-type': 'application/json; charset=utf-8',
          'link': '<https://api.github.com/user/orgs?page=2>; rel="next"'
        });

      await producer.list(pagedResults);

      expect(scope.isDone()).to.be.true;
      expect(pagedResults.pageToken).to.equal('has-next');
    });
  });
});