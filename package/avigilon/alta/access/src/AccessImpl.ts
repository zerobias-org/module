/* eslint-disable */
// TODO - enable lint for implementation ^
import {
  AccessConnector,
  AuditApi,
  AuthApi,
  CredentialApi,
  EntryApi,
  GroupApi,
  IdentityProviderApi,
  RoleApi,
  ScheduleApi,
  SiteApi,
  UserApi,
  ZoneApi,
  wrapAuditProducer,
  wrapAuthProducer,
  wrapCredentialProducer,
  wrapEntryProducer,
  wrapGroupProducer,
  wrapIdentityProviderProducer,
  wrapRoleProducer,
  wrapScheduleProducer,
  wrapSiteProducer,
  wrapUserProducer,
  wrapZoneProducer
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
import { AuditProducerApiImpl } from './AuditProducerApiImpl';
import { AuthProducerApiImpl } from './AuthProducerApiImpl';
import { CredentialProducerApiImpl } from './CredentialProducerApiImpl';
import { EntryProducerApiImpl } from './EntryProducerApiImpl';
import { GroupProducerApiImpl } from './GroupProducerApiImpl';
import { IdentityProviderProducerApiImpl } from './IdentityProviderProducerApiImpl';
import { RoleProducerApiImpl } from './RoleProducerApiImpl';
import { ScheduleProducerApiImpl } from './ScheduleProducerApiImpl';
import { SiteProducerApiImpl } from './SiteProducerApiImpl';
import { UserProducerApiImpl } from './UserProducerApiImpl';
import { ZoneProducerApiImpl } from './ZoneProducerApiImpl';
import { ConnectionState } from '../generated/model';
import { AxiosInstance } from 'axios';

export class AccessImpl implements AccessConnector {
  private client: AvigilonAltaAccessClient;
  private auditApiProducer?: AuditApi;
  private authApiProducer?: AuthApi;
  private credentialApiProducer?: CredentialApi;
  private entryApiProducer?: EntryApi;
  private groupApiProducer?: GroupApi;
  private identityProviderApiProducer?: IdentityProviderApi;
  private roleApiProducer?: RoleApi;
  private scheduleApiProducer?: ScheduleApi;
  private siteApiProducer?: SiteApi;
  private userApiProducer?: UserApi;
  private zoneApiProducer?: ZoneApi;

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

  getAuthApi(): AuthApi {
    if (!this.authApiProducer) {
      const producer = new AuthProducerApiImpl(this.client);
      this.authApiProducer = wrapAuthProducer(producer);
    }
    return this.authApiProducer;
  }

  getEntryApi(): EntryApi {
    if (!this.entryApiProducer) {
      const producer = new EntryProducerApiImpl(this.client);
      this.entryApiProducer = wrapEntryProducer(producer);
    }
    return this.entryApiProducer;
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

  getZoneApi(): ZoneApi {
    if (!this.zoneApiProducer) {
      const producer = new ZoneProducerApiImpl(this.client);
      this.zoneApiProducer = wrapZoneProducer(producer);
    }
    return this.zoneApiProducer;
  }

  getAuditApi(): AuditApi {
    if (!this.auditApiProducer) {
      const producer = new AuditProducerApiImpl(this.client);
      this.auditApiProducer = wrapAuditProducer(producer);
    }
    return this.auditApiProducer;
  }

  getCredentialApi(): CredentialApi {
    if (!this.credentialApiProducer) {
      const producer = new CredentialProducerApiImpl(this.client);
      this.credentialApiProducer = wrapCredentialProducer(producer);
    }
    return this.credentialApiProducer;
  }

  getIdentityProviderApi(): IdentityProviderApi {
    if (!this.identityProviderApiProducer) {
      const producer = new IdentityProviderProducerApiImpl(this.client);
      this.identityProviderApiProducer = wrapIdentityProviderProducer(producer);
    }
    return this.identityProviderApiProducer;
  }

  getRoleApi(): RoleApi {
    if (!this.roleApiProducer) {
      const producer = new RoleProducerApiImpl(this.client);
      this.roleApiProducer = wrapRoleProducer(producer);
    }
    return this.roleApiProducer;
  }

  getScheduleApi(): ScheduleApi {
    if (!this.scheduleApiProducer) {
      const producer = new ScheduleProducerApiImpl(this.client);
      this.scheduleApiProducer = wrapScheduleProducer(producer);
    }
    return this.scheduleApiProducer;
  }
}
