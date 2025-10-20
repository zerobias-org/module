import { expect } from 'chai';
import nock from 'nock';
import {
  InvalidCredentialsError,
  UnexpectedError,
  URL,
  Email
} from '@auditmation/types-core-js';
import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus
} from '@auditmation/hub-core';
import { AccessImpl } from '../../src/AccessImpl';
import { AvigilonAltaAccessClient } from '../../src/AvigilonAltaAccessClient';
import { ConnectionProfile } from '../../generated/model/ConnectionProfile';
import { UserProducerApiImpl } from '../../src/UserProducerApiImpl';
import { GroupProducerApiImpl } from '../../src/GroupProducerApiImpl';
import { mockAuthenticatedRequest, cleanNock, loadFixture } from '../utils/nock-helpers';

describe('AccessImpl', () => {
  let accessImpl: AccessImpl;
  let client: AvigilonAltaAccessClient;
  const baseUrl = 'https://helium.prod.openpath.com';
  const testEmail = process.env.AVIGILON_EMAIL || 'test@example.com';
  const testPassword = process.env.AVIGILON_PASSWORD || 'testpass123';

  beforeEach(() => {
    nock.disableNetConnect();
  });

  afterEach(() => {
    cleanNock();
  });

  describe('Constructor and Initialization', () => {
    it('should create AccessImpl instance', () => {
      accessImpl = new AccessImpl();
      expect(accessImpl).to.be.instanceOf(AccessImpl);
    });

    it('should initialize internal client', () => {
      accessImpl = new AccessImpl();
      // Access internal client via reflection or test if methods work
      expect(accessImpl).to.have.property('client');
    });
  });

  describe('Connection Lifecycle', () => {
    beforeEach(() => {
      accessImpl = new AccessImpl();
    });

    describe('connect()', () => {
      it('should delegate connect to client', async () => {
        const profile: ConnectionProfile = {
          email: new Email(testEmail),
          password: testPassword,
        };

        // Mock login endpoint
        nock(baseUrl)
          .post('/auth/login')
          .reply(200, {
            data: {
              token: 'mock-token-123',
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
            },
          });

        await accessImpl.connect(profile);

        const connected = await accessImpl.isConnected();
        expect(connected).to.be.true;
      });

      it('should handle connection successfully', async () => {
        const profile: ConnectionProfile = {
          email: new Email(testEmail),
          password: testPassword,
        };

        // Mock login endpoint
        nock(baseUrl)
          .post('/auth/login')
          .reply(200, {
            data: {
              token: 'mock-token-123',
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
            },
          });

        await accessImpl.connect(profile);

        const connected = await accessImpl.isConnected();
        expect(connected).to.be.true;
      });

      it('should propagate connection errors from client', async () => {
        const profile: ConnectionProfile = {
          email: new Email(testEmail),
          password: '', // Invalid token
        };

        try {
          await accessImpl.connect(profile);
          expect.fail('Expected InvalidCredentialsError to be thrown');
        } catch (error) {
          expect(error).to.be.instanceOf(InvalidCredentialsError);
        }
      });
    });

    describe('isConnected()', () => {
      it('should delegate isConnected to client', async () => {
        const connected = await accessImpl.isConnected();
        expect(connected).to.be.false;
      });

      it('should return true when connected', async () => {
        const profile: ConnectionProfile = {
          email: new Email(testEmail),
          password: testPassword,
        };

        // Mock login endpoint
        nock(baseUrl)
          .post('/auth/login')
          .reply(200, {
            data: {
              token: 'mock-token-123',
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
            },
          });

        await accessImpl.connect(profile);
        const connected = await accessImpl.isConnected();
        expect(connected).to.be.true;
      });
    });

    describe('disconnect()', () => {
      it('should delegate disconnect to client', async () => {
        const profile: ConnectionProfile = {
          email: new Email(testEmail),
          password: testPassword,
        };

        // Mock login endpoint
        nock(baseUrl)
          .post('/auth/login')
          .reply(200, {
            data: {
              token: 'mock-token-123',
              expiresAt: new Date(Date.now() + 3600000).toISOString(),
            },
          });

        await accessImpl.connect(profile);
        expect(await accessImpl.isConnected()).to.be.true;

        await accessImpl.disconnect();
        expect(await accessImpl.isConnected()).to.be.false;
      });

      it('should handle disconnect when not connected', async () => {
        await accessImpl.disconnect();
        expect(await accessImpl.isConnected()).to.be.false;
      });
    });
  });

  describe('Standard Methods', () => {
    beforeEach(async () => {
      accessImpl = new AccessImpl();
      const profile: ConnectionProfile = {
        email: new Email(testEmail),
        password: testPassword,
      };

      // Mock login endpoint
      nock(baseUrl)
        .post('/auth/login')
        .reply(200, {
          data: {
            token: 'mock-token-123',
            expiresAt: new Date(Date.now() + 3600000).toISOString(),
          },
        });

      await accessImpl.connect(profile);
    });

    describe('isSupported()', () => {
      it('should return Maybe for any operation', async () => {
        const result = await accessImpl.isSupported('users.list');
        expect(result).to.equal(OperationSupportStatus.Maybe);
      });

      it('should return Maybe for known operations', async () => {
        const operations = ['users.get', 'groups.list'];

        for (const operation of operations) {
          const result = await accessImpl.isSupported(operation);
          expect(result).to.equal(OperationSupportStatus.Maybe);
        }
      });

      it('should return Maybe for unknown operations', async () => {
        const result = await accessImpl.isSupported('unknown.operation');
        expect(result).to.equal(OperationSupportStatus.Maybe);
      });
    });

    describe('metadata()', () => {
      it('should return module metadata', async () => {
        const metadata = await accessImpl.metadata();

        expect(metadata).to.be.instanceOf(ConnectionMetadata);
        expect(metadata).to.have.property('status');
      });

      it('should include correct service information', async () => {
        const metadata = await accessImpl.metadata();

        expect(metadata.status).to.equal(ConnectionStatus.Down);
      });
    });
  });

  describe('API Getters', () => {
    beforeEach(async () => {
      accessImpl = new AccessImpl();
      const profile: ConnectionProfile = {
        email: new Email(testEmail),
        password: testPassword,
      };

      // Mock login endpoint
      nock(baseUrl)
        .post('/auth/login')
        .reply(200, {
          data: {
            token: 'mock-token-123',
            expiresAt: new Date(Date.now() + 3600000).toISOString(),
          },
        });

      await accessImpl.connect(profile);
    });

    describe('getUserApi', () => {
      it('should return UserApi instance', () => {
        const userApi = accessImpl.getUserApi();
        // Test that it's the wrapped UserProducerApiImpl
        expect(userApi).to.be.an('object');
        expect(userApi).to.have.property('list');
        expect(userApi).to.have.property('get');
      });

      it('should return same instance on multiple calls', () => {
        const userApi1 = accessImpl.getUserApi();
        const userApi2 = accessImpl.getUserApi();
        expect(userApi1).to.equal(userApi2);
      });
    });

    describe('getAuthApi', () => {
      it('should return AuthApi instance', () => {
        const authApi = accessImpl.getAuthApi();
        // Test that it's the wrapped AuthProducerApiImpl
        expect(authApi).to.be.an('object');
        expect(authApi).to.have.property('getTokenProperties');
      });

      it('should return same instance on multiple calls', () => {
        const authApi1 = accessImpl.getAuthApi();
        const authApi2 = accessImpl.getAuthApi();
        expect(authApi1).to.equal(authApi2);
      });
    });

    describe('getGroupApi', () => {
      it('should return GroupApi instance', () => {
        const groupApi = accessImpl.getGroupApi();
        // Test that it's the wrapped GroupProducerApiImpl
        expect(groupApi).to.be.an('object');
        expect(groupApi).to.have.property('list');
        expect(groupApi).to.have.property('get');
      });

      it('should return same instance on multiple calls', () => {
        const groupApi1 = accessImpl.getGroupApi();
        const groupApi2 = accessImpl.getGroupApi();
        expect(groupApi1).to.equal(groupApi2);
      });
    });
  });

  describe('Error Propagation', () => {
    beforeEach(() => {
      accessImpl = new AccessImpl();
    });

    it('should propagate client connection errors', async () => {
      const profile: ConnectionProfile = {
        email: new Email(testEmail),
        password: '', // Invalid
      };

      try {
        await accessImpl.connect(profile);
        expect.fail('Expected error to be propagated');
      } catch (error) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }
    });

    it('should handle errors during producer initialization gracefully', async () => {
      const profile: ConnectionProfile = {
        email: new Email(testEmail),
        password: testPassword,
      };

      // Mock login endpoint
      nock(baseUrl)
        .post('/auth/login')
        .reply(200, {
          data: {
            token: 'mock-token-123',
            expiresAt: new Date(Date.now() + 3600000).toISOString(),
          },
        });

      await accessImpl.connect(profile);

      // Test that producers can be created without throwing
      expect(() => accessImpl.getUserApi()).to.not.throw();
      expect(() => accessImpl.getGroupApi()).to.not.throw();
    });
  });
});
