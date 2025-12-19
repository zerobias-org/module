/* eslint-disable */
// TODO - enable lint for implementation ^
import {
  BitbucketConnector,
  WorkspaceApi,
  RepositoryApi,
  wrapWorkspaceProducer,
  wrapRepositoryProducer
} from '../generated/api';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { ConnectionState } from '../generated/model';
import {
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus
} from '@auditmation/hub-core';
import { AtlassianCloudBitbucketClient } from './AtlassianCloudBitbucketClient';
import { WorkspaceProducerApiImpl } from './WorkspaceProducerApiImpl';
import { RepositoryProducerApiImpl } from './RepositoryProducerApiImpl';

export class BitbucketImpl implements BitbucketConnector {
  private client: AtlassianCloudBitbucketClient;
  private workspaceApiProducer?: WorkspaceApi;
  private repositoryApiProducer?: RepositoryApi;

  constructor() {
    this.client = new AtlassianCloudBitbucketClient();
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

  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  getWorkspaceApi(): WorkspaceApi {
    if (!this.workspaceApiProducer) {
      const producer = new WorkspaceProducerApiImpl(this.client);
      this.workspaceApiProducer = wrapWorkspaceProducer(producer);
    }
    return this.workspaceApiProducer;
  }

  getRepositoryApi(): RepositoryApi {
    if (!this.repositoryApiProducer) {
      const producer = new RepositoryProducerApiImpl(this.client);
      this.repositoryApiProducer = wrapRepositoryProducer(producer);
    }
    return this.repositoryApiProducer;
  }
}
