import { pgTable, uuid, text, decimal, integer, boolean, timestamp, pgEnum } from 'drizzle-orm/pg-core';
import { relations } from 'drizzle-orm';

// Enums
export const availabilityStatusEnum = pgEnum('availability_status', ['available', 'busy', 'unavailable']);
export const pricingTypeEnum = pgEnum('pricing_type', ['fixed', 'hourly', 'subscription', 'custom']);
export const budgetTypeEnum = pgEnum('budget_type', ['fixed', 'hourly', 'negotiable']);
export const requestStatusEnum = pgEnum('request_status', ['open', 'in_progress', 'completed', 'cancelled']);
export const proposalStatusEnum = pgEnum('proposal_status', ['pending', 'accepted', 'rejected', 'withdrawn']);
export const proficiencyLevelEnum = pgEnum('proficiency_level', ['beginner', 'intermediate', 'expert']);

// Provider profiles (extends ZeroBias User)
export const providerProfiles = pgTable('provider_profiles', {
  id: uuid('id').primaryKey().defaultRandom(),
  slug: text('slug').notNull().unique(),
  zerobiasUserId: text('zerobias_user_id').notNull().unique(),
  zerobiasOrgId: text('zerobias_org_id'),
  displayName: text('display_name').notNull(),
  headline: text('headline'),
  about: text('about'),
  avatarUrl: text('avatar_url'),
  hourlyRate: decimal('hourly_rate', { precision: 10, scale: 2 }),
  availabilityStatus: availabilityStatusEnum('availability_status').default('available'),
  responseTime: text('response_time'),
  totalJobsCompleted: integer('total_jobs_completed').default(0),
  totalEarnings: decimal('total_earnings', { precision: 12, scale: 2 }).default('0'),
  ratingAverage: decimal('rating_average', { precision: 3, scale: 2 }),
  createdAt: timestamp('created_at').defaultNow(),
  updatedAt: timestamp('updated_at').defaultNow()
});

// Provider skills/expertise
export const providerSkills = pgTable('provider_skills', {
  id: uuid('id').primaryKey().defaultRandom(),
  providerId: uuid('provider_id').references(() => providerProfiles.id, { onDelete: 'cascade' }),
  skillName: text('skill_name').notNull(),
  skillCategory: text('skill_category'),
  proficiencyLevel: proficiencyLevelEnum('proficiency_level'),
  yearsExperience: integer('years_experience'),
  verified: boolean('verified').default(false)
});

// Service offerings
export const serviceOfferings = pgTable('service_offerings', {
  id: uuid('id').primaryKey().defaultRandom(),
  providerId: uuid('provider_id').references(() => providerProfiles.id, { onDelete: 'cascade' }),
  title: text('title').notNull(),
  description: text('description'),
  category: text('category').notNull(),
  subcategory: text('subcategory'),
  pricingType: pricingTypeEnum('pricing_type').notNull(),
  price: decimal('price', { precision: 10, scale: 2 }),
  deliveryTime: text('delivery_time'),
  includes: text('includes').array(),
  requirements: text('requirements'),
  isActive: boolean('is_active').default(true),
  createdAt: timestamp('created_at').defaultNow()
});

// Work requests from buyers
export const workRequests = pgTable('work_requests', {
  id: uuid('id').primaryKey().defaultRandom(),
  buyerZerobiasUserId: text('buyer_zerobias_user_id').notNull(),
  buyerZerobiasOrgId: text('buyer_zerobias_org_id'),
  title: text('title').notNull(),
  description: text('description'),
  category: text('category').notNull(),
  budgetType: budgetTypeEnum('budget_type'),
  budgetMin: decimal('budget_min', { precision: 10, scale: 2 }),
  budgetMax: decimal('budget_max', { precision: 10, scale: 2 }),
  timeline: text('timeline'),
  status: requestStatusEnum('status').default('open'),
  zerobiasBoundaryId: text('zerobias_boundary_id'),
  zerobiasTaskId: text('zerobias_task_id'),
  createdAt: timestamp('created_at').defaultNow()
});

// Proposals from providers
export const proposals = pgTable('proposals', {
  id: uuid('id').primaryKey().defaultRandom(),
  requestId: uuid('request_id').references(() => workRequests.id, { onDelete: 'cascade' }),
  providerId: uuid('provider_id').references(() => providerProfiles.id, { onDelete: 'cascade' }),
  coverLetter: text('cover_letter'),
  proposedPrice: decimal('proposed_price', { precision: 10, scale: 2 }),
  proposedTimeline: text('proposed_timeline'),
  status: proposalStatusEnum('status').default('pending'),
  createdAt: timestamp('created_at').defaultNow()
});

// Reviews and ratings
export const reviews = pgTable('reviews', {
  id: uuid('id').primaryKey().defaultRandom(),
  providerId: uuid('provider_id').references(() => providerProfiles.id, { onDelete: 'cascade' }),
  reviewerZerobiasUserId: text('reviewer_zerobias_user_id').notNull(),
  requestId: uuid('request_id').references(() => workRequests.id),
  rating: integer('rating').notNull(),
  reviewText: text('review_text'),
  approved: boolean('approved').default(false),
  approvedAt: timestamp('approved_at'),
  approvedBy: text('approved_by'),
  createdAt: timestamp('created_at').defaultNow()
});

// Categories taxonomy
export const categories = pgTable('categories', {
  id: uuid('id').primaryKey().defaultRandom(),
  name: text('name').notNull().unique(),
  slug: text('slug').notNull().unique(),
  description: text('description'),
  parentId: uuid('parent_id'),
  icon: text('icon'),
  sortOrder: integer('sort_order').default(0)
});

// Relations
export const providerProfilesRelations = relations(providerProfiles, ({ many }) => ({
  skills: many(providerSkills),
  serviceOfferings: many(serviceOfferings),
  proposals: many(proposals),
  reviews: many(reviews)
}));

export const providerSkillsRelations = relations(providerSkills, ({ one }) => ({
  provider: one(providerProfiles, {
    fields: [providerSkills.providerId],
    references: [providerProfiles.id]
  })
}));

export const serviceOfferingsRelations = relations(serviceOfferings, ({ one }) => ({
  provider: one(providerProfiles, {
    fields: [serviceOfferings.providerId],
    references: [providerProfiles.id]
  })
}));

export const workRequestsRelations = relations(workRequests, ({ many }) => ({
  proposals: many(proposals),
  reviews: many(reviews)
}));

export const proposalsRelations = relations(proposals, ({ one }) => ({
  request: one(workRequests, {
    fields: [proposals.requestId],
    references: [workRequests.id]
  }),
  provider: one(providerProfiles, {
    fields: [proposals.providerId],
    references: [providerProfiles.id]
  })
}));

export const reviewsRelations = relations(reviews, ({ one }) => ({
  provider: one(providerProfiles, {
    fields: [reviews.providerId],
    references: [providerProfiles.id]
  }),
  request: one(workRequests, {
    fields: [reviews.requestId],
    references: [workRequests.id]
  })
}));

export const categoriesRelations = relations(categories, ({ one, many }) => ({
  parent: one(categories, {
    fields: [categories.parentId],
    references: [categories.id],
    relationName: 'parentChild'
  }),
  children: many(categories, { relationName: 'parentChild' })
}));
