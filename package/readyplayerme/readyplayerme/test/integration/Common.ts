import { expect } from 'chai';
import { config } from 'dotenv';
import { newReadyPlayerMe } from '../../src/index.js';
import type { ConnectionProfile, ReadyPlayerMe } from '../../src/index.js';

config();

const { READYPLAYERME_API_KEY } = process.env;
export const { READYPLAYERME_APP_ID, READYPLAYERME_USER_ID } = process.env;

export const connectionProfile: ConnectionProfile = { apiToken: `${READYPLAYERME_API_KEY}` };

export function prepareApi(): ReadyPlayerMe {
  const api = newReadyPlayerMe();

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
