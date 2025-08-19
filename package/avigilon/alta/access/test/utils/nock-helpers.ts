import nock from 'nock';
import fs from 'fs';
import path from 'path';

/**
 * Mock authenticated request with proper headers
 */
export function mockAuthenticatedRequest(baseUrl: string, token: string) {
  return nock(baseUrl)
    .matchHeader('authorization', `Bearer ${token}`)
    .matchHeader('accept', 'application/json')
    .matchHeader('content-type', 'application/json');
}

/**
 * Mock paginated API response
 */
export function mockPaginatedResponse(
  scope: nock.Scope,
  method: string,
  path: string,
  query: Record<string, any>,
  items: any[],
  totalCount: number
) {
  const httpMethod = method.toLowerCase();
  let interceptor;
  
  switch (httpMethod) {
    case 'get':
      interceptor = scope.get(path);
      break;
    case 'post':
      interceptor = scope.post(path);
      break;
    case 'put':
      interceptor = scope.put(path);
      break;
    case 'delete':
      interceptor = scope.delete(path);
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
      pageCount: Math.ceil(totalCount / (query.limit || items.length)),
      pageNumber: Math.floor(query.offset / (query.limit || items.length)) + 1,
      pageSize: query.limit || items.length
    });
}

/**
 * Mock single object response
 */
export function mockSingleResponse(
  scope: nock.Scope,
  method: string,
  path: string,
  responseData: any
) {
  const httpMethod = method.toLowerCase();
  let interceptor;
  
  switch (httpMethod) {
    case 'get':
      interceptor = scope.get(path);
      break;
    case 'post':
      interceptor = scope.post(path);
      break;
    case 'put':
      interceptor = scope.put(path);
      break;
    case 'delete':
      interceptor = scope.delete(path);
      break;
    default:
      throw new Error(`Unsupported HTTP method: ${method}`);
  }
  
  return interceptor.reply(200, responseData);
}

/**
 * Mock error response
 */
export function mockErrorResponse(
  scope: nock.Scope,
  method: string,
  path: string,
  query: Record<string, any>,
  statusCode: number,
  errorMessage?: string
) {
  const errorBody = {
    error: errorMessage || `HTTP ${statusCode} Error`,
    statusCode,
    timestamp: new Date().toISOString()
  };

  const httpMethod = method.toLowerCase();
  let interceptor;
  
  switch (httpMethod) {
    case 'get':
      interceptor = scope.get(path);
      break;
    case 'post':
      interceptor = scope.post(path);
      break;
    case 'put':
      interceptor = scope.put(path);
      break;
    case 'delete':
      interceptor = scope.delete(path);
      break;
    default:
      throw new Error(`Unsupported HTTP method: ${method}`);
  }
  
  return interceptor
    .query(query)
    .reply(statusCode, errorBody);
}

/**
 * Load fixture data from JSON files
 */
export function loadFixture(fixturePath: string): any {
  const fullPath = path.join(__dirname, '../fixtures', fixturePath);
  
  if (!fs.existsSync(fullPath)) {
    throw new Error(`Fixture file not found: ${fullPath}`);
  }
  
  const content = fs.readFileSync(fullPath, 'utf8');
  return JSON.parse(content);
}

/**
 * Setup nock from fixture data
 */
export function setupNockFromFixture(
  baseUrl: string,
  token: string,
  fixturePath: string,
  method: string,
  path: string,
  query?: Record<string, any>
): nock.Scope {
  const fixtureData = loadFixture(fixturePath);
  const scope = mockAuthenticatedRequest(baseUrl, token);
  
  if (Array.isArray(fixtureData)) {
    // Handle paginated response
    return mockPaginatedResponse(
      scope,
      method,
      path,
      query || {},
      fixtureData,
      fixtureData.length
    );
  } else {
    // Handle single object response
    return mockSingleResponse(scope, method, path, fixtureData);
  }
}

/**
 * Clean up all nock mocks
 */
export function cleanNock(): void {
  nock.cleanAll();
}