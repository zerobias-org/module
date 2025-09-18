/* eslint-disable */
// TODO - enable lint for implementation ^
import {
  AccessConnector,
  AcuApi,
  AuthApi,
  GroupApi,
  SiteApi,
  UserApi,
  wrapAcuProducer,
  wrapAuthProducer,
  wrapGroupProducer,
  wrapSiteProducer,
  wrapUserProducer
} from '../generated/api';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import {
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus,
  OAuthConnectionDetails
} from '@auditmation/hub-core';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { UserProducerApiImpl } from './UserProducerApiImpl';
import { AcuProducerApiImpl } from './AcuProducerApiImpl';
import { AuthProducerApiImpl } from './AuthProducerApiImpl';
import { GroupProducerApiImpl } from './GroupProducerApiImpl';
import { SiteProducerApiImpl } from './SiteProducerApiImpl';
import { ConnectionState } from '../generated/model';
import { AxiosInstance } from 'axios';

export class AccessImpl implements AccessConnector {
  private client: AvigilonAltaAccessClient;
  private userApiProducer?: UserApi;
  private acuApiProducer?: AcuApi;
  private authApiProducer?: AuthApi;
  private groupApiProducer?: GroupApi;
  private siteApiProducer?: SiteApi;

  constructor() {
    this.client = new AvigilonAltaAccessClient();
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
  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    // ALWAYS return OperationSupportStatus.Maybe - replaced by platform
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    // ALWAYS return ConnectionStatus.Down - replaced by platform
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  // Producer getters (lazy initialization with exact interface names)
  getUserApi(): UserApi {
    if (!this.userApiProducer) {
      const producer = new UserProducerApiImpl(this.client);
      this.userApiProducer = wrapUserProducer(producer);
    }
    return this.userApiProducer;
  }

  getAcuApi(): AcuApi {
    if (!this.acuApiProducer) {
      const producer = new AcuProducerApiImpl(this.client);
      this.acuApiProducer = wrapAcuProducer(producer);
    }
    return this.acuApiProducer;
  }

  getAuthApi(): AuthApi {
    if (!this.authApiProducer) {
      const producer = new AuthProducerApiImpl(this.client);
      this.authApiProducer = wrapAuthProducer(producer);
    }
    return this.authApiProducer;
  }

  getGroupApi(): GroupApi {
    if (!this.groupApiProducer) {
      const producer = new GroupProducerApiImpl(this.client);
      this.groupApiProducer = wrapGroupProducer(producer);
    }
    return this.groupApiProducer;
  }

  getSiteApi(): SiteApi {
    if (!this.siteApiProducer) {
      const producer = new SiteProducerApiImpl(this.client);
      this.siteApiProducer = wrapSiteProducer(producer);
    }
    return this.siteApiProducer;
  }
}
