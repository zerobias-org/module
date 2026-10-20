import { expect } from 'chai';
import { readdirSync, readFileSync } from 'node:fs';
import yaml from 'js-yaml';

type Schema = {
  $ref?: string; allOf?: Schema[]; items?: Schema; properties?: Record<string, Schema>;
  'x-ocsf-class-uid'?: number; 'x-ocsf-name'?: string;
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const spec = yaml.load(readFileSync(new URL('../../api.yml', import.meta.url), 'utf8')) as any;
const models: Record<string, Schema> = spec.components.schemas;
const extensionAttributes: string[] = spec.info['x-ocsf-extension-attributes'];
const FIXTURES = new URL('../../fixtures/generated/', import.meta.url);

const modelName = (s: Schema): string | undefined => {
  const r = s.$ref ?? s.allOf?.[0]?.$ref ?? s.items?.$ref ?? s.items?.allOf?.[0]?.$ref;
  return r?.split('/').pop();
};
const classModel = (uid: number) => Object.keys(models).find((n) => models[n]['x-ocsf-class-uid'] === uid);

// Every field of an event must be declared by its model, recursively through objects.
// Array-ness is not checked: the ocsf-server sample generator randomly emits a single value
// for array attributes, and fixtures are regenerated on every build.
function undeclared(value: Record<string, unknown>, model: string, path: string, out: string[]) {
  const props = models[model].properties ?? {};
  for (const [key, v] of Object.entries(value)) {
    const p = props[key];
    if (!p) {
      if (!extensionAttributes.includes(`${models[model]['x-ocsf-name']}.${key}`)) out.push(`${path}.${key}`);
      continue;
    }
    const target = modelName(p);
    if (!target) continue;
    for (const item of (Array.isArray(v) ? v : [v])) {
      if (item && typeof item === 'object') undeclared(item as Record<string, unknown>, target, `${path}.${key}`, out);
    }
  }
}

describe('OCSF interface spec', function () {
  it('declares OCSF 1.9.0 classes and objects as models', function () {
    expect(spec.info['x-ocsf-version']).to.equal('1.9.0');
    expect(models).to.include.keys('Authentication', 'User', 'NetworkEndpoint', 'Metadata', 'OcsfClass');
    expect(classModel(3002)).to.equal('Authentication');
  });

  it('has a typed list and get operation per class', function () {
    const list = spec.paths['/iam/authentication'].get;
    expect(list.operationId).to.equal('listAuthentication');
    expect(list.parameters.map((p: Schema) => p.$ref?.split('/').pop()))
      .to.deep.equal(['startTimeParam', 'endTimeParam', 'pageNumberParam', 'pageSizeParam']);
    expect(list.responses['200'].content['application/json'].schema.items.$ref).to.equal('#/components/schemas/Authentication');
    expect(spec.paths['/iam/authentication/{uid}'].get.operationId).to.equal('getAuthentication');
    expect(spec.paths['/classes'].get.operationId).to.equal('listClasses');
  });

  it('declares the authentication fields evidence relies on', function () {
    expect(models.Authentication.properties).to.include.keys(
      'actor', 'user', 'status_id', 'is_mfa', 'src_endpoint', 'time', 'severity_id', 'unmapped', 'metadata');
    expect(modelName(models.Authentication.properties!.user)).to.equal('User');
  });

  it('marks profile attributes: 50 base + 30 from profiles on Authentication', function () {
    const props = Object.values(models.Authentication.properties!) as Record<string, unknown>[];
    expect(props.filter((p) => !p['x-ocsf-profiles'])).to.have.length(50);
    expect((models.Authentication.properties!.api as Record<string, unknown>)['x-ocsf-profiles']).to.deep.equal(['cloud']);
    expect((models.Authentication as Record<string, unknown>)['x-ocsf-profiles']).to.include('cloud');
  });

  for (const version of readdirSync(FIXTURES)) {
    it(`fits every ${version} sample event into the 1.9.0 models`, function () {
      const problems: string[] = [];
      for (const file of readdirSync(new URL(`${version}/`, FIXTURES))) {
        const event = JSON.parse(readFileSync(new URL(`${version}/${file}`, FIXTURES), 'utf8'));
        const model = classModel(event.class_uid);
        if (!model) { problems.push(`${file}: unknown class_uid ${event.class_uid}`); continue; }
        undeclared(event, model, file, problems);
      }
      expect(problems).to.deep.equal([]);
    });
  }
});
