/**
 * Utility functions for mappers
 *
 * Mirrors the helper set used by shipped connectors (see
 * package/avigilon/alta/access/src/util.ts).
 */

import { InvalidStateError } from '@zerobias-org/types-core-js';

/**
 * Applies a mapper function to a value, handling null/undefined
 */
export function mapWith<T>(mapper: (raw: Record<string, unknown>) => T, value: unknown): T | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  return mapper(value as Record<string, unknown>);
}

/**
 * Normalizes null to undefined for optional properties while preserving
 * other falsy values (0, false, "").
 */
export function optional<T>(value: T | null | undefined | unknown): T | undefined {
  return (value ?? undefined) as T | undefined;
}

/**
 * Validates that required properties exist on an object (not null/undefined).
 */
export function ensureProperties<K extends string>(
  raw: Record<string, unknown>,
  property: readonly K[]
): asserts raw is Record<string, unknown> & Record<K, unknown> {
  for (const prop of property) {
    const value = raw[prop];
    if (value === null || value === undefined) {
      throw new InvalidStateError(`Missing required field: ${String(prop)}`);
    }
  }
}
