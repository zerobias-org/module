import { UserProducerApi } from '../generated/api/index.js';
import { CreateUserRequest, CreateUserResponse, TokenResponse } from '../generated/model/index.js';
import { toCreateUserResponse, toTokenResponse } from './mappers.js';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient.js';
import { handleAxiosError } from './util.js';

export class UserProducerImpl implements UserProducerApi {
  constructor(private client: ReadyPlayerMeClient) {}

  async getToken(userId: string, partner: string): Promise<TokenResponse> {
    const { apiClient } = this.client;

    const { data } = await apiClient
      .request({
        url: '/auth/token',
        method: 'get',
        params: {
          userId,
          partner,
        },
      })
      .catch(handleAxiosError);

    return toTokenResponse(data);
  }

  async create(createUserRequest: CreateUserRequest): Promise<CreateUserResponse> {
    const { apiClient } = this.client;

    const { data } = await apiClient.request(
      {
        url: '/users',
        method: 'post',
        headers: { 'Content-Type': 'application/json' },
        data: JSON.stringify(createUserRequest),
      }
    ).catch(handleAxiosError);

    return toCreateUserResponse(data);
  }
}
