import { AxiosInstance } from 'axios';
import { NoSuchObjectError } from '@zerobias-org/types-core-js';
import { StoreProducerApi } from '../generated/api/StoreApi.js';
import { Order } from '../generated/model/index.js';
import { PetstoreClient } from './PetstoreClient.js';
import { toOrder, toInventory } from './Mappers.js';

/**
 * Store producer — implements the generated StoreProducerApi.
 *
 * getInventory returns `{ [key: string]: number }` (the Petstore status->count
 * map). The Inventory schema is represented inline (alias-to-map, no model
 * class). The toInventory mapper passes through with int coercion.
 *
 * 404 -> NoSuchObjectError.
 */
export class StoreProducerApiImpl implements StoreProducerApi {
  private readonly httpClient: AxiosInstance;

  constructor(client: PetstoreClient) {
    this.httpClient = client.getHttpClient();
  }

  async getOrder(orderId: string): Promise<Order> {
    try {
      const response = await this.httpClient.get(`/store/order/${orderId}`);
      if (!response.data) {
        throw new NoSuchObjectError('order', orderId);
      }
      return toOrder(response.data);
    } catch (error: any) {
      if (error.response?.status === 404) {
        throw new NoSuchObjectError('order', orderId);
      }
      throw error;
    }
  }

  async getInventory(): Promise<{ [key: string]: number }> {
    const response = await this.httpClient.get('/store/inventory');
    return toInventory(response.data);
  }
}
