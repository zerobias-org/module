import { PagedResults, SortDirection } from '@zerobias-org/types-core-js';
import { ApplicationProducerApi } from '../generated/api/index.js';
import { AssetInfo, AssetTypeDef, AssetGenderDef } from '../generated/model/index.js';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient.js';
import { handleAxiosError } from './util.js';
import { toAssetInfo } from './mappers.js';

export class ApplicationProducerImpl implements ApplicationProducerApi {
  constructor(private client: ReadyPlayerMeClient) {}

  async listAssets(
    results: PagedResults<AssetInfo>,
    appId: string,
    name?: string,
    organizationId?: string,
    type?: Array<AssetTypeDef>,
    gender?: Array<AssetGenderDef>,
    ids?: Array<string>,
    applicationIds?: Array<string>
  ): Promise<void> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: '/assets',
        method: 'get',
        headers: { 'X-APP-ID': appId },
        params: {
          order: results.sortBy
            && `${SortDirection.Desc.eq(results.sortDir) ? '-' : ''}${results.sortBy}`,
          limit: results.pageSize,
          page: results.pageNumber,
          name,
          organizationId,
          type: type && type.map((t) => t.toString()),
          gender: gender && gender.map((g) => g.toString()),
          ids,
          applicationIds,
        },
      })
      .catch(handleAxiosError);

    return results.ingest(data.data?.map((asset) => toAssetInfo(asset)));
  }
}
