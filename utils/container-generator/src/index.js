const fs = require('fs');
const mustache = require('mustache');
const path = require('path');
const yaml = require('js-yaml');
const util = require('util');

function camelize(str) {
  const camel = str.replace(/\W+(.)/g, function(match, chr) {
    return chr.toUpperCase();
  });
  return camel[0].toLowerCase() + camel.slice(1);
}

function pascalize(str) {
  const camel = str.replace(/\W+(.)/g, function(match, chr) {
    return chr.toUpperCase();
  });
  return camel[0].toUpperCase() + camel.slice(1);
}

function render(templateFile, destFile) {
  const template = fs.readFileSync(path.join(__dirname, '..', 'templates', templateFile), 'utf8');
  const rendered = mustache.render(template, view);
  fs.writeFileSync(path.join(outDir, destFile), rendered);
}

const { name, location } = process.env
const outDir = path.join(process.cwd(), 'generated')

const apiPath = path.join(process.cwd(), `${name}.yml`);
const apiYaml = yaml.load(fs.readFileSync(apiPath, 'utf8'));
let implName = apiYaml.info['x-impl-name'];
if (!implName) implName = name;

const camel = camelize(implName);
const pascal = pascalize(implName);

let oauth = false;
let isSupported = false;
let esModuleInterop = false;

const implFilePath = path.join(process.cwd(), 'dist', 'src', `${pascal}Impl.js`);
if (fs.existsSync(implFilePath)) {
  const impl = require(implFilePath);
  const inst = new impl[`${pascal}Impl`]();

  if (inst.connect.length > 1) {
    oauth = true;
  }

  if (inst.isSupported.length > 0) {
    isSupported = true;
  }
}

const tsconfig = JSON.parse(fs.readFileSync(path.join(process.cwd(), 'tsconfig.json'), 'utf-8'));
if (tsconfig.compilerOptions.esModuleInterop) {
  esModuleInterop = tsconfig.compilerOptions.esModuleInterop;
}

const dryRun = process.argv[2] === '--dry-run' ? true : false;

const view = {
  name,
  camel,
  pascal,
  oauth,
  isSupported,
  esModuleInterop,
  dryRun,
};

render('server.ts.mustache', 'server.ts');
