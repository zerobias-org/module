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
  return CreateUserResponse.newInstance(typeof data === 'string' ? JSON.parse(data) : data);
}

export function toTokenResponse(data: any): TokenResponse {
  return TokenResponse.newInstance(typeof data === 'string' ? JSON.parse(data) : data);
}

export function toAssetResponse(data: any): AssetResponse {
  return AssetResponse.newInstance(typeof data === 'string' ? JSON.parse(data) : data);
}

export function toAssetInfoResponse(data: any): AssetInfoResponse {
  return AssetInfoResponse.newInstance(typeof data === 'string' ? JSON.parse(data) : data);
}

export function toAssetInfo(data: any): AssetInfo {
  return AssetInfo.newInstance(typeof data === 'string' ? JSON.parse(data) : data);
}

export function toAvatarMetadata(data: any): AvatarMetadata {
  return AvatarMetadata.newInstance(typeof data === 'string' ? JSON.parse(data) : data);
}

export function toRequestFile(data: any, filename: string): RequestDetailedFile {
  return { value: data, options: { filename } } as RequestDetailedFile;
}
