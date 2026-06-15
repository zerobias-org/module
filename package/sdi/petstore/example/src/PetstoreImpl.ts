
// TODO - enable lint for implementation ^
import {
  PetstoreConnector,
  PetApi,
  StoreApi,
  UserApi,
  wrapPetProducer,
  wrapStoreProducer,
  wrapUserProducer,
} from '../generated/api/index.js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import {
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus,
} from '@zerobias-org/types-core-js';
import { PetstoreClient } from './PetstoreClient.js';
import { PetProducerApiImpl } from './PetProducerApiImpl.js';
import { StoreProducerApiImpl } from './StoreProducerApiImpl.js';
import { UserProducerApiImpl } from './UserProducerApiImpl.js';
import { ConnectionState } from '../generated/model/index.js';

/**
 * Petstore connector — implements the generated PetstoreConnector type
 * (= Petstore & Connector<ConnectionProfile, ConnectionState>).
 *
 * 3 lazy producer getters using the generated wrap*Producer helpers (NOT raw
 * producer instances).
 *
 * refresh delegates to client.refresh() (NOT throws) — Petstore is anonymous
 * and has no token to refresh, so the no-op delegation is the correct shape.
 *
 * isSupported returns Maybe; metadata returns ConnectionStatus.Down. The
 * platform replaces both at runtime.
 */
export class PetstoreImpl implements PetstoreConnector {
  private client: PetstoreClient;

  private petApiProducer?: PetApi;

  private storeApiProducer?: StoreApi;

  private userApiProducer?: UserApi;

  constructor() {
    this.client = new PetstoreClient();
  }

  async connect(connectionProfile: ConnectionProfile): Promise<ConnectionState> {
    return this.client.connect(connectionProfile);
  }

  async refresh(
    connectionProfile: ConnectionProfile,
    connectionState: ConnectionState
  ): Promise<ConnectionState> {
    return this.client.refresh();
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

  getPetApi(): PetApi {
    if (!this.petApiProducer) {
      const producer = new PetProducerApiImpl(this.client);
      this.petApiProducer = wrapPetProducer(producer);
    }
    return this.petApiProducer;
  }

  getStoreApi(): StoreApi {
    if (!this.storeApiProducer) {
      const producer = new StoreProducerApiImpl(this.client);
      this.storeApiProducer = wrapStoreProducer(producer);
    }
    return this.storeApiProducer;
  }

  getUserApi(): UserApi {
    if (!this.userApiProducer) {
      const producer = new UserProducerApiImpl(this.client);
      this.userApiProducer = wrapUserProducer(producer);
    }
    return this.userApiProducer;
  }
}
