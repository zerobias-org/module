// Fragment for src/<Class>Impl.ts: the six DataProducer getters.
// The Api types and wrap*Producer factories come from the published interface
// package; the producer interfaces DataProducerImpl implements come from the
// module's own generated/ folder.
import {
  ObjectsApi, SchemasApi, CollectionsApi, DocumentsApi, FunctionsApi, BinaryApi,
  wrapObjectsProducer, wrapSchemasProducer, wrapCollectionsProducer,
  wrapDocumentsProducer, wrapFunctionsProducer, wrapBinaryProducer,
} from '@zerobias-org/module-interface-dataproducer';
import { DataProducerImpl } from './impl/dataproducer/DataProducerImpl.js';

// inside the module Impl class:

  private dataProducer?: DataProducerImpl;

  private getDataProducer(): DataProducerImpl {
    this.dataProducer ??= new DataProducerImpl(this);
    return this.dataProducer;
  }

  getObjectsApi(): ObjectsApi { return wrapObjectsProducer(this.getDataProducer()); }
  getSchemasApi(): SchemasApi { return wrapSchemasProducer(this.getDataProducer()); }
  getCollectionsApi(): CollectionsApi { return wrapCollectionsProducer(this.getDataProducer()); }
  getDocumentsApi(): DocumentsApi { return wrapDocumentsProducer(this.getDataProducer()); }
  getFunctionsApi(): FunctionsApi { return wrapFunctionsProducer(this.getDataProducer()); }
  getBinaryApi(): BinaryApi { return wrapBinaryProducer(this.getDataProducer()); }
