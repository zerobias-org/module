import { eq } from 'drizzle-orm';
import type { UUID } from '@zerobias-org/types-core-js';
import { RequestProducerApi } from '../generated/api/RequestApi.js';
import { RequestApi } from '../generated/api/RequestApi.js';
import type { WorkRequest, WorkRequestInput, Proposal, ProposalInput } from '../generated/model/index.js';
import { getDb } from './db/index.js';
import { workRequests, proposals } from './db/schema.js';

export class RequestProducerApiImpl implements RequestProducerApi {

  async list(status?: RequestApi.StatusEnumDef): Promise<Array<WorkRequest>> {
    const db = getDb();
    const requests = await db.query.workRequests.findMany({
      with: { proposals: true },
      orderBy: (wr, { desc }) => [desc(wr.createdAt)],
    });

    if (status) {
      const val = status.toString();
      return requests.filter(r => r.status === val) as unknown as Array<WorkRequest>;
    }
    return requests as unknown as Array<WorkRequest>;
  }

  async create(workRequestInput: WorkRequestInput): Promise<WorkRequest> {
    const db = getDb();
    // TODO: get buyer identity from Hub auth context
    const [request] = await db.insert(workRequests).values({
      buyerZerobiasUserId: 'hub-caller',
      title: workRequestInput.title,
      description: workRequestInput.description,
      category: workRequestInput.category,
      budgetType: (workRequestInput.budgetType as any) || null,
      budgetMin: workRequestInput.budgetMin,
      budgetMax: workRequestInput.budgetMax,
      timeline: workRequestInput.timeline,
    }).returning();

    return request as unknown as WorkRequest;
  }

  async get(requestId: UUID): Promise<WorkRequest> {
    const db = getDb();
    const request = await db.query.workRequests.findFirst({
      where: eq(workRequests.id, requestId.toString()),
      with: { proposals: true },
    });

    return (request || null) as unknown as WorkRequest;
  }

  async update(requestId: UUID, workRequestInput: WorkRequestInput): Promise<WorkRequest> {
    const db = getDb();
    const updateData: Record<string, unknown> = {};
    if (workRequestInput.title !== undefined) updateData.title = workRequestInput.title;
    if (workRequestInput.description !== undefined) updateData.description = workRequestInput.description;
    if (workRequestInput.category !== undefined) updateData.category = workRequestInput.category;
    if (workRequestInput.budgetType !== undefined) updateData.budgetType = workRequestInput.budgetType;
    if (workRequestInput.budgetMin !== undefined) updateData.budgetMin = workRequestInput.budgetMin;
    if (workRequestInput.budgetMax !== undefined) updateData.budgetMax = workRequestInput.budgetMax;
    if (workRequestInput.timeline !== undefined) updateData.timeline = workRequestInput.timeline;

    const [updated] = await db.update(workRequests)
      .set(updateData)
      .where(eq(workRequests.id, requestId.toString()))
      .returning();

    return updated as unknown as WorkRequest;
  }

  async createProposal(proposalInput: ProposalInput): Promise<Proposal> {
    const db = getDb();
    // TODO: get provider identity from Hub auth context
    const [proposal] = await db.insert(proposals).values({
      requestId: proposalInput.requestId?.toString(),
      providerId: 'hub-caller',  // TODO: resolve from auth
      coverLetter: proposalInput.coverLetter,
      proposedPrice: proposalInput.proposedPrice,
      proposedTimeline: proposalInput.proposedTimeline,
    }).returning();

    return proposal as unknown as Proposal;
  }

  async updateProposal(proposalId: UUID, proposalInput: ProposalInput): Promise<Proposal> {
    const db = getDb();
    const updateData: Record<string, unknown> = {};
    if (proposalInput.coverLetter !== undefined) updateData.coverLetter = proposalInput.coverLetter;
    if (proposalInput.proposedPrice !== undefined) updateData.proposedPrice = proposalInput.proposedPrice;
    if (proposalInput.proposedTimeline !== undefined) updateData.proposedTimeline = proposalInput.proposedTimeline;

    const [updated] = await db.update(proposals)
      .set(updateData)
      .where(eq(proposals.id, proposalId.toString()))
      .returning();

    return updated as unknown as Proposal;
  }

  async deleteProposal(proposalId: UUID): Promise<void> {
    const db = getDb();
    await db.delete(proposals).where(eq(proposals.id, proposalId.toString()));
  }
}
