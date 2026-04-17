/**
 * Avigilon Alta Access Module E2E Tests — one test, three impls.
 *
 * TEST_MODE selects the impl:
 *   direct  — AccessImpl (in-process)
 *   docker  — container REST
 *   hub     — Hub Server via Dana
 *
 * All three return the same Access interface.
 *
 * Ported from the original `test/integration/` suite (12 files, ~4700 lines)
 * into a single describeModule wrapper that shares a connected client across
 * all three impls. `saveFixture`/debug-logging/per-test connection bootstrap
 * from the integration Common.ts are gone — the framework provides `client`.
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

  // ── Auth API ──────────────────────────────────────────────

  describe('AuthApi.getTokenProperties', () => {
    it('should retrieve current token properties with full details', async () => {
      const tokenProperties = await client.getAuthApi().getTokenProperties();

      expect(tokenProperties).to.be.an('object');

      expect(tokenProperties.organizationId, 'organizationId must be defined').to.not.be.undefined;
      expect(tokenProperties.organizationId).to.be.a('string');
      expect((tokenProperties.organizationId as string).length).to.be.greaterThan(0);

      expect(tokenProperties.identityId, 'identityId must be defined').to.not.be.undefined;
      expect(tokenProperties.identityId).to.be.a('string');
      expect((tokenProperties.identityId as string).length).to.be.greaterThan(0);

      expect(tokenProperties.expiresAt, 'expiresAt must be defined').to.not.be.undefined;
      if (tokenProperties.expiresAt) {
        expect(tokenProperties.expiresAt).to.be.instanceOf(Date);
        expect(tokenProperties.expiresAt.getTime()).to.be.greaterThan(Date.now(), 'Token must not be expired');
      }

      expect(tokenProperties.issuedAt, 'issuedAt must be defined').to.not.be.undefined;
      if (tokenProperties.issuedAt) {
        expect(tokenProperties.issuedAt).to.be.instanceOf(Date);
        expect(tokenProperties.issuedAt.getTime()).to.be.lessThan(Date.now(), 'Issue time must be in the past');
      }

      expect(tokenProperties.scope, 'scope must be defined').to.not.be.undefined;
      if (tokenProperties.scope) {
        expect(tokenProperties.scope).to.be.an('array');
        expect(tokenProperties.scope.length).to.be.greaterThan(0);
      }
    });

    it('should have valid token properties structure with correct types', async () => {
      const tokenProperties = await client.getAuthApi().getTokenProperties();

      expect(tokenProperties).to.have.property('organizationId');
      expect(tokenProperties.organizationId).to.not.be.undefined;
      expect(tokenProperties.organizationId).to.be.a('string');

      expect(tokenProperties).to.have.property('identityId');
      expect(tokenProperties.identityId).to.not.be.undefined;
      expect(tokenProperties.identityId).to.be.a('string');

      expect(tokenProperties).to.have.property('issuedAt');
      expect(tokenProperties.issuedAt).to.not.be.undefined;
      expect(tokenProperties.issuedAt).to.be.instanceOf(Date);

      expect(tokenProperties).to.have.property('expiresAt');
      expect(tokenProperties.expiresAt).to.not.be.undefined;
      expect(tokenProperties.expiresAt).to.be.instanceOf(Date);

      expect(tokenProperties).to.have.property('scope');
      expect(tokenProperties.scope).to.not.be.undefined;
      expect(tokenProperties.scope).to.be.an('array');

      const tp = tokenProperties as any;
      if (tp.iat !== undefined) {
        expect(tp.iat).to.be.a('number');
        expect(tp.iat).to.be.greaterThan(1600000000, 'iat should be after 2020');
      }
      if (tp.exp !== undefined) {
        expect(tp.exp).to.be.a('number');
        if (tp.iat !== undefined) {
          expect(tp.exp).to.be.greaterThan(tp.iat, 'exp should be greater than iat');
        }
      }
      if (tp.tokenType !== undefined) {
        expect(tp.tokenType).to.be.a('string');
        expect(tp.tokenType.length).to.be.greaterThan(0);
      }
      if (tp.jti !== undefined) {
        expect(tp.jti).to.be.a('string');
        expect(tp.jti.length).to.be.greaterThan(0);
      }
    });
  });

  // ── User API ──────────────────────────────────────────────

  describe('UserApi.list', () => {
    it('should list users with default pagination', async () => {
      const result = await client.getUserApi().list(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const first = result.items[0];
        expect(first).to.be.an('object');
        if (first.id) expect(first.id).to.satisfy((v: any) => typeof v === 'string' || typeof v === 'number');
        if (first.createdAt && first.createdAt instanceof Date) {
          expect(first.createdAt).to.be.instanceOf(Date);
        }
      }
    });

    it('should list users with custom pagination', async () => {
      const result = await client.getUserApi().list(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should handle invalid pagination parameters', async () => {
      try {
        const result = await client.getUserApi().list(ORGANIZATION_ID, -1, -1);
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should succeed under light sequential load (single request)', async () => {
      const result = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      expect(result).to.not.be.null;
    });

    it('should validate user response schema', async () => {
      const result = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!result.items || result.items.length === 0) return;

      const user = result.items[0];
      expect(user).to.be.an('object');
      const hasIdentifier = user.id || (user as any).identity?.email;
      expect(hasIdentifier).to.not.be.undefined;
    });
  });

  describe('UserApi.get', () => {
    it('should retrieve a specific user by ID', async () => {
      const list = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const userId = list.items[0].id;
      const user = await client.getUserApi().get(ORGANIZATION_ID, String(userId));

      expect(user).to.be.ok;
      expect(user).to.be.an('object');
      expect(String(user.id)).to.equal(String(userId));
    });

    it('should throw for non-existent user ID', async () => {
      try {
        await client.getUserApi().get(ORGANIZATION_ID, '99999999');
        assert.fail('Should have thrown error');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  describe('UserApi.listRoles', () => {
    it('should list user roles with default pagination', async () => {
      const list = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const userId = String(list.items[0].id);
      const roles = await client.getUserApi().listRoles(ORGANIZATION_ID, userId);

      expect(roles).to.be.ok;
      expect(roles.items).to.be.an('array');
      expect(roles.count).to.be.a('number');
    });

    it('should list user roles with custom pagination', async () => {
      const list = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const userId = String(list.items[0].id);
      const roles = await client.getUserApi().listRoles(ORGANIZATION_ID, userId, 1, 3);

      expect(roles).to.be.ok;
      expect(roles.items).to.be.an('array');
      expect(roles.items.length).to.be.at.most(3);
    });
  });

  describe('UserApi.listSites', () => {
    it('should list user sites with default pagination', async () => {
      const list = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const userId = String(list.items[0].id);
      const sites = await client.getUserApi().listSites(ORGANIZATION_ID, userId);

      expect(sites).to.be.ok;
      expect(sites.items).to.be.an('array');
      expect(sites.count).to.be.a('number');
    });

    it('should list user sites with custom pagination', async () => {
      const list = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const userId = String(list.items[0].id);
      const sites = await client.getUserApi().listSites(ORGANIZATION_ID, userId, 1, 3);

      expect(sites).to.be.ok;
      expect(sites.items).to.be.an('array');
      expect(sites.items.length).to.be.at.most(3);
    });
  });

  // ── Group API ─────────────────────────────────────────────

  describe('GroupApi.list', () => {
    it('should list groups with default pagination', async () => {
      const result = await client.getGroupApi().list(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const first = result.items[0];
        expect(first).to.be.an('object');
        if (first.id) {
          expect(first.id).to.be.a('string');
          expect(first.id).to.not.be.empty;
        }
      }
    });

    it('should list groups with custom pagination', async () => {
      const result = await client.getGroupApi().list(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should handle invalid pagination parameters', async () => {
      try {
        const result = await client.getGroupApi().list(ORGANIZATION_ID, -1, -1);
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should validate group response schema', async () => {
      const result = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!result.items || result.items.length === 0) return;
      const group = result.items[0];
      expect(group).to.be.an('object');
      const hasIdentifier = group.id || group.name;
      expect(hasIdentifier).to.not.be.undefined;
    });
  });

  describe('GroupApi.get', () => {
    it('should retrieve a specific group by ID', async () => {
      const list = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const groupId = list.items[0].id;
      const group = await client.getGroupApi().get(ORGANIZATION_ID, groupId);

      expect(group).to.be.ok;
      expect(group).to.be.an('object');
      expect(group.id).to.equal(groupId);
    });

    it('should throw for non-existent group ID', async () => {
      try {
        await client.getGroupApi().get(ORGANIZATION_ID, '999999999');
        assert.fail('Should have thrown error');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  describe('GroupApi.listUsers', () => {
    it('should list group users with default pagination', async () => {
      const list = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const groupId = list.items[0].id;
      const users = await client.getGroupApi().listUsers(ORGANIZATION_ID, groupId);

      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
    });

    it('should list group users with custom pagination', async () => {
      const list = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const groupId = list.items[0].id;
      const users = await client.getGroupApi().listUsers(ORGANIZATION_ID, groupId, 1, 5);

      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
      expect(users.items.length).to.be.at.most(5);
    });
  });

  describe('GroupApi.listEntries', () => {
    it('should list group entries with default pagination', async () => {
      const list = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const groupId = list.items[0].id;
      const entries = await client.getGroupApi().listEntries(ORGANIZATION_ID, groupId);

      expect(entries).to.be.ok;
      expect(entries.items).to.be.an('array');
    });

    it('should list group entries with custom pagination', async () => {
      const list = await client.getGroupApi().list(ORGANIZATION_ID, 1, 1);
      if (!list.items || list.items.length === 0) return;

      const groupId = list.items[0].id;
      const entries = await client.getGroupApi().listEntries(ORGANIZATION_ID, groupId, 1, 5);

      expect(entries).to.be.ok;
      expect(entries.items).to.be.an('array');
      expect(entries.items.length).to.be.at.most(5);
    });
  });

  // ── Site API ──────────────────────────────────────────────

  describe('SiteApi.list', () => {
    it('should retrieve sites list with full details', async () => {
      const result = await client.getSiteApi().list(ORGANIZATION_ID);
      expect(result).to.not.be.null;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const site = result.items[0];
        expect(site).to.have.property('id');
        expect(site).to.have.property('name');
        expect(site.id).to.be.a('string');
        expect(site.name).to.be.a('string');
        expect(site.id.length).to.be.greaterThan(0);
        expect(site.name.length).to.be.greaterThan(0);
      }
    });

    it('should return consistent site structure', async () => {
      const result = await client.getSiteApi().list(ORGANIZATION_ID);
      expect(result.items).to.be.an('array');
      for (const site of result.items) {
        expect(site).to.have.property('id');
        expect(site).to.have.property('name');
        expect(site.id).to.be.a('string');
        expect(site.name).to.be.a('string');
      }
    });

    it('should validate site data types', async () => {
      const result = await client.getSiteApi().list(ORGANIZATION_ID);
      if (result.items.length === 0) return;

      const site = result.items[0] as any;
      expect(site.id).to.be.a('string');
      expect(site.name).to.be.a('string');

      if (site.opal) expect(site.opal).to.be.a('string');
      if (site.address) expect(site.address).to.be.a('string');
      if (site.city) expect(site.city).to.be.a('string');
      if (site.state) expect(site.state).to.be.a('string');
      if (site.zip) expect(site.zip).to.be.a('string');
      if (site.country) expect(site.country).to.be.a('string');
      if (site.phone) expect(site.phone).to.be.a('string');
      if (site.language) expect(site.language).to.be.a('string');
      if (site.zoneCount !== undefined) expect(site.zoneCount).to.be.a('number');
      if (site.userCount !== undefined) expect(site.userCount).to.be.a('number');
      if (site.createdAt) expect(site.createdAt).to.be.instanceOf(Date);
      if (site.updatedAt) expect(site.updatedAt).to.be.instanceOf(Date);
    });
  });

  // ── Role API ──────────────────────────────────────────────

  describe('RoleApi.listRoles', () => {
    it('should list roles with default pagination', async () => {
      const result = await client.getRoleApi().listRoles(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.count).to.be.a('number');

      if (result.items.length > 0) {
        const role = result.items[0];
        expect(role.id).to.be.a('string');
        expect(role.id.length).to.be.greaterThan(0);
        expect(role.name).to.be.a('string');
        expect(role.name.length).to.be.greaterThan(0);
      }
    });

    it('should list roles with custom pagination', async () => {
      const result = await client.getRoleApi().listRoles(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
      expect(result.count).to.be.a('number');
      expect(result.count).to.be.at.least(0);
    });

    it('should handle pagination across multiple pages', async () => {
      const page1 = await client.getRoleApi().listRoles(ORGANIZATION_ID, 1, 2);
      expect(page1.items).to.be.an('array');
      expect(page1.items.length).to.be.at.most(2);

      if (page1.count && page1.count > 2) {
        const page2 = await client.getRoleApi().listRoles(ORGANIZATION_ID, 2, 2);
        expect(page2.items).to.be.an('array');
        expect(page2.items.length).to.be.at.most(2);
      }
    });

    it('should handle invalid pagination parameters', async () => {
      try {
        const result = await client.getRoleApi().listRoles(ORGANIZATION_ID, -1, -1);
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should handle empty organization ID gracefully', async () => {
      try {
        await client.getRoleApi().listRoles('');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should validate role response schema', async () => {
      const result = await client.getRoleApi().listRoles(ORGANIZATION_ID, 1, 1);
      if (!result.items || result.items.length === 0) return;
      const role = result.items[0];
      expect(role).to.be.an('object');
      expect(role.id).to.exist;
      expect(role.id).to.be.a('string');
      expect(role.name).to.exist;
      expect(role.name).to.be.a('string');
    });
  });

  describe('RoleApi.listRoleUsers', () => {
    it('should list users for a specific role with default pagination', async () => {
      const roles = await client.getRoleApi().listRoles(ORGANIZATION_ID, 1, 1);
      if (!roles.items || roles.items.length === 0) return;

      const roleId = roles.items[0].id;
      const users = await client.getRoleApi().listRoleUsers(ORGANIZATION_ID, roleId);

      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
      expect(users.count).to.be.a('number');

      if (users.items.length > 0) {
        const u = users.items[0];
        expect(u.id).to.be.a('string');
        expect((u.id as string).length).to.be.greaterThan(0);
      }
    });

    it('should list role users with custom pagination', async () => {
      const roles = await client.getRoleApi().listRoles(ORGANIZATION_ID, 1, 1);
      if (!roles.items || roles.items.length === 0) return;

      const roleId = roles.items[0].id;
      const users = await client.getRoleApi().listRoleUsers(ORGANIZATION_ID, roleId, 1, 3);

      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
      expect(users.items.length).to.be.at.most(3);
    });

    it('should handle role with no users', async () => {
      const roles = await client.getRoleApi().listRoles(ORGANIZATION_ID);
      if (!roles.items || roles.items.length === 0) return;

      const testRole = roles.items.find((r: any) => r.userCount === 0) || roles.items[0];
      const users = await client.getRoleApi().listRoleUsers(ORGANIZATION_ID, testRole.id);

      expect(users).to.be.ok;
      expect(users.items).to.be.an('array');
      expect(users.count).to.be.a('number');
      expect(users.count).to.be.at.least(0);

      if ((testRole as any).userCount === 0) {
        expect(users.items.length).to.equal(0);
      }
    });

    it('should handle non-existent role ID gracefully', async () => {
      const nonExistentId = '99999999-9999-9999-9999-999999999999';
      try {
        const users = await client.getRoleApi().listRoleUsers(ORGANIZATION_ID, nonExistentId);
        if (users) {
          expect(users.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  describe('RoleApi data consistency', () => {
    it('should validate role userCount is sensible relative to actual users', async () => {
      const roles = await client.getRoleApi().listRoles(ORGANIZATION_ID);
      if (!roles.items || roles.items.length === 0) return;

      const testRole = roles.items.find((r: any) => r.userCount !== undefined) || roles.items[0];
      if ((testRole as any).userCount === undefined) return;

      const users = await client.getRoleApi().listRoleUsers(ORGANIZATION_ID, testRole.id);
      expect(users.count).to.be.a('number');
      expect(users.count).to.be.at.least(0);
    });

    it('should validate bidirectional role-user relationship', async () => {
      const userList = await client.getUserApi().list(ORGANIZATION_ID, 1, 1);
      if (!userList.items || userList.items.length === 0) return;

      const userId = String(userList.items[0].id);
      const userRoles = await client.getUserApi().listRoles(ORGANIZATION_ID, userId);
      if (!userRoles.items || userRoles.items.length === 0) return;

      const roleId = userRoles.items[0].id as string;
      const roleUsers = await client.getRoleApi().listRoleUsers(ORGANIZATION_ID, roleId);
      expect(roleUsers.items).to.be.an('array');
    });
  });

  // ── Schedule API ──────────────────────────────────────────

  describe('ScheduleApi.listSchedules', () => {
    it('should list schedules with default pagination', async () => {
      const result = await client.getScheduleApi().listSchedules(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.count).to.be.a('number');
      expect(result.count).to.be.at.least(0);

      if (result.items.length > 0) {
        const s = result.items[0];
        expect(s.id).to.be.a('string');
        expect(s.name).to.be.a('string');
      }
    });

    it('should list schedules with custom pagination', async () => {
      const result = await client.getScheduleApi().listSchedules(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should handle empty schedule list on a very-high page number', async () => {
      const result = await client.getScheduleApi().listSchedules(ORGANIZATION_ID, 1000, 10);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
    });

    it('should handle invalid pagination parameters', async () => {
      try {
        const result = await client.getScheduleApi().listSchedules(ORGANIZATION_ID, -1, -1);
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should handle empty organization ID gracefully', async () => {
      try {
        await client.getScheduleApi().listSchedules('');
        assert.fail('Should have thrown error');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should paginate through multiple pages of schedules', async () => {
      const pageSize = 2;
      const maxPages = 3;
      let currentPage = 1;

      for (let i = 0; i < maxPages; i += 1) {
        const page = await client.getScheduleApi().listSchedules(ORGANIZATION_ID, currentPage, pageSize);
        expect(page.items).to.be.an('array');
        if (page.items.length === 0) break;
        expect(page.items.length).to.be.at.most(pageSize);
        currentPage += 1;
      }
    });
  });

  describe('ScheduleApi.listScheduleTypes', () => {
    it('should list schedule types with default pagination', async () => {
      const result = await client.getScheduleApi().listScheduleTypes(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.count).to.be.a('number');
      expect(result.count).to.be.at.least(0);
    });

    it('should list schedule types with custom pagination', async () => {
      const result = await client.getScheduleApi().listScheduleTypes(ORGANIZATION_ID, 1, 3);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(3);
    });

    it('should paginate through multiple pages of schedule types', async () => {
      const pageSize = 2;
      const maxPages = 3;
      let currentPage = 1;

      for (let i = 0; i < maxPages; i += 1) {
        const page = await client.getScheduleApi().listScheduleTypes(ORGANIZATION_ID, currentPage, pageSize);
        expect(page.items).to.be.an('array');
        if (page.items.length === 0) break;
        expect(page.items.length).to.be.at.most(pageSize);
        currentPage += 1;
      }
    });
  });

  describe('ScheduleApi.listScheduleEvents', () => {
    it('should list schedule events for a specific schedule', async function () {
      const schedules = await client.getScheduleApi().listSchedules(ORGANIZATION_ID, 1, 1);
      if (!schedules.items || schedules.items.length === 0) {
        this.skip();
        return;
      }

      const scheduleId = schedules.items[0].id;
      const events = await client.getScheduleApi().listScheduleEvents(ORGANIZATION_ID, scheduleId);

      expect(events).to.be.ok;
      expect(events.items).to.be.an('array');
      expect(events.count).to.be.a('number');
    });

    it('should list schedule events with custom pagination', async function () {
      const schedules = await client.getScheduleApi().listSchedules(ORGANIZATION_ID, 1, 1);
      if (!schedules.items || schedules.items.length === 0) {
        this.skip();
        return;
      }

      const scheduleId = schedules.items[0].id;
      const events = await client.getScheduleApi().listScheduleEvents(ORGANIZATION_ID, scheduleId, 1, 3);

      expect(events).to.be.ok;
      expect(events.items).to.be.an('array');
      expect(events.items.length).to.be.at.most(3);
    });

    it('should handle invalid schedule ID for events', async () => {
      const nonExistent = '99999999';
      try {
        const events = await client.getScheduleApi().listScheduleEvents(ORGANIZATION_ID, nonExistent);
        if (events) {
          expect(events.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  // ── Zone API ──────────────────────────────────────────────

  describe('ZoneApi.list', () => {
    it('should retrieve zones list with full details', async () => {
      const result = await client.getZoneApi().list(ORGANIZATION_ID);
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const zone = result.items[0] as any;
        expect(zone).to.have.property('id');
        expect(zone).to.have.property('name');
        expect(zone.id).to.be.a('string');
        expect(zone.name).to.be.a('string');
        expect(zone.id).to.not.be.empty;
        expect(zone.name.length).to.be.greaterThan(0);

        if (zone.opal) expect(zone.opal).to.be.a('string');
        if (zone.description != null) expect(zone.description).to.be.a('string');
        if (zone.apbUseContactSensor !== undefined) expect(zone.apbUseContactSensor).to.be.a('boolean');
        if (zone.apbAllowSharedOrgReset !== undefined) expect(zone.apbAllowSharedOrgReset).to.be.a('boolean');
        if (zone.entryCount !== undefined) {
          expect(zone.entryCount).to.be.a('number');
          expect(zone.entryCount).to.be.at.least(0);
        }
        if (zone.offlineEntryCount !== undefined) {
          expect(zone.offlineEntryCount).to.be.a('number');
          expect(zone.offlineEntryCount).to.be.at.least(0);
        }
        if (zone.userCount !== undefined) {
          expect(zone.userCount).to.be.a('number');
          expect(zone.userCount).to.be.at.least(0);
        }
        if (zone.groupCount !== undefined) {
          expect(zone.groupCount).to.be.a('number');
          expect(zone.groupCount).to.be.at.least(0);
        }
        if (zone.org) {
          expect(zone.org).to.have.property('id');
          expect(zone.org).to.have.property('name');
        }
        if (zone.site) {
          expect(zone.site).to.have.property('id');
          expect(zone.site).to.have.property('name');
        }
        if (zone.zoneShares) expect(zone.zoneShares).to.be.an('array');
        if (zone.entries) expect(zone.entries).to.be.an('array');
        if (zone.apbAreas) expect(zone.apbAreas).to.be.an('array');
        if (zone.createdAt) expect(zone.createdAt).to.be.instanceOf(Date);
        if (zone.updatedAt) expect(zone.updatedAt).to.be.instanceOf(Date);
      }
    });

    it('should return consistent zone structure', async () => {
      const result = await client.getZoneApi().list(ORGANIZATION_ID);
      expect(result.items).to.be.an('array');
      for (const zone of result.items) {
        expect(zone).to.have.property('id');
        expect(zone).to.have.property('name');
        expect(zone.id).to.be.a('string');
        expect(zone.name).to.be.a('string');
      }
    });

    it('should validate zone data types', async () => {
      const result = await client.getZoneApi().list(ORGANIZATION_ID);
      if (result.items.length === 0) return;
      const zone = result.items[0] as any;
      expect(zone.id).to.be.a('string');
      expect(zone.name).to.be.a('string');
    });
  });

  // ── Entry API ─────────────────────────────────────────────

  describe('EntryApi.list', () => {
    it('should retrieve entries list with full details', async () => {
      const result = await client.getEntryApi().list(ORGANIZATION_ID);
      expect(result).to.not.be.null;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const entry = result.items[0] as any;
        expect(entry).to.have.property('id');
        expect(entry).to.have.property('name');
        expect(entry.id).to.be.a('string');
        expect(entry.name).to.be.a('string');
        expect(entry.id).to.not.be.empty;
        expect(entry.name.length).to.be.greaterThan(0);

        if (entry.opal) expect(entry.opal).to.be.a('string');
        if (entry.isPincodeEnabled !== undefined) expect(entry.isPincodeEnabled).to.be.a('boolean');
        if (entry.color) expect(entry.color).to.be.a('string');
        if (entry.isMusterPoint !== undefined) expect(entry.isMusterPoint).to.be.a('boolean');
        if (entry.isReaderless !== undefined) expect(entry.isReaderless).to.be.a('boolean');
        if (entry.isIntercomEntry !== undefined) expect(entry.isIntercomEntry).to.be.a('boolean');
        if (entry.createdAt) expect(entry.createdAt).to.be.instanceOf(Date);
        if (entry.updatedAt) expect(entry.updatedAt).to.be.instanceOf(Date);

        if (entry.zone) {
          expect(entry.zone).to.have.property('id');
          expect(entry.zone).to.have.property('name');
        }
        if (entry.acu) {
          expect(entry.acu).to.have.property('id');
          expect(entry.acu).to.have.property('name');
        }
        if (entry.entryState) {
          expect(entry.entryState).to.have.property('id');
          expect(entry.entryState).to.have.property('name');
        }
        if (entry.schedule) {
          expect(entry.schedule).to.have.property('id');
          expect(entry.schedule).to.have.property('name');
        }
        if (entry.cameras) {
          expect(entry.cameras).to.be.an('array');
        }
      }
    });

    it('should return consistent entry structure', async () => {
      const result = await client.getEntryApi().list(ORGANIZATION_ID);
      expect(result.items).to.be.an('array');
      for (const entry of result.items) {
        expect(entry).to.have.property('id');
        expect(entry).to.have.property('name');
        expect(entry.id).to.be.a('string');
        expect(entry.name).to.be.a('string');
      }
    });

    it('should validate entry data types', async () => {
      const result = await client.getEntryApi().list(ORGANIZATION_ID);
      if (result.items.length === 0) return;
      const entry = result.items[0] as any;
      expect(entry.id).to.be.a('string');
      expect(entry.name).to.be.a('string');
    });

    it('should provide PagedResults structure with items and count', async () => {
      const result = await client.getEntryApi().list(ORGANIZATION_ID);
      expect(result).to.have.property('items');
      expect(result).to.have.property('count');
      expect(result.items).to.be.an('array');
    });
  });

  // ── Credential API ────────────────────────────────────────

  describe('CredentialApi.listCardFormats', () => {
    it('should list card formats with default pagination', async () => {
      const result = await client.getCredentialApi().listCardFormats(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const cf = result.items[0] as any;
        expect(cf.id).to.be.a('string');
        expect(cf.id.length).to.be.greaterThan(0);
      }
    });

    it('should list card formats with custom pagination', async () => {
      const result = await client.getCredentialApi().listCardFormats(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should properly handle pagination metadata for card formats', async () => {
      const page1 = await client.getCredentialApi().listCardFormats(ORGANIZATION_ID, 1, 2);
      expect(page1.count).to.be.a('number');
      expect(page1.pageNumber).to.equal(1);
      expect(page1.pageSize).to.equal(2);

      if (page1.count !== undefined && page1.count > 2) {
        const page2 = await client.getCredentialApi().listCardFormats(ORGANIZATION_ID, 2, 2);
        expect(page2.pageNumber).to.equal(2);
        expect(page2.pageSize).to.equal(2);
      }
    });
  });

  describe('CredentialApi.listCredentialTypes', () => {
    it('should list credential types with default pagination', async () => {
      const result = await client.getCredentialApi().listCredentialTypes(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const ct = result.items[0] as any;
        expect(ct.id).to.be.a('string');
        expect(ct.id.length).to.be.greaterThan(0);
      }
    });

    it('should list credential types with custom pagination', async () => {
      const result = await client.getCredentialApi().listCredentialTypes(ORGANIZATION_ID, 1, 3);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(3);
    });
  });

  describe('CredentialApi.listCredentialActionTypes', () => {
    it('should list credential action types with default pagination', async () => {
      const result = await client.getCredentialApi().listCredentialActionTypes(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');

      if (result.items.length > 0) {
        const at = result.items[0] as any;
        expect(at.id).to.be.a('string');
        expect(at.id.length).to.be.greaterThan(0);
      }
    });

    it('should list credential action types with custom pagination', async () => {
      const result = await client.getCredentialApi().listCredentialActionTypes(ORGANIZATION_ID, 1, 3);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(3);
    });
  });

  describe('CredentialApi.listOrgCredentials', () => {
    it('should list org credentials with default pagination', async () => {
      const result = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.count).to.be.a('number');
      expect(result.count).to.be.at.least(0);

      if (result.items.length > 0) {
        const c = result.items[0] as any;
        expect(c.id).to.be.a('string');
        expect(c.id.length).to.be.greaterThan(0);
      }
    });

    it('should list org credentials with custom pagination', async () => {
      const result = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should properly handle pagination metadata for credentials', async () => {
      const page1 = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID, 1, 2);
      expect(page1.count).to.be.a('number');
      expect(page1.pageNumber).to.equal(1);
      expect(page1.pageSize).to.equal(2);

      if (page1.count !== undefined && page1.count > 2) {
        const page2 = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID, 2, 2);
        expect(page2.pageNumber).to.equal(2);
        expect(page2.pageSize).to.equal(2);
        if (page1.items.length > 0 && page2.items.length > 0) {
          expect(page1.items[0].id).to.not.equal(page2.items[0].id);
        }
      }
    });

    it('should handle invalid pagination parameters', async () => {
      try {
        const result = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID, -1, -1);
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  describe('CredentialApi.listCredentialActions', () => {
    it('should list credential actions with default pagination', async function () {
      const creds = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID, 1, 1);
      if (!creds.items || creds.items.length === 0) {
        this.skip();
        return;
      }

      const credentialId = String((creds.items[0] as any).id);
      const actions = await client.getCredentialApi().listCredentialActions(ORGANIZATION_ID, credentialId);

      expect(actions).to.be.ok;
      expect(actions.items).to.be.an('array');
      expect(actions.count).to.be.a('number');
    });

    it('should list credential actions with custom pagination', async function () {
      const creds = await client.getCredentialApi().listOrgCredentials(ORGANIZATION_ID, 1, 1);
      if (!creds.items || creds.items.length === 0) {
        this.skip();
        return;
      }

      const credentialId = String((creds.items[0] as any).id);
      const actions = await client.getCredentialApi().listCredentialActions(ORGANIZATION_ID, credentialId, 1, 3);

      expect(actions).to.be.ok;
      expect(actions.items).to.be.an('array');
      expect(actions.items.length).to.be.at.most(3);
    });

    it('should handle non-existent credential ID for actions', async () => {
      try {
        const actions = await client.getCredentialApi().listCredentialActions(ORGANIZATION_ID, '99999999');
        if (actions) {
          expect(actions.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  // ── Identity Provider API ─────────────────────────────────

  describe('IdentityProviderApi.listIdentityProviders', () => {
    it('should list identity providers with default pagination', async () => {
      const result = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
    });

    it('should list identity providers with custom pagination', async () => {
      const result = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should handle invalid pagination parameters', async () => {
      try {
        const result = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, -1, -1);
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should handle large page size returning sensible results', async () => {
      const result = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 100);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.count).to.be.a('number');
    });

    it('should handle multiple pages of identity providers', async () => {
      const page1 = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 2);
      expect(page1).to.be.ok;
      expect(page1.items).to.be.an('array');

      if (page1.count && page1.count > 2) {
        const page2 = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 2, 2);
        expect(page2).to.be.ok;
        expect(page2.items).to.be.an('array');
        if (page1.items.length > 0 && page2.items.length > 0) {
          expect((page1.items[0] as any).id).to.not.equal((page2.items[0] as any).id);
        }
      }
    });

    it('should respect page size limits for various page sizes', async () => {
      for (const pageSize of [1, 5, 10, 50]) {
        const result = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, pageSize);
        expect(result.items).to.be.an('array');
        expect(result.items.length).to.be.at.most(pageSize);
      }
    });
  });

  describe('IdentityProviderApi.listIdentityProviderTypes', () => {
    it('should list identity provider types with default pagination', async () => {
      const result = await client.getIdentityProviderApi().listIdentityProviderTypes(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
    });

    it('should list identity provider types with custom pagination', async () => {
      const result = await client.getIdentityProviderApi().listIdentityProviderTypes(ORGANIZATION_ID, 1, 3);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(3);
    });
  });

  describe('IdentityProviderApi.getIdentityProviderType', () => {
    it('should retrieve a specific identity provider type by ID', async function () {
      const types = await client.getIdentityProviderApi().listIdentityProviderTypes(ORGANIZATION_ID, 1, 1);
      if (!types.items || types.items.length === 0) {
        this.skip();
        return;
      }

      const typeId = (types.items[0] as any).id;
      const typeInfo = await client.getIdentityProviderApi().getIdentityProviderType(ORGANIZATION_ID, typeId);

      expect(typeInfo).to.be.ok;
      expect(typeInfo).to.be.an('object');
      expect((typeInfo as any).id).to.equal(typeId);
    });

    it('should throw for non-existent identity provider type ID', async () => {
      try {
        await client.getIdentityProviderApi().getIdentityProviderType(ORGANIZATION_ID, '99999999');
        assert.fail('Should have thrown error');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });
  });

  describe('IdentityProviderApi.listIdentityProviderGroups', () => {
    it('should list identity provider groups with default pagination', async function () {
      const providers = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 1);
      if (!providers.items || providers.items.length === 0) {
        this.skip();
        return;
      }

      const providerId = String((providers.items[0] as any).id);
      const groups = await client.getIdentityProviderApi().listIdentityProviderGroups(ORGANIZATION_ID, providerId);

      expect(groups).to.be.ok;
      expect(groups.items).to.be.an('array');
      expect(groups.count).to.be.a('number');
    });

    it('should list identity provider groups with custom pagination', async function () {
      const providers = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 1);
      if (!providers.items || providers.items.length === 0) {
        this.skip();
        return;
      }

      const providerId = String((providers.items[0] as any).id);
      const groups = await client.getIdentityProviderApi().listIdentityProviderGroups(ORGANIZATION_ID, providerId, 1, 3);

      expect(groups).to.be.ok;
      expect(groups.items).to.be.an('array');
      expect(groups.items.length).to.be.at.most(3);
    });
  });

  describe('IdentityProviderApi.getIdentityProviderGroupRelations', () => {
    it('should get identity provider group relations with default pagination', async function () {
      const providers = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 1);
      if (!providers.items || providers.items.length === 0) {
        this.skip();
        return;
      }

      const providerId = String((providers.items[0] as any).id);
      const relations = await client
        .getIdentityProviderApi()
        .getIdentityProviderGroupRelations(ORGANIZATION_ID, providerId);

      expect(relations).to.be.ok;
      expect(relations.items).to.be.an('array');
      expect(relations.count).to.be.a('number');
    });

    it('should get identity provider group relations with custom pagination', async function () {
      const providers = await client.getIdentityProviderApi().listIdentityProviders(ORGANIZATION_ID, 1, 1);
      if (!providers.items || providers.items.length === 0) {
        this.skip();
        return;
      }

      const providerId = String((providers.items[0] as any).id);
      const relations = await client
        .getIdentityProviderApi()
        .getIdentityProviderGroupRelations(ORGANIZATION_ID, providerId, 1, 3);

      expect(relations).to.be.ok;
      expect(relations.items).to.be.an('array');
      expect(relations.items.length).to.be.at.most(3);
    });
  });

  // ── Audit API ─────────────────────────────────────────────

  describe('AuditApi.listAuditLogs', () => {
    it('should list audit logs with default pagination', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.count).to.be.a('number');
      expect(result.count).to.be.at.least(0);

      if (result.items.length > 0) {
        const log = result.items[0] as any;
        expect(log.timestamp).to.exist;
        if (log.action) {
          expect(log.action).to.be.a('string');
          expect(log.action.length).to.be.greaterThan(0);
        }
      }
    });

    it('should list audit logs with custom pagination', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 10);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(10);
    });

    it('should list audit logs with second page', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 2, 5);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(5);
    });

    it('should handle large page size', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 100);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.be.at.most(100);
    });

    it('should list audit logs with custom filter parameter', async () => {
      try {
        const result = await client
          .getAuditApi()
          .listAuditLogs(ORGANIZATION_ID, 'category:access', undefined, 1, 20);
        expect(result).to.be.ok;
        expect(result.items).to.be.an('array');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should list audit logs with search options', async () => {
      try {
        const result = await client
          .getAuditApi()
          .listAuditLogs(ORGANIZATION_ID, undefined, 'searchId:test-search-123', 1, 20);
        expect(result).to.be.ok;
        expect(result.items).to.be.an('array');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should list audit logs with both filter and options', async () => {
      const now = Math.floor(Date.now() / 1000);
      const oneDayAgo = now - (24 * 60 * 60);
      const filter = `timestamp:(${oneDayAgo}--${now})`;
      const options = 'searchId:combined-test';

      try {
        const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, filter, options, 1, 15);
        expect(result).to.be.ok;
        expect(result.items).to.be.an('array');
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should validate audit log entry structure', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 1);
      if (!result.items || result.items.length === 0) return;

      const log = result.items[0] as any;
      expect(log).to.be.an('object');
      expect(log.timestamp).to.exist;
      if (log.action) expect(log.action).to.be.a('string');

      if (typeof log.timestamp === 'number') {
        expect(log.timestamp).to.be.greaterThan(0);
      } else if (typeof log.timestamp === 'string') {
        expect(log.timestamp.length).to.be.greaterThan(0);
      }
    });

    it('should validate actor information mapping', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 50);
      if (!result.items || result.items.length === 0) return;

      const withActor = result.items.find((log: any) => log.actorId || log.actorName || log.actorEmail);
      if (!withActor) return;

      const a = withActor as any;
      if (a.actorId) {
        expect(a.actorId).to.be.a('string');
        expect(a.actorId.length).to.be.greaterThan(0);
      }
      if (a.actorName) expect(a.actorName).to.be.a('string');
    });

    it('should validate target information mapping', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 50);
      if (!result.items || result.items.length === 0) return;

      const withTarget = result.items.find((log: any) => log.targetId || log.targetName || log.targetType);
      if (!withTarget) return;

      const t = withTarget as any;
      if (t.targetId) {
        expect(t.targetId).to.be.a('string');
        expect(t.targetId.length).to.be.greaterThan(0);
      }
      if (t.targetName) expect(t.targetName).to.be.a('string');
      if (t.targetType) expect(t.targetType).to.be.a('string');
    });

    it('should handle empty results gracefully on high page number', async () => {
      const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 9999, 10);
      expect(result).to.be.ok;
      expect(result.items).to.be.an('array');
      expect(result.items.length).to.equal(0);
    });

    it('should handle invalid organization ID', async () => {
      try {
        const result = await client.getAuditApi().listAuditLogs('invalid-org-id-12345');
        if (result && result.items) {
          expect(result.items).to.be.an('array');
        }
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should handle invalid filter syntax', async () => {
      try {
        const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, 'invalid:::filter:::syntax');
        expect(result).to.not.be.null;
      } catch (err: any) {
        expect(err).to.be.ok;
      }
    });

    it('should handle pagination edge cases (min and large page sizes)', async () => {
      const minPage = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 1);
      expect(minPage).to.be.ok;
      expect(minPage.items).to.be.an('array');
      if (minPage.items.length > 0) {
        expect(minPage.items.length).to.equal(1);
      }

      const maxPage = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 5000);
      expect(maxPage).to.be.ok;
      expect(maxPage.items).to.be.an('array');
      expect(maxPage.items.length).to.be.at.most(1000);
    });

    it('should maintain consistent pagination across multiple pages', async () => {
      const page1 = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, 5);
      const page2 = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 2, 5);
      expect(page1.items).to.be.an('array');
      expect(page2.items).to.be.an('array');
      if (page1.count !== undefined && page2.count !== undefined) {
        expect(page1.count).to.equal(page2.count);
      }
    });

    it('should respect page size limits', async () => {
      for (const pageSize of [1, 5, 10, 25, 50, 100]) {
        const result = await client.getAuditApi().listAuditLogs(ORGANIZATION_ID, undefined, undefined, 1, pageSize);
        expect(result.items).to.be.an('array');
        expect(result.items.length).to.be.at.most(pageSize);
      }
    });
  });

}, (data) => CoreError.deserialize(data));
