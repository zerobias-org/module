/* eslint-disable */
// TODO - enable lint for implementation ^
import { 
  AccessConnector, 
  AcuApi, 
  GroupApi, 
  UserApi,
  wrapAcuProducer,
  wrapGroupProducer,
  wrapUserProducer
} from '../generated/api';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { 
  ConnectionMetadata, 
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus
} from '@auditmation/hub-core';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { UserProducerApiImpl } from './UserProducerApiImpl';
import { AcuProducerApiImpl } from './AcuProducerApiImpl';
import { GroupProducerApiImpl } from './GroupProducerApiImpl';

export class AccessImpl implements AccessConnector {
  private client: AvigilonAltaAccessClient;
  private userApiProducer?: UserApi;
  private acuApiProducer?: AcuApi;
  private groupApiProducer?: GroupApi;

  constructor() {
    this.client = new AvigilonAltaAccessClient();
  }

  // Connection methods (delegate to client)
  async connect(profile: ConnectionProfile): Promise<void> {
    await this.client.connect(profile);
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

  getGroupApi(): GroupApi {
    if (!this.groupApiProducer) {
      const producer = new GroupProducerApiImpl(this.client);
      this.groupApiProducer = wrapGroupProducer(producer);
    }
    return this.groupApiProducer;
  }
}
