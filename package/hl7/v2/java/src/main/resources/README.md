# Generated schema content

This directory holds the **committed** output of the build-time schema codegen
(DESIGN §6, PLAN.md Phase 1). It is generated, but checked into git so diffs
across HL7 versions are reviewable and request-time has zero reflection.

Layout once Phase 1 lands:

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
