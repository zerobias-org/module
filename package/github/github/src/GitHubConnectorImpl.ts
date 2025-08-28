import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@auditmation/hub-core';
import { GitHubConnector, OrganizationApi, wrapOrganizationProducer } from '../generated/api';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { GitHubClient } from './GitHubClient';
import { OrganizationProducerApiImpl } from './OrganizationProducerApiImpl';

export class GitHubConnectorImpl implements GitHubConnector {
  private client: GitHubClient;

  private organizationApi?: OrganizationApi;

  constructor() {
    this.client = new GitHubClient();
  }

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    await this.client.connect(connectionProfile);
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    await this.client.disconnect();
    this.organizationApi = undefined;
  }

  // eslint-disable-next-line class-methods-use-this, @typescript-eslint/no-unused-vars
  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }

  // eslint-disable-next-line class-methods-use-this
  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  getOrganizationApi(): OrganizationApi {
    if (!this.organizationApi) {
      const producer = new OrganizationProducerApiImpl(this.client);
      this.organizationApi = wrapOrganizationProducer(producer);
    }
    return this.organizationApi;
  }
}
