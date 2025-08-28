import {
  Connector,
  ConnectionMetadata,
  OperationSupportStatus,
  OperationSupportStatusDef,
  ConnectionStatus
} from '@auditmation/hub-core';
import { ConnectionProfile } from '../generated/model/ConnectionProfile';
import {
  Bitbucket,
  BitbucketConnector,
  BranchApi,
  BranchrestrictionApi,
  CommitApi,
  IssueApi,
  MemberApi,
  ProjectApi,
  PullrequestApi,
  RepositoryApi,
  WorkspaceApi,
  wrapBranchProducer,
  wrapBranchrestrictionProducer,
  wrapCommitProducer,
  wrapIssueProducer,
  wrapMemberProducer,
  wrapProjectProducer,
  wrapPullrequestProducer,
  wrapRepositoryProducer,
  wrapWorkspaceProducer
} from '../generated/api';
import { BitbucketClient } from './BitbucketClient';
import { BranchProducerApiImpl } from './BranchProducerApiImpl';
import { BranchrestrictionProducerApiImpl } from './BranchrestrictionProducerApiImpl';
import { CommitProducerApiImpl } from './CommitProducerApiImpl';
import { IssueProducerApiImpl } from './IssueProducerApiImpl';
import { MemberProducerApiImpl } from './MemberProducerApiImpl';
import { ProjectProducerApiImpl } from './ProjectProducerApiImpl';
import { PullrequestProducerApiImpl } from './PullrequestProducerApiImpl';
import { RepositoryProducerApiImpl } from './RepositoryProducerApiImpl';
import { WorkspaceProducerApiImpl } from './WorkspaceProducerApiImpl';

export class BitbucketImpl implements BitbucketConnector {
  private client: BitbucketClient;

  private branchApi?: BranchApi;

  private branchrestrictionApi?: BranchrestrictionApi;

  private commitApi?: CommitApi;

  private issueApi?: IssueApi;

  private memberApi?: MemberApi;

  private projectApi?: ProjectApi;

  private pullrequestApi?: PullrequestApi;

  private repositoryApi?: RepositoryApi;

  private workspaceApi?: WorkspaceApi;

  constructor() {
    this.client = new BitbucketClient();
  }

  async connect(profile: ConnectionProfile): Promise<void> {
    await this.client.connect(profile);
  }

  async isConnected(): Promise<boolean> {
    return this.client.isConnected();
  }

  async disconnect(): Promise<void> {
    await this.client.disconnect();
  }

  async isSupported(operationId: string): Promise<OperationSupportStatusDef> {
    return OperationSupportStatus.Maybe;
  }

  async metadata(): Promise<ConnectionMetadata> {
    return new ConnectionMetadata(ConnectionStatus.Down);
  }

  getBranchApi(): BranchApi {
    if (!this.branchApi) {
      this.branchApi = wrapBranchProducer(new BranchProducerApiImpl(this.client));
    }
    return this.branchApi;
  }

  getBranchrestrictionApi(): BranchrestrictionApi {
    if (!this.branchrestrictionApi) {
      this.branchrestrictionApi = wrapBranchrestrictionProducer(new BranchrestrictionProducerApiImpl(this.client));
    }
    return this.branchrestrictionApi;
  }

  getCommitApi(): CommitApi {
    if (!this.commitApi) {
      this.commitApi = wrapCommitProducer(new CommitProducerApiImpl(this.client));
    }
    return this.commitApi;
  }

  getIssueApi(): IssueApi {
    if (!this.issueApi) {
      this.issueApi = wrapIssueProducer(new IssueProducerApiImpl(this.client));
    }
    return this.issueApi;
  }

  getMemberApi(): MemberApi {
    if (!this.memberApi) {
      this.memberApi = wrapMemberProducer(new MemberProducerApiImpl(this.client));
    }
    return this.memberApi;
  }

  getProjectApi(): ProjectApi {
    if (!this.projectApi) {
      this.projectApi = wrapProjectProducer(new ProjectProducerApiImpl(this.client));
    }
    return this.projectApi;
  }

  getPullrequestApi(): PullrequestApi {
    if (!this.pullrequestApi) {
      this.pullrequestApi = wrapPullrequestProducer(new PullrequestProducerApiImpl(this.client));
    }
    return this.pullrequestApi;
  }

  getRepositoryApi(): RepositoryApi {
    if (!this.repositoryApi) {
      this.repositoryApi = wrapRepositoryProducer(new RepositoryProducerApiImpl(this.client));
    }
    return this.repositoryApi;
  }

  getWorkspaceApi(): WorkspaceApi {
    if (!this.workspaceApi) {
      this.workspaceApi = wrapWorkspaceProducer(new WorkspaceProducerApiImpl(this.client));
    }
    return this.workspaceApi;
  }
}
