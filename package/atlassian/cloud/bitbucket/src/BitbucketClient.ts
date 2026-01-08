import axios, { AxiosInstance, AxiosError } from 'axios';
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  RateLimitExceededError,
  UnexpectedError,
  NotConnectedError
} from '@auditmation/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { ConnectionState } from '../generated/model';

export class BitbucketClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private baseUrl = 'https://api.bitbucket.org/2.0';

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    if (!profile.username || !profile.password) {
      throw new InvalidCredentialsError();
    }

    const authHeader = `Basic ${Buffer.from(`${profile.username}:${profile.password}`).toString('base64')}`;

    this.httpClient = axios.create({
      baseURL: this.baseUrl,
      timeout: 30000,
      headers: {
        Authorization: authHeader,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
    });

    this.httpClient.interceptors.response.use(
      (response) => response,
      (error) => BitbucketClient.handleError(error)
    );

    try {
      await this.httpClient.get('/user');
      this.connected = true;

      return {
        expiresIn: -1,
      };
    } catch (error) {
      this.httpClient = null;
      this.connected = false;

      if (axios.isAxiosError(error)) {
        BitbucketClient.handleError(error);
      }
      throw new InvalidCredentialsError();
    }
  }

  async isConnected(): Promise<boolean> {
    if (!this.connected || !this.httpClient) {
      return false;
    }

    try {
      await this.httpClient.get('/user');
      return true;
    } catch {
      return false;
    }
  }

  async disconnect(): Promise<void> {
    this.httpClient = null;
    this.connected = false;
  }

  getHttpClient(): AxiosInstance {
    if (!this.httpClient) {
      throw new NotConnectedError();
    }
    return this.httpClient;
  }

  private static handleError = (error: AxiosError): never => {
    const status = error.response?.status || 500;
    const responseData = error.response?.data as Record<string, unknown>;
    const errorObj = responseData?.error as Record<string, unknown> | undefined;
    const message = (errorObj?.message as string) ||
      (responseData?.message as string) ||
      error.message ||
      'Unknown error';

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
      case 429:
        throw new RateLimitExceededError();
      default:
        throw new UnexpectedError(message, status);
    }
  };
}
