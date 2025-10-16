import { describe, it, before, after } from 'mocha';
import { expect } from 'chai';
import { prepareApi } from './Common';
import { AccessImpl } from '../../src/AccessImpl';

describe('Avigilon Alta Access - Site Producer Tests', () => {
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

  describe('Sites List Operations', () => {
    it('should retrieve sites list with full details', async function () {
      this.timeout(10000);

      const siteApi = access.getSiteApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const sitesResult = await siteApi.list(organizationId);


      // Validate the response is PagedResults
      expect(sitesResult).to.not.be.null;
      expect(sitesResult.items).to.be.an('array');

      // If we have sites, validate their structure
      if (sitesResult.items.length > 0) {
        const site = sitesResult.items[0];
        
        // Required fields
        expect(site).to.have.property('id');
        expect(site).to.have.property('name');
        expect(site.id).to.be.a('string');
        expect(site.name).to.be.a('string');
        expect(site.id.length).to.be.greaterThan(0);
        expect(site.name.length).to.be.greaterThan(0);
        
        // Optional fields validation (if present)
        if (site.opal) {
          expect(site.opal).to.be.a('string');
        }
        
        if (site.address) {
          expect(site.address).to.be.a('string');
        }
        
        if (site.city) {
          expect(site.city).to.be.a('string');
        }
        
        if (site.state) {
          expect(site.state).to.be.a('string');
        }
        
        if (site.zip) {
          expect(site.zip).to.be.a('string');
        }
        
        if (site.country) {
          expect(site.country).to.be.a('string');
        }
        
        if (site.zoneCount !== undefined) {
          expect(site.zoneCount).to.be.a('number');
          expect(site.zoneCount).to.be.at.least(0);
        }
        
        if (site.userCount !== undefined) {
          expect(site.userCount).to.be.a('number');
          expect(site.userCount).to.be.at.least(0);
        }
        
        if (site.createdAt) {
          expect(site.createdAt).to.be.instanceOf(Date);
        }
        
        if (site.updatedAt) {
          expect(site.updatedAt).to.be.instanceOf(Date);
        }
      }
    });

    it('should return consistent site structure', async function () {
      this.timeout(10000);

      const siteApi = access.getSiteApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const sitesResult = await siteApi.list(organizationId);

      expect(sitesResult.items).to.be.an('array');

      // Each site should have consistent structure
      for (const site of sitesResult.items) {
        expect(site).to.have.property('id');
        expect(site).to.have.property('name');
        expect(site.id).to.be.a('string');
        expect(site.name).to.be.a('string');
      }
    });

    it('should validate site data types', async function () {
      this.timeout(10000);

      const siteApi = access.getSiteApi();
      
      // Get organization ID from auth token
      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();
      const organizationId = tokenProperties.organizationId!.toString();

      const sitesResult = await siteApi.list(organizationId);

      expect(sitesResult.items).to.be.an('array');

      if (sitesResult.items.length > 0) {
        const site = sitesResult.items[0];

        // Validate all possible data types
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
      }
    });
  });
});
