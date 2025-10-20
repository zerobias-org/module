import nock from 'nock';
import fs from 'fs';
import path from 'path';

export function mockAuthenticatedRequest(baseUrl: string, token: string) {
  return nock(baseUrl)
    .matchHeader('authorization', `Bearer ${token}`)
    .matchHeader('accept', 'application/json')
    .matchHeader('content-type', 'application/json');
}

export function mockPaginatedResponse(
  scope: nock.Scope,
  method: string,
  urlPath: string,
  query: Record<string, unknown>,
  items: unknown[],
  totalCount: number
) {
  const httpMethod = method.toLowerCase();
  let interceptor;

  switch (httpMethod) {
    case 'get':
      interceptor = scope.get(urlPath);
      break;
    case 'post':
      interceptor = scope.post(urlPath);
      break;
    case 'put':
      interceptor = scope.put(urlPath);
      break;
    case 'delete':
      interceptor = scope.delete(urlPath);
      break;
    default:
      throw new Error(`Unsupported HTTP method: ${method}`);
  }

  return interceptor
    .query(query)
    .reply(200, {
      data: items,
      totalCount,
      count: items.length,
      pageCount: Math.ceil(totalCount / ((query.limit as number) || items.length)),
      pageNumber: Math.floor((query.offset as number) / ((query.limit as number) || items.length)) + 1,
      pageSize: (query.limit as number) || items.length,
    });
}

export function mockSingleResponse(
  scope: nock.Scope,
  method: string,
  urlPath: string,
  responseData: unknown
) {
  const httpMethod = method.toLowerCase();
  let interceptor;

  switch (httpMethod) {
    case 'get':
      interceptor = scope.get(urlPath);
      break;
    case 'post':
      interceptor = scope.post(urlPath);
      break;
    case 'put':
      interceptor = scope.put(urlPath);
      break;
    case 'delete':
      interceptor = scope.delete(urlPath);
      break;
    default:
      throw new Error(`Unsupported HTTP method: ${method}`);
  }

  return interceptor.reply(200, responseData);
}

export function mockErrorResponse(
  scope: nock.Scope,
  method: string,
  urlPath: string,
  query: Record<string, unknown>,
  statusCode: number,
  errorMessage?: string
) {
  const errorBody = {
    error: errorMessage || `HTTP ${statusCode} Error`,
    statusCode,
    timestamp: new Date().toISOString(),
  };

  const httpMethod = method.toLowerCase();
  let interceptor;

  switch (httpMethod) {
    case 'get':
      interceptor = scope.get(urlPath);
      break;
    case 'post':
      interceptor = scope.post(urlPath);
      break;
    case 'put':
      interceptor = scope.put(urlPath);
      break;
    case 'delete':
      interceptor = scope.delete(urlPath);
      break;
    default:
      throw new Error(`Unsupported HTTP method: ${method}`);
  }

  return interceptor
    .query(query)
    .reply(statusCode, errorBody);
}

export function loadFixture(fixturePath: string): { data: unknown[]; totalCount: number } {
  const fullPath = path.join(__dirname, '../fixtures', fixturePath);

  if (!fs.existsSync(fullPath)) {
    throw new Error(`Fixture file not found: ${fullPath}`);
  }

  const fileContent = fs.readFileSync(fullPath, 'utf8');
  return JSON.parse(fileContent) as { data: unknown[]; totalCount: number };
}

export function setupNockFromFixture(
  baseUrl: string,
  token: string,
  fixturePath: string,
  method: string,
  urlPath: string,
  query?: Record<string, unknown>
): nock.Scope {
  const fixtureData = loadFixture(fixturePath);
  const scope = mockAuthenticatedRequest(baseUrl, token);

  if (Array.isArray(fixtureData)) {
    // Handle paginated response
    return mockPaginatedResponse(
      scope,
      method,
      urlPath,
      query || {},
      fixtureData,
      fixtureData.length
    );
  }
  // Handle single object response
  return mockSingleResponse(scope, method, urlPath, fixtureData);
}

export function cleanNock(): void {
  nock.cleanAll();
}
