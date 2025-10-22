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

  describe('Token Properties Operations', () => {
    it('should retrieve current token properties with full details', async function () {
      this.timeout(10000);

      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();

      // STRICT VALIDATION: These fields MUST be present and valid
      expect(tokenProperties).to.be.an('object');

      // Organization ID is REQUIRED - token must be associated with an org
      expect(tokenProperties.organizationId, 'organizationId must be defined').to.not.be.undefined;
      expect(tokenProperties.organizationId).to.be.a('string');
      expect(tokenProperties.organizationId).to.have.length.greaterThan(0);

      // Identity ID is REQUIRED - token must be associated with an identity
      expect(tokenProperties.identityId, 'identityId must be defined').to.not.be.undefined;
      expect(tokenProperties.identityId).to.be.a('string');
      expect(tokenProperties.identityId).to.have.length.greaterThan(0);

      // Expiration is REQUIRED - all tokens must have expiry
      expect(tokenProperties.expiresAt, 'expiresAt must be defined').to.not.be.undefined;
      if (tokenProperties.expiresAt) {
        expect(tokenProperties.expiresAt).to.be.instanceOf(Date);
        expect(tokenProperties.expiresAt.getTime()).to.be.greaterThan(Date.now(), 'Token must not be expired');
      }

      // Issued At is REQUIRED - all tokens must have issue time
      expect(tokenProperties.issuedAt, 'issuedAt must be defined').to.not.be.undefined;
      if (tokenProperties.issuedAt) {
        expect(tokenProperties.issuedAt).to.be.instanceOf(Date);
        expect(tokenProperties.issuedAt.getTime()).to.be.lessThan(Date.now(), 'Issue time must be in the past');
      }

      // Scope is REQUIRED - all tokens must have scope/permissions
      expect(tokenProperties.scope, 'scope must be defined').to.not.be.undefined;
      if (tokenProperties.scope) {
        expect(tokenProperties.scope).to.be.an('array');
        expect(tokenProperties.scope.length, 'scope must contain at least one permission').to.be.greaterThan(0);
      }
    });

    it('should have valid token properties structure with correct types', async function () {
      this.timeout(10000);

      const authApi = access.getAuthApi();
      const tokenProperties = await authApi.getTokenProperties();

      // STRICT VALIDATION: Test required fields are present and properly typed
      expect(tokenProperties).to.have.property('organizationId');
      expect(tokenProperties.organizationId, 'organizationId must not be undefined').to.not.be.undefined;
      expect(tokenProperties.organizationId, 'organizationId must be a string').to.be.a('string');

      expect(tokenProperties).to.have.property('identityId');
      expect(tokenProperties.identityId, 'identityId must not be undefined').to.not.be.undefined;
      expect(tokenProperties.identityId, 'identityId must be a string').to.be.a('string');

      expect(tokenProperties).to.have.property('issuedAt');
      expect(tokenProperties.issuedAt, 'issuedAt must not be undefined').to.not.be.undefined;
      expect(tokenProperties.issuedAt, 'issuedAt must be a Date').to.be.instanceOf(Date);

      expect(tokenProperties).to.have.property('expiresAt');
      expect(tokenProperties.expiresAt, 'expiresAt must not be undefined').to.not.be.undefined;
      expect(tokenProperties.expiresAt, 'expiresAt must be a Date').to.be.instanceOf(Date);

      expect(tokenProperties).to.have.property('scope');
      expect(tokenProperties.scope, 'scope must not be undefined').to.not.be.undefined;
      expect(tokenProperties.scope, 'scope must be an array').to.be.an('array');

      // Validate Unix timestamps if present
      if (tokenProperties.iat !== undefined) {
        expect(tokenProperties.iat).to.be.a('number');
        // iat should be a reasonable Unix timestamp
        expect(tokenProperties.iat).to.be.greaterThan(1600000000, 'iat should be after 2020');
      }

      if (tokenProperties.exp !== undefined) {
        expect(tokenProperties.exp).to.be.a('number');
        // exp should be greater than iat
        if (tokenProperties.iat !== undefined) {
          expect(tokenProperties.exp).to.be.greaterThan(tokenProperties.iat, 'exp should be greater than iat');
        }
      }

      // tokenType and jti are optional but should be strings if present
      if (tokenProperties.tokenType !== undefined) {
        expect(tokenProperties.tokenType).to.be.a('string');
        expect(tokenProperties.tokenType).to.have.length.greaterThan(0);
      }

      if (tokenProperties.jti !== undefined) {
        expect(tokenProperties.jti).to.be.a('string');
        expect(tokenProperties.jti).to.have.length.greaterThan(0);
      }
    });
  });
});
