/**
 * Azureresourcegraph Module E2E Tests — one test, three impls.
 *
 * TEST_MODE selects the impl:
 *   direct  — AzureresourcegraphImpl (in-process)
 *   docker  — AzureresourcegraphClient (container REST)
 *   hub     — AzureresourcegraphClient (Hub Server via Dana)
 *
 * All three return the same Azureresourcegraph interface. Requires a live
 * Azure AD tenant with a Reader-scoped service principal (secrets:
 * client_id / client_secret / directoryId); skips otherwise.
 */

import { expect } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { Azureresourcegraph } from '../../hub-sdk/generated/api/index.js';
import { AZURE_SUBSCRIPTION_ID, hasCredentials } from './constants.js';

describeModule<Azureresourcegraph>('Azureresourcegraph Module', (client) => {

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  // ── Resource API ─────────────────────────────────────────

  describe('ResourceApi.list', () => {
    it('should retrieve a page of resources at tenant scope', async () => {
      const results = await client.getResourceApi().list();

      expect(results.items).to.be.an('array');
      expect(results.count ?? 0).to.be.a('number');

      for (const resource of results.items) {
        expect(resource.id, 'every row carries an ARM id').to.be.a('string');
        expect(resource.name, 'every row carries a name').to.be.a('string');
        expect(resource.type, 'every row carries a type (assetType discriminator)').to.be.a('string');
      }
    });

    it('should honor pageSize and expose a continuation token on truncation', async function () {
      const firstPage = await client.getResourceApi().list(1);

      expect(firstPage.items.length).to.be.at.most(1);

      if ((firstPage.count ?? 0) <= 1) {
        // Tenant holds at most one visible resource — nothing to paginate.
        this.skip();
      }

      expect(firstPage.pageToken, 'truncated result must carry $skipToken').to.be.a('string');

      const secondPage = await client.getResourceApi().list(undefined, firstPage.pageToken);
      expect(secondPage.items).to.be.an('array');
      if (firstPage.items.length && secondPage.items.length) {
        expect(secondPage.items[0].id).to.not.equal(firstPage.items[0].id);
      }
    });

    it('should scope by subscription when one is provided', async function () {
      if (!AZURE_SUBSCRIPTION_ID) {
        this.skip();
      }

      const results = await client.getResourceApi().list(undefined, undefined, [AZURE_SUBSCRIPTION_ID]);

      for (const resource of results.items) {
        expect(resource.subscriptionId).to.equal(AZURE_SUBSCRIPTION_ID);
      }
    });
  });

  // ── ResourceContainer API ────────────────────────────────

  describe('ResourceContainerApi.list', () => {
    it('should retrieve subscriptions / resource groups / management groups', async () => {
      const results = await client.getResourceContainerApi().list();

      expect(results.items).to.be.an('array');
      expect(results.items.length, 'a readable tenant always has at least one container').to.be.greaterThan(0);

      const knownTypes = new Set([
        'microsoft.resources/subscriptions',
        'microsoft.resources/subscriptions/resourcegroups',
        'microsoft.management/managementgroups',
      ]);

      for (const container of results.items) {
        expect(container.id).to.be.a('string');
        expect(container.name).to.be.a('string');
        expect(knownTypes.has(container.type), `unexpected container type ${container.type}`).to.be.true;
      }
    });
  });

}, (data) => CoreError.deserialize(data));
