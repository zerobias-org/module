import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export function findSpecPath(): string {
  const here = dirname(fileURLToPath(import.meta.url));
  // src/impl/dataproducer/ at dev time, dist/src/impl/dataproducer/ when built
  const candidates = ['../../..', '../../../..'].map((up) => join(here, up));

  for (const root of candidates) {
    const pkgJson = join(root, 'package.json');
    if (!existsSync(pkgJson)) continue;
    const name: string = JSON.parse(readFileSync(pkgJson, 'utf-8')).name;
    const specName = `${name.split('/').pop()}.yml`;
    for (const dir of ['dist', 'generated', '.']) {
      const candidate = join(root, dir, specName);
      if (existsSync(candidate)) return candidate;
    }
  }
  throw new Error('Bundled OpenAPI spec not found — run `zbb generate` first');
}
