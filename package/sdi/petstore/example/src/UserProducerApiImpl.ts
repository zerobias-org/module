import { AxiosInstance } from 'axios';
import { NoSuchObjectError } from '@zerobias-org/types-core-js';
import { UserProducerApi } from '../generated/api/UserApi.js';
import { PetstoreUser } from '../generated/model/index.js';
import { PetstoreClient } from './PetstoreClient.js';
import { toPetstoreUser } from './Mappers.js';

/**
 * User producer — implements the generated UserProducerApi.
 *
 * Petstore identifies users by username (not numeric id).
 *
 * 404 -> NoSuchObjectError.
 */
export class UserProducerApiImpl implements UserProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: PetstoreClient) {
    this.httpClient = client.getHttpClient();
  }

  async get(username: string): Promise<PetstoreUser> {
    try {
      const response = await this.httpClient.get(`/user/${username}`);
      if (!response.data) {
        throw new NoSuchObjectError('user', username);
      }
      return toPetstoreUser(response.data);
    } catch (error: any) {
      if (error.response?.status === 404) {
        throw new NoSuchObjectError('user', username);
      }
      throw error;
    }
  }
}
