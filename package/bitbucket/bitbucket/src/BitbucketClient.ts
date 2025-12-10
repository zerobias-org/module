import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@auditmation/hub-core';
import { Email, InvalidCredentialsError, NotConnectedError } from '@auditmation/types-core-js';
import axios, { AxiosInstance } from 'axios';
import { HttpsProxyAgent } from 'https-proxy-agent';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';

export class BitbucketClient {
  private _apiClient?: AxiosInstance;

  public get apiClient(): AxiosInstance {
    if (!this._apiClient) {
      throw new NotConnectedError();
    }
    return this._apiClient;
  }

  private _email?: Email;

  private _apiToken?: string;

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    if (!connectionProfile.email || !connectionProfile.apiToken) {
      throw new InvalidCredentialsError();
    }
    this._email = connectionProfile.email;
    this._apiToken = connectionProfile.apiToken;

    const emailStr = this._email.toString();
    const authString = Buffer.from(`${emailStr}:${this._apiToken}`).toString('base64');

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const config: any = {
      baseURL: 'https://api.bitbucket.org/2.0',
      timeout: 30000,
      headers: {
        'Authorization': `Basic ${authString}`,
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
    };

    // Configure proxy if environment variable is set
    const httpsProxy = process.env.https_proxy || process.env.HTTPS_PROXY;
    if (httpsProxy) {
      config.httpsAgent = new HttpsProxyAgent(httpsProxy);
      config.proxy = false; // Disable axios built-in proxy to use the agent
    }

    this._apiClient = axios.create(config);
  }

  async isConnected(): Promise<boolean> {
    return !!this._apiClient;
  }

  async disconnect(): Promise<void> {
    this._email = undefined;
    this._apiToken = undefined;
    this._apiClient = undefined;
  }

  // eslint-disable-next-line class-methods-use-this
  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  // eslint-disable-next-line class-methods-use-this
  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }
}
