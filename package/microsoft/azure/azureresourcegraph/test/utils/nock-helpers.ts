import nock from 'nock';

/** Azure AD authority host used in every unit test. */
export const LOGIN_BASE_URL = 'https://login.microsoftonline.com';

/** Azure Resource Manager host ARG lives under. */
export const ARM_BASE_URL = 'https://management.azure.com';

/** ARG query path + pinned api-version. */
export const ARG_QUERY_PATH = '/providers/Microsoft.ResourceGraph/resources';
export const ARG_API_VERSION = '2024-04-01';

export const TEST_DIRECTORY_ID = '00000000-1111-2222-3333-444444444444';
export const TEST_CLIENT_ID = 'test-client-id';
export const TEST_CLIENT_SECRET = 'test-client-secret';
export const TEST_ACCESS_TOKEN = 'mock-arm-token';

/** Mock a successful client-credentials token mint for the test tenant. */
export function mockTokenSuccess(expiresIn = 3600): nock.Scope {
  return nock(LOGIN_BASE_URL)
    .post(`/${TEST_DIRECTORY_ID}/oauth2/v2.0/token`)
    .reply(200, {
      token_type: 'Bearer',
      expires_in: expiresIn,
      access_token: TEST_ACCESS_TOKEN,
    });
}

/** Mock a failed token mint (bad client secret → AADSTS 401-style response). */
export function mockTokenFailure(status = 401): nock.Scope {
  return nock(LOGIN_BASE_URL)
    .post(`/${TEST_DIRECTORY_ID}/oauth2/v2.0/token`)
    .reply(status, {
      error: 'invalid_client',
      error_description: 'AADSTS7000215: Invalid client secret provided.',
    });
}

/** Mock one ARG query POST answering with the given body. */
export function mockArgQuery(
  responseBody: unknown,
  status = 200,
  headers?: Record<string, string>
): nock.Scope {
  return nock(ARM_BASE_URL)
    .matchHeader('authorization', `Bearer ${TEST_ACCESS_TOKEN}`)
    .post(ARG_QUERY_PATH)
    .query({ 'api-version': ARG_API_VERSION })
    .reply(status, responseBody as nock.Body, headers);
}

export function cleanNock(): void {
  nock.cleanAll();
}
