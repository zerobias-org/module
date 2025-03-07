import { Avatar3dApi, Avatar3dProducerApi } from '../generated/api';
import { RequestFile } from '../generated/model';
import { ReadyPlayerMeClient } from './ReadyPlayerMeClient';

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
    throw new Error('Method not implemented.');
  }

}
