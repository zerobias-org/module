import { Avatar2dApi, Avatar2dProducerApi } from '../generated/api';
import { RequestFile } from '../generated/model';
import { toRequestFile } from './mappers';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';
import { handleAxiosError } from './util';

export class Avatar2dProducerImpl implements Avatar2dProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async get2DRender(
    avatarId: string,
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
    const { modelClient } = this.client;

    const { data } = await modelClient
      .request({
        url: `/${avatarId}`,
        method: 'get',
        params: {
          blendShapes,
          cacheControl,
          uat,
          size,
          quality,
          camera: camera && camera.toString(),
          background,
          expression: expression && expression.toString(),
          pose: pose && pose.toString()
        }
      })
      .catch(handleAxiosError);

    return toRequestFile(data, avatarId);
  }
}
