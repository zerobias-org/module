import { DateTime, NoSuchObjectError, PagedResults } from '@zerobias-org/types-core-js';
import { CertificateProducerApi } from '../generated/api/index.js';
import { Certificate, CertificateSummary } from '../generated/model/index.js';
import { CmvpClient, DETAIL_PATH_PREFIX, LISTING_PATH } from './CmvpClient.js';
import { parseDetailPage, parseListingPage } from './mappers.js';
import { handleAxiosError } from './util.js';

export class CertificateProducerImpl implements CertificateProducerApi {
  constructor(private client: CmvpClient) {}

  async list(results: PagedResults<CertificateSummary>): Promise<void> {
    let html: string;
    try {
      const response = await this.client.httpClient.get<string>(LISTING_PATH);
      html = response.data;
    } catch (error: any) {
      handleAxiosError(error, 'cmvp-listing', 'all');
    }

    const all = parseListingPage(html, this.client.baseUrl);

    // The registry returns one flat listing with no native pagination, so the
    // page is sliced client-side (pagination skill, Approach 2).
    results.ingest(all, results.pageNumber, results.pageSize);
  }

  async get(certificateNumber: string): Promise<Certificate> {
    let html: string;
    try {
      const response = await this.client.httpClient.get<string>(`${DETAIL_PATH_PREFIX}${certificateNumber}`);
      html = response.data;
    } catch (error: any) {
      handleAxiosError(error, 'cmvp-certificate', certificateNumber);
    }

    if (!html || !html.includes('Certificate #')) {
      throw new NoSuchObjectError('cmvp-certificate', certificateNumber);
    }

    return parseDetailPage(html, certificateNumber, this.client.baseUrl, new DateTime(new Date()));
  }
}
