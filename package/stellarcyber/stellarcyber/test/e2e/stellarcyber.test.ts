/**
 * Stellar Cyber Module E2E Tests.
 *
 * The describeModule framework owns the connect/isConnected/disconnect
 * lifecycle, so those paths are not re-exercised here. Skips unless live
 * Stellar Cyber credentials (STELLARCYBER_HOST + STELLARCYBER_API_TOKEN) are set.
 */

import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { StellarCyber } from '../../hub-sdk/generated/api/index.js';
import { STELLARCYBER_CASE_ID, hasCredentials } from './constants.js';

describeModule<StellarCyber>('Stellar Cyber Module', (client) => {

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('CaseApi.list', () => {
    it('should list cases', async () => {
      const results = await client.getCaseApi().list();
      expect(results).to.be.ok;
    });
  });

  describe('CaseApi.listAlerts', () => {
    it('should list alerts for a case', async function () {
      if (!STELLARCYBER_CASE_ID) {
        this.skip();
      }
      const results = await client.getCaseApi().listAlerts(STELLARCYBER_CASE_ID);
      expect(results).to.be.ok;
    });
  });

}, (data) => CoreError.deserialize(data));
