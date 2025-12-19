import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError, EnumValue } from '@auditmation/types-core-js';
import { WorkspaceProducerApi, WorkspaceApi } from '../generated/api/WorkspaceApi';
import { Workspace } from '../generated/model';
import { AtlassianCloudBitbucketClient } from './AtlassianCloudBitbucketClient';
import { toWorkspace } from './Mappers';

export class WorkspaceProducerApiImpl implements WorkspaceProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AtlassianCloudBitbucketClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(
    results: PagedResults<Workspace>,
    role?: WorkspaceApi.RoleEnumDef,
    q?: string,
    orderBy?: string
  ): Promise<void> {
    const params: Record<string, string | number> = {};

    // Bitbucket uses pagelen (max 100) for page size
    params.pagelen = Math.min(results.pageSize || 50, 100);

    // Bitbucket uses page (1-based) for page number
    params.page = results.pageNumber || 1;

    if (role && role instanceof EnumValue) {
      params.role = role.value as string;
    }
    if (q) {
      params.q = q;
    }
    if (orderBy) {
      params.sort = orderBy;
    }

    const response = await this.httpClient.get('/workspaces', { params });

    if (!response.data || !Array.isArray(response.data.values)) {
      throw new UnexpectedError('Invalid response format: expected values array');
    }

    results.items = response.data.values.map(toWorkspace);
    results.count = response.data.size || results.items.length;
  }
}
