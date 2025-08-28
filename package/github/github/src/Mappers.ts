import { map } from '@auditmation/util-hub-module-utils';
import { URL, Email } from '@auditmation/types-core-js';
import { Organization, OrganizationInfo } from '../generated/model';

export function mapOrganization(raw: Record<string, unknown>): Organization {
  const output: Organization = {
    id: raw.id as number,
    login: raw.login as string,
    url: map(URL, `${raw.url}`),
    reposUrl: map(URL, raw.repos_url as string | undefined),
    avatarUrl: map(URL, raw.avatar_url as string | undefined),
    description: raw.description as string | null,
  };
  return output;
}

export function mapOrganizationInfo(raw: Record<string, unknown>): OrganizationInfo {
  const output: OrganizationInfo = {
    id: raw.id as number,
    login: raw.login as string,
    url: map(URL, `${raw.url}`),
    reposUrl: map(URL, raw.repos_url as string | undefined),
    avatarUrl: map(URL, raw.avatar_url as string | undefined),
    description: raw.description as string | null,
    name: raw.name as string | null,
    company: raw.company as string | null,
    blog: map(URL, raw.blog as string | undefined),
    location: raw.location as string | null,
    email: map(Email, raw.email as string | undefined),
    twitterUsername: raw.twitter_username as string | null,
    publicRepos: raw.public_repos as number | undefined,
    followers: raw.followers as number | undefined,
    following: raw.following as number | undefined,
    createdAt: map(Date, `${raw.created_at}`),
    updatedAt: map(Date, `${raw.updated_at}`),
  };
  return output;
}
