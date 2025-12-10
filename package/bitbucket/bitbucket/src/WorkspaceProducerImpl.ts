import { PagedResults } from '@auditmation/types-core-js';
import { WorkspaceProducerApi } from '../generated/api';
import { Workspace } from '../generated/model';
import { BitbucketClient } from './BitbucketClient';
import { handleAxiosError } from './util';
import { toWorkspace } from './mappers';

export class WorkspaceProducerImpl implements WorkspaceProducerApi {
  constructor(private client: BitbucketClient) { }

  async list(results: PagedResults<Workspace>): Promise<void> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: '/workspaces',
        method: 'get',
        params: {
          page: results.pageNumber,
          pagelen: results.pageSize,
        },
      })
      .catch(handleAxiosError);

    const parsed = typeof data === 'string' ? JSON.parse(data) : data;
    const workspaces = parsed.values?.map(toWorkspace) || [];

    results.items = workspaces;
    results.count = parsed.size || workspaces.length;
  }
}
