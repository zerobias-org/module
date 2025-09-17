import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { AcuProducerApi } from '../generated/api/AcuApi';
import { Acu, AcuInfo, Port } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapAcu, mapAcuInfo, mapPort } from './Mappers';

export class AcuProducerApiImpl implements AcuProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(organizationId: string, acuId: number): Promise<AcuInfo> {
    const response = await this.httpClient.get(
      `/orgs/${organizationId}/acus/${acuId.toString()}`
    );

    // Individual ACU response is directly in response.data, not nested in data.data like list responses
    const rawData = response.data;

    // For AcuInfo, we need to map the same fields as Acu but with additional extended properties
    const acuData = mapAcu(rawData);
    const acuInfo = mapAcuInfo(acuData, rawData);

    return acuInfo;
  }

  async listPorts(
    results: PagedResults<Port>,
    organizationId: string,
    acuId: number
  ): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(
      `/orgs/${organizationId}/acus/${acuId.toString()}/ports`,
      { params }
    );

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapPort) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }

  async list(results: PagedResults<Acu>, organizationId: string): Promise<void> {
    const params: Record<string, number> = {};

    // Convert pageNumber/pageSize to offset/limit
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0;
    }

    const response = await this.httpClient.get(`/orgs/${organizationId}/acus`, { params });

    // Apply mappers and set pagination info from new response structure
    results.items = response.data.data ? response.data.data.map(mapAcu) : [];
    results.count = response.data.totalCount || 0;

    // Handle pageToken if available (only for operations with pageToken in OpenAPI params)
    results.pageToken = response.headers['x-next-page-token'];
  }
}
