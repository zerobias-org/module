import { expect } from 'chai';
import { prepareApi, testWorkspace } from './Common';

describe('RepositoryTest', () => {
  const api = prepareApi();

  describe('#list', () => {
    it('should list repositories in workspace', async function () {
      if (!testWorkspace) {
        this.skip();
        return;
      }

      const repositoryApi = api.getRepositoryApi();
      const result = await repositoryApi.list(testWorkspace);

      expect(result).to.not.be.undefined;
      expect(result.items).to.be.an('array');

      console.log(`Found ${result.items.length} repositories in workspace ${testWorkspace}`);

      if (result.items.length > 0) {
        const repo = result.items[0];
        expect(repo.uuid).to.be.a('string');
        expect(repo.slug).to.be.a('string');
        expect(repo.fullName).to.be.a('string');

        console.log(`First repository: ${repo.name} (${repo.fullName})`);
      }
    });
  });
});
