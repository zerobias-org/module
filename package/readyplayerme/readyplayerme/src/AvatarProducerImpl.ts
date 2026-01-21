import { AvatarProducerApi } from '../generated/api/index.js';
import { EquipAssetRequest, UnequipAssetRequest } from '../generated/model/index.js';
import { toAvatarMetadata } from './mappers.js';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient.js';
import { handleAxiosError } from './util.js';

export class AvatarProducerImpl implements AvatarProducerApi {
  constructor(private client: ReadyPlayerMeClient) {}

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
