/* eslint-disable */
import { AxiosInstance } from 'axios';
import { PagedResults } from '@auditmation/types-core-js';
import { RoleProducerApi } from '../generated/api/RoleApi';
import { RoleDetails, User } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';

export class RoleProducerApiImpl implements RoleProducerApi {
  private httpClient: AxiosInstance;

  constructor(private client: AvigilonAltaAccessClient) {
    this.httpClient = client.getHttpClient();
  }

  async listScopeIds(results: PagedResults<string>, organizationId: string, roleId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async listUsers(results: PagedResults<User>, organizationId: string, roleId: string): Promise<void> {
    throw new Error('Not implemented');
  }

  async list(results: PagedResults<RoleDetails>, organizationId: string): Promise<void> {
    throw new Error('Not implemented');
  }
}
