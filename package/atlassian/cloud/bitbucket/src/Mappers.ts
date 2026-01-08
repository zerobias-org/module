import { map } from '@auditmation/util-hub-module-utils';
import { URL } from '@auditmation/types-core-js';
import {
  User,
  UserLinks,
  Link,
  Repository,
  RepositoryLinks,
  Workspace,
  WorkspaceLinks,
  WorkspaceSummary,
  Project,
  Branch
} from '../generated/model';

// Helper to safely parse Date
function toDate(value: unknown): Date | undefined {
  if (!value || typeof value !== 'string') return undefined;
  const date = new Date(value);
  return isNaN(date.getTime()) ? undefined : date;
}

// Helper to map a single link
function toLink(raw: Record<string, unknown> | undefined): Link | undefined {
  if (!raw) return undefined;
  return {
    href: map(URL, raw.href as string | undefined),
    name: raw.name as string | undefined
  };
}

// Helper to map an array of links
function toLinkArray(raw: Array<Record<string, unknown>> | undefined): Array<Link> | undefined {
  if (!raw || !Array.isArray(raw)) return undefined;
  return raw.map((item) => toLink(item) as Link).filter(Boolean);
}

export function toUserLinks(raw: Record<string, unknown> | undefined): UserLinks | undefined {
  if (!raw) return undefined;
  return {
    self: toLink(raw.self as Record<string, unknown>),
    html: toLink(raw.html as Record<string, unknown>),
    avatar: toLink(raw.avatar as Record<string, unknown>),
    repositories: toLink(raw.repositories as Record<string, unknown>),
    snippets: toLink(raw.snippets as Record<string, unknown>)
  };
}

export function toUser(raw: Record<string, unknown>): User {
  return {
    uuid: raw.uuid as string,
    username: raw.username as string | undefined,
    displayName: raw.display_name as string | undefined,
    accountId: raw.account_id as string,
    nickname: raw.nickname as string | undefined,
    type: raw.type as string | undefined,
    createdOn: toDate(raw.created_on),
    isStaff: raw.is_staff as boolean | undefined,
    links: toUserLinks(raw.links as Record<string, unknown>)
  };
}

export function toWorkspaceLinks(raw: Record<string, unknown> | undefined): WorkspaceLinks | undefined {
  if (!raw) return undefined;
  return {
    self: toLink(raw.self as Record<string, unknown>),
    html: toLink(raw.html as Record<string, unknown>),
    avatar: toLink(raw.avatar as Record<string, unknown>),
    members: toLink(raw.members as Record<string, unknown>),
    projects: toLink(raw.projects as Record<string, unknown>),
    repositories: toLink(raw.repositories as Record<string, unknown>)
  };
}

export function toWorkspace(raw: Record<string, unknown>): Workspace {
  return {
    uuid: raw.uuid as string,
    slug: raw.slug as string,
    name: raw.name as string | undefined,
    type: raw.type as string | undefined,
    isPrivate: raw.is_private as boolean | undefined,
    createdOn: toDate(raw.created_on),
    links: toWorkspaceLinks(raw.links as Record<string, unknown>)
  };
}

export function toWorkspaceSummary(raw: Record<string, unknown> | undefined): WorkspaceSummary | undefined {
  if (!raw) return undefined;
  return {
    uuid: raw.uuid as string | undefined,
    slug: raw.slug as string | undefined,
    name: raw.name as string | undefined,
    type: raw.type as string | undefined
  };
}

export function toProject(raw: Record<string, unknown> | undefined): Project | undefined {
  if (!raw) return undefined;
  return {
    uuid: raw.uuid as string | undefined,
    key: raw.key as string | undefined,
    name: raw.name as string | undefined,
    type: raw.type as string | undefined
  };
}

export function toBranch(raw: Record<string, unknown> | undefined): Branch | undefined {
  if (!raw) return undefined;
  return {
    name: raw.name as string | undefined,
    type: raw.type as string | undefined
  };
}

export function toRepositoryLinks(raw: Record<string, unknown> | undefined): RepositoryLinks | undefined {
  if (!raw) return undefined;
  return {
    self: toLink(raw.self as Record<string, unknown>),
    html: toLink(raw.html as Record<string, unknown>),
    avatar: toLink(raw.avatar as Record<string, unknown>),
    clone: toLinkArray(raw.clone as Array<Record<string, unknown>>),
    commits: toLink(raw.commits as Record<string, unknown>),
    downloads: toLink(raw.downloads as Record<string, unknown>),
    forks: toLink(raw.forks as Record<string, unknown>),
    hooks: toLink(raw.hooks as Record<string, unknown>),
    issues: toLink(raw.issues as Record<string, unknown>),
    pullRequests: toLink(raw.pullrequests as Record<string, unknown>),
    source: toLink(raw.source as Record<string, unknown>),
    watchers: toLink(raw.watchers as Record<string, unknown>)
  };
}

// Helper to safely convert fork policy enum
function toForkPolicy(value: unknown): Repository.ForkPolicyEnumDef | undefined {
  if (!value || typeof value !== 'string') return undefined;
  try {
    return Repository.ForkPolicyEnum.from(value);
  } catch {
    return undefined;
  }
}

// Helper to safely convert scm enum
function toScm(value: unknown): Repository.ScmEnumDef | undefined {
  if (!value || typeof value !== 'string') return undefined;
  try {
    return Repository.ScmEnum.from(value);
  } catch {
    return undefined;
  }
}

export function toRepository(raw: Record<string, unknown>): Repository {
  return {
    uuid: raw.uuid as string,
    name: raw.name as string,
    fullName: raw.full_name as string,
    slug: raw.slug as string | undefined,
    description: raw.description as string | undefined,
    isPrivate: raw.is_private as boolean | undefined,
    forkPolicy: toForkPolicy(raw.fork_policy),
    language: raw.language as string | undefined,
    scm: toScm(raw.scm),
    size: raw.size as number | undefined,
    hasIssues: raw.has_issues as boolean | undefined,
    hasWiki: raw.has_wiki as boolean | undefined,
    owner: raw.owner ? toUser(raw.owner as Record<string, unknown>) : undefined,
    workspace: toWorkspaceSummary(raw.workspace as Record<string, unknown>),
    project: toProject(raw.project as Record<string, unknown>),
    mainBranch: toBranch(raw.mainbranch as Record<string, unknown>),
    createdOn: toDate(raw.created_on),
    updatedOn: toDate(raw.updated_on),
    links: toRepositoryLinks(raw.links as Record<string, unknown>)
  };
}
