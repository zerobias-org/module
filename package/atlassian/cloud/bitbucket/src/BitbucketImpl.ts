/* eslint-disable */
// TODO - enable lint for implementation ^
import {
  BitbucketConnector,
  RepositoryApi,
  UserApi,
  wrapRepositoryProducer,
  wrapUserProducer
} from '../generated/api';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { ConnectionState } from '../generated/model';
import {
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus
} from '@auditmation/hub-core';
import { BitbucketClient } from './BitbucketClient';
import { RepositoryProducerApiImpl } from './RepositoryProducerApiImpl';
import { UserProducerApiImpl } from './UserProducerApiImpl';

export class BitbucketImpl implements BitbucketConnector {
  private client: BitbucketClient;
  private repositoryApiProducer?: RepositoryApi;
  private userApiProducer?: UserApi;

  constructor() {
    this.client = new BitbucketClient();
  }

  async connect(connectionProfile: ConnectionProfile): Promise<ConnectionState> {
    return this.client.connect(connectionProfile);
  }

  async refresh(
    connectionProfile: ConnectionProfile,
    connectionState: ConnectionState
  ): Promise<ConnectionState> {
    throw new Error('Token refresh not required for basic auth');
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    await this.client.disconnect();
  }

  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  getRepositoryApi(): RepositoryApi {
    if (!this.repositoryApiProducer) {
      const producer = new RepositoryProducerApiImpl(this.client);
      this.repositoryApiProducer = wrapRepositoryProducer(producer);
    }
    return this.repositoryApiProducer;
  }

  getUserApi(): UserApi {
    if (!this.userApiProducer) {
      const producer = new UserProducerApiImpl(this.client);
      this.userApiProducer = wrapUserProducer(producer);
    }
    return this.userApiProducer;
  }
}
