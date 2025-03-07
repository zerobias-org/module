import { UserProducerApi } from '../generated/api';
import { CreateUserRequest, CreateUserResponse } from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';

export class UserProducerImpl implements UserProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async create(createUserRequest: CreateUserRequest): Promise<CreateUserResponse> {
    throw new Error('Method not implemented.');
  }
}
