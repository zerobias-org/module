import { AuthProducerApi } from '../generated/api';
import { TokenProperties } from '../generated/model';
import { AvigilonAltaAccessClient } from './AvigilonAltaAccessClient';
import { mapTokenProperties } from './Mappers';

export class AuthProducerApiImpl implements AuthProducerApi {
  private client: AvigilonAltaAccessClient;

  constructor(client: AvigilonAltaAccessClient) {
    this.client = client;
  }

  async getTokenProperties(): Promise<TokenProperties> {
    const httpClient = this.client.getHttpClient();
    const connectionState = this.client.getConnectionState();
    
    if (!connectionState?.accessToken) {
      throw new Error('No access token available');
    }

    const response = await httpClient.get(`/auth/accessTokens/${connectionState.accessToken}`);
    
    const { data } = response.data;
    
    return mapTokenProperties(data);
  }
}