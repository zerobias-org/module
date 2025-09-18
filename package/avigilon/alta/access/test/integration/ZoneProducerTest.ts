import { describe, it, before, after } from 'mocha';
import { expect } from 'chai';
import { prepareApi } from './Common';
import { AccessImpl } from '../../src/AccessImpl';

describe('Avigilon Alta Access - Zone Producer Tests', () => {
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

  describe('Zones List Operations', () => {
    it('should retrieve zones list with full details', async function () {
      this.timeout(10000);

      const zoneApi = access.getZoneApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const zones = await zoneApi.list(organizationId);

      console.log('\n=== ZONES LIST RETRIEVED ===');
      console.log(`Zones in Response: ${zones.length}`);
      
      if (zones.length > 0) {
        const firstZone = zones[0];
        console.log(`First Zone - ID: ${firstZone.id}, Name: ${firstZone.name}`);
        console.log(`Description: ${firstZone.description || 'N/A'}`);
        console.log(`Entry Count: ${firstZone.entryCount || 0}, User Count: ${firstZone.userCount || 0}`);
        console.log(`APB Use Contact Sensor: ${firstZone.apbUseContactSensor}`);
        if (firstZone.org) {
          console.log(`Organization: ${firstZone.org.name} (ID: ${firstZone.org.id})`);
        }
        if (firstZone.site) {
          console.log(`Site: ${firstZone.site.name} (ID: ${firstZone.site.id})`);
        }
      }
      console.log('===========================\n');

      // Validate the response is a flat array
      expect(zones).to.be.an('array');
      
      // If we have zones, validate their structure
      if (zones.length > 0) {
        const zone = zones[0];
        
        // Required fields
        expect(zone).to.have.property('id');
        expect(zone).to.have.property('name');
        expect(zone.id).to.be.a('number');
        expect(zone.name).to.be.a('string');
        expect(zone.id).to.be.greaterThan(0);
        expect(zone.name.length).to.be.greaterThan(0);
        
        // Optional fields validation (if present)
        if (zone.opal) {
          expect(zone.opal).to.be.a('string');
        }
        
        if (zone.description !== undefined) {
          if (zone.description !== null) {
            expect(zone.description).to.be.a('string');
          }
        }
        
        if (zone.apbResetIcalText !== undefined) {
          if (zone.apbResetIcalText !== null) {
            expect(zone.apbResetIcalText).to.be.a('string');
          }
        }
        
        if (zone.apbExpirationSeconds !== undefined) {
          if (zone.apbExpirationSeconds !== null) {
            expect(zone.apbExpirationSeconds).to.be.a('number');
          }
        }
        
        if (zone.apbUseContactSensor !== undefined) {
          expect(zone.apbUseContactSensor).to.be.a('boolean');
        }
        
        if (zone.apbAllowSharedOrgReset !== undefined) {
          expect(zone.apbAllowSharedOrgReset).to.be.a('boolean');
        }
        
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
          expect(zone.org.id).to.be.a('number');
          expect(zone.org.name).to.be.a('string');
        }
        
        if (zone.site) {
          expect(zone.site).to.have.property('id');
          expect(zone.site).to.have.property('name');
          expect(zone.site.id).to.be.a('number');
          expect(zone.site.name).to.be.a('string');
        }
        
        if (zone.zoneShares) {
          expect(zone.zoneShares).to.be.an('array');
        }
        
        if (zone.entries) {
          expect(zone.entries).to.be.an('array');
          zone.entries.forEach(entry => {
            expect(entry).to.have.property('id');
            expect(entry).to.have.property('name');
            expect(entry.id).to.be.a('number');
            expect(entry.name).to.be.a('string');
            
            if (entry.acu) {
              expect(entry.acu).to.have.property('id');
              expect(entry.acu.id).to.be.a('number');
            }
          });
        }
        
        if (zone.apbAreas) {
          expect(zone.apbAreas).to.be.an('array');
        }
        
        if (zone.createdAt) {
          expect(zone.createdAt).to.be.instanceOf(Date);
        }
        
        if (zone.updatedAt) {
          expect(zone.updatedAt).to.be.instanceOf(Date);
        }
      }
    });

    it('should return consistent zone structure', async function () {
      this.timeout(10000);

      const zoneApi = access.getZoneApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const zones = await zoneApi.list(organizationId);

      expect(zones).to.be.an('array');
      
      // Each zone should have consistent structure
      zones.forEach(zone => {
        expect(zone).to.have.property('id');
        expect(zone).to.have.property('name');
        expect(zone.id).to.be.a('number');
        expect(zone.name).to.be.a('string');
      });
    });

    it('should validate zone data types', async function () {
      this.timeout(10000);

      const zoneApi = access.getZoneApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const zones = await zoneApi.list(organizationId);

      expect(zones).to.be.an('array');
      
      if (zones.length > 0) {
        const zone = zones[0];
        
        // Validate all possible data types
        expect(zone.id).to.be.a('number');
        expect(zone.name).to.be.a('string');
        
        if (zone.opal) expect(zone.opal).to.be.a('string');
        if (zone.description !== null && zone.description !== undefined) expect(zone.description).to.be.a('string');
        if (zone.apbResetIcalText !== null && zone.apbResetIcalText !== undefined) expect(zone.apbResetIcalText).to.be.a('string');
        if (zone.apbExpirationSeconds !== null && zone.apbExpirationSeconds !== undefined) expect(zone.apbExpirationSeconds).to.be.a('number');
        if (zone.apbUseContactSensor !== undefined) expect(zone.apbUseContactSensor).to.be.a('boolean');
        if (zone.apbAllowSharedOrgReset !== undefined) expect(zone.apbAllowSharedOrgReset).to.be.a('boolean');
        if (zone.entryCount !== undefined) expect(zone.entryCount).to.be.a('number');
        if (zone.offlineEntryCount !== undefined) expect(zone.offlineEntryCount).to.be.a('number');
        if (zone.userCount !== undefined) expect(zone.userCount).to.be.a('number');
        if (zone.groupCount !== undefined) expect(zone.groupCount).to.be.a('number');
        if (zone.zoneShares) expect(zone.zoneShares).to.be.an('array');
        if (zone.entries) expect(zone.entries).to.be.an('array');
        if (zone.apbAreas) expect(zone.apbAreas).to.be.an('array');
        if (zone.createdAt) expect(zone.createdAt).to.be.instanceOf(Date);
        if (zone.updatedAt) expect(zone.updatedAt).to.be.instanceOf(Date);
      }
    });
  });
});