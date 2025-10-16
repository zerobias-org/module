import { describe, it, before, after } from 'mocha';
import { expect } from 'chai';
import { prepareApi, debugLog, testConfig } from './Common';
import { AccessImpl } from '../../src/AccessImpl';

describe('Avigilon Alta Access - Entry Producer Tests', () => {
  let access: AccessImpl;

  before(async function () {
    this.timeout(30000);
    access = await prepareApi();
  });

  after(async function () {
    if (access) {
      await access.disconnect();
    }
  });

  describe('Entries List Operations', () => {
    it('should retrieve entries list with full details', async function () {
      this.timeout(10000);

      const entryApi = access.getEntryApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const entriesResult = await entryApi.list(organizationId);

      // Debug logging (only shows when LOG_LEVEL=debug)
      debugLog('listEntries', { organizationId }, entriesResult);


      // Validate the response is PagedResults
      expect(entriesResult).to.not.be.null;
      expect(entriesResult.items).to.be.an('array');

      // If we have entries, validate their structure
      if (entriesResult.items.length > 0) {
        const entry = entriesResult.items[0];

        // Required fields
        expect(entry).to.have.property('id');
        expect(entry).to.have.property('name');
        expect(entry.id).to.be.a('string');
        expect(entry.name).to.be.a('string');
        expect(entry.id).to.not.be.empty;
        expect(entry.name.length).to.be.greaterThan(0);
        
        // Optional fields validation (if present)
        if (entry.opal) {
          expect(entry.opal).to.be.a('string');
        }
        
        if (entry.pincode !== undefined) {
          expect(entry.pincode).to.satisfy((val: any) => val === null || typeof val === 'string');
        }
        
        if (entry.isPincodeEnabled !== undefined) {
          expect(entry.isPincodeEnabled).to.be.a('boolean');
        }
        
        if (entry.color) {
          expect(entry.color).to.be.a('string');
        }
        
        if (entry.isMusterPoint !== undefined) {
          expect(entry.isMusterPoint).to.be.a('boolean');
        }
        
        if (entry.notes !== undefined) {
          expect(entry.notes).to.satisfy((val: any) => val === null || typeof val === 'string');
        }
        
        if (entry.externalUuid !== undefined) {
          expect(entry.externalUuid).to.satisfy((val: any) => val === null || typeof val === 'string');
        }
        
        if (entry.isReaderless !== undefined) {
          expect(entry.isReaderless).to.be.a('boolean');
        }
        
        if (entry.isIntercomEntry !== undefined) {
          expect(entry.isIntercomEntry).to.be.a('boolean');
        }
        
        if (entry.createdAt) {
          expect(entry.createdAt).to.be.instanceOf(Date);
        }
        
        if (entry.updatedAt) {
          expect(entry.updatedAt).to.be.instanceOf(Date);
        }
        
        // Nested object validation
        if (entry.zone) {
          expect(entry.zone).to.have.property('id');
          expect(entry.zone).to.have.property('name');
          expect(entry.zone.id).to.be.a('string');
          expect(entry.zone.name).to.be.a('string');

          if (entry.zone.site) {
            expect(entry.zone.site).to.have.property('id');
            expect(entry.zone.site).to.have.property('name');
            expect(entry.zone.site.id).to.be.a('string');
            expect(entry.zone.site.name).to.be.a('string');
          }
        }

        if (entry.acu) {
          expect(entry.acu).to.have.property('id');
          expect(entry.acu).to.have.property('name');
          expect(entry.acu.id).to.be.a('string');
          expect(entry.acu.name).to.be.a('string');

          if (entry.acu.isGatewayMode !== undefined) {
            expect(entry.acu.isGatewayMode).to.satisfy((val: any) => val === null || typeof val === 'boolean');
          }
        }

        if (entry.entryState) {
          expect(entry.entryState).to.have.property('id');
          expect(entry.entryState).to.have.property('name');
          expect(entry.entryState.id).to.be.a('string');
          expect(entry.entryState.name).to.be.a('string');
        }

        if (entry.schedule) {
          expect(entry.schedule).to.have.property('id');
          expect(entry.schedule).to.have.property('name');
          expect(entry.schedule.id).to.be.a('string');
          expect(entry.schedule.name).to.be.a('string');
        }

        if (entry.cameras) {
          expect(entry.cameras).to.be.an('array');
          entry.cameras.forEach(camera => {
            expect(camera).to.have.property('id');
            expect(camera).to.have.property('name');
            expect(camera.id).to.be.a('string');
            expect(camera.name).to.be.a('string');
          });
        }
      }
    });

    it('should return consistent entry structure', async function () {
      this.timeout(10000);

      const entryApi = access.getEntryApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const entriesResult = await entryApi.list(organizationId);

      expect(entriesResult.items).to.be.an('array');

      // Each entry should have consistent structure
      for (const entry of entriesResult.items) {
        expect(entry).to.have.property('id');
        expect(entry).to.have.property('name');
        expect(entry.id).to.be.a('string');
        expect(entry.name).to.be.a('string');
      }
    });

    it('should validate entry data types', async function () {
      this.timeout(10000);

      const entryApi = access.getEntryApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const entriesResult = await entryApi.list(organizationId);

      expect(entriesResult.items).to.be.an('array');

      if (entriesResult.items.length > 0) {
        const entry = entriesResult.items[0];

        // Validate all possible data types
        expect(entry.id).to.be.a('string');
        expect(entry.name).to.be.a('string');
        
        if (entry.opal) expect(entry.opal).to.be.a('string');
        if (entry.pincode !== undefined) expect(entry.pincode).to.satisfy((val: any) => val === null || typeof val === 'string');
        if (entry.isPincodeEnabled !== undefined) expect(entry.isPincodeEnabled).to.be.a('boolean');
        if (entry.color) expect(entry.color).to.be.a('string');
        if (entry.isMusterPoint !== undefined) expect(entry.isMusterPoint).to.be.a('boolean');
        if (entry.notes !== undefined) expect(entry.notes).to.satisfy((val: any) => val === null || typeof val === 'string');
        if (entry.externalUuid !== undefined) expect(entry.externalUuid).to.satisfy((val: any) => val === null || typeof val === 'string');
        if (entry.isReaderless !== undefined) expect(entry.isReaderless).to.be.a('boolean');
        if (entry.isIntercomEntry !== undefined) expect(entry.isIntercomEntry).to.be.a('boolean');
        if (entry.createdAt) expect(entry.createdAt).to.be.instanceOf(Date);
        if (entry.updatedAt) expect(entry.updatedAt).to.be.instanceOf(Date);
        if (entry.wirelessLock !== undefined) expect(entry.wirelessLock).to.satisfy((val: any) => val === null || typeof val === 'object');
        if (entry.cameras) expect(entry.cameras).to.be.an('array');
      }
    });

    it('should flatten response data array and drop metadata', async function () {
      this.timeout(10000);

      const entryApi = access.getEntryApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const entriesResult = await entryApi.list(organizationId);

      // Verify we get PagedResults with items array
      expect(entriesResult).to.not.be.null;
      expect(entriesResult.items).to.be.an('array');

      // Verify PagedResults structure
      expect(entriesResult).to.have.property('items');
      expect(entriesResult).to.have.property('count');
    });
  });
});