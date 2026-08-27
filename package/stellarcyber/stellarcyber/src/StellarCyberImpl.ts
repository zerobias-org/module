import {
  ConnectionMetadata,
  OperationSupportStatusDef
} from '@zerobias-org/types-core-js';
import {
  CaseApi,
  StellarCyberConnector,
  wrapCaseProducer
} from '../generated/api/index.js';
import { ConnectionProfile } from '../generated/model/index.js';
import { CaseProducerImpl } from './CaseProducerImpl.js';
import { StellarCyberClient } from './StellarCyberClient.js';

export class StellarCyberImpl implements StellarCyberConnector {
  private client = new StellarCyberClient();

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

  getCaseApi(): CaseApi {
    return wrapCaseProducer(new CaseProducerImpl(this.client));
  }
}
