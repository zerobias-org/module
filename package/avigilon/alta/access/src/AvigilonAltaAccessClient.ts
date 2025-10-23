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

export class AvigilonAltaAccessClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private baseUrl = 'https://helium.prod.openpath.com';

  private accessToken: string | null = null;

  private tokenExpiresAt: Date | null = null;

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    if (!profile.email || !profile.password) {
      throw new InvalidCredentialsError();
    }

    // Create temporary client for login
    const tempClient = axios.create({
      baseURL: this.baseUrl,
      timeout: 30000,
      headers: {
        accept: 'application/json',
        'content-type': 'application/json',
      },
    });

    try {
      // Perform login to get access token
      const loginData: { email: string; password: string; mfa?: { totpCode: string } } = {
        email: profile.email.toString(),
        password: profile.password,
      };

      const totpCode = profile.totpCode?.toString().trim();
      if (totpCode) {
        loginData.mfa = { totpCode };
      }

      const loginResponse = await tempClient.post('/auth/login', loginData);

      const { data } = loginResponse.data;
      const { token, expiresAt } = data;

      if (!token || !expiresAt) {
        throw new InvalidCredentialsError();
      }

      this.accessToken = token;
      this.tokenExpiresAt = new Date(expiresAt);

      // Create authenticated client
      this.httpClient = axios.create({
        baseURL: this.baseUrl,
        timeout: 30000,
        headers: {
          Authorization: `Bearer ${this.accessToken}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
      });

      // Set up error interceptor
      this.httpClient.interceptors.response.use(
        (response) => response,
        (error) => AvigilonAltaAccessClient.handleError(error)
      );

      this.connected = true;

      // Calculate seconds until expiration
      const expiresIn = Math.max(
        0,
        Math.floor((this.tokenExpiresAt.getTime() - Date.now()) / 1000)
      );

      return {
        accessToken: this.accessToken || undefined,
        expiresIn,
      };
    } catch (error) {
      if (axios.isAxiosError(error)) {
        AvigilonAltaAccessClient.handleError(error);
      }
      throw new InvalidCredentialsError();
    }
  }

  async isConnected(): Promise<boolean> {
    if (!this.connected || !this.httpClient || !this.tokenExpiresAt) {
      return false;
    }

    // Check if token is still valid (not expired)
    return this.tokenExpiresAt.getTime() > Date.now();
  }

  async disconnect(): Promise<void> {
    this.httpClient = null;
    this.connected = false;
    this.accessToken = null;
    this.tokenExpiresAt = null;
  }

  getHttpClient(): AxiosInstance {
    if (!this.httpClient) {
      throw new NotConnectedError();
    }
    return this.httpClient;
  }

  getConnectionState(): ConnectionState | null {
    if (!this.accessToken || !this.tokenExpiresAt) {
      return null;
    }

    const expiresIn = Math.max(
      0,
      Math.floor((this.tokenExpiresAt.getTime() - Date.now()) / 1000)
    );

    return {
      accessToken: this.accessToken || undefined,
      expiresIn,
    };
  }

  async refresh(): Promise<ConnectionState> {
    if (!this.accessToken || !this.httpClient) {
      throw new NotConnectedError();
    }

    try {
      // Use current token to refresh
      const refreshResponse = await this.httpClient.post('/auth/accessTokens/refresh');

      const { data } = refreshResponse.data;
      const { token, expiresAt } = data;

      if (!token || !expiresAt) {
        throw new UnexpectedError(
          'Invalid refresh response - missing token or expiration'
        );
      }

      // Update stored token and expiration
      this.accessToken = token;
      this.tokenExpiresAt = new Date(expiresAt);

      // Update the Authorization header in the existing HTTP client
      // eslint-disable-next-line @typescript-eslint/dot-notation
      this.httpClient.defaults.headers['Authorization'] = `Bearer ${this.accessToken}`;

      // Calculate seconds until expiration
      const expiresIn = Math.max(
        0,
        Math.floor((this.tokenExpiresAt.getTime() - Date.now()) / 1000)
      );

      return {
        accessToken: this.accessToken || undefined,
        expiresIn,
      };
    } catch (error) {
      if (axios.isAxiosError(error)) {
        AvigilonAltaAccessClient.handleError(error);
      }
      throw new UnexpectedError('Token refresh failed');
    }
  }

  private static handleError = (error: AxiosError): never => {
    const status = error.response?.status || 500;
    const responseData = error.response?.data as Record<string, unknown>;
    const message = responseData?.message as string || error.message || 'Unknown error';

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
