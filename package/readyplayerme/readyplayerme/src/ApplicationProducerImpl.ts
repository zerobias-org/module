import { PagedResults, SortDirection } from '@auditmation/types-core-js';
import {
  ApplicationApi,
  ApplicationProducerApi
} from '../generated/api';
import { AssetInfo } from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';
import { handleAxiosError } from './util';
import { toAssetInfo } from './mappers';

export class ApplicationProducerImpl implements ApplicationProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async listAssets(
    results: PagedResults<AssetInfo>,
    appId: string,
    name?: string,
    organizationId?: string,
    type?: Array<ApplicationApi.TypeEnumDef>,
    gender?: Array<ApplicationApi.GenderEnumDef>,
    ids?: Array<string>,
    applicationIds?: Array<string>
  ): Promise<void> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: `/assets`,
        method: 'get',
        headers: {
          'X-APP-ID': appId
        },
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
          applicationIds
        }
      })
      .catch(handleAxiosError);

    return results.ingest(data.data?.map(toAssetInfo));
  }
}
