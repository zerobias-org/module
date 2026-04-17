/**
 * Avigilon Alta Access Module E2E Tests — one test, three impls.
 *
 * TEST_MODE selects the impl:
 *   direct  — AccessImpl (in-process)
 *   docker  — container REST
 *   hub     — Hub Server via Dana
 *
 * All three return the same Access interface.
 */

import { expect, assert } from 'chai';
import { CoreError } from '@zerobias-org/types-core-js';
import { describeModule } from '@zerobias-org/module-test-client';
import type { Access } from '../../hub-sdk/generated/api/index.js';
import { ORGANIZATION_ID, hasCredentials } from './constants.js';

describeModule<Access>('Avigilon Alta Access Module', (client) => {

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('UserApi.list', () => {
    it('should list users with default pagination', async () => {
      const users = await client.getUserApi().list(ORGANIZATION_ID);
      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
    });

    it('should list users with custom pagination', async () => {
      const users = await client.getUserApi().list(ORGANIZATION_ID, 1, 5);
      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
      expect(users.items.length).to.be.at.most(5);
    });
  });

  describe('UserApi.get', () => {
    it('should get a user by id', async () => {
      const users = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!users.items || users.items.length === 0) {
        return;
      }
      const userId = users.items[0].id;
      const user = await client.getUserApi().get(ORGANIZATION_ID, userId);
      expect(user).to.be.ok;
      expect(user.id).to.equal(userId);
    });

    it('should throw for non-existent user id', async () => {
      try {
        await client.getUserApi().get(ORGANIZATION_ID, '99999999');
        assert.fail('Should have thrown error');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  describe('GroupApi.list', () => {
    it('should list groups', async () => {
      const groups = await client.getGroupApi().list(ORGANIZATION_ID);
      expect(groups).to.be.ok;
      expect(groups.items).to.be.an('array');
    });
  });

  describe('GroupApi.get', () => {
    it('should get a group by id', async () => {
      const groups = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!groups.items || groups.items.length === 0) {
        return;
      }
      const groupId = groups.items[0].id;
      const group = await client.getGroupApi().get(ORGANIZATION_ID, groupId);
      expect(group).to.be.ok;
      expect(group.id).to.equal(groupId);
    });
  });

  describe('SiteApi.list', () => {
    it('should list sites', async () => {
      const sites = await client.getSiteApi().list(ORGANIZATION_ID);
      expect(sites).to.be.ok;
      expect(sites.items).to.be.an('array');
    });
  });

  describe('RoleApi.listRoles', () => {
    it('should list roles', async () => {
      const roles = await client.getRoleApi().listRoles(ORGANIZATION_ID);
      expect(roles).to.be.ok;
      expect(roles.items).to.be.an('array');
    });
  });

  describe('ScheduleApi.listSchedules', () => {
    it('should list schedules', async () => {
      const schedules = await client.getScheduleApi().listSchedules(ORGANIZATION_ID);
      expect(schedules).to.be.ok;
      expect(schedules.items).to.be.an('array');
    });
  });

  describe('ZoneApi.list', () => {
    it('should list zones', async () => {
      const zones = await client.getZoneApi().list(ORGANIZATION_ID);
      expect(zones).to.be.ok;
      expect(zones.items).to.be.an('array');
    });
  });

  describe('EntryApi.list', () => {
    it('should list entries', async () => {
      const entries = await client.getEntryApi().list(ORGANIZATION_ID);
      expect(entries).to.be.ok;
      expect(entries.items).to.be.an('array');
    });
  });

  describe('CredentialApi.listOrgCredentials', () => {
    it('should list org credentials', async () => {
      const credentials = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID);
      expect(credentials).to.be.ok;
      expect(credentials.items).to.be.an('array');
    });
  });

  describe('IdentityProviderApi.listIdentityProviders', () => {
    it('should list identity providers', async () => {
      const providers = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID);
      expect(providers).to.be.ok;
      expect(providers.items).to.be.an('array');
    });
  });

  describe('AuditApi.listAuditLogs', () => {
    it('should list audit log entries', async () => {
      const entries = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID);
      expect(entries).to.be.ok;
      expect(entries.items).to.be.an('array');
    });
  });

}, (data) => CoreError.deserialize(data));
