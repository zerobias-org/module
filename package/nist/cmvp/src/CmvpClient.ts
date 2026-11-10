import axios, { AxiosInstance } from 'axios';

export const DEFAULT_BASE_URL = 'https://csrc.nist.gov';
export const LISTING_PATH = '/projects/cryptographic-module-validation-program/validated-modules/search/all';
export const DETAIL_PATH_PREFIX = '/projects/cryptographic-module-validation-program/certificate/';

/**
 * HTTP client for the public CMVP registry. The registry needs no credentials,
 * so the module has no connection profile and no connect() lifecycle: the
 * client is ready as soon as it is constructed.
 */
export class CmvpClient {
  public readonly baseUrl: string;

  public readonly httpClient: AxiosInstance;

  constructor(baseUrl: string = DEFAULT_BASE_URL) {
    this.baseUrl = baseUrl;
    this.httpClient = axios.create({
      baseURL: baseUrl,
      timeout: 30000,
      headers: { 'User-Agent': 'zerobias-module-nist-cmvp' },
      validateStatus: (status) => status >= 200 && status < 300,
    });
  }
}
