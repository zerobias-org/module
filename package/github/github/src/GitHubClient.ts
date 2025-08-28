import { Octokit } from 'octokit';
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError
} from '@auditmation/types-core-js';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';

export class GitHubClient {
  private octokit?: Octokit;

  private connected = false;

  private baseUrl = 'https://api.github.com';

  async connect(profile: ConnectionProfile): Promise<void> {
    try {
      const options: { auth: string; baseUrl?: string } = { auth: profile.apiToken };

      if (profile.url) {
        this.baseUrl = profile.url.toString();
        if (!this.baseUrl.includes('api.github.com')) {
          options.baseUrl = this.baseUrl;
        }
      }

      this.octokit = new Octokit(options);

      await this.octokit.rest.users.getAuthenticated();
      this.connected = true;
    } catch (error: unknown) {
      GitHubClient.handleError(error);
    }
  }

  async isConnected(): Promise<boolean> {
    if (!this.connected || !this.octokit) {
      return false;
    }

    try {
      await this.octokit.rest.users.getAuthenticated();
      return true;
    } catch {
      this.connected = false;
      return false;
    }
  }

  async disconnect(): Promise<void> {
    this.octokit = undefined;
    this.connected = false;
  }

  getHttpClient(): Octokit {
    if (!this.octokit) {
      throw new UnexpectedError('Client not connected. Call connect() first.');
    }
    return this.octokit;
  }

  private static handleError(error: unknown): never {
    const typedError = error as any;
    const status = typedError.status || typedError.response?.status || 500;
    const message = typedError.message || 'Unknown error';

    switch (status) {
      case 401: throw new InvalidCredentialsError();
      case 403:
        if (message.includes('rate limit') || message.includes('API rate limit')) {
          throw new RateLimitExceededError();
        }
        throw new UnauthorizedError();
      case 404: throw new NoSuchObjectError('resource', 'unknown');
      case 400:
      case 422: throw new InvalidInputError('request', message);
      case 429: throw new RateLimitExceededError();
      default: throw new UnexpectedError(message, status);
    }
  }
}
