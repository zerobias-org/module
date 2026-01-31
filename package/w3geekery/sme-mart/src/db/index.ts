import { neon } from '@neondatabase/serverless';
import { drizzle } from 'drizzle-orm/neon-http';
import type { NeonHttpDatabase } from 'drizzle-orm/neon-http';
import * as schema from './schema.js';

export type SmeMartDb = NeonHttpDatabase<typeof schema>;

let dbInstance: SmeMartDb | null = null;

/**
 * Initialize or return the Drizzle database client.
 * Called by SmeMartImpl.connect() with the databaseUrl from the connection profile.
 */
export function initDb(databaseUrl: string): SmeMartDb {
  const sql = neon(databaseUrl);
  dbInstance = drizzle(sql, { schema });
  return dbInstance;
}

/**
 * Get the current database instance. Throws if not initialized.
 */
export function getDb(): SmeMartDb {
  if (!dbInstance) {
    throw new Error('Database not initialized. Call initDb() first.');
  }
  return dbInstance;
}

export * from './schema.js';
