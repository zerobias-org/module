import {
  ConnectionMetadata,
  ConnectionStatus,
  InvalidCredentialsError,
  NotConnectedError,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@zerobias-org/types-core-js';
import axios, { AxiosInstance } from 'axios';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';

/**
 * Stellar Cyber REST client.
 *
 * Auth: a scoped API key is exchanged for a short-lived (~10 min) JWT bearer at
 * connect time (`POST /access_token`). The JWT is held in-memory for the lifetime
 * of the connection; a collector run completes well within the token window, and
 * the platform re-connects (re-exchanges the key) for a fresh scope/run.
 */
export class StellarCyberClient {
  private _apiClient?: AxiosInstance;

  private _jwt?: string;

  public get apiClient(): AxiosInstance {
    if (!this._jwt || !this._apiClient) {
      throw new NotConnectedError();
    }
    return this._apiClient;
  }

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    if (!connectionProfile.apiToken || !connectionProfile.host) {
      throw new InvalidCredentialsError();
    }

    const host = connectionProfile.host.replace(/^https?:\/\//, '').replace(/\/+$/, '');
    const baseURL = `https://${host}/connect/api/v1`;

    // Exchange the scoped API key for a short-lived JWT bearer.
    const { data } = await axios.request({
      url: `${baseURL}/access_token`,
      method: 'post',
      headers: { Authorization: `Bearer ${connectionProfile.apiToken}` },
      validateStatus: (status) => status >= 200 && status < 300,
    });

    this._jwt = data?.access_token;
    if (!this._jwt) {
      throw new InvalidCredentialsError();
    }

    this._apiClient = axios.create({
      baseURL,
      headers: { Authorization: `Bearer ${this._jwt}` },
      validateStatus: (status) => status >= 200 && status < 300,
    });
  }

  async isConnected(): Promise<boolean> {
    return !!this._jwt;
  }

  async disconnect(): Promise<void> {
    this._jwt = undefined;
    this._apiClient = undefined;
  }


  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }


  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }
}
