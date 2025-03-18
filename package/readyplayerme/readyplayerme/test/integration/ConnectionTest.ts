import { NotConnectedError } from '@auditmation/types-core-js';
import { expect } from 'chai';
import { newReadyPlayerMe } from '../../src';
import { connectionProfile } from './Common';

describe('ConnectionTest', () => {

  const api = newReadyPlayerMe();

  describe('#connect', () => {

    it('should connect succesfully', async () => {
      expect(await api.isConnected()).to.equal(false);
      await api.connect(connectionProfile);
      expect(await api.isConnected()).equals(true);
    });

    it('should throw NotConnected error', async () => {
      try {
        await api.connect(connectionProfile);
        const workspaceApi = api.getUserApi();
        await api.disconnect();
        await workspaceApi.create({ data: { applicationId: 'dummy' } });
        expect.fail('Expected error not thrown');
      } catch (err) {
        expect(err).to.be.instanceOf(NotConnectedError);
      }
    });
  });

  describe('#disconnect', () => {
    before('make sure api is connected', async () => {
      await api.connect(connectionProfile);
      expect(await api.isConnected()).equals(true);
    });

    it('should disconnect', async () => {
      await api.disconnect();
      expect(await api.isConnected()).to.equal(false);
    });
  });
});
