import { Avatar3dApi, Avatar3dProducerApi } from '../generated/api';
import { RequestFile } from '../generated/model';
import { toRequestFile } from './mappers';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';
import { handleAxiosError } from './util';

export class Avatar3dProducerImpl implements Avatar3dProducerApi {
  constructor(private client: ReadyPlayerMeClient) { }

  async get3DModel(
    avatarId: string,
    quality?: Avatar3dApi.QualityEnumDef,
    textureSizeLimit?: number,
    textureQuality?: Avatar3dApi.TextureQualityEnumDef,
    textureAtlas?: Avatar3dApi.TextureAtlasEnumDef,
    textureChannels?: string,
    morphTargets?: string,
    useDracoMeshCompression?: boolean,
    useQuantizeMeshOptCompression?: boolean,
    pose?: Avatar3dApi.PoseEnumDef,
    useHands?: boolean,
    textureFormat?: Avatar3dApi.TextureFormatEnumDef,
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
          lod
        }
      })
      .catch(handleAxiosError);

    return toRequestFile(data, avatarId);
  }
}
