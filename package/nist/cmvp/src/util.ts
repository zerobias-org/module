import {
  Duration,
  NoSuchObjectError,
  RateLimitExceededError,
  TimeoutError,
  UnexpectedError,
} from '@zerobias-org/types-core-js';

export function handleAxiosError(error: any, resourceType: string, resourceId: string): never {
  const status = error.response?.status ?? error.status;

  if (status === 404) {
    throw new NoSuchObjectError(resourceType, resourceId);
  }
  if (status === 429) {
    throw new RateLimitExceededError();
  }
  if (error.code === 'ECONNABORTED' || status === 408 || status === 504) {
    throw new TimeoutError(Duration.fromMilliseconds(30000));
  }
  throw new UnexpectedError(`CMVP request failed: ${error.message ?? 'unknown error'}`, status ?? 500);
}
