const deepMerge = require('deepmerge');
const fs = require('fs');
const yaml = require('js-yaml');
const { stdout } = require('process');

function mergeAllOf(schema) {
  if (schema.allOf) {
    schema = schema.allOf.reduce((res, subschema) => {
      return deepMerge.all([res, subschema]);
    }, {});
  }

  if (schema.required) {
    schema.required = [...new Set(schema.required)];
  }

  if (schema.enum) {
    schema.enum = [...new Set(schema.enum)];
  }

  if (schema.properties) {
    Object.keys(schema.properties).forEach((key) => {
      schema.properties[key] = mergeAllOf(schema.properties[key]);
    });
  }

  delete schema.allOf;

  return schema;
}

(() => {
  const schema = yaml.load(fs.readFileSync(process.argv[2], 'utf8'));
  const original = schema.components.schemas.ConnectionProfile;
  const connectionProfile = mergeAllOf(original);
  if(original.description) {
    connectionProfile['description'] = original['description'];
  }
  if(original['x-ui-hidden']) {
    connectionProfile['x-ui-hidden'] = original['x-ui-hidden'];
  }
  if(original['x-ui-required']) {
    connectionProfile['x-ui-required'] = original['x-ui-required'];
  }
  stdout.write(yaml.dump(connectionProfile, null, 2));
})();
