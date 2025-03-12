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
import { toAssetInfoResponse, toAssetResponse } from './mappers';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';
import { handleAxiosError } from './util';

export class AssetProducerImpl implements AssetProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async addToApplication(
    assetId: string,
    addAssetToApplicationRequest: AddAssetToApplicationRequest
  ): Promise<AssetResponse> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: `/assets/${assetId}/application`,
        method: 'post',
        data: addAssetToApplicationRequest
      })
      .catch(handleAxiosError);

    return toAssetResponse(data);
  }

  async create(createAssetRequest: CreateAssetRequest): Promise<AssetInfoResponse> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: `/assets`,
        method: 'post',
        data: createAssetRequest
      })
      .catch(handleAxiosError);

    return toAssetInfoResponse(data);
  }

  async lock(assetId: string, lockAssetRequest: LockAssetRequest): Promise<void> {
    const { apiClient } = this.client;

    await apiClient
      .request({
        url: `/assets/${assetId}/lock`,
        method: 'put',
        data: lockAssetRequest
      })
      .catch(handleAxiosError);
  }

  async removeFromApplication(
    assetId: string,
    removeAssetFromApplicationRequest: RemoveAssetFromApplicationRequest
  ): Promise<AssetResponse> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: `/assets/${assetId}/application`,
        method: 'delete',
        data: removeAssetFromApplicationRequest
      })
      .catch(handleAxiosError);

    return toAssetResponse(data);
  }

  async unlock(assetId: string, unlockAssetRequest: UnlockAssetRequest): Promise<void> {
    const { apiClient } = this.client;

    await apiClient
      .request({
        url: `/assets/${assetId}/unlock`,
        method: 'put',
        data: unlockAssetRequest
      })
      .catch(handleAxiosError);
  }

  async update(
    assetId: string,
    updateAssetRequest: UpdateAssetRequest
  ): Promise<AssetInfoResponse> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: `/assets/${assetId}`,
        method: 'post',
        data: updateAssetRequest
      })
      .catch(handleAxiosError);

    return toAssetInfoResponse(data);
  }
}
