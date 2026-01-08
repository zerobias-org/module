import { expect } from 'chai';
import { BitbucketImpl, newBitbucket } from '../../src';
import { OperationSupportStatus, ConnectionStatus } from '@auditmation/hub-core';

describe('BitbucketImpl', () => {
  describe('constructor', () => {
    it('should create an instance', () => {
      const impl = new BitbucketImpl();
      expect(impl).to.be.instanceOf(BitbucketImpl);
    });
  });

  describe('factory function', () => {
    it('should create an instance via newBitbucket()', () => {
      const impl = newBitbucket();
      expect(impl).to.be.instanceOf(BitbucketImpl);
    });
  });

  describe('isConnected', () => {
    it('should return false when not connected', async () => {
      const impl = new BitbucketImpl();
      const result = await impl.isConnected();
      expect(result).to.be.false;
    });
  });

  describe('isSupported', () => {
    it('should return Maybe for any operation', async () => {
      const impl = new BitbucketImpl();
      const result = await impl.isSupported('anyOperation');
      expect(result).to.equal(OperationSupportStatus.Maybe);
    });
  });

  describe('metadata', () => {
    it('should return Down status', async () => {
      const impl = new BitbucketImpl();
      const result = await impl.metadata();
      expect(result.status).to.equal(ConnectionStatus.Down);
    });
  });

  describe('API accessors', () => {
    it('should throw NotConnectedError when accessing UserApi without connection', () => {
      const impl = new BitbucketImpl();
      expect(() => impl.getUserApi()).to.throw('Not connected');
    });

    it('should throw NotConnectedError when accessing RepositoryApi without connection', () => {
      const impl = new BitbucketImpl();
      expect(() => impl.getRepositoryApi()).to.throw('Not connected');
    });
  });

  describe('disconnect', () => {
    it('should not throw when disconnecting without connection', async () => {
      const impl = new BitbucketImpl();
      await impl.disconnect();
      // Should complete without error
    });
  });

  describe('refresh', () => {
    it('should throw error since basic auth does not require refresh', async () => {
      const impl = new BitbucketImpl();
      try {
        await impl.refresh({} as any, {} as any);
        expect.fail('Should have thrown');
      } catch (error: any) {
        expect(error.message).to.include('Token refresh not required');
      }
    });
  });
});
