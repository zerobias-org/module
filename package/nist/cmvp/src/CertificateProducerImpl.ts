import { NoSuchObjectError } from '@zerobias-org/types-core-js';
import { CertificateProducerApi } from '../generated/api/index.js';
import { Certificate, CertificateSummary } from '../generated/model/index.js';
import { CmvpClient, DETAIL_PATH_PREFIX, LISTING_PATH } from './CmvpClient.js';
import { parseDetailPage, parseListingPage } from './mappers.js';
import { handleAxiosError } from './util.js';

export class CertificateProducerImpl implements CertificateProducerApi {
  constructor(private client: CmvpClient) {}

  async list(pageNumber = 1, pageSize = 50): Promise<Array<CertificateSummary>> {
    let html: string;
    try {
      const response = await this.client.httpClient.get<string>(LISTING_PATH);
      html = response.data;
    } catch (error: any) {
      handleAxiosError(error, 'cmvp-listing', 'all');
    }

    const all = parseListingPage(html, this.client.baseUrl);

    // No native pagination support (see .claude/skills/pagination/SKILL.md
    // "Approach 2") — the registry returns one flat listing, sliced client-side.
    const start = (pageNumber - 1) * pageSize;
    return all.slice(start, start + pageSize);
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

    return parseDetailPage(html, certificateNumber, this.client.baseUrl, new Date());
  }
}
