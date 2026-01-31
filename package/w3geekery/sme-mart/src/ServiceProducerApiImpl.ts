import { eq } from 'drizzle-orm';
import { ServiceProducerApi } from '../generated/api/ServiceApi.js';
import type { ServiceOffering } from '../generated/model/index.js';
import { getDb } from './db/index.js';
import { serviceOfferings } from './db/schema.js';

export class ServiceProducerApiImpl implements ServiceProducerApi {

  async list(category?: string, search?: string): Promise<Array<ServiceOffering>> {
    const db = getDb();
    const services = await db.query.serviceOfferings.findMany({
      where: eq(serviceOfferings.isActive, true),
      with: {
        provider: true,
      },
    });

    let result = services;

    if (category) {
      result = result.filter(s => s.category === category);
    }

    if (search) {
      const q = search.toLowerCase();
      result = result.filter(s =>
        s.title.toLowerCase().includes(q) ||
        s.description?.toLowerCase().includes(q)
      );
    }

    return result as unknown as Array<ServiceOffering>;
  }
}
