import { AvatarProducerApi } from '../generated/api';
import { EquipAssetRequest, UnequipAssetRequest } from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';

export class AvatarProducerImpl implements AvatarProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async getMetadata(avatarId: string): Promise<object> {
    throw new Error('Method not implemented.');
  }

  async equip(avatarId: string, equipAssetRequest: EquipAssetRequest): Promise<void> {
    throw new Error('Method not implemented.');
  }

  async unequip(avatarId: string, unequipAssetRequest: UnequipAssetRequest): Promise<void> {
    throw new Error('Method not implemented.');
  }

}
