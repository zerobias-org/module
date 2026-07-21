/**
 * Cmvp Module E2E Tests — one test, three impls.
 *
 * TEST_MODE selects the impl:
 *   direct  — CmvpImpl (in-process)
 *   docker  — CmvpClient (container REST)
 *   hub     — CmvpClient (Hub Server via Dana)
 *
 * CMVP has no authentication, so every test here hits the real public
 * registry directly — nothing to skip on missing credentials.
 */

import { expect, assert } from 'chai';
import { CoreError, NoSuchObjectError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { Cmvp } from '../../hub-sdk/generated/api/index.js';
import { TEST_CERTIFICATE_NUMBER } from './constants.js';

describeModule<Cmvp>('Cmvp Module', (client) => {

  describe('CertificateApi.list', () => {
    it('lists CMVP certificates from the live registry', async () => {
      const rows = await client.getCertificateApi().list(1, 5);
      expect(rows).to.be.an('array').with.length.greaterThan(0);
      expect(rows[0]).to.have.property('certificateNumber');
      expect(rows[0]).to.have.property('detailUrl');
    });
  });

  describe('CertificateApi.get', () => {
    it('gets a known certificate by number', async () => {
      const cert = await client.getCertificateApi().get(TEST_CERTIFICATE_NUMBER);
      expect(cert.certificateNumber).to.equal(TEST_CERTIFICATE_NUMBER);
      expect(cert.caveatText).to.be.a('string');
      expect(cert.rawEntryHtml).to.be.a('string').and.not.empty;
    });

    it('rejects with NoSuchObjectError for a certificate number that does not exist', async () => {
      try {
        await client.getCertificateApi().get('999999999');
        assert.fail('should have thrown');
      } catch (e) {
        expect(e).to.be.instanceOf(NoSuchObjectError);
      }
    });
  });

}, (data) => CoreError.deserialize(data));
