import {
  ConnectionMetadata,
  ConnectionStatus,
  NotConnectedError,
  OperationSupportStatus,
  OperationSupportStatusDef,
} from '@zerobias-org/types-core-js';
import axios, { AxiosInstance } from 'axios';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import { handleAxiosError } from './util.js';

export const DEFAULT_BASE_URL = 'https://csrc.nist.gov';
export const LISTING_PATH = '/projects/cryptographic-module-validation-program/validated-modules/search/all';
export const DETAIL_PATH_PREFIX = '/projects/cryptographic-module-validation-program/certificate/';

export class CmvpClient {
  private _httpClient?: AxiosInstance;

  private _baseUrl?: string;

  public get httpClient(): AxiosInstance {
    if (!this._httpClient) {
      throw new NotConnectedError();
    }
    return this._httpClient;
  }

  public get baseUrl(): string {
    if (!this._baseUrl) {
      throw new NotConnectedError();
    }
    return this._baseUrl;
  }

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    this._baseUrl = connectionProfile.baseUrl?.toString() ?? DEFAULT_BASE_URL;
    this._httpClient = axios.create({
      baseURL: this._baseUrl,
      timeout: 30000,
      headers: { 'User-Agent': 'zerobias-module-nist-cmvp' },
      validateStatus: (status) => status >= 200 && status < 300,
    });

    // CMVP has no credentials — connect() just confirms the site is reachable.
    try {
      await this._httpClient.get(LISTING_PATH);
    } catch (error: any) {
      this._httpClient = undefined;
      this._baseUrl = undefined;
      handleAxiosError(error, 'cmvp', 'listing');
    }
  }

  async isConnected(): Promise<boolean> {
    if (!this._httpClient) {
      return false;
    }
    try {
      await this._httpClient.get(LISTING_PATH);
      return true;
    } catch {
      return false;
    }
  }

  async disconnect(): Promise<void> {
    this._httpClient = undefined;
    this._baseUrl = undefined;
  }

  async metadata(): Promise<ConnectionMetadata> {
    const connected = await this.isConnected();
    return new ConnectionMetadata(connected ? ConnectionStatus.On : ConnectionStatus.Down);
  }

  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Yes;
  }
}
