import {
  ConnectionMetadata,
  OperationSupportStatusDef
} from '@auditmation/hub-core';
import {
  BitbucketConnector,
  RepositoryApi,
  WorkspaceApi,
  wrapRepositoryProducer,
  wrapWorkspaceProducer
} from '../generated/api';
import { ConnectionProfile } from '../generated/model';
import { BitbucketClient } from './BitbucketClient';
import { RepositoryProducerImpl } from './RepositoryProducerImpl';
import { WorkspaceProducerImpl } from './WorkspaceProducerImpl';

export class BitbucketImpl implements BitbucketConnector {
  private client = new BitbucketClient();

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    return this.client.connect(connectionProfile);
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    return this.client.disconnect();
  }

  async metadata(): Promise<ConnectionMetadata> {
    return this.client.metadata();
  }

  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    return this.client.isSupported(operationId);
  }

  getWorkspaceApi(): WorkspaceApi {
    return wrapWorkspaceProducer(new WorkspaceProducerImpl(this.client));
  }

  getRepositoryApi(): RepositoryApi {
    return wrapRepositoryProducer(new RepositoryProducerImpl(this.client));
  }
}
