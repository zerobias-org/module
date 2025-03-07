import { Avatar2dApi, Avatar2dProducerApi } from '../generated/api';
import { RequestFile } from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';

export class Avatar2dProducerImpl implements Avatar2dProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async get2DRender(
    avatarId: string,
    format: Avatar2dApi.FormatEnumDef,
    expression?: Avatar2dApi.ExpressionEnumDef,
    pose?: Avatar2dApi.PoseEnumDef,
    blendShapes?: string,
    camera?: Avatar2dApi.CameraEnumDef,
    background?: string,
    quality?: number,
    size?: number,
    uat?: Date,
    cacheControl?: boolean
  ): Promise<RequestFile> {
    throw new Error('Method not implemented.');
  }

}
