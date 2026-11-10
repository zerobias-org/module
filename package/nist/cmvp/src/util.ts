import {
  Duration,
  InvalidStateError,
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

/**
 * Normalizes null to undefined for optional properties while preserving
 * other falsy values (0, false, "").
 */
export function optional<T>(value: T | null | undefined | unknown): T | undefined {
  return (value ?? undefined) as T | undefined;
}

/**
 * Validates that required properties exist on a raw record (not null/undefined).
 * Other falsy values (0, false, "") pass.
 */
export function ensureProperties<K extends keyof any>(
  raw: any,
  properties: readonly K[],
): asserts raw is Record<string, any> & { [P in K]: any } {
  for (const prop of properties) {
    const value = raw[prop];
    if (value === null || value === undefined) {
      throw new InvalidStateError(`Missing required field: ${String(prop)}`);
    }
  }
}
