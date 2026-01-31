import { eq, asc, count } from 'drizzle-orm';
import type { UUID } from '@zerobias-org/types-core-js';
import { AdminProducerApi } from '../generated/api/AdminApi.js';
import type { AdminStats, Category, CategoryInput, InlineObject, Review } from '../generated/model/index.js';
import { getDb } from './db/index.js';
import {
  providerProfiles, serviceOfferings, workRequests,
  categories, reviews,
} from './db/schema.js';

export class AdminProducerApiImpl implements AdminProducerApi {

  async getStats(): Promise<AdminStats> {
    const db = getDb();

    const [providersCount] = await db.select({ value: count() }).from(providerProfiles);
    const [servicesCount] = await db.select({ value: count() }).from(serviceOfferings);
    const [requestsCount] = await db.select({ value: count() }).from(workRequests);
    const [categoriesCount] = await db.select({ value: count() }).from(categories);

    const recentProviders = await db.query.providerProfiles.findMany({
      orderBy: (pp, { desc }) => [desc(pp.createdAt)],
      limit: 5,
    });

    return {
      totalProviders: providersCount.value,
      totalServices: servicesCount.value,
      totalRequests: requestsCount.value,
      totalCategories: categoriesCount.value,
      recentProviders: recentProviders as any,
    } as AdminStats;
  }

  async listCategories(): Promise<Array<Category>> {
    const db = getDb();
    const result = await db.query.categories.findMany({
      orderBy: [asc(categories.sortOrder)],
    });
    return result as unknown as Array<Category>;
  }

  async createCategory(categoryInput: CategoryInput): Promise<Category> {
    const db = getDb();
    const slug = categoryInput.slug || categoryInput.name
      .toLowerCase().replaceAll(/[^\d\sa-z-]/g, '')
      .replaceAll(/\s+/g, '-').replaceAll(/-+/g, '-').replaceAll(/^-|-$/g, '');

    const [created] = await db.insert(categories).values({
      name: categoryInput.name,
      slug,
      description: categoryInput.description || null,
      parentId: categoryInput.parentId?.toString() || null,
      icon: categoryInput.icon || null,
      sortOrder: categoryInput.sortOrder ?? 0,
    }).returning();

    return created as unknown as Category;
  }

  async updateCategory(categoryId: UUID, categoryInput: CategoryInput): Promise<Category> {
    const db = getDb();
    const id = categoryId.toString();

    const updateData: Record<string, unknown> = {};
    if (categoryInput.name !== undefined) updateData.name = categoryInput.name;
    if (categoryInput.slug !== undefined) updateData.slug = categoryInput.slug;
    if (categoryInput.description !== undefined) updateData.description = categoryInput.description;
    if (categoryInput.parentId !== undefined) updateData.parentId = categoryInput.parentId?.toString();
    if (categoryInput.icon !== undefined) updateData.icon = categoryInput.icon;
    if (categoryInput.sortOrder !== undefined) updateData.sortOrder = categoryInput.sortOrder;

    const [updated] = await db.update(categories)
      .set(updateData)
      .where(eq(categories.id, id))
      .returning();

    return updated as unknown as Category;
  }

  async deleteCategory(categoryId: UUID): Promise<void> {
    const db = getDb();
    const id = categoryId.toString();
    await db.delete(categories).where(eq(categories.parentId, id));
    await db.delete(categories).where(eq(categories.id, id));
  }

  async listAllReviews(): Promise<Array<Review>> {
    const db = getDb();
    const result = await db.query.reviews.findMany({
      with: { provider: true },
      orderBy: (r, { desc }) => [desc(r.createdAt)],
    });
    return result as unknown as Array<Review>;
  }

  async moderateReview(reviewId: UUID, inlineObject: InlineObject): Promise<Review> {
    const db = getDb();
    const id = reviewId.toString();
    const updateData: Record<string, unknown> = {};

    const statusVal = String(inlineObject.status);
    if (statusVal === 'approved') {
      updateData.approved = true;
      updateData.approvedAt = new Date();
    } else if (statusVal === 'removed' || statusVal === 'flagged') {
      updateData.approved = false;
    }

    const [updated] = await db.update(reviews)
      .set(updateData)
      .where(eq(reviews.id, id))
      .returning();

    return updated as unknown as Review;
  }
}
