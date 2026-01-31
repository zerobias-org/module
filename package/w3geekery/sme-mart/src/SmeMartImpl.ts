import {
  SmeMartConnector,
  SmeMart,
  AdminApi,
  ProfileApi,
  ProviderApi,
  RequestApi,
  ServiceApi,
  wrapAdminProducer,
  wrapProfileProducer,
  wrapProviderProducer,
  wrapRequestProducer,
  wrapServiceProducer,
} from '../generated/api/index.js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import { ConnectionState } from '../generated/model/ConnectionState.js';
import {
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus,
} from '@zerobias-org/types-core-js';
import { initDb } from './db/index.js';
import { AdminProducerApiImpl } from './AdminProducerApiImpl.js';
import { ProfileProducerApiImpl } from './ProfileProducerApiImpl.js';
import { ProviderProducerApiImpl } from './ProviderProducerApiImpl.js';
import { RequestProducerApiImpl } from './RequestProducerApiImpl.js';
import { ServiceProducerApiImpl } from './ServiceProducerApiImpl.js';

export class SmeMartImpl implements SmeMartConnector {
  private connected = false;
  private adminApiProducer?: AdminApi;
  private profileApiProducer?: ProfileApi;
  private providerApiProducer?: ProviderApi;
  private requestApiProducer?: RequestApi;
  private serviceApiProducer?: ServiceApi;

  async connect(connectionProfile: ConnectionProfile): Promise<ConnectionState> {
    initDb(connectionProfile.databaseUrl.toString());
    this.connected = true;
    return new ConnectionState();
  }

  async refresh(
    connectionProfile: ConnectionProfile,
    connectionState: ConnectionState,
  ): Promise<ConnectionState> {
    return connectionState;
  }

  async isConnected(): Promise<boolean> {
    return this.connected;
  }

  async disconnect(): Promise<void> {
    this.connected = false;
  }

  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  getAdminApi(): AdminApi {
    if (!this.adminApiProducer) {
      this.adminApiProducer = wrapAdminProducer(new AdminProducerApiImpl());
    }
    return this.adminApiProducer;
  }

  getProfileApi(): ProfileApi {
    if (!this.profileApiProducer) {
      this.profileApiProducer = wrapProfileProducer(new ProfileProducerApiImpl());
    }
    return this.profileApiProducer;
  }

  getProviderApi(): ProviderApi {
    if (!this.providerApiProducer) {
      this.providerApiProducer = wrapProviderProducer(new ProviderProducerApiImpl());
    }
    return this.providerApiProducer;
  }

  getRequestApi(): RequestApi {
    if (!this.requestApiProducer) {
      this.requestApiProducer = wrapRequestProducer(new RequestProducerApiImpl());
    }
    return this.requestApiProducer;
  }

  getServiceApi(): ServiceApi {
    if (!this.serviceApiProducer) {
      this.serviceApiProducer = wrapServiceProducer(new ServiceProducerApiImpl());
    }
    return this.serviceApiProducer;
  }
}
