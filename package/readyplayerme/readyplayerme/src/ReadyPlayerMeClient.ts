import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@auditmation/hub-core';
import { InvalidCredentialsError, NotConnectedError } from '@auditmation/types-core-js';
import { Axios } from 'axios';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';

export class ReadyPlayerMeClient {
  private _apiClient?: Axios;

  private _modelClient?: Axios;

  public get apiClient(): Axios {
    if (!this._apiKey || !this._apiClient) {
      throw new NotConnectedError();
    }
    return this._apiClient;
  }

  public get modelClient(): Axios {
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

    this._apiClient = new Axios({
      baseURL: 'https://api.readyplayer.me/v1',
      headers: { 'x-api-key': this.apiKey },
    });

    this._modelClient = new Axios({
      baseURL: 'https://model.readyplayer.me',
      headers: { 'x-api-key': this.apiKey },
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
