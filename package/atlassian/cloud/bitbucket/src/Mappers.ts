import { toEnum, map } from '@auditmation/util-hub-module-utils';
import { URL, UUID } from '@auditmation/types-core-js';
import {
  Repository,
  Workspace,
  Project,
  Issue,
  PullRequest,
  Branch,
  Commit,
  Member,
  BranchRestriction,
  Account,
  BitbucketLinks,
  Link,
  Participant
} from '../generated/model';

function mapAccount(raw: any): Account {
  if (!raw) return {} as Account;

  const output: Account = {
    uuid: map(UUID, raw.uuid),
    username: raw.username,
    nickname: raw.nickname,
    displayName: raw.display_name,
    accountId: raw.account_id,
    type: raw.type,
    links: mapBitbucketLinks(raw.links),
  };
  return output;
}

function mapBitbucketLinks(raw: any): BitbucketLinks {
  if (!raw) return {} as BitbucketLinks;

  const output: BitbucketLinks = {
    self: mapLink(raw.self),
    html: mapLink(raw.html),
    avatar: mapLink(raw.avatar),
    clone: raw.clone?.map(mapLink),
    commits: mapLink(raw.commits),
    issues: mapLink(raw.issues),
    pullrequests: mapLink(raw.pullrequests),
    branches: mapLink(raw.branches),
    tags: mapLink(raw.tags),
    downloads: mapLink(raw.downloads),
    source: mapLink(raw.source),
  };
  return output;
}

function mapLink(raw: any): Link {
  if (!raw) return {} as Link;

  const output: Link = {
    href: map(URL, raw.href),
    name: raw.name,
  };
  return output;
}

function mapParticipant(raw: any): Participant {
  if (!raw) return {} as Participant;

  const output: Participant = {
    user: mapAccount(raw.user),
    role: raw.role,
    approved: raw.approved,
    participatedOn: map(Date, raw.participated_on),
  };
  return output;
}

export function mapRepository(raw: any): Repository {
  const output: Repository = {
    uuid: map(UUID, raw.uuid),
    name: raw.name,
    fullName: raw.full_name,
    isPrivate: raw.is_private,
    scm: raw.scm,
    description: raw.description,
    size: raw.size,
    language: raw.language,
    hasIssues: raw.has_issues,
    hasWiki: raw.has_wiki,
    forkPolicy: raw.fork_policy,
    type: raw.type,
    links: mapBitbucketLinks(raw.links),
    owner: mapAccount(raw.owner),
    project: mapProject(raw.project),
    createdOn: map(Date, raw.created_on),
    updatedOn: map(Date, raw.updated_on),
  };
  return output;
}

export function mapWorkspace(raw: any): Workspace {
  const output: Workspace = {
    uuid: map(UUID, raw.uuid),
    name: raw.name,
    slug: raw.slug,
    isPrivate: raw.is_private,
    type: raw.type,
    links: mapBitbucketLinks(raw.links),
  };
  return output;
}

export function mapProject(raw: any): Project {
  if (!raw) return {} as Project;

  const output: Project = {
    uuid: map(UUID, raw.uuid),
    key: raw.key,
    name: raw.name,
    description: raw.description,
    isPrivate: raw.is_private,
    type: raw.type,
    links: mapBitbucketLinks(raw.links),
    owner: mapAccount(raw.owner),
    createdOn: map(Date, raw.created_on),
    updatedOn: map(Date, raw.updated_on),
  };
  return output;
}

export function mapIssue(raw: any): Issue {
  const output: Issue = {
    id: Number(raw.id),
    title: raw.title,
    content: raw.content,
    state: raw.state,
    kind: raw.kind,
    priority: raw.priority,
    version: raw.version,
    component: raw.component,
    votes: raw.votes,
    watches: raw.watches,
    type: raw.type,
    repository: mapRepository(raw.repository),
    links: mapBitbucketLinks(raw.links),
    reporter: mapAccount(raw.reporter),
    assignee: mapAccount(raw.assignee),
    createdOn: map(Date, `${raw.created_on}`),
    updatedOn: map(Date, raw.updated_on),
    editedOn: map(Date, raw.edited_on),
  };
  return output;
}

export function mapPullRequest(raw: any): PullRequest {
  const output: PullRequest = {
    id: Number(raw.id),
    title: raw.title,
    description: raw.description,
    state: raw.state,
    mergeCommit: raw.merge_commit,
    closeSourceBranch: raw.close_source_branch,
    commentCount: raw.comment_count,
    taskCount: raw.task_count,
    reason: raw.reason,
    type: raw.type,
    author: mapAccount(raw.author),
    source: raw.source,
    destination: raw.destination,
    participants: raw.participants?.map(mapParticipant) || [],
    reviewers: raw.reviewers?.map(mapAccount) || [],
    links: mapBitbucketLinks(raw.links),
    createdOn: map(Date, `${raw.created_on}`),
    updatedOn: map(Date, raw.updated_on),
  };
  return output;
}

export function mapBranch(raw: any): Branch {
  const output: Branch = {
    name: raw.name,
    type: raw.type,
    target: raw.target,
    links: mapBitbucketLinks(raw.links),
  };
  return output;
}

export function mapCommit(raw: any): Commit {
  const output: Commit = {
    hash: raw.hash,
    date: map(Date, `${raw.date}`),
    message: raw.message,
    type: raw.type,
    author: mapAccount(raw.author),
    parents: raw.parents || [],
    links: mapBitbucketLinks(raw.links),
  };
  return output;
}

export function mapMember(raw: any): Member {
  const output: Member = {
    type: raw.type,
    links: mapBitbucketLinks(raw.links),
  };
  return output;
}

export function mapBranchRestriction(raw: any): BranchRestriction {
  const output: BranchRestriction = {
    id: Number(raw.id),
    kind: raw.kind,
    pattern: raw.pattern,
    users: raw.users?.map(mapAccount) || [],
    groups: raw.groups || [],
    type: raw.type,
    links: mapBitbucketLinks(raw.links),
  };
  return output;
}
