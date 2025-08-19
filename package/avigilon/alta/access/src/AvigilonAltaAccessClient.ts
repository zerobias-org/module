import axios, { AxiosInstance, AxiosError } from 'axios';
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';

export class AvigilonAltaAccessClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private baseUrl = 'https://api.openpath.com';

  async connect(profile: ConnectionProfile): Promise<void> {
    if (!profile.apiToken) {
      throw new InvalidCredentialsError();
    }

    // Use URL from profile if provided, otherwise use default baseUrl
    const apiUrl = profile.url ? profile.url.toString() : this.baseUrl;

    this.httpClient = axios.create({
      baseURL: apiUrl,
      timeout: 30000,
      headers: {
        Authorization: `Bearer ${profile.apiToken}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
    });

    // Set up error interceptor
    this.httpClient.interceptors.response.use(
      (response) => response,
      (error) => this.handleError(error)
    );

    this.connected = true;
  }

  async isConnected(): Promise<boolean> {
    return this.connected && this.httpClient !== null;
  }

  async disconnect(): Promise<void> {
    this.httpClient = null;
    this.connected = false;
  }

  getHttpClient(): AxiosInstance {
    if (!this.httpClient) {
      throw new UnexpectedError('Client not connected. Call connect() first.');
    }
    return this.httpClient;
  }

  private handleError = (error: AxiosError): never => {
    const status = error.response?.status || 500;
    const responseData = error.response?.data as any;
    const message = responseData?.message || error.message || 'Unknown error';

    switch (status) {
      case 401:
        throw new InvalidCredentialsError();
      case 403:
        if (message.toLowerCase().includes('rate limit')) {
          throw new RateLimitExceededError();
        }
        throw new UnauthorizedError();
      case 404:
        throw new NoSuchObjectError('resource', 'unknown');
      case 400:
      case 422:
        throw new InvalidInputError('request', message);
      case 429:
        throw new RateLimitExceededError();
      default:
        throw new UnexpectedError(message, status);
    }
  }
}
