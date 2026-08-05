/**
 * Test constants for the Azure Resource Graph e2e suite.
 *
 * Connection credentials (client_id / client_secret / directoryId) are
 * injected by the framework as zbb secrets whose names match the
 * connectionProfile fields. Tests skip gracefully when no live tenant is
 * wired up.
 */

/** Optional subscription ID to exercise scoped queries against. */
export const AZURE_SUBSCRIPTION_ID = process.env.AZURE_SUBSCRIPTION_ID ?? '';

export function hasCredentials(): boolean {
  return !!(process.env.directoryId ?? process.env.AZURE_DIRECTORY_ID);
}
