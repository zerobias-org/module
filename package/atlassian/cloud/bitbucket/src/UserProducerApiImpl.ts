import { AxiosInstance } from 'axios';
import { UserProducerApi } from '../generated/api/UserApi';
import { User } from '../generated/model';
import { BitbucketClient } from './BitbucketClient';
import { toUser } from './Mappers';

export class UserProducerApiImpl implements UserProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: BitbucketClient) {
    this.httpClient = client.getHttpClient();
  }

  async getCurrentUser(): Promise<User> {
    const response = await this.httpClient.get('/user');
    return toUser(response.data);
  }
}
