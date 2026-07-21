import { expect, assert } from 'chai';
import nock from 'nock';
import { EnumValue, OperationSupportStatus, URL, UnexpectedError } from '@zerobias-org/types-core-js';
import { ConnectionProfile } from '../../generated/model/index.js';
import { CmvpImpl } from '../../src/CmvpImpl.js';
import { LISTING_PATH } from '../../src/CmvpClient.js';
import { MOCK_BASE_URL } from './Common.js';

afterEach(() => {
  nock.cleanAll();
});

describe('Cmvp connection lifecycle', () => {
  it('connects when the registry is reachable', async () => {
    // connect() and isConnected() each do their own reachability GET.
    nock(MOCK_BASE_URL).get(LISTING_PATH).twice().reply(200, '<html></html>');
    const client = new CmvpImpl();

    await client.connect(new ConnectionProfile(new URL(MOCK_BASE_URL)));

    expect(await client.isConnected()).to.be.true;
    expect(nock.pendingMocks()).to.be.empty;
  });

  it('throws when the registry is unreachable', async () => {
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(503);
    const client = new CmvpImpl();

    try {
      await client.connect(new ConnectionProfile(new URL(MOCK_BASE_URL)));
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(UnexpectedError);
    }
    expect(await client.isConnected()).to.be.false;
  });

  it('reports disconnected state after disconnect()', async () => {
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, '<html></html>');
    const client = new CmvpImpl();
    await client.connect(new ConnectionProfile(new URL(MOCK_BASE_URL)));

    await client.disconnect();

    expect(await client.isConnected()).to.be.false;
  });

  it('reports every operation as supported (no per-operation gating)', async () => {
    const client = new CmvpImpl();

    const status = await client.isSupported('getCertificate');

    expect((status as EnumValue).eq(OperationSupportStatus.Yes)).to.be.true;
  });
});
