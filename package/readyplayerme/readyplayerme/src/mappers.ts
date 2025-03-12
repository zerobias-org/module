import { RequestDetailedFile } from '@auditmation/hub-core';
import {
  AssetInfo,
  AssetInfoResponse,
  AssetResponse,
  AvatarMetadata,
  CreateUserResponse,
  TokenResponse
} from '../generated/model';

export function toCreateUserResponse(data: any): CreateUserResponse {
  return CreateUserResponse.newInstance(data);
}

export function toTokenResponse(data: any): TokenResponse {
  return TokenResponse.newInstance(data);
}

export function toAssetResponse(data: any): AssetResponse {
  return AssetResponse.newInstance(data);
}

export function toAssetInfoResponse(data: any): AssetInfoResponse {
  return AssetInfoResponse.newInstance(data);
}

export function toAssetInfo(data: any): AssetInfo {
  return AssetInfo.newInstance(data);
}

export function toAvatarMetadata(data: any): AvatarMetadata {
  return AvatarMetadata.newInstance(data);
}

export function toRequestFile(data: any, filename: string): RequestDetailedFile {
  return { value: data, options: { filename } } as RequestDetailedFile;
}
