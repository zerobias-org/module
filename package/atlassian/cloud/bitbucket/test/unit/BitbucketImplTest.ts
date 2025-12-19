import { expect } from 'chai';
import { BitbucketImpl } from '../../src/BitbucketImpl';

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

  describe('getConnectionMetadataDefaults', () => {
    it('should return default connection metadata', () => {
      const metadata = impl.getConnectionMetadataDefaults();
      expect(metadata).to.have.property('pageSize');
      expect(metadata).to.have.property('pageStart');
      expect(metadata.pageSize).to.equal(100);
      expect(metadata.pageStart).to.equal(1);
    });
  });

  // Additional unit tests will be added in Phase 5 (Testing)
  // - connect() tests with mocked responses
  // - Producer API tests with fixtures
});
