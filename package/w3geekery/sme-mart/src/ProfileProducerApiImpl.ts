import { eq, and } from 'drizzle-orm';
import type { UUID } from '@zerobias-org/types-core-js';
import { ProfileProducerApi } from '../generated/api/ProfileApi.js';
import type {
  Provider, ProviderDetail, Review,
  ServiceOffering, ServiceOfferingInput,
  Skill, SkillInput, ProfileUpsertInput,
} from '../generated/model/index.js';
import { getDb } from './db/index.js';
import { providerProfiles, providerSkills, serviceOfferings } from './db/schema.js';

export class ProfileProducerApiImpl implements ProfileProducerApi {

  async get(): Promise<ProviderDetail> {
    // TODO: get zerobiasUserId from Hub auth context
    const db = getDb();
    const profile = await db.query.providerProfiles.findFirst({
      with: {
        skills: true,
        serviceOfferings: true,
        reviews: true,
      },
    });
    return (profile || null) as unknown as ProviderDetail;
  }

  async upsert(profileUpsertInput: ProfileUpsertInput): Promise<Provider> {
    // TODO: get zerobiasUserId from Hub auth context
    const db = getDb();
    const zerobiasUserId = 'hub-caller';

    const existing = await db.query.providerProfiles.findFirst({
      where: eq(providerProfiles.zerobiasUserId, zerobiasUserId),
    });

    if (existing) {
      const updateData: Record<string, unknown> = { updatedAt: new Date() };
      if (profileUpsertInput.displayName !== undefined) updateData.displayName = profileUpsertInput.displayName;
      if (profileUpsertInput.headline !== undefined) updateData.headline = profileUpsertInput.headline;
      if (profileUpsertInput.about !== undefined) updateData.about = profileUpsertInput.about;
      if (profileUpsertInput.avatarUrl !== undefined) updateData.avatarUrl = profileUpsertInput.avatarUrl;
      if (profileUpsertInput.hourlyRate !== undefined) updateData.hourlyRate = profileUpsertInput.hourlyRate;
      if (profileUpsertInput.availabilityStatus !== undefined) updateData.availabilityStatus = profileUpsertInput.availabilityStatus;
      if (profileUpsertInput.responseTime !== undefined) updateData.responseTime = profileUpsertInput.responseTime;

      const [updated] = await db.update(providerProfiles)
        .set(updateData)
        .where(eq(providerProfiles.id, existing.id))
        .returning();
      return updated as unknown as Provider;
    } else {
      const slug = (profileUpsertInput.displayName || zerobiasUserId)
        .toLowerCase().replaceAll(/[^\d\sa-z-]/g, '').replaceAll(/\s+/g, '-');
      const [created] = await db.insert(providerProfiles).values({
        zerobiasUserId,
        slug,
        displayName: profileUpsertInput.displayName || 'New Provider',
        headline: profileUpsertInput.headline,
        about: profileUpsertInput.about,
        avatarUrl: profileUpsertInput.avatarUrl,
        hourlyRate: profileUpsertInput.hourlyRate,
        availabilityStatus: (profileUpsertInput.availabilityStatus as any) || 'available',
        responseTime: profileUpsertInput.responseTime,
      }).returning();
      return created as unknown as Provider;
    }
  }

  async addSkill(skillInput: SkillInput): Promise<Skill> {
    const db = getDb();
    // TODO: resolve profile from Hub auth context
    const profile = await db.query.providerProfiles.findFirst();
    if (!profile) throw new Error('Profile not found');

    const [skill] = await db.insert(providerSkills).values({
      providerId: profile.id,
      skillName: skillInput.skillName,
      skillCategory: skillInput.skillCategory,
      proficiencyLevel: (skillInput.proficiencyLevel as any) || null,
      yearsExperience: skillInput.yearsExperience,
    }).returning();

    return skill as unknown as Skill;
  }

  async removeSkill(skillId: UUID): Promise<void> {
    const db = getDb();
    await db.delete(providerSkills).where(eq(providerSkills.id, skillId.toString()));
  }

  async addService(serviceOfferingInput: ServiceOfferingInput): Promise<ServiceOffering> {
    const db = getDb();
    // TODO: resolve profile from Hub auth context
    const profile = await db.query.providerProfiles.findFirst();
    if (!profile) throw new Error('Profile not found');

    const [service] = await db.insert(serviceOfferings).values({
      providerId: profile.id,
      title: serviceOfferingInput.title,
      description: serviceOfferingInput.description,
      category: serviceOfferingInput.category,
      subcategory: serviceOfferingInput.subcategory,
      pricingType: serviceOfferingInput.pricingType as any,
      price: serviceOfferingInput.price,
      deliveryTime: serviceOfferingInput.deliveryTime,
      includes: serviceOfferingInput.includes,
      requirements: serviceOfferingInput.requirements,
    }).returning();

    return service as unknown as ServiceOffering;
  }

  async updateService(serviceId: UUID, serviceOfferingInput: ServiceOfferingInput): Promise<ServiceOffering> {
    const db = getDb();
    const [updated] = await db.update(serviceOfferings)
      .set({
        title: serviceOfferingInput.title,
        description: serviceOfferingInput.description,
        category: serviceOfferingInput.category,
        subcategory: serviceOfferingInput.subcategory,
        pricingType: serviceOfferingInput.pricingType as any,
        price: serviceOfferingInput.price,
        deliveryTime: serviceOfferingInput.deliveryTime,
        includes: serviceOfferingInput.includes,
        requirements: serviceOfferingInput.requirements,
      })
      .where(eq(serviceOfferings.id, serviceId.toString()))
      .returning();

    return updated as unknown as ServiceOffering;
  }

  async removeService(serviceId: UUID): Promise<void> {
    const db = getDb();
    await db.delete(serviceOfferings).where(eq(serviceOfferings.id, serviceId.toString()));
  }

  async getProfileReviews(): Promise<Array<Review>> {
    const db = getDb();
    // TODO: resolve profile from Hub auth context
    const profile = await db.query.providerProfiles.findFirst({
      with: { reviews: true },
    });
    return (profile?.reviews || []) as unknown as Array<Review>;
  }
}
