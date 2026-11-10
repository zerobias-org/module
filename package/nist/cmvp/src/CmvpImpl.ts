import { CertificateApi, Cmvp, wrapCertificateProducer } from '../generated/api/index.js';
import { CertificateProducerImpl } from './CertificateProducerImpl.js';
import { CmvpClient } from './CmvpClient.js';

export class CmvpImpl implements Cmvp {
  private client = new CmvpClient();

  getCertificateApi(): CertificateApi {
    return wrapCertificateProducer(new CertificateProducerImpl(this.client));
  }
}
