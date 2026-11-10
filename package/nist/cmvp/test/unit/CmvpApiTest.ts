import { expect } from 'chai';
import { newCmvp } from '../../src/index.js';

describe('new Cmvp', function () {
  it('should return a new instance', async function () {
    const api = newCmvp();
    expect(api).to.be.ok;
  });
});
