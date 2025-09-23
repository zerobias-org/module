/* eslint-disable */
// TODO - enable lint for implementation ^
import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@auditmation/hub-core';
import { AxiosInstance } from 'axios';
import {
  AccessConnector,
  AuthApi
} from '../generated/api';
import { ConnectionState } from '../generated/model';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class AccessImpl implements AccessConnector {
  private client: AvigilonAltaAccessClient;

  constructor() {
    this.client = new AvigilonAltaAccessClient();
  }
  getAuthApi(): AuthApi {
    throw new Error('Method not implemented.');
  }
  httpClient?(): AxiosInstance | undefined {
    throw new Error('Method not implemented.');
  }

  async connect(
    connectionProfile: ConnectionProfile
  ): Promise<ConnectionState> {
    return this.client.connect(connectionProfile);
  }

  async refresh(
    connectionProfile: ConnectionProfile,
    connectionState: ConnectionState
  ): Promise<ConnectionState> {
    throw new Error('Method not implemented.');
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    await this.client.disconnect();
  }

  // Standard methods (mandatory implementations as per task instructions)
  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    // ALWAYS return OperationSupportStatus.Maybe - replaced by platform
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    // ALWAYS return ConnectionStatus.Down - replaced by platform
    return new ConnectionMetadata(ConnectionStatus.Down);
  }
}
