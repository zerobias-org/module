import { GitHubConnectorImpl } from './GitHubConnectorImpl';
import { GitHubConnector } from '../generated/api';

export function newGitHub(): GitHubConnector {
  return new GitHubConnectorImpl();
}

export { GitHubConnectorImpl } from './GitHubConnectorImpl';
export { GitHubClient } from './GitHubClient';
export * from './Mappers';
export * from '../generated/api';
export * from '../generated/model';
