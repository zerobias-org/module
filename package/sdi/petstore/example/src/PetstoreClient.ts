import axios, { AxiosInstance } from 'axios';
import { NotConnectedError } from '@zerobias-org/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import { ConnectionState } from '../generated/model/index.js';

/**
 * Petstore connection client (axios-based, anonymous-friendly).
 *
 * Petstore is anonymous; the optional apiKey is sent as the Petstore-canonical
 * `api_key` header (snake_case, per the live OpenAPI spec) only when provided.
 * There is no token lifecycle and no login round-trip.
 *
 * Thin axios wrapper — NO response interceptor; producers self-handle 404 via
 * try/catch and throw NoSuchObjectError. NO retry/backoff.
 */
export class PetstoreClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private baseUrl = 'https://petstore3.swagger.io/api/v3';

  private apiKey: string | undefined;

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    this.apiKey = profile.apiKey;

    const headers: Record<string, string> = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    };
    if (this.apiKey) {
      // Petstore's canonical auth header is snake_case (verified via
      // https://petstore3.swagger.io/api/v3/openapi.json — securitySchemes.api_key.name).
      headers.api_key = this.apiKey;
    }

    this.httpClient = axios.create({
      baseURL: this.baseUrl,
      timeout: 30000,
      headers,
    });

    this.connected = true;

    // baseConnectionState has no fields.
    return {} as ConnectionState;
  }

  async refresh(): Promise<ConnectionState> {
    // No-op: Petstore is anonymous and has no token to refresh.
    return {} as ConnectionState;
  }

  async isConnected(): Promise<boolean> {
    return this.connected && this.httpClient !== null;
  }

  async disconnect(): Promise<void> {
    this.httpClient = null;
    this.connected = false;
    this.apiKey = undefined;
  }

  getHttpClient(): AxiosInstance {
    if (!this.httpClient) {
      throw new NotConnectedError();
    }
    return this.httpClient;
  }
}
