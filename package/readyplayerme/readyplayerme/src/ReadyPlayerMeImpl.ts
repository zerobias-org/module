import { ConnectionMetadata, OAuthConnectionDetails, OperationSupportStatusDef } from '@auditmation/hub-core';
import { AxiosInstance } from 'axios';
import { AssetApi, Avatar2dApi, Avatar3dApi, AvatarApi, ReadyPlayerMeConnector, UserApi } from '../generated/api';
import { ConnectionProfile } from '../generated/model';

export class ReadyPlayerMeImpl implements ReadyPlayerMeConnector {
  connect(connectionProfile: ConnectionProfile, oauthConnectionDetails?: OAuthConnectionDetails): Promise<void> {
    throw new Error('Method not implemented.');
  }
  isConnected(): Promise<boolean> {
    throw new Error('Method not implemented.');
  }
  disconnect(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  refresh?(connectionProfile: ConnectionProfile, connectionState: void, oauthConnectionDetails?: OAuthConnectionDetails): Promise<void> {
    throw new Error('Method not implemented.');
  }
  metadata(): Promise<ConnectionMetadata> {
    throw new Error('Method not implemented.');
  }
  isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    throw new Error('Method not implemented.');
  }
  httpClient?(): AxiosInstance | undefined {
    throw new Error('Method not implemented.');
  }
  getAssetApi(): AssetApi {
    throw new Error('Method not implemented.');
  }
  getAvatarApi(): AvatarApi {
    throw new Error('Method not implemented.');
  }
  getAvatar2dApi(): Avatar2dApi {
    throw new Error('Method not implemented.');
  }
  getAvatar3dApi(): Avatar3dApi {
    throw new Error('Method not implemented.');
  }
  getUserApi(): UserApi {
    throw new Error('Method not implemented.');
  }
}
