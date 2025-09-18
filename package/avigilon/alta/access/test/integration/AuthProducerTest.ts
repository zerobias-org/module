import { describe, it, before, after } from 'mocha';
import { expect } from 'chai';
import { prepareApi } from './Common';
import { AccessImpl } from '../../src/AccessImpl';

describe('Avigilon Alta Access - Auth Producer Tests', () => {
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

  describe('Token Properties Operations', () => {
    it('should retrieve current token properties with full details', async function () {
      this.timeout(10000);

      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();


      // Validate the key properties we care about
      expect(tokenProperties).to.be.an('object');
      expect(tokenProperties.organizationId).to.be.a('number');
      expect(tokenProperties.identityId).to.be.a('number');
      
      if (tokenProperties.expiresAt) {
        expect(tokenProperties.expiresAt).to.be.instanceOf(Date);
        // Validate token is not expired
        expect(tokenProperties.expiresAt.getTime()).to.be.greaterThan(Date.now());
      }
      
      if (tokenProperties.scope) {
        expect(tokenProperties.scope).to.be.an('array');
        // Validate scope contains some permissions
        expect(tokenProperties.scope.length).to.be.greaterThan(0);
      }

      // Validate organization ID is reasonable
      expect(tokenProperties.organizationId!).to.be.greaterThan(0);
      expect(tokenProperties.identityId!).to.be.greaterThan(0);
    });

    it('should have valid token properties structure', async function () {
      this.timeout(10000);

      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();

      // Test required fields
      expect(tokenProperties).to.have.property('organizationId');
      expect(tokenProperties).to.have.property('identityId');
      expect(tokenProperties).to.have.property('issuedAt');
      expect(tokenProperties).to.have.property('expiresAt');

      // Test optional fields (if present)
      if (tokenProperties.scope) {
        expect(tokenProperties.scope).to.be.an('array');
      }
      
      if (tokenProperties.tokenType) {
        expect(tokenProperties.tokenType).to.be.a('string');
      }

      if (tokenProperties.iat) {
        expect(tokenProperties.iat).to.be.a('number');
        // iat should be a reasonable Unix timestamp
        expect(tokenProperties.iat).to.be.greaterThan(1600000000); // After 2020
      }

      if (tokenProperties.exp) {
        expect(tokenProperties.exp).to.be.a('number');
        // exp should be greater than iat
        if (tokenProperties.iat) {
          expect(tokenProperties.exp).to.be.greaterThan(tokenProperties.iat);
        }
      }
    });
  });
});