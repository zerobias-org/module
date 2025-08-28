import * as nock from 'nock';
import * as fs from 'fs';
import * as path from 'path';

export interface FixtureData {
  request: {
    url: string;
    method: string;
    headers?: Record<string, string>;
    body?: any;
  };
  response: {
    status: number;
    headers?: Record<string, string>;
    body: any;
  };
}

export function mockAuthenticatedRequest(baseUrl: string): nock.Scope {
  return nock(baseUrl, {
    reqheaders: {
      'authorization': /^token .+$/
    }
  });
}

export function mockPaginatedResponse(baseUrl: string, path: string, responses: any[]): nock.Scope {
  const scope = nock(baseUrl);
  
  responses.forEach((response, index) => {
    const queryParams = index === 0 ? {} : { page: (index + 1).toString() };
    scope.get(path)
      .query(queryParams)
      .reply(200, response, {
        'content-type': 'application/json; charset=utf-8'
      });
  });
  
  return scope;
}

export function mockErrorResponse(baseUrl: string, path: string, statusCode: number, errorMessage: string): nock.Scope {
  const errorBody = {
    message: errorMessage,
    documentation_url: 'https://docs.github.com/rest'
  };
  
  return nock(baseUrl)
    .get(path)
    .reply(statusCode, errorBody, {
      'content-type': 'application/json; charset=utf-8'
    });
}

export function loadFixture(fixtureName: string, fixtureDir: string = 'templates'): any {
  const fixturePath = path.join(__dirname, '..', 'fixtures', fixtureDir, `${fixtureName}.json`);
  
  if (!fs.existsSync(fixturePath)) {
    throw new Error(`Fixture file not found: ${fixturePath}`);
  }
  
  const fixtureContent = fs.readFileSync(fixturePath, 'utf-8');
  return JSON.parse(fixtureContent);
}

export function setupNockFromFixture(baseUrl: string, fixtureName: string, fixtureDir: string = 'templates'): nock.Scope {
  const fixture: FixtureData = loadFixture(fixtureName, fixtureDir);
  
  const parsedUrl = new URL(fixture.request.url);
  const scope = nock(baseUrl);
  
  let interceptor = scope.intercept(parsedUrl.pathname + parsedUrl.search, fixture.request.method);
  
  if (fixture.request.headers) {
    interceptor = interceptor.matchHeader('authorization', fixture.request.headers.authorization || /^token .+$/);
  }
  
  return interceptor.reply(
    fixture.response.status,
    fixture.response.body,
    fixture.response.headers || { 'content-type': 'application/json; charset=utf-8' }
  );
}

export function cleanupNock(): void {
  nock.cleanAll();
  nock.restore();
}

export function enableNockRecording(): void {
  nock.recorder.rec({
    dont_print: true,
    output_objects: true,
    enable_reqheaders_recording: true
  });
}

export function stopNockRecording(): any[] {
  const recordings = nock.recorder.play();
  nock.recorder.clear();
  return recordings;
}