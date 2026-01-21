import { Avatar3dProducerApi } from '../generated/api/index.js';
import { RequestFile, Avatar3DQualityDef, Avatar3DTextureAtlasDef, Avatar3DPoseDef, Avatar3DTextureFormatDef } from '../generated/model/index.js';
import { toRequestFile } from './mappers.js';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient.js';
import { handleAxiosError } from './util.js';

export class Avatar3dProducerImpl implements Avatar3dProducerApi {
  constructor(private client: ReadyPlayerMeClient) {}

  async get3DModel(
    avatarId: string,
    quality?: Avatar3DQualityDef,
    textureSizeLimit?: number,
    textureQuality?: Avatar3DQualityDef,
    textureAtlas?: Avatar3DTextureAtlasDef,
    textureChannels?: string,
    morphTargets?: string,
    useDracoMeshCompression?: boolean,
    useQuantizeMeshOptCompression?: boolean,
    pose?: Avatar3DPoseDef,
    useHands?: boolean,
    textureFormat?: Avatar3DTextureFormatDef,
    lod?: number
  ): Promise<RequestFile> {
    const { modelClient } = this.client;

    const { data } = await modelClient
      .request({
        url: `/${avatarId}`,
        method: 'get',
        params: {
          quality: quality && quality.toString(),
          textureSizeLimit,
          textureQuality: textureQuality && textureQuality.toString(),
          textureAtlas: textureAtlas && textureAtlas.toString(),
          textureChannels,
          morphTargets,
          useDracoMeshCompression,
          useQuantizeMeshOptCompression,
          pose: pose && pose.toString(),
          useHands,
          textureFormat: textureFormat && textureFormat.toString(),
          lod,
        },
      })
      .catch(handleAxiosError);

    return toRequestFile(data, avatarId);
  }
}
