import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function deriveSpecName(): string {
  const candidates = [
    path.join(__dirname, '../../../package.json'),
    path.join(__dirname, '../../../../package.json')
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) {
      const name = JSON.parse(fs.readFileSync(p, 'utf-8')).name as string;
      return `${name.replace(/^@[^/]+\//, '')}.yml`;
    }
  }
  throw new Error('package.json not found — cannot derive spec filename');
}

export function findSpecPath(): string {
  const specName = deriveSpecName();
  const searchPaths = [
    path.join(__dirname, '../../../dist', specName),
    path.join(__dirname, '../../../../dist', specName),
    path.join(__dirname, '../../../generated', specName),
    path.join(__dirname, '../../../../generated', specName),
    path.join(__dirname, '../../..', specName),
    path.join(__dirname, '../../../..', specName)
  ];
  const found = searchPaths.find((p) => fs.existsSync(p));
  if (!found) {
    throw new Error(`OpenAPI spec '${specName}' not found in any search path`);
  }
  return found;
}
