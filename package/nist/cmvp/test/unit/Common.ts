import nock from 'nock';
import { URL } from '@zerobias-org/types-core-js';
import { ConnectionProfile } from '../../generated/model/index.js';
import { CmvpImpl } from '../../src/CmvpImpl.js';
import { LISTING_PATH } from '../../src/CmvpClient.js';

export const MOCK_BASE_URL = 'https://mock.cmvp.test';

// Every operation is scoped under a connected CmvpImpl — connect() itself does a
// reachability GET against LISTING_PATH, so tests must mock that first.
export async function connectedClient(): Promise<CmvpImpl> {
  nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, '<html></html>');
  const client = new CmvpImpl();
  await client.connect(new ConnectionProfile(new URL(MOCK_BASE_URL)));
  return client;
}
