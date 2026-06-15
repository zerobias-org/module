import { AxiosInstance } from 'axios';
import { NoSuchObjectError, UnexpectedError } from '@zerobias-org/types-core-js';
import { PetProducerApi } from '../generated/api/PetApi.js';
import { Pet, PetStatusDef } from '../generated/model/index.js';
import { PetstoreClient } from './PetstoreClient.js';
import { toPet } from './Mappers.js';

/**
 * Pet producer — implements the generated PetProducerApi.
 *
 * Producers ONLY call PetstoreClient + map response.
 *
 * api.yml uses lowercase enums (available/pending/sold) so status.toString()
 * already yields lowercase; .toLowerCase() is a defensive idempotent guard.
 *
 * 404 -> NoSuchObjectError (no centralized axios interceptor; each producer
 * self-handles).
 */
export class PetProducerApiImpl implements PetProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: PetstoreClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(status: PetStatusDef): Promise<Array<Pet>> {
    // Petstore wire enum is lowercase; .toString() returns EnumValue.value
    // ('available'/'pending'/'sold'). .toLowerCase() is a defensive idempotent
    // guard.
    const apiStatus = status.toString().toLowerCase();
    const response = await this.httpClient.get('/pet/findByStatus', { params: { status: apiStatus } });
    if (!Array.isArray(response.data)) {
      throw new UnexpectedError('Invalid response: expected array');
    }
    return response.data.map(toPet);
  }

  async get(petId: string): Promise<Pet> {
    try {
      const response = await this.httpClient.get(`/pet/${petId}`);
      if (!response.data) {
        throw new NoSuchObjectError('pet', petId);
      }
      return toPet(response.data);
    } catch (error: any) {
      if (error.response?.status === 404) {
        throw new NoSuchObjectError('pet', petId);
      }
      throw error;
    }
  }
}
