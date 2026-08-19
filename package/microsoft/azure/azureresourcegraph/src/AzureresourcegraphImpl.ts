import {
  AzureresourcegraphConnector,
  ResourceApi,
  ResourceContainerApi,
  BinaryApi,
  CollectionsApi,
  DocumentsApi,
  FunctionsApi,
  ObjectsApi,
  SchemasApi,
  wrapResourceProducer,
  wrapResourceContainerProducer,
  wrapBinaryProducer,
  wrapCollectionsProducer,
  wrapDocumentsProducer,
  wrapFunctionsProducer,
  wrapObjectsProducer,
  wrapSchemasProducer
} from '../generated/api/index.js';
import { ConnectionProfile } from '../generated/model/index.js';
import { ConnectionState } from '../generated/model/index.js';
import {
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus
} from '@zerobias-org/types-core-js';
import { AzureResourceGraphClient } from './AzureResourceGraphClient.js';
import { ResourceProducerApiImpl } from './ResourceProducerApiImpl.js';
import { ResourceContainerProducerApiImpl } from './ResourceContainerProducerApiImpl.js';
import { DataProducerImpl } from './impl/dataproducer/DataProducerImpl.js';

export class AzureresourcegraphImpl implements AzureresourcegraphConnector {
  private client: AzureResourceGraphClient;

  private resourceApiProducer?: ResourceApi;

  private resourceContainerApiProducer?: ResourceContainerApi;

  private dataProducer?: DataProducerImpl;

  constructor() {
    this.client = new AzureResourceGraphClient();
  }

  async connect(connectionProfile: ConnectionProfile): Promise<ConnectionState> {
    return this.client.connect(connectionProfile);
  }

  async refresh(
    connectionProfile: ConnectionProfile,
    connectionState: ConnectionState
  ): Promise<ConnectionState> {
    // Client-credentials grant has no refresh token — re-mint via connect().
    void connectionState;
    return this.client.connect(connectionProfile);
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    await this.client.disconnect();
  }

  // Standard methods (mandatory implementations)
  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    // ALWAYS return OperationSupportStatus.Maybe - replaced by platform
    void operationId;
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    // ALWAYS return ConnectionStatus.Down - replaced by platform
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  // Producer getters (lazy initialization with exact interface names)
  getResourceApi(): ResourceApi {
    if (!this.resourceApiProducer) {
      const producer = new ResourceProducerApiImpl(this.client);
      this.resourceApiProducer = wrapResourceProducer(producer);
    }
    return this.resourceApiProducer;
  }

  getResourceContainerApi(): ResourceContainerApi {
    if (!this.resourceContainerApiProducer) {
      const producer = new ResourceContainerProducerApiImpl(this.client);
      this.resourceContainerApiProducer = wrapResourceContainerProducer(producer);
    }
    return this.resourceContainerApiProducer;
  }

  // DataProducer interface — one impl backs all six producer APIs.
  private getDataProducer(): DataProducerImpl {
    if (!this.dataProducer) {
      this.dataProducer = new DataProducerImpl(this.client);
    }
    return this.dataProducer;
  }

  getObjectsApi(): ObjectsApi {
    return wrapObjectsProducer(this.getDataProducer());
  }

  getCollectionsApi(): CollectionsApi {
    return wrapCollectionsProducer(this.getDataProducer());
  }

  getDocumentsApi(): DocumentsApi {
    return wrapDocumentsProducer(this.getDataProducer());
  }

  getFunctionsApi(): FunctionsApi {
    return wrapFunctionsProducer(this.getDataProducer());
  }

  getBinaryApi(): BinaryApi {
    return wrapBinaryProducer(this.getDataProducer());
  }

  getSchemasApi(): SchemasApi {
    return wrapSchemasProducer(this.getDataProducer());
  }
}
