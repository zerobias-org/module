import axios, { AxiosInstance, AxiosError } from 'axios';
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
  NotConnectedError
} from '@zerobias-org/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile.js';
import { ConnectionState } from '../generated/model/index.js';

/** Azure AD (Entra ID) authority host — the tenant segment comes from directoryId. */
const DEFAULT_LOGIN_BASE_URL = 'https://login.microsoftonline.com';

/** Azure Resource Manager endpoint Azure Resource Graph lives under. */
const ARM_BASE_URL = 'https://management.azure.com';

/** Token audience for ARM (client-credentials scope). */
const ARM_SCOPE = 'https://management.azure.com/.default';

/** Azure Resource Graph query endpoint — api-version is PINNED to the 2024-04-01 stable release. */
const ARG_QUERY_PATH = '/providers/Microsoft.ResourceGraph/resources';
const ARG_API_VERSION = '2024-04-01';

/** ARG per-user quota headers surfaced on throttling (HTTP 429). */
const QUOTA_REMAINING_HEADER = 'x-ms-user-quota-remaining';
const QUOTA_RESETS_AFTER_HEADER = 'x-ms-user-quota-resets-after';

/** Options accepted by the ARG query endpoint (subset used by this module). */
export interface ArgQueryOptions {
  /** Page size — ARG caps $top at 1000. */
  $top?: number;
  /** Rows to skip — first page only; ignored by ARG when $skipToken is present. */
  $skip?: number;
  /** Continuation token from a previous response; overrides $skip/$top scope. */
  $skipToken?: string;
  /** Response shape — this module always queries objectArray. */
  resultFormat?: 'objectArray' | 'table';
}

/** ARG query request body. */
export interface ArgQueryRequest {
  query: string;
  subscriptions?: string[];
  managementGroups?: string[];
  options?: ArgQueryOptions;
}

/** ARG query response envelope (2024-04-01, resultFormat objectArray). */
export interface ArgQueryResponse {
  totalRecords?: number;
  count?: number;
  resultTruncated?: string;
  $skipToken?: string;
  data: Record<string, unknown>[];
}

export class AzureResourceGraphClient {
  private httpClient: AxiosInstance | null = null;

  private connected = false;

  private accessToken: string | null = null;

  private tokenExpiresAt: Date | null = null;

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    // The core oauthClientProfile carries client_id / client_secret (RFC 6749
    // naming); accept camelCase aliases defensively — the node→module boundary
    // has delivered both casings before (see msgraph precedent).
    const raw = profile as unknown as Record<string, unknown>;
    const clientId = (raw.client_id ?? raw.clientId) as string | undefined;
    const clientSecret = (raw.client_secret ?? raw.clientSecret) as string | undefined;
    const directoryId = raw.directoryId as string | undefined;

    if (!clientId || !clientSecret || !directoryId) {
      throw new InvalidCredentialsError();
    }

    const loginBaseUrl = (raw.url as string | undefined)?.toString().replace(/\/$/, '')
      || DEFAULT_LOGIN_BASE_URL;

    const tokenClient = axios.create({
      baseURL: loginBaseUrl,
      timeout: 30_000,
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
    });

    try {
      const form = new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: clientId,
        client_secret: clientSecret,
        scope: ARM_SCOPE,
      });

      const tokenResponse = await tokenClient.post(
        `/${directoryId}/oauth2/v2.0/token`,
        form.toString()
      );

      const { access_token: accessToken, expires_in: expiresIn } = tokenResponse.data ?? {};

      if (!accessToken || !expiresIn) {
        throw new InvalidCredentialsError();
      }

      this.accessToken = accessToken;
      this.tokenExpiresAt = new Date(Date.now() + Number(expiresIn) * 1000);

      this.httpClient = axios.create({
        baseURL: ARM_BASE_URL,
        timeout: 60_000,
        headers: {
          Authorization: `Bearer ${this.accessToken}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
      });

      this.httpClient.interceptors.response.use(
        (response) => response,
        (error) => AzureResourceGraphClient.handleError(error)
      );

      this.connected = true;

      return {
        accessToken: this.accessToken || undefined,
        expiresIn: Math.max(
          0,
          Math.floor((this.tokenExpiresAt.getTime() - Date.now()) / 1000)
        ),
      };
    } catch (error) {
      if (error instanceof InvalidCredentialsError) {
        throw error;
      }
      if (axios.isAxiosError(error)) {
        const status = error.response?.status;
        if (status === 400 || status === 401) {
          // AADSTS invalid client / secret / tenant all surface here.
          throw new InvalidCredentialsError();
        }
        AzureResourceGraphClient.handleError(error);
      }
      throw new InvalidCredentialsError();
    }
  }

  async isConnected(): Promise<boolean> {
    if (!this.connected || !this.httpClient || !this.tokenExpiresAt) {
      return false;
    }
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

  /**
   * Run an Azure Resource Graph query (api-version 2024-04-01, objectArray).
   * Tenant scope by default — subscriptions / managementGroups narrow it.
   */
  async query(request: ArgQueryRequest): Promise<ArgQueryResponse> {
    const httpClient = this.getHttpClient();

    const body: ArgQueryRequest = {
      ...request,
      options: {
        resultFormat: 'objectArray',
        ...request.options,
      },
    };

    const response = await httpClient.post(ARG_QUERY_PATH, body, {
      params: { 'api-version': ARG_API_VERSION },
    });

    const data = response.data as ArgQueryResponse | undefined;
    if (!data || !Array.isArray(data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    return data;
  }

  private static handleError = (error: AxiosError): never => {
    const status = error.response?.status || 500;
    const responseData = error.response?.data as Record<string, unknown> | undefined;
    const errorObject = responseData?.error as Record<string, unknown> | undefined;
    const message = (errorObject?.message as string)
      || (responseData?.message as string)
      || error.message
      || 'Unknown error';

    switch (status) {
      case 401: {
        throw new UnauthorizedError();
      }
      case 403: {
        // ARG returns 403 only when the principal can read NOTHING in scope.
        throw new UnauthorizedError();
      }
      case 404: {
        throw new NoSuchObjectError('resource', 'unknown');
      }
      case 400:
      case 422: {
        throw new InvalidInputError('request', message);
      }
      case 429: {
        // Azure Resource Graph per-user throttling; quota headers say when it resets.
        const resetsAfter = error.response?.headers?.[QUOTA_RESETS_AFTER_HEADER];
        const remaining = error.response?.headers?.[QUOTA_REMAINING_HEADER];
        void resetsAfter;
        void remaining;
        throw new RateLimitExceededError();
      }
      default: {
        throw new UnexpectedError(message, status);
      }
    }
  };
}
