/**
 * Utility functions for mappers
 *
 * TODO: Move to @auditmation/util-hub-module-utils once stabilized
 */

import { InvalidStateError } from '@auditmation/types-core-js';

/**
 * Applies a mapper function to a value, handling null/undefined
 *
 * @param mapper - The mapper function to apply
 * @param value - The value to map
 * @returns Mapped value or undefined if input is null/undefined
 */
export function mapWith<T>(mapper: (raw: Record<string, unknown>) => T, value: unknown): T | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  return mapper(value as Record<string, unknown>);
}

/**
 * Normalizes null to undefined for optional properties
 *
 * Converts null to undefined while preserving other falsy values (0, false, "")
 *
 * @param value - The value to normalize
 * @returns undefined if input is null or undefined, otherwise returns the value as-is
 */
export function optional<T>(value: T | null | undefined | unknown): T | undefined {
  return (value ?? undefined) as T | undefined;
}

/**
 * Validates that required properties exist on an object (not null/undefined)
 *
 * Uses TypeScript assertion signatures to inform the type system that the
 * specified properties are guaranteed to exist. Allows other falsy values
 * (0, false, "") to pass validation.
 *
 * @param raw - The raw object to validate
 * @param properties - Array of property names that must exist
 * @throws InvalidStateError if any property is null or undefined
 */

// Overload for Record<string, unknown>
export function ensureProperties<K extends string>(
  raw: Record<string, unknown>,
  property: readonly K[]
): asserts raw is Record<string, unknown> & Record<K, unknown>;

// Overload for any type (backward compatibility)
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function ensureProperties<K extends keyof any>(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  raw: any,
  property: readonly K[]
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): asserts raw is Record<string, any> & { [P in K]: any };

// Implementation
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function ensureProperties<T extends Record<string, any>>(
  raw: T,
  property: readonly (keyof T)[]
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): asserts raw is T & Record<string, any> {
  property.forEach((prop) => {
    const value = raw[prop];
    if (value === null || value === undefined) {
      throw new InvalidStateError(`Missing required field: ${String(prop)}`);
    }
  });
}
