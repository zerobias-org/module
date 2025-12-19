import axios, { AxiosInstance, AxiosError } from 'axios';
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
  NotConnectedError
} from '@auditmation/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import { ConnectionState } from '../generated/model';

export class AtlassianCloudBitbucketClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private readonly baseUrl = 'https://api.bitbucket.org/2.0';

  private accessToken: string | null = null;

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    if (!profile.email || !profile.apiToken) {
      throw new InvalidCredentialsError();
    }

    const credentials = `${profile.email}:${profile.apiToken}`;
    const encodedCredentials = Buffer.from(credentials).toString('base64');

    this.httpClient = axios.create({
      baseURL: this.baseUrl,
      timeout: 30000,
      headers: {
        Authorization: `Basic ${encodedCredentials}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
    });

    this.httpClient.interceptors.response.use(
      (response) => response,
      (error) => AtlassianCloudBitbucketClient.handleError(error)
    );

    try {
      await this.httpClient.get('/user');

      this.accessToken = encodedCredentials;
      this.connected = true;

      const oneYearInSeconds = 365 * 24 * 60 * 60;

      return {
        accessToken: this.accessToken,
        expiresIn: oneYearInSeconds,
      };
    } catch (error) {
      this.httpClient = null;
      this.connected = false;
      this.accessToken = null;

      if (axios.isAxiosError(error)) {
        AtlassianCloudBitbucketClient.handleError(error);
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
    this.accessToken = null;
  }

  getHttpClient(): AxiosInstance {
    if (!this.httpClient) {
      throw new NotConnectedError();
    }
    return this.httpClient;
  }

  getConnectionState(): ConnectionState | null {
    if (!this.accessToken) {
      return null;
    }

    const oneYearInSeconds = 365 * 24 * 60 * 60;

    return {
      accessToken: this.accessToken,
      expiresIn: oneYearInSeconds,
    };
  }

  private static handleError = (error: AxiosError): never => {
    const status = error.response?.status || 500;
    const responseData = error.response?.data as Record<string, unknown>;
    const errorObj = responseData?.error as Record<string, unknown> | undefined;
    const message = errorObj?.message as string
      || responseData?.message as string
      || error.message
      || 'Unknown error';

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
  };
}
