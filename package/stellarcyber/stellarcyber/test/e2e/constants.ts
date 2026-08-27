export const STELLARCYBER_HOST = process.env.STELLARCYBER_HOST ?? '';
export const STELLARCYBER_API_TOKEN = process.env.STELLARCYBER_API_TOKEN ?? '';
export const STELLARCYBER_CASE_ID = process.env.STELLARCYBER_CASE_ID ?? '';

export function hasCredentials(): boolean {
  return !!STELLARCYBER_HOST && !!STELLARCYBER_API_TOKEN;
}
