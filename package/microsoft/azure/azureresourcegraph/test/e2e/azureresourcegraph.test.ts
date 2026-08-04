/**
 * Azure Resource Graph Module E2E Tests — one test suite, three impls.
 *
 * The describeModule framework owns the connect/isConnected/disconnect
 * lifecycle, so those paths are not re-exercised here. Requires a service
 * principal with the Azure RBAC Reader role at subscription or
 * management-group scope (see test/e2e/constants.ts for the env contract).
 */

import { expect } from 'chai';
import { CoreError, PagedResults } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { AzureResourceGraph } from '../../hub-sdk/generated/api/index.js';
import { AZURE_SUBSCRIPTION_ID, hasCredentials } from './constants.js';

describeModule<AzureResourceGraph>('Azure Resource Graph Module', (client) => {

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('ResourceApi.list', () => {
    it('should list resources at tenant scope', async () => {
      const results = new PagedResults<any>();
      results.pageSize = 10;
      await client.getResourceApi().list(results);
      expect(results.items).to.be.an('array');
      for (const resource of results.items) {
        expect(resource).to.have.property('id');
        expect(resource).to.have.property('type');
      }
    });

    it('should list resources scoped to a subscription', async function () {
      if (!AZURE_SUBSCRIPTION_ID) {
        this.skip();
      }
      const results = new PagedResults<any>();
      results.pageSize = 10;
      await client.getResourceApi().list(results, [AZURE_SUBSCRIPTION_ID]);
      expect(results.items).to.be.an('array');
      for (const resource of results.items) {
        expect(resource.subscriptionId).to.equal(AZURE_SUBSCRIPTION_ID);
      }
    });
  });

  describe('ResourceContainerApi.list', () => {
    it('should list resource containers', async () => {
      const results = new PagedResults<any>();
      results.pageSize = 10;
      await client.getResourceContainerApi().list(results);
      expect(results.items).to.be.an('array');
      expect(results.items.length).to.be.greaterThan(0);
      for (const container of results.items) {
        expect(container).to.have.property('id');
        expect(container.type).to.be.oneOf([
          'microsoft.resources/subscriptions',
          'microsoft.resources/subscriptions/resourcegroups',
          'microsoft.management/managementgroups',
        ]);
      }
    });
  });

}, (data) => CoreError.deserialize(data));
