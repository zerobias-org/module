// Generates api.yml — the OCSF interface as an OpenAPI spec — from the OCSF schema server,
// which serves each class and object with inheritance and profiles resolved:
//   - components/schemas: every core OCSF class and object as a data model
//   - paths: per class, `list<Class>` (time range + paging) and `get<Class>` (by metadata.uid),
//     tagged by category, plus `listClasses` (which classes a producer holds)
//
//   npm run generate:api        # api.yml — committed; review the diff before merging
//   npm run generate:fixtures   # fixtures/generated/ — git-ignored test sample events
//
// The build never regenerates api.yml; it only fetches fixtures before the unit tests
// (build.gradle.kts). Plain node with no dependencies, so api.yml goes through `toYaml` below.
import { mkdir, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const OCSF_VERSION = '1.9.0';
// Versions sample events are also fetched for: Security Lake writes 1.1.0.
const FIXTURE_VERSIONS = ['1.1.0', OCSF_VERSION];
const SERVER = 'https://schema.ocsf.io';
// Core OCSF only; extensions (e.g. `win`) are out of scope for this interface version.
const CORE = '?extensions=';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pascal = (s) => s.replace(/(^|_)([a-z0-9])/g, (_, __, c) => c.toUpperCase());
const strip = (s) => (s ?? '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();

// Block YAML for plain JSON data. Scalars and odd keys go through JSON.stringify: a JSON
// string is a valid YAML double-quoted scalar. Each value renders with its leading space or
// newline, so it can follow `key:` or `-`.
const yamlKey = (k) => (/^[A-Za-z_][\w-]*$/.test(k) ? k : JSON.stringify(k));
function toYaml(v, indent = '') {
  if (Array.isArray(v)) {
    const item = (x) => {
      const r = toYaml(x, `${indent}  `);
      // A mapping item starts on the dash line: `- key: value`.
      return r.startsWith(`\n${indent}  `) && !Array.isArray(x) ? ` ${r.slice(indent.length + 3)}` : r;
    };
    return v.length ? v.map((x) => `\n${indent}-${item(x)}`).join('') : ' []';
  }
  if (v && typeof v === 'object') {
    const entries = Object.entries(v);
    return entries.length
      ? entries.map(([k, x]) => `\n${indent}${yamlKey(k)}:${toYaml(x, `${indent}  `)}`).join('')
      : ' {}';
  }
  return ` ${JSON.stringify(v)}`;
}

async function get(path) {
  for (let attempt = 1; ; attempt++) {
    let failure;
    try {
      // Timeout: a server that accepts and then stalls must not hang the build.
      const res = await fetch(`${SERVER}${path}${path.includes('?') ? '' : CORE}`, { signal: AbortSignal.timeout(30_000) });
      if (res.ok) return await res.json();
      failure = `HTTP ${res.status}`;
    } catch (e) {
      failure = e.message; // network error, DNS, timeout: retried like an HTTP error
    }
    if (attempt === 3) throw new Error(`${path}: ${failure}`);
    await new Promise((r) => setTimeout(r, 1000 * attempt));
  }
}

async function mapLimit(items, limit, fn) {
  const out = new Array(items.length);
  let next = 0;
  await Promise.all(Array.from({ length: limit }, async () => {
    while (next < items.length) { const i = next++; out[i] = await fn(items[i]); }
  }));
  return out;
}

// OCSF scalar types. Strings carry no `format`: source data is dirty ("-" as an IP), and a
// typed format would make the generated client reject whole events.
const SCALARS = {
  boolean_t: { type: 'boolean' },
  float_t: { type: 'number', format: 'double' },
  integer_t: { type: 'integer', format: 'int32' },
  port_t: { type: 'integer', format: 'int32' },
  long_t: { type: 'integer', format: 'int64' },
  timestamp_t: { type: 'integer', format: 'int64' },
  json_t: {},
};

const extensionAttributes = new Set();
// Extension profiles are namespaced (`linux/linux_users`); core OCSF only.
const coreProfiles = (profiles) => (profiles ?? []).filter((p) => !p.includes('/'));
const ref = (model) => ({ $ref: `#/components/schemas/${model}` });

function property(owner, name, a, modelOf) {
  const notes = [strip(a.caption), strip(a.description)];
  if (a.enum) {
    notes.push(`Values: ${Object.entries(a.enum).sort(([x], [y]) => Number(x) - Number(y))
      .map(([v, e]) => `${v} ${e.caption}`).join(', ')}.`);
  }
  if (a.requirement) notes.push(`OCSF requirement: ${a.requirement}.`);
  const description = notes.filter(Boolean).join(' — ');
  let schema;
  if (a.type === 'object_t') {
    const target = modelOf.get(a.object_type);
    if (!target) throw new Error(`${owner}.${name}: unknown object ${a.object_type}`);
    schema = ref(target);
  } else {
    schema = SCALARS[a.type] ?? { type: 'string' };
  }
  if (a.is_array) schema = { type: 'array', items: schema };
  // A $ref cannot carry siblings in OpenAPI 3.0: wrap it to keep the description.
  const out = schema.$ref ? { allOf: [schema], description } : { ...schema, description };
  if (a['@deprecated']) out.deprecated = true;
  // Present only when the event declares one of these in metadata.profiles; absent = base attribute.
  const profiles = coreProfiles(a.profiles);
  if (profiles.length) out['x-ocsf-profiles'] = profiles;
  return out;
}

function model(def, modelOf) {
  const properties = {};
  for (const [name, a] of Object.entries(def.attributes).sort(([x], [y]) => x.localeCompare(y))) {
    // `?extensions=` still leaves extension-contributed attributes on core objects.
    // Same for attributes that only extension profiles (`linux/linux_users`) contribute.
    const fromExtensionProfiles = a.profiles?.length && !coreProfiles(a.profiles).length;
    if (a.extension || fromExtensionProfiles) { extensionAttributes.add(`${def.name}.${name}`); continue; }
    properties[name] = property(def.name, name, a, modelOf);
  }
  // No `required`: real events often miss OCSF-required attributes, and the generated client
  // would reject them. The requirement is in each property's description.
  return {
    type: 'object',
    description: strip(def.description),
    properties,
    ...(def['@deprecated'] ? { deprecated: true } : {}),
    // Profiles this class or object supports; their attributes are included, marked per property.
    ...(coreProfiles(def.profiles).length ? { 'x-ocsf-profiles': coreProfiles(def.profiles) } : {}),
  };
}

async function generateApi() {
  const categories = (await get(`/${OCSF_VERSION}/api/categories`)).attributes;
  const classList = await get(`/${OCSF_VERSION}/api/classes`);
  const objectList = await get(`/${OCSF_VERSION}/api/objects`);
  const classes = await mapLimit(classList.map((c) => c.name), 8, (n) => get(`/${OCSF_VERSION}/api/classes/${n}`));
  const objects = await mapLimit(objectList.map((o) => o.name), 8, (n) => get(`/${OCSF_VERSION}/api/objects/${n}`));

  // Model names: objects keep their OCSF name (User, NetworkEndpoint); a class whose name
  // collides with an object gets an `Event` suffix.
  // Names under 3 characters (`os`) crash the hub-module codegen (toTsImports strips a 3-char
  // suffix): those use their caption instead — `os` → OperatingSystem.
  const modelName = (o) => (o.name.length < 3 ? o.caption.replace(/\(.*?\)/g, '').replace(/[^A-Za-z0-9]+(.)?/g, (_, c) => (c ?? '').toUpperCase()).replace(/^./, (c) => c.toUpperCase()) : pascal(o.name));
  const modelOf = new Map(objects.map((o) => [o.name, modelName(o)]));
  const classModel = new Map(classes.map((c) => [c.name, modelOf.has(c.name) ? `${pascal(c.name)}Event` : pascal(c.name)]));
  const names = [...modelOf.values(), ...classModel.values(), 'OcsfClass'];
  if (new Set(names).size !== names.length) throw new Error('model name collision');

  const schemas = {
    OcsfClass: {
      type: 'object',
      description: 'An OCSF event class a producer can return.',
      required: ['uid', 'name', 'caption', 'categoryUid', 'category'],
      properties: {
        uid: { type: 'integer', format: 'int32', description: 'class_uid, e.g. 3002' },
        name: { type: 'string', description: 'OCSF class name, e.g. authentication' },
        caption: { type: 'string', description: 'e.g. Authentication' },
        categoryUid: { type: 'integer', format: 'int32', description: 'category_uid, e.g. 3' },
        category: { type: 'string', description: 'OCSF category name, e.g. iam' },
      },
    },
  };
  for (const o of objects) schemas[modelOf.get(o.name)] = { ...model(o, modelOf), 'x-ocsf-name': o.name };
  for (const c of classes) {
    schemas[classModel.get(c.name)] = {
      ...model(c, modelOf),
      'x-ocsf-name': c.name,
      'x-ocsf-caption': c.caption,
      'x-ocsf-class-uid': c.uid,
      'x-ocsf-category': c.category ?? null,
      'x-ocsf-category-uid': c.category_uid ?? null,
    };
  }

  const pageHeaders = {
    link: { $ref: '#/components/headers/pagedLinkHeader' },
    count: { $ref: '#/components/headers/pageCountHeader' },
  };
  const paths = {
    '/classes': {
      get: {
        tags: ['classes'],
        operationId: 'listClasses',
        summary: 'List the OCSF classes this producer holds',
        description: 'Only these classes\' list/get operations return data on this producer.',
        responses: {
          200: {
            description: 'Classes',
            content: { 'application/json': { schema: { type: 'array', items: ref('OcsfClass') } } },
          },
        },
      },
    },
  };

  const tags = [{ name: 'classes', description: 'Classes a producer holds' }];
  const coreClasses = new Set(classList.map((c) => c.name));
  for (const [catName, cat] of Object.entries(categories).sort(([, a], [, b]) => a.uid - b.uid)) {
    tags.push({ name: catName, description: `${cat.caption} (category_uid ${cat.uid})` });
    for (const [cn, cl] of Object.entries(cat.classes).sort(([, a], [, b]) => a.uid - b.uid)) {
      if (!coreClasses.has(cn)) continue;
      const m = classModel.get(cn);
      const deprecated = cl['@deprecated'] ? { deprecated: true } : {};
      paths[`/${catName}/${cn}`] = {
        get: {
          tags: [catName],
          operationId: `list${m}`,
          summary: `List ${cl.caption} events (class_uid ${cl.uid})`,
          parameters: [
            { $ref: '#/components/parameters/startTimeParam' },
            { $ref: '#/components/parameters/endTimeParam' },
            { $ref: '#/components/parameters/pageNumberParam' },
            { $ref: '#/components/parameters/pageSizeParam' },
          ],
          responses: {
            200: {
              description: `${cl.caption} events, newest first`,
              headers: pageHeaders,
              content: { 'application/json': { schema: { type: 'array', items: ref(m) } } },
            },
          },
          ...deprecated,
        },
      };
      paths[`/${catName}/${cn}/{uid}`] = {
        get: {
          tags: [catName],
          operationId: `get${m}`,
          summary: `Get one ${cl.caption} event by metadata.uid`,
          parameters: [{ $ref: '#/components/parameters/uidParam' }],
          responses: {
            200: { description: `The ${cl.caption} event`, content: { 'application/json': { schema: ref(m) } } },
            404: { $ref: '#/components/responses/NoSuchObjectError' },
          },
          ...deprecated,
        },
      };
    }
  }

  const timeParam = (name, which) => ({
    name, in: 'query', required: false,
    description: `${which} of the event time range: OCSF \`time\`, epoch milliseconds.`,
    schema: { type: 'integer', format: 'int64' },
  });

  const spec = {
    openapi: '3.0.3',
    info: {
      'x-product-infos': [{ $ref: './node_modules/@zerobias-org/product-zerobias-zerobias/catalog.yml#/Product' }],
      'x-impl-name': 'Ocsf',
      version: '1.0.0',
      title: '@zerobias-org/module-interface-ocsf',
      description: `OCSF (Open Cybersecurity Schema Framework) ${OCSF_VERSION} interface: the OCSF event `
        + 'classes and objects as models, and per class a list (time range, paging) and a get '
        + 'operation. Any module that holds OCSF data implements it; an OCSF collector reads any of '
        + 'them. Generated from schema.ocsf.io by scripts/generate.mjs.',
      'x-ocsf-version': OCSF_VERSION,
      'x-ocsf-extension-attributes': [...extensionAttributes].sort(),
    },
    tags,
    paths,
    components: {
      parameters: {
        uidParam: { name: 'uid', in: 'path', required: true, description: 'The event\'s metadata.uid', schema: { type: 'string' } },
        startTimeParam: timeParam('startTime', 'Start (inclusive)'),
        endTimeParam: timeParam('endTime', 'End (exclusive)'),
        pageNumberParam: {
          name: 'pageNumber', in: 'query', description: 'The requested page (1-indexed)',
          schema: { type: 'integer', format: 'int32', default: 1, minimum: 1 },
        },
        pageSizeParam: {
          name: 'pageSize', in: 'query', description: 'The number of items in each page',
          schema: { type: 'integer', format: 'int32', default: 100, minimum: 1, maximum: 1000 },
        },
      },
      headers: {
        pageCountHeader: { description: 'The total count of elements in the result set', schema: { type: 'integer', format: 'int64' } },
        pagedLinkHeader: { description: 'Links for paging results (first, prev, next, last)', schema: { type: 'array', items: { type: 'string' } } },
      },
      responses: {
        NoSuchObjectError: {
          description: 'No event with that uid',
          content: { 'application/json': { schema: { $ref: './node_modules/@zerobias-org/types-core/schema/errors/noSuchObjectError.yml' } } },
        },
      },
      schemas,
    },
  };

  // Self-check: every local $ref resolves.
  const refs = JSON.stringify(spec).match(/"#\/components\/(\w+)\/([^"]+)"/g) ?? [];
  for (const r of refs) {
    const [, kind, name] = r.slice(1, -1).split('/').slice(1);
    if (!spec.components[kind]?.[name]) throw new Error(`dangling ${r}`);
  }

  await writeFile(join(root, 'api.yml'),
    `# GENERATED by scripts/generate.mjs (npm run generate:api) from ${SERVER}/${OCSF_VERSION} — do not edit by hand.\n${toYaml(spec).trimStart()}\n`);
  console.log(`api.yml: OCSF ${OCSF_VERSION}, ${classes.length} classes, ${objects.length} objects, ${Object.keys(paths).length} paths`);
}

// Fixtures: one random but schema-complete sample event per class and fixture version.
async function generateFixtures() {
  const MAX_FIXTURE_BYTES = 256 * 1024; // shortcut: random samples can reach ~1 MB; skip those, raise if a class needs one
  await rm(join(root, 'fixtures', 'generated'), { recursive: true, force: true });
  for (const v of FIXTURE_VERSIONS) {
    const list = (await get(`/${v}/api/classes`)).map((c) => c.name).filter((n) => n !== 'base_event');
    await mkdir(join(root, 'fixtures', 'generated', v), { recursive: true });
    const kept = await mapLimit(list, 8, async (n) => {
      const body = JSON.stringify(await get(`/${v}/sample/classes/${n}`));
      if (body.length > MAX_FIXTURE_BYTES) return 0;
      await writeFile(join(root, 'fixtures', 'generated', v, `${n}.json`), `${body}\n`);
      return 1;
    });
    console.log(`fixtures/generated/${v}: ${kept.reduce((a, b) => a + b, 0)} of ${list.length} samples`);
  }
}

const modes = { '--api': generateApi, '--fixtures': generateFixtures };
const chosen = process.argv.slice(2).filter((a) => modes[a]);
if (!chosen.length) throw new Error('usage: node scripts/generate.mjs --api | --fixtures');
for (const m of chosen) await modes[m]();
