import {
  ConnectionMetadata,
  OperationSupportStatusDef
} from '@auditmation/hub-core';
import {
  ApplicationApi,
  AssetApi,
  Avatar2dApi,
  Avatar3dApi,
  AvatarApi,
  ReadyPlayerMeConnector,
  UserApi,
  wrapApplicationProducer,
  wrapAssetProducer,
  wrapAvatar2dProducer,
  wrapAvatar3dProducer,
  wrapAvatarProducer,
  wrapUserProducer
} from '../generated/api';
import { ConnectionProfile } from '../generated/model';
import { ApplicationProducerImpl } from './ApplicationProducerImpl';
import { AssetProducerImpl } from './AssetProducerImpl';
import { Avatar2dProducerImpl } from './Avatar2dProducerImpl';
import { Avatar3dProducerImpl } from './Avatar3dProducerImpl';
import { AvatarProducerImpl } from './AvatarProducerImpl';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';
import { UserProducerImpl } from './UserProducerImpl';

export class ReadyPlayerMeImpl implements ReadyPlayerMeConnector {
  private client = new ReadyPlayerMeClient();

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

  getAssetApi(): AssetApi {
    return wrapAssetProducer(new AssetProducerImpl(this.client))
  }

  getAvatarApi(): AvatarApi {
    return wrapAvatarProducer(new AvatarProducerImpl(this.client))
  }

  getAvatar2dApi(): Avatar2dApi {
    return wrapAvatar2dProducer(new Avatar2dProducerImpl(this.client))
  }

  getAvatar3dApi(): Avatar3dApi {
    return wrapAvatar3dProducer(new Avatar3dProducerImpl(this.client))
  }

  getUserApi(): UserApi {
    return wrapUserProducer(new UserProducerImpl(this.client))
  }

  getApplicationApi(): ApplicationApi {
    return wrapApplicationProducer(new ApplicationProducerImpl(this.client))
  }
}
