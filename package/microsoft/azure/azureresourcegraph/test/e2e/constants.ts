export const AZURE_DIRECTORY_ID = process.env.AZURE_DIRECTORY_ID ?? '';
export const AZURE_CLIENT_ID = process.env.AZURE_CLIENT_ID ?? '';
export const AZURE_CLIENT_SECRET = process.env.AZURE_CLIENT_SECRET ?? '';
export const AZURE_SUBSCRIPTION_ID = process.env.AZURE_SUBSCRIPTION_ID ?? '';

export function hasCredentials(): boolean {
  return !!(AZURE_DIRECTORY_ID && AZURE_CLIENT_ID && AZURE_CLIENT_SECRET);
}
