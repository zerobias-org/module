import {
  CertificateApi,
  CmvpConnector,
  wrapCertificateProducer,
} from '../generated/api/index.js';
import { ConnectionProfile } from '../generated/model/index.js';
import { ConnectionMetadata, OperationSupportStatusDef } from '@zerobias-org/types-core-js';
import { CertificateProducerImpl } from './CertificateProducerImpl.js';
import { CmvpClient } from './CmvpClient.js';

export class CmvpImpl implements CmvpConnector {
  private client = new CmvpClient();

  async connect(connectionProfile: ConnectionProfile): Promise<void> {
    return this.client.connect(connectionProfile);
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    return this.client.disconnect();
  }

  async metadata(): Promise<ConnectionMetadata> {
    return this.client.metadata();
  }

  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    return this.client.isSupported(operationId);
  }

  getCertificateApi(): CertificateApi {
    return wrapCertificateProducer(new CertificateProducerImpl(this.client));
  }
}
