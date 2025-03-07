import { PagedResults } from '@auditmation/types-core-js';
import {
  ApplicationApi,
  ApplicationProducerApi
} from '../generated/api';
import { AssetInfo } from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';

export class ApplicationProducerImpl implements ApplicationProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async listAssets(results: PagedResults<AssetInfo>,
    appId: string,
    name?: string,
    organizationId?: string,
    type?: Array<ApplicationApi.TypeEnumDef>,
    gender?: Array<ApplicationApi.GenderEnumDef>,
    ids?: Array<string>,
    applicationIds?: Array<string>
  ): Promise<void> {
    throw new Error('Method not implemented.');
  }

}
