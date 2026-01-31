import { eq } from 'drizzle-orm';
import type { UUID } from '@zerobias-org/types-core-js';
import { ProviderProducerApi } from '../generated/api/ProviderApi.js';
import type { Provider, ProviderDetail, Review, ReviewInput } from '../generated/model/index.js';
import { ProviderApi } from '../generated/api/ProviderApi.js';
import { getDb } from './db/index.js';
import { providerProfiles, reviews } from './db/schema.js';

export class ProviderProducerApiImpl implements ProviderProducerApi {

  async list(category?: string, search?: string, availability?: ProviderApi.AvailabilityEnumDef): Promise<Array<Provider>> {
    const db = getDb();
    const providers = await db.query.providerProfiles.findMany({
      with: {
        skills: true,
        serviceOfferings: true,
      },
    });

    let result = providers;

    if (category) {
      result = result.filter(p =>
        p.serviceOfferings.some(s => s.category === category)
      );
    }

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(p =>
        p.displayName.toLowerCase().includes(q) ||
        p.headline?.toLowerCase().includes(q)
      );
    }

    if (availability) {
      const val = availability.toString();
      result = result.filter(p => p.availabilityStatus === val);
    }

    return result as unknown as Array<Provider>;
  }

  async get(providerId: UUID): Promise<ProviderDetail> {
    const db = getDb();
    const provider = await db.query.providerProfiles.findFirst({
      where: eq(providerProfiles.id, providerId.toString()),
      with: {
        skills: true,
        serviceOfferings: true,
        reviews: true,
      },
    });

    return (provider || null) as unknown as ProviderDetail;
  }

  async submitReview(providerId: UUID, reviewInput: ReviewInput): Promise<Review> {
    const db = getDb();
    const [review] = await db.insert(reviews).values({
      providerId: providerId.toString(),
      reviewerZerobiasUserId: 'hub-caller',  // TODO: get from Hub auth context
      rating: reviewInput.rating,
      reviewText: reviewInput.reviewText || null,
    }).returning();

    return review as unknown as Review;
  }
}
