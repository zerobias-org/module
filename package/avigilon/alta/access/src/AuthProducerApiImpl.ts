import { NotConnectedError, NoSuchObjectError } from '@auditmation/types-core-js';
import { AuthProducerApi } from '../generated/api';
import { TokenProperties } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { toTokenProperties } from './Mappers';

export class AuthProducerApiImpl implements AuthProducerApi {
  private readonly client: AvigilonAltaAccessClient;

  constructor(client: AvigilonAltaAccessClient) {
    this.client = client;
  }

  async getTokenProperties(): Promise<TokenProperties> {
    const httpClient = this.client.getHttpClient();
    const connectionState = this.client.getConnectionState();

    if (!connectionState?.accessToken) {
      throw new NotConnectedError();
    }

    const response = await httpClient.get(`/auth/accessTokens/${connectionState.accessToken}`);

    const rawData = response.data.data || response.data;

    if (!rawData) {
      throw new NoSuchObjectError('token', connectionState.accessToken);
    }

    return toTokenProperties(rawData);
  }
}
