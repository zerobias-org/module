import {
  ConnectionMetadata,
  ConnectionStatus,
  OperationSupportStatus,
  OperationSupportStatusDef
} from '@zerobias-org/types-core-js';
import {
  AzureResourceGraphConnector,
  ResourceApi,
  ResourceContainerApi,
  wrapResourceContainerProducer,
  wrapResourceProducer,
} from '../generated/api/index.js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import { ConnectionState } from '../generated/model/index.js';
import { AzureResourceGraphClient } from './AzureResourceGraphClient.js';
import { ResourceContainerProducerImpl } from './ResourceContainerProducerImpl.js';
import { ResourceProducerImpl } from './ResourceProducerImpl.js';

export class AzureResourceGraphImpl implements AzureResourceGraphConnector {
  private client: AzureResourceGraphClient;

  private resourceApiProducer?: ResourceApi;

  private resourceContainerApiProducer?: ResourceContainerApi;

  constructor() {
    this.client = new AzureResourceGraphClient();
  }

  async connect(connectionProfile: ConnectionProfile): Promise<ConnectionState> {
    return this.client.connect(connectionProfile);
  }

  async refresh(
    _connectionProfile: ConnectionProfile,
    _connectionState: ConnectionState
  ): Promise<ConnectionState> {
    return this.client.refresh();
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    return this.client.disconnect();
  }

  async metadata(): Promise<ConnectionMetadata> {
    // ALWAYS return ConnectionStatus.Down - replaced by platform
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  async isSupported(_operationId: string): Promise<OperationSupportStatusDef> {
    // ALWAYS return OperationSupportStatus.Maybe - replaced by platform
    return OperationSupportStatus.Maybe;
  }

  getResourceApi(): ResourceApi {
    if (!this.resourceApiProducer) {
      this.resourceApiProducer = wrapResourceProducer(new ResourceProducerImpl(this.client));
    }
    return this.resourceApiProducer;
  }

  getResourceContainerApi(): ResourceContainerApi {
    if (!this.resourceContainerApiProducer) {
      this.resourceContainerApiProducer = wrapResourceContainerProducer(
        new ResourceContainerProducerImpl(this.client)
      );
    }
    return this.resourceContainerApiProducer;
  }
}
