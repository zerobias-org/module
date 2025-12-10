import { Email } from '@auditmation/types-core-js';
import { expect } from 'chai';
import { config } from 'dotenv';
import { ConnectionProfile, newBitbucket, Bitbucket } from '../../src';

config();

const { BITBUCKET_EMAIL, BITBUCKET_API_TOKEN, BITBUCKET_WORKSPACE } = process.env;

export const connectionProfile: ConnectionProfile = {
  email: new Email(`${BITBUCKET_EMAIL}`),
  apiToken: `${BITBUCKET_API_TOKEN}`
};

export const testWorkspace = BITBUCKET_WORKSPACE || '';

export function prepareApi(): Bitbucket {
  const api = newBitbucket();

  before('connect to API', async () => {
    await api.connect(connectionProfile);
    expect(await api.isConnected()).to.equal(true);
  });

  after('disconnect from API', async () => {
    await api.disconnect();
    expect(await api.isConnected()).to.equal(false);
  });

  return api;
}
