import { expect } from 'chai';
import { newAzureresourcegraph } from '../../src/index.js';

describe('new Azureresourcegraph', function () {
  it('should return a new instance', async function () {
    const api = newAzureresourcegraph();
    expect(api).to.be.ok;
  });
});
