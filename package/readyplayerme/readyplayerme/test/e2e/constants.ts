export const READYPLAYERME_APP_ID = process.env.READYPLAYERME_APP_ID ?? '';
export const READYPLAYERME_USER_ID = process.env.READYPLAYERME_USER_ID ?? '';

export function hasCredentials(): boolean {
  return !!READYPLAYERME_APP_ID;
}
