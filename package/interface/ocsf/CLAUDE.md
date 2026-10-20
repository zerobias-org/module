# OCSF Interface — AI/Developer Notes

Rules for working on this package. What implementers do lives in
[`documentation/Implementing.md`](documentation/Implementing.md); overview in
[`README.md`](README.md).

## Hard constraints

1. **`api.yml` is generated but committed — it is the interface.** Regenerate it with
   `npm run generate:api` (network to `schema.ocsf.io`) and commit it with the generator change
   that caused it; never edit it by hand. The build never regenerates it. `fixtures/generated/`
   is git-ignored; `fetchOcsfFixtures` runs before `testUnitExec`.
2. **The interface is OCSF's own models, never a product's data.** No field, class or
   operation exists because one source happens to emit it.
3. **Per-class typed methods.** `list<Class>` (startTime, endTime, paging) and `get<Class>`
   (by `metadata.uid`), plus `listClasses`. Adding a filter changes every list method's
   signature — decide it once, for all classes.
4. **No DataProducer here.** Producers may implement it separately; this interface does not
   reference it.
5. **Core OCSF only.** Extension attributes — and attributes only extension profiles
   (`linux/…`, `macos/…`) contribute — are dropped and listed in
   `info.x-ocsf-extension-attributes`.
6. **Models stay permissive.** No `required` lists, no OpenAPI `enum`s, no string `format`s:
   real events break each of them and the generated client would reject the whole event.
   Requirement levels and enum values go in descriptions. Changing this is a design decision,
   not a cleanup.

## Gotchas

- **Model names under 3 characters crash the hub-module codegen** (`toTsImports` in
  `org/util` strips a 3-character suffix). The generator names those by caption:
  `os` → `OperatingSystem`.
- **`api.yml` is written by the generator's own YAML emitter** (`toYaml`): the generator stays
  dependency-free so it runs on a fresh clone. Strings are JSON-quoted, which is valid YAML.
- **Don't regenerate `api.yml` from a build task.** Several plugin tasks read it, gradle fails
  a task that reads another task's undeclared output, and a build-time fetch would publish
  whatever the server returned that day, untested.
- **Fixtures are random** (ocsf-server `sample/` endpoint, regenerated each build). Tests
  check that every field is declared, not value shapes: the sample generator randomly emits a
  single value for array attributes.
- Gate-stamp inputs: the default set (including `api.yml`) plus `scripts/`
  (`project.extra["sourceDirs"]`), so a regenerated spec or a generator change needs a new gate.

## Checks

```bash
npm run generate:api                   # api.yml; fails on a dangling $ref
npm run generate:fixtures              # sample events for the tests
npx mocha 'test/unit/**/*Test.ts'      # spec shape; every 1.1.0/1.9.0 sample fits the models
./gradlew :interface:ocsf:gate         # from the repo root
```
