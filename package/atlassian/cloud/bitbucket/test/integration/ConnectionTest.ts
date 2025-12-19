import { expect } from 'chai';
import { createConnectedImpl, getTestWorkspace, hasCredentials } from './Common';

describe('Bitbucket Connection Integration Tests', function() {
  // Integration tests may take longer due to network calls
  this.timeout(30000);

  describe('connect()', () => {
    it('should connect successfully with valid credentials', async function() {
      if (!hasCredentials()) {
        console.log('Skipping: BITBUCKET_EMAIL and BITBUCKET_API_TOKEN not set');
        this.skip();
        return;
      }

      const impl = await createConnectedImpl();
      expect(impl).to.not.be.undefined;

      const isConnected = await impl.isConnected();
      expect(isConnected).to.be.true;

      await impl.disconnect();
    });
  });

  describe('listWorkspaces()', () => {
    it('should list workspaces for authenticated user', async function() {
      if (!hasCredentials()) {
        console.log('Skipping: BITBUCKET_EMAIL and BITBUCKET_API_TOKEN not set');
        this.skip();
        return;
      }

      const impl = await createConnectedImpl();
      const workspaceApi = impl.getWorkspaceApi();

      const result = await workspaceApi.list();

      expect(result).to.not.be.undefined;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.greaterThan(0);

      // Verify workspace structure
      const workspace = result.items[0];
      expect(workspace).to.have.property('uuid');
      expect(workspace).to.have.property('slug');
      expect(workspace).to.have.property('name');

      await impl.disconnect();
    });
  });

  describe('listRepositories()', () => {
    it('should list repositories in a workspace', async function() {
      if (!hasCredentials()) {
        console.log('Skipping: BITBUCKET_EMAIL and BITBUCKET_API_TOKEN not set');
        this.skip();
        return;
      }

      const impl = await createConnectedImpl();
      const repositoryApi = impl.getRepositoryApi();
      const workspace = getTestWorkspace();

      const result = await repositoryApi.list(workspace);

      expect(result).to.not.be.undefined;
      expect(result.items).to.be.an('array');

      // If there are repositories, verify structure
      if (result.items.length > 0) {
        const repo = result.items[0];
        expect(repo).to.have.property('uuid');
        expect(repo).to.have.property('slug');
        expect(repo).to.have.property('name');
        expect(repo).to.have.property('fullName');
      }

      await impl.disconnect();
    });
  });
});
