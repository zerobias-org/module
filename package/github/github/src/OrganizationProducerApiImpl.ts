import { PagedResults } from '@auditmation/types-core-js';
import { OrganizationProducerApi } from '../generated/api/OrganizationApi';
import { Organization, OrganizationInfo } from '../generated/model';
import { GitHubClient } from './GitHubClient';
import { mapOrganization, mapOrganizationInfo } from './Mappers';

export class OrganizationProducerApiImpl implements OrganizationProducerApi {
  private client: GitHubClient;

  constructor(client: GitHubClient) {
    this.client = client;
  }

  async get(organizationId: string): Promise<OrganizationInfo> {
    const octokit = this.client.getHttpClient();

    const response = await octokit.rest.orgs.get({ org: organizationId });

    return mapOrganizationInfo(response.data);
  }

  async list(results: PagedResults<Organization>): Promise<void> {
    const octokit = this.client.getHttpClient();

    const response = await octokit.rest.orgs.listForAuthenticatedUser({
      per_page: results.pageSize,
      page: results.pageNumber,
    });

    results.items = response.data.map(mapOrganization);

    const linkHeader = response.headers.link;
    if (linkHeader && linkHeader.includes('rel="next"')) {
      results.pageToken = 'has-next';
    } else {
      results.pageToken = undefined;
    }

    if (response.headers['x-total-count']) {
      results.count = Number(response.headers['x-total-count']);
    }
  }
}
