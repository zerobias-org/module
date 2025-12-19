import { expect } from 'chai';
import { createConnectedImpl } from './Common';

describe('Bitbucket Connection Integration Tests', function() {
  // Integration tests may take longer due to network calls
  this.timeout(30000);

  describe('connect()', () => {
    it('should connect successfully with valid credentials', async () => {
      // Skip if no credentials configured
      if (!process.env.BITBUCKET_ACCESS_TOKEN) {
        console.log('Skipping: BITBUCKET_ACCESS_TOKEN not set');
        return;
      }

      const impl = await createConnectedImpl();
      expect(impl).to.not.be.undefined;
    });

    // Additional integration tests will be added in Phase 5 (Testing)
    // - listWorkspaces() integration test
    // - listRepositories() integration test
  });
});
