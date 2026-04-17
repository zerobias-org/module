/**
 * Ready Player Me Module E2E Tests — one test, three impls.
 *
 * Ported from the original test/integration/{ConnectionTest,UserTest}.ts.
 * The describeModule framework owns the connect/isConnected/disconnect
 * lifecycle, so those paths are not re-exercised here.
 */

import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { ReadyPlayerMe } from '../../hub-sdk/generated/api/index.js';
import { READYPLAYERME_APP_ID, hasCredentials } from './constants.js';

describeModule<ReadyPlayerMe>('Ready Player Me Module', (client) => {

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('UserApi.create', () => {
    it('should create a user successfully', async () => {
      const res = await client.getUserApi().create({
        data: { applicationId: READYPLAYERME_APP_ID },
      });
      expect(res).to.be.ok;
      expect(res).to.have.property('data');
      expect(res.data).to.have.property('id');
    });
  });

}, (data) => CoreError.deserialize(data));
