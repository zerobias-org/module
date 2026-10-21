# @zerobias-org/module-interface-ocsf

OCSF ([Open Cybersecurity Schema Framework](https://schema.ocsf.io/)) 1.9.0 as a ZeroBias
module interface: the OCSF data models as an OpenAPI spec, with per-class methods that
return them. Any module that holds OCSF data implements it (OCSF Receiver, AWS Security
Lake, …); an OCSF collector reads any of them through the same typed client.

```ts
// a consumer, through the hub SDK client of any OCSF producer
const classes = await client.getClassesApiApi().listClasses();          // OcsfClass[]
const page    = await client.getIamApiApi().listAuthentication(startTime, endTime, 1, 100);
page.items.filter((e) => e.statusId === 2);   // typed Authentication; wire JSON keeps status_id
```

- `api.yml` — 79 event classes and 190 objects as models; `list<Class>` / `get<Class>` per
  class, tagged by category; `listClasses`. OCSF metadata rides along as extensions:
  `x-ocsf-class-uid` / `x-ocsf-category` on class models, `x-ocsf-object` naming the model of a
  nested object (models link by name, not `$ref`), `x-ocsf-profiles` on profile
  attributes and models, `info.x-ocsf-extension-attributes` for what is left out.
- `documentation/Implementing.md` — what an implementer does, and how the models map OCSF
  (no `required`, enums and requirement levels in descriptions, profiles).

## Regenerating

`api.yml` is the interface, so it is committed. It is generated from the OCSF schema server
and never edited by hand:

```bash
npm run generate:api        # rewrite api.yml from schema.ocsf.io — review the diff in the PR
npm run generate:fixtures   # test sample events (git-ignored; the build fetches them too)
```

The build never touches `api.yml`; it only fetches the sample events before the unit tests.

To move to a new OCSF version, change `OCSF_VERSION` in `scripts/generate.mjs`; renamed or
removed attributes make it a major version of this interface.

## Attribution

Generated from the OCSF schema ([ocsf/ocsf-schema](https://github.com/ocsf/ocsf-schema),
Apache-2.0) through the OCSF schema server
([ocsf/ocsf-server](https://github.com/ocsf/ocsf-server), Apache-2.0).
