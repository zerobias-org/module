import { config } from 'dotenv';
import { ConnectionProfile } from '../../src';

config();

const { READYPLAYERME_API_KEY } = process.env;

export const connectionProfile: ConnectionProfile = {
  apiToken: `${READYPLAYERME_API_KEY}`
}
