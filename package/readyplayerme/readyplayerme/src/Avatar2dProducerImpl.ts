import { Avatar2dProducerApi } from '../generated/api/index.js';
import { RequestFile, Avatar2DExpressionDef, Avatar2DPoseDef, Avatar2DCameraDef } from '../generated/model/index.js';
import { toRequestFile } from './mappers.js';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient.js';
import { handleAxiosError } from './util.js';

export class Avatar2dProducerImpl implements Avatar2dProducerApi {
  constructor(private client: ReadyPlayerMeClient) {}

  async get2DRender(
    avatarId: string,
    expression?: Avatar2DExpressionDef,
    pose?: Avatar2DPoseDef,
    blendShapes?: string,
    camera?: Avatar2DCameraDef,
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
          pose: pose && pose.toString(),
        },
      })
      .catch(handleAxiosError);

    return toRequestFile(data, avatarId);
  }
}
