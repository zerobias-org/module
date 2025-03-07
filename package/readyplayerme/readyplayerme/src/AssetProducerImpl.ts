import { AssetProducerApi } from '../generated/api';
import {
  AddAssetToApplicationRequest,
  AssetInfoResponse,
  AssetResponse,
  CreateAssetRequest,
  LockAssetRequest,
  RemoveAssetFromApplicationRequest,
  UnlockAssetRequest,
  UpdateAssetRequest
} from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';

export class AssetProducerImpl implements AssetProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async addToApplication(assetId: string,
    addAssetToApplicationRequest: AddAssetToApplicationRequest): Promise<AssetResponse> {
    throw new Error('Method not implemented.');
  }

  async create(createAssetRequest: CreateAssetRequest): Promise<AssetInfoResponse> {
    throw new Error('Method not implemented.');
  }

  async lock(assetId: string, lockAssetRequest: LockAssetRequest): Promise<void> {
    throw new Error('Method not implemented.');
  }

  async removeFromApplication(assetId: string,
    removeAssetFromApplicationRequest: RemoveAssetFromApplicationRequest): Promise<AssetResponse> {
    throw new Error('Method not implemented.');
  }

  async unlock(assetId: string, unlockAssetRequest: UnlockAssetRequest): Promise<void> {
    throw new Error('Method not implemented.');
  }

  async update(assetId: string,
    updateAssetRequest: UpdateAssetRequest): Promise<AssetInfoResponse> {
    throw new Error('Method not implemented.');
  }

}
