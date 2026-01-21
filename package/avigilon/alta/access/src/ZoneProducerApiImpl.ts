import { AxiosInstance } from 'axios';
import { PagedResults, UnexpectedError } from '@zerobias-org/types-core-js';
import { ZoneProducerApi } from '../generated/api/ZoneApi.js';
import { Zone, ZoneShare, ZoneZoneUser } from '../generated/model/index.js';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient.js';
import { toZone, toZoneShare, toZoneZoneUser } from './Mappers.js';

export class ZoneProducerApiImpl implements ZoneProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<Zone>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/zones`, { params });

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toZone(item));
    results.count = response.data.totalCount || 0;
  }

  async listZoneShares(
    results: PagedResults<ZoneShare>,
    organizationId: string,
    zoneId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/zones/${zoneId}/zoneShares`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toZoneShare(item));
    results.count = response.data.totalCount || 0;
  }

  async listZoneUsers(
    results: PagedResults<ZoneZoneUser>,
    organizationId: string,
    zoneId: string
  ): Promise<void> {
    const params: Record<string, number> = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/zones/${zoneId}/zoneUsers`,
      { params }
    );

    if (!response.data || !Array.isArray(response.data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    results.items = response.data.data.map((item) => toZoneZoneUser(item));
    results.count = response.data.totalCount || 0;
  }
}
