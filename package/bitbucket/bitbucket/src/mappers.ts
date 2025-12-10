import { Repository, Workspace } from '../generated/model';

export function toWorkspace(data: any): Workspace {
  const workspace = new Workspace(
    data.uuid,
    data.slug,
    data.name,
    data.is_private,
    data.created_on ? new Date(data.created_on) : undefined,
    data.updated_on ? new Date(data.updated_on) : undefined
  );
  return workspace;
}

export function toRepository(data: any): Repository {
  const repo = new Repository(
    data.uuid,
    data.full_name,
    data.slug,
    data.name,
    data.description,
    data.scm ? Repository.ScmEnum.from(data.scm) : undefined,
    data.is_private,
    data.created_on ? new Date(data.created_on) : undefined,
    data.updated_on ? new Date(data.updated_on) : undefined,
    data.size,
    data.language,
    data.has_wiki,
    data.has_issues,
    data.fork_policy,
    data.mainbranch?.name,
    data.workspace?.slug,
    data.project?.key
  );
  return repo;
}
