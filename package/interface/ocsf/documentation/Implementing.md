# Implementing the OCSF interface

A module that holds OCSF data — the OCSF Receiver, AWS Security Lake, Splunk, S3, … —
implements this interface so one OCSF collector reads any of them.

## What the spec declares

Generated from the OCSF schema (schema.ocsf.io), core OCSF 1.9.0 only:

| OCSF | In `api.yml` | Example |
|---|---|---|
| Object | model | `User`, `NetworkEndpoint`, `Metadata` |
| Event class | model, with `x-ocsf-class-uid` / `x-ocsf-category` | `Authentication` (3002, `iam`) |
| Category | tag | `iam`, `findings`, `network` |
| Class operations | `GET /<category>/<class>` → `list<Class>` | `GET /iam/authentication` → `listAuthentication` |
| | `GET /<category>/<class>/{uid}` → `get<Class>` | `getAuthentication(uid)` |
| What a producer holds | `GET /classes` → `listClasses` | `[{uid: 3002, name: 'authentication', …}]` |

`list<Class>` takes `startTime` / `endTime` (OCSF `time`, epoch ms) and `pageNumber` /
`pageSize`, and returns the page as an array with `count` and `link` headers — the module
repo's paging convention.

## What an implementer does

1. Depend on `@zerobias-org/module-interface-ocsf` and `$ref` its paths from the bundled
   spec, like DataProducer implementers do:

   ```yaml
   paths:
     /classes:
       $ref: './node_modules/@zerobias-org/module-interface-ocsf/dist/module-interface-ocsf.yml#/paths/~1classes'
     /iam/authentication:
       $ref: './node_modules/@zerobias-org/module-interface-ocsf/dist/module-interface-ocsf.yml#/paths/~1iam~1authentication'
     /iam/authentication/{uid}:
       $ref: './node_modules/@zerobias-org/module-interface-ocsf/dist/module-interface-ocsf.yml#/paths/~1iam~1authentication~1{uid}'
   ```

2. `listClasses` returns the classes the producer can hold. For any other class, `list`
   returns an empty page and `get` a 404.
3. Return events as the source wrote them: no renaming, dropping or coercing.
   Normalization (placeholder values such as `"-"`, `"NULL SID"`) is the consumer's job.
   Keep each event's own `metadata.version`: events from OCSF 1.0–1.8 fit the 1.9.0 models
   (1.x is additive; the unit tests check 1.1.0 samples against them).
4. `list` filters on `time` and returns newest first; `get` looks up `metadata.uid`.
5. Pass through attributes the models do not declare (`unmapped`, extension attributes
   listed in `info.x-ocsf-extension-attributes`).

DataProducer is not part of this interface. A producer may implement it as well — the OCSF
Receiver does, for its upload inbox — but consumers of OCSF data use only these operations.

## Notes on the models

- No `required` lists and no string `format`s: real events miss OCSF-required attributes
  and carry dirty values, and the generated client would reject them. Requirement levels and
  enum values (`status_id`: `0 Unknown, 1 Success, 2 Failure, 99 Other`) are in each
  property's description.
- `json_t` attributes (`unmapped`, `data`, `api.request.data`) are free-form.
- Models include every core OCSF profile's attributes, because any event may declare any
  profile in `metadata.profiles` (a CloudTrail event declares `cloud` and carries `api` and
  `cloud`). Profile attributes are marked `x-ocsf-profiles: [cloud]`; unmarked ones are the
  class's base attributes — the default view on schema.ocsf.io (Authentication: 50 base + 30
  from profiles). Each model lists its profiles in `x-ocsf-profiles`. Extension profiles
  (`linux/…`, `macos/…`) are out of scope like other extension attributes.
- Consumers match on numeric `*_id` / `*_uid` values, never captions: OCSF renamed captions
  across versions (category 3 was "Audit Activity" in 1.0.0-rc.2; class 2001 Security
  Finding was split into 2002–2004).
