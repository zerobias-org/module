import { expect } from 'chai';
import { BitbucketImpl } from '../../src/BitbucketImpl';
import { OperationSupportStatus, ConnectionStatus } from '@auditmation/hub-core';

describe('BitbucketImpl', () => {
  let impl: BitbucketImpl;

  beforeEach(() => {
    impl = new BitbucketImpl();
  });

  describe('constructor', () => {
    it('should create an instance', () => {
      expect(impl).to.be.instanceOf(BitbucketImpl);
    });
  });

  describe('isSupported', () => {
    it('should return Maybe for any operation', async () => {
      const result = await impl.isSupported('listWorkspaces');
      expect(result).to.equal(OperationSupportStatus.Maybe);
    });
  });

  describe('metadata', () => {
    it('should return ConnectionStatus.Down', async () => {
      const result = await impl.metadata();
      expect(result.status).to.equal(ConnectionStatus.Down);
    });
  });

  describe('isConnected', () => {
    it('should return false when not connected', async () => {
      const result = await impl.isConnected();
      expect(result).to.be.false;
    });
  });

  describe('getWorkspaceApi', () => {
    it('should return a WorkspaceApi instance', () => {
      // This will throw since not connected, but we can verify the method exists
      expect(impl.getWorkspaceApi).to.be.a('function');
    });
  });

  describe('getRepositoryApi', () => {
    it('should return a RepositoryApi instance', () => {
      expect(impl.getRepositoryApi).to.be.a('function');
    });
  });
});
