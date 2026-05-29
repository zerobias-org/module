# Generated schema content

This directory holds the output of the build-time schema codegen (DESIGN §6,
PLAN.md Phase 1). It is a **build artifact**: the `schemas/` and
`structure-index/` trees are generated into the jar's resources at build time
(by CI / the toolchain) and are **git-ignored**, not committed. Request-time
still serves them from the classpath with zero reflection.

> Note: this is a deliberate deviation from DESIGN §6 ("Generated JSON is
> checked into git") — decided 2026-05-29 to keep the tree out of version
> control. The build/CI MUST run the generator before packaging so the
> resources land in the jar (see PLAN.md Phase 1).

Layout produced under this directory:

```
schemas/
  v251/
    messages/   ADT_A01.json, ORU_R01.json, ...   schema:table:hl7v2.v251.<MSG>
    segments/   MSH.json, PID.json, ...            schema:type:hl7v2.v251.<SEG>
    datatypes/  CX.json, XPN.json, HD.json, ...    schema:type:hl7v2.v251.<DT>
    tables/     HL70001.json, ...                  schema:enum:hl7v2.v251.<HL7nnnn>
structure-index/
  v251.json     runtime materializer driver
```

Regenerate with:

```
mvn -f java/codegen/pom.xml compile exec:java \
  -Dexec.mainClass=com.zerobias.module.hl7.codegen.SchemaGenerator \
  -Dexec.args="v251 java/src/main/resources"
```

(or `mvn -Pregen-schemas generate-resources` from `java/`).
