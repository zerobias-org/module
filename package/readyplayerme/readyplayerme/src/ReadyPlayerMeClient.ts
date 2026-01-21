import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@zerobias-org/types-core-js';
import { InvalidCredentialsError, NotConnectedError } from '@zerobias-org/types-core-js';
import axios, { AxiosInstance } from 'axios';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';

export class ReadyPlayerMeClient {
  private _apiClient?: AxiosInstance;

  private _modelClient?: AxiosInstance;

  public get apiClient(): AxiosInstance {
    if (!this._apiKey || !this._apiClient) {
      throw new NotConnectedError();
    }
    return this._apiClient;
  }

  public get modelClient(): AxiosInstance {
    if (!this._apiKey || !this._modelClient) {
      throw new NotConnectedError();
    }
    return this._modelClient;
  }

  private _apiKey?: string;

  private get apiKey(): string {
    if (!this._apiKey) {
      throw new NotConnectedError();
    }
    return this._apiKey;
  }

  private set apiKey(value: string) {
    this._apiKey = value;
  }

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    if (!connectionProfile.apiToken) {
      throw new InvalidCredentialsError();
    }

    this.apiKey = connectionProfile.apiToken;
    this._apiClient = axios.create({
      baseURL: 'https://api.readyplayer.me/v1',
      headers: { 'x-api-key': this.apiKey },
      validateStatus: (status) => status >= 200 && status < 300,
    });

    this._modelClient = axios.create({
      baseURL: 'https://model.readyplayer.me',
      headers: { 'x-api-key': this.apiKey },
      validateStatus: (status) => status >= 200 && status < 300,
    });
  }

  async isConnected(): Promise<boolean> {
    return !!this._apiKey;
  }

  async disconnect(): Promise<void> {
    this._apiKey = undefined;
    this._apiClient = undefined;
    this._modelClient = undefined;
  }

   
  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

   
  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }
}
