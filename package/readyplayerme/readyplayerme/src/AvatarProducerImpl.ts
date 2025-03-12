import { AvatarProducerApi } from '../generated/api';
import { EquipAssetRequest, UnequipAssetRequest } from '../generated/model';
import { toAvatarMetadata } from './mappers';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';
import { handleAxiosError } from './util';

export class AvatarProducerImpl implements AvatarProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async getMetadata(avatarId: string): Promise<object> {
    const { modelClient } = this.client;

    const { data } = await modelClient
      .request({
        url: `/${avatarId}`,
        method: 'get',
      })
      .catch(handleAxiosError);

    return toAvatarMetadata(data);
  }

  async equip(avatarId: string, equipAssetRequest: EquipAssetRequest): Promise<void> {
    const { apiClient } = this.client;

    await apiClient
      .request({
        url: `/avatars/${avatarId}/equip`,
        method: 'put',
        data: JSON.stringify(equipAssetRequest),
      })
      .catch(handleAxiosError);
  }

  async unequip(avatarId: string, unequipAssetRequest: UnequipAssetRequest): Promise<void> {
    const { apiClient } = this.client;

    await apiClient
      .request({
        url: `/avatars/${avatarId}/unequip`,
        method: 'put',
        data: JSON.stringify(unequipAssetRequest),
      })
      .catch(handleAxiosError);
  }
}
