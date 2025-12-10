import { expect } from 'chai';
import { prepareApi } from './Common';

describe('WorkspaceTest', () => {
  const api = prepareApi();

  describe('#list', () => {
    it('should list workspaces', async () => {
      const workspaceApi = api.getWorkspaceApi();
      const result = await workspaceApi.list();

      expect(result).to.not.be.undefined;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.greaterThan(0);

      const workspace = result.items[0];
      expect(workspace.uuid).to.be.a('string');
      expect(workspace.slug).to.be.a('string');

      console.log(`Found ${result.items.length} workspaces`);
      console.log(`First workspace: ${workspace.name} (${workspace.slug})`);
    });
  });
});
