import axios, { AxiosInstance, AxiosError } from 'axios';
import {
  InvalidCredentialsError,
  InvalidInputError,
  NoSuchObjectError,
  NotConnectedError,
  PagedResults,
  RateLimitExceededError,
  UnauthorizedError,
  UnexpectedError
} from '@zerobias-org/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import { ConnectionState } from '../generated/model/index.js';

const BASE_TOKEN_URI = 'https://login.microsoftonline.com/';
const TOKEN_ENDPOINT = '/oauth2/v2.0/token';
const ARM_SCOPE = 'https://management.azure.com/.default';
const ARG_RESOURCES_URL = 'https://management.azure.com/providers/Microsoft.ResourceGraph/resources';
const ARG_API_VERSION = '2024-04-01';
const MAX_TOP_PARAM_VALUE = 1000;

/** The paged envelope returned by the Resource Graph resources endpoint. */
export interface ArgQueryResponse {
  data: Record<string, unknown>[];
  count?: number;
  totalRecords?: number;
  resultTruncated?: string | boolean;
  $skipToken?: string;
}

export class AzureResourceGraphClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private connectionProfile?: ConnectionProfile;

  private accessToken: string | null = null;

  private tokenExpiresAt: Date | null = null;

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    if (!profile.directoryId) {
      throw new InvalidCredentialsError();
    }
    if (!profile.accessToken && !(profile.clientId && profile.clientSecret)) {
      throw new InvalidCredentialsError();
    }

    this.connectionProfile = profile;

    if (profile.clientId && profile.clientSecret) {
      // compatibility/manual mode — acquire an ARM-audience token via client credentials
      await this.acquireAccessToken();
    } else {
      // oauth mode — a token was provided on the profile by the platform
      this.accessToken = `${profile.accessToken}`;
      this.tokenExpiresAt = profile.expiresIn
        ? new Date(Date.now() + Number(profile.expiresIn) * 1000)
        : null;
    }

    if (!this.accessToken) {
      throw new InvalidCredentialsError();
    }

    this.httpClient = axios.create({
      timeout: 60_000,
      headers: {
        Authorization: `Bearer ${this.accessToken}`,
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
    });

    // Probe: a minimal one-row tenant-scope query proves both the token audience
    // and that the principal can read at least something (ARG 403s when nothing
    // at all is readable).
    await this.query('Resources', undefined, { $top: 1, resultFormat: 'objectArray' }, true);
    this.connected = true;

    return this.getConnectionState() as ConnectionState;
  }

  async isConnected(): Promise<boolean> {
    if (!this.connected || !this.httpClient) {
      return false;
    }
    if (this.tokenExpiresAt) {
      return this.tokenExpiresAt.getTime() > Date.now();
    }
    return true;
  }

  async disconnect(): Promise<void> {
    this.httpClient = null;
    this.connected = false;
    this.accessToken = null;
    this.tokenExpiresAt = null;
    delete this.connectionProfile;
  }

  async refresh(): Promise<ConnectionState> {
    // Client-credentials tokens are not refreshed — they are re-acquired.
    if (!this.connectionProfile?.clientId || !this.connectionProfile?.clientSecret) {
      throw new NotConnectedError();
    }
    await this.acquireAccessToken();
    if (this.httpClient && this.accessToken) {
      this.httpClient.defaults.headers.Authorization = `Bearer ${this.accessToken}`;
    }
    return this.getConnectionState() as ConnectionState;
  }

  getConnectionState(): ConnectionState | null {
    if (!this.accessToken) {
      return null;
    }
    const expiresIn = this.tokenExpiresAt
      ? Math.max(0, Math.floor((this.tokenExpiresAt.getTime() - Date.now()) / 1000))
      : undefined;
    return {
      accessToken: this.accessToken,
      expiresIn,
    } as ConnectionState;
  }

  /**
   * Executes one Resource Graph query call.
   *
   * @param queryString - the KQL query (e.g. `Resources` or `ResourceContainers`)
   * @param subscriptions - optional subscription IDs to scope to; tenant scope when omitted
   * @param options - the ARG options bag ($top/$skip/$skipToken/resultFormat)
   * @param connecting - allow the call before `connected` flips (connection probe)
   */
  async query(
    queryString: string,
    subscriptions: string[] | undefined,
    options: Record<string, unknown>,
    connecting = false
  ): Promise<ArgQueryResponse> {
    if (!this.httpClient || (!connecting && !this.connected)) {
      throw new NotConnectedError();
    }

    const body: Record<string, unknown> = { query: queryString, options };
    if (subscriptions && subscriptions.length > 0) {
      body.subscriptions = subscriptions;
    }

    try {
      const response = await this.httpClient.post(
        `${ARG_RESOURCES_URL}?api-version=${ARG_API_VERSION}`,
        body
      );
      return response.data as ArgQueryResponse;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        AzureResourceGraphClient.handleError(error);
      }
      throw new UnexpectedError(`${error}`);
    }
  }

  /**
   * Populates a PagedResults from an ARG table using the documented paging
   * contract: options.$top (max 1000) + options.$skip on the first page, then
   * options.$skipToken (which overrides $skip/$top) on subsequent pages; stop
   * when the response carries no $skipToken.
   */
  async listQuery<T>(
    results: PagedResults<T>,
    table: string,
    subscriptions: string[] | undefined,
    mapper: (row: Record<string, unknown>) => T
  ): Promise<void> {
    if (results.pageSize > MAX_TOP_PARAM_VALUE) {
      throw new InvalidInputError(
        `pageSize cannot be greater than the maximum allowed value (${MAX_TOP_PARAM_VALUE})`,
        results.pageSize
      );
    }

    const options: Record<string, unknown> = {
      resultFormat: 'objectArray',
      $top: results.pageSize,
    };
    if (results.pageToken) {
      // $skipToken overrides $skip/$top server-side and encodes the continuation
      options.$skipToken = results.pageToken;
    } else if (results.pageNumber > 1) {
      options.$skip = (results.pageNumber - 1) * results.pageSize;
    }

    const response = await this.query(table, subscriptions, options);

    results.items = (response.data || []).map(mapper);
    if (response.totalRecords !== undefined) {
      results.count = response.totalRecords;
    }
    // Absent $skipToken means the last page — pageToken clears and paging stops.
    results.pageToken = response.$skipToken;
  }

  private async acquireAccessToken(): Promise<void> {
    if (!this.connectionProfile?.clientId || !this.connectionProfile?.clientSecret) {
      throw new InvalidCredentialsError();
    }
    const tokenUri = `${BASE_TOKEN_URI}${this.connectionProfile.directoryId}${TOKEN_ENDPOINT}`;
    const postData = new URLSearchParams({
      client_id: `${this.connectionProfile.clientId}`,
      client_secret: `${this.connectionProfile.clientSecret}`,
      scope: ARM_SCOPE,
      grant_type: 'client_credentials',
    });

    let response;
    try {
      response = await axios.post(tokenUri, postData.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        timeout: 60_000,
      });
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 400) {
        // AADSTS errors (bad tenant, bad client, bad secret) come back as 400
        throw new InvalidCredentialsError();
      }
      if (axios.isAxiosError(error)) {
        AzureResourceGraphClient.handleError(error);
      }
      throw new UnexpectedError(`${error}`);
    }

    const accessToken = response?.data?.access_token;
    if (!accessToken) {
      throw new InvalidCredentialsError();
    }
    this.accessToken = accessToken;
    const expiresIn = Number(response?.data?.expires_in);
    this.tokenExpiresAt = Number.isFinite(expiresIn)
      ? new Date(Date.now() + expiresIn * 1000)
      : null;
  }

  private static handleError = (error: AxiosError): never => {
    const status = error.response?.status || 500;
    const responseData = error.response?.data as Record<string, any> | undefined;
    const message = responseData?.error?.message
      || responseData?.message
      || error.message
      || 'Unknown error';

    switch (status) {
      case 400: {
        throw new InvalidInputError('request', message);
      }
      case 401: {
        throw new InvalidCredentialsError();
      }
      case 403: {
        // ARG returns 403 when the principal can read no objects at all;
        // partial access yields 200 with unreadable rows omitted.
        throw new UnauthorizedError();
      }
      case 404: {
        throw new NoSuchObjectError('resource', 'unknown');
      }
      case 429: {
        // Throttled — quota headers: x-ms-user-quota-remaining /
        // x-ms-user-quota-resets-after describe when the quota window resets.
        throw new RateLimitExceededError();
      }
      default: {
        throw new UnexpectedError(message, status);
      }
    }
  };
}
