/* eslint-disable */
import { map } from '@auditmation/util-hub-module-utils';
import { mapWith, ensureProperties, optional } from './util';
import {
  Workspace,
  WorkspaceLinks,
  Repository,
  RepositoryLinks,
  Link,
  CloneLink,
  Account,
  AccountLinks,
  Project,
  ProjectLinks,
  Branch
} from '../generated/model';

// ============================================================================
// HELPER FUNCTIONS (NON-EXPORTED) - Declared before exported mappers
// ============================================================================

/**
 * Helper for Link mapping
 */
function toLink(raw: any): Link {
  const output: Link = {
    href: optional(raw.href)
  };

  return output;
}

/**
 * Helper for CloneLink mapping
 */
function toCloneLink(raw: any): CloneLink {
  const output: CloneLink = {
    href: optional(raw.href),
    name: optional(raw.name)
  };

  return output;
}

/**
 * Helper for AccountLinks mapping
 */
function toAccountLinks(raw: any): AccountLinks {
  const output: AccountLinks = {
    self: mapWith(toLink, raw.self),
    html: mapWith(toLink, raw.html),
    avatar: mapWith(toLink, raw.avatar)
  };

  return output;
}

/**
 * Helper for Account mapping
 */
function toAccount(raw: any): Account {
  const output: Account = {
    type: optional(raw.type),
    uuid: optional(raw.uuid),
    username: optional(raw.username),
    displayName: optional(raw.display_name),
    nickname: optional(raw.nickname),
    accountId: optional(raw.account_id),
    links: mapWith(toAccountLinks, raw.links)
  };

  return output;
}

/**
 * Helper for ProjectLinks mapping
 */
function toProjectLinks(raw: any): ProjectLinks {
  const output: ProjectLinks = {
    self: mapWith(toLink, raw.self),
    html: mapWith(toLink, raw.html),
    avatar: mapWith(toLink, raw.avatar)
  };

  return output;
}

/**
 * Helper for Project mapping
 */
function toProject(raw: any): Project {
  const output: Project = {
    type: optional(raw.type),
    uuid: optional(raw.uuid),
    key: optional(raw.key),
    name: optional(raw.name),
    links: mapWith(toProjectLinks, raw.links)
  };

  return output;
}

/**
 * Helper for Branch mapping
 */
function toBranch(raw: any): Branch {
  const output: Branch = {
    type: optional(raw.type),
    name: optional(raw.name)
  };

  return output;
}

/**
 * Helper for WorkspaceLinks mapping
 */
function toWorkspaceLinks(raw: any): WorkspaceLinks {
  const output: WorkspaceLinks = {
    self: mapWith(toLink, raw.self),
    html: mapWith(toLink, raw.html),
    avatar: mapWith(toLink, raw.avatar),
    members: mapWith(toLink, raw.members),
    owners: mapWith(toLink, raw.owners),
    projects: mapWith(toLink, raw.projects),
    repositories: mapWith(toLink, raw.repositories),
    snippets: mapWith(toLink, raw.snippets)
  };

  return output;
}

/**
 * Helper for RepositoryLinks mapping
 */
function toRepositoryLinks(raw: any): RepositoryLinks {
  const output: RepositoryLinks = {
    self: mapWith(toLink, raw.self),
    html: mapWith(toLink, raw.html),
    avatar: mapWith(toLink, raw.avatar),
    clone: Array.isArray(raw.clone) ? raw.clone.map(toCloneLink) : undefined,
    commits: mapWith(toLink, raw.commits),
    branches: mapWith(toLink, raw.branches),
    tags: mapWith(toLink, raw.tags),
    pullrequests: mapWith(toLink, raw.pullrequests),
    forks: mapWith(toLink, raw.forks),
    downloads: mapWith(toLink, raw.downloads),
    watchers: mapWith(toLink, raw.watchers),
    source: mapWith(toLink, raw.source)
  };

  return output;
}

// ============================================================================
// EXPORTED MAPPER FUNCTIONS - Alphabetical order
// ============================================================================

/**
 * Maps raw API repository data to Repository interface
 *
 * Handles snake_case to camelCase conversions:
 * - full_name -> fullName
 * - is_private -> isPrivate
 * - has_issues -> hasIssues
 * - has_wiki -> hasWiki
 * - fork_policy -> forkPolicy
 * - created_on -> createdOn
 * - updated_on -> updatedOn
 */
export function toRepository(raw: any): Repository {
  // 1. Check for required fields
  ensureProperties(raw, ['uuid', 'slug', 'name', 'full_name']);

  // 2. Create output object
  const output: Repository = {
    type: optional(raw.type),
    uuid: raw.uuid,
    slug: raw.slug,
    name: raw.name,
    fullName: raw.full_name,
    description: optional(raw.description),
    scm: optional(raw.scm),
    isPrivate: optional(raw.is_private),
    language: optional(raw.language),
    size: optional(raw.size),
    hasIssues: optional(raw.has_issues),
    hasWiki: optional(raw.has_wiki),
    forkPolicy: optional(raw.fork_policy),
    createdOn: map(Date, raw.created_on),
    updatedOn: map(Date, raw.updated_on),
    owner: mapWith(toAccount, raw.owner),
    project: mapWith(toProject, raw.project),
    mainbranch: mapWith(toBranch, raw.mainbranch),
    links: mapWith(toRepositoryLinks, raw.links)
  };

  return output;
}

/**
 * Maps raw API workspace data to Workspace interface
 *
 * Handles snake_case to camelCase conversions:
 * - is_private -> isPrivate
 * - is_privacy_enforced -> isPrivacyEnforced
 * - forking_mode -> forkingMode
 * - created_on -> createdOn
 * - updated_on -> updatedOn
 */
export function toWorkspace(raw: any): Workspace {
  // 1. Check for required fields
  ensureProperties(raw, ['uuid', 'slug', 'name']);

  // 2. Create output object
  const output: Workspace = {
    type: optional(raw.type),
    uuid: raw.uuid,
    slug: raw.slug,
    name: raw.name,
    isPrivate: optional(raw.is_private),
    isPrivacyEnforced: optional(raw.is_privacy_enforced),
    forkingMode: optional(raw.forking_mode),
    createdOn: map(Date, raw.created_on),
    updatedOn: map(Date, raw.updated_on),
    links: mapWith(toWorkspaceLinks, raw.links)
  };

  return output;
}
