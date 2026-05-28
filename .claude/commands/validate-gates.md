---
description: Run all 6 quality gates for an ESM/gradle module (5–10 min)
argument-hint: <module-identifier>
---

Validate all 6 gates for module: $1

`$1` is `<vendor>-[<suite>-]<service>`. Run all commands from inside the module directory (`cd package/<vendor>/[<suite>/]<service>`).

The fastest way to run all 6 gates in one shot is `zbb gate --slot local` (Gate 6 driver — chains everything). What follows is a per-gate breakdown for when you want to know *which* gate failed without re-running the full chain.

## Gate 1 — API specification

Driver: `zbb bundleSpec`

Cheap pre-checks (don't run the task yet):

```bash
# Forbidden tokens in api.yml
grep -E "describe[A-Z]|nullable:|'40[0-9]'|'50[0-9]'" api.yml
grep -E "^\s*[a-z]+_[a-z]" api.yml                    # snake_case property names
grep -E "name: (apiKey|token|baseUrl|organizationId)" api.yml
# Each grep should print nothing.

# Inline schemas in response bodies
yq '.paths.*.*.responses.*.content.*.schema | select(has("properties"))' api.yml
# Should print business object schemas only, never response envelopes.
```

Pass: `bundleSpec` exits 0 + greps clean. Full checklist: `@.claude/skills/gate-api-spec/SKILL.md`.

## Gate 2 — Type generation

Driver: `zbb generate`

```bash
test -d generated/api && test -d generated/model && test -f generated/api/manifest.json
grep -rln "InlineResponse\|InlineRequestBody" generated/ \
  && echo "✗ inline-response leakage"
```

Pass: `zbb generate` exits 0, `generated/` populated, no `Inline*` leakage. The `hub-sdk/generated/` typed client is produced later by `buildHubSdk` (part of Gate 6's `build` chain), not here. Full checklist: `@.claude/skills/gate-type-generation/SKILL.md`.

## Gate 3 — Implementation

Drivers: `zbb compile` + `zbb lint`

```bash
grep -rn "Promise<any>" src/                       # forbidden
grep -rn "process\.env" src/                        # forbidden
grep -rnE "from '\.\.?\/[^']*'" src/ test/ | grep -v "\.js'"   # missing .js ext

# Connector lifecycle methods present
ls src/*Impl.ts | while read f; do
  for m in connect disconnect metadata isConnected isSupported; do
    grep -qE "async ${m}\s*\(" "$f" || echo "✗ $f missing async ${m}()"
  done
done
```

Pass: both tasks exit 0 + greps clean. Full checklist: `@.claude/skills/gate-implementation/SKILL.md`.

## Gate 4a — Unit tests (creation)

```bash
ls test/unit/*.test.ts >/dev/null
grep -hcE "^\s*describe\(" test/unit/*.test.ts
grep -hcE "^\s*it\("       test/unit/*.test.ts
grep -rn "import \* as nock" test/unit/             # forbidden (use default import)
grep -rnE "jest\.mock|from 'sinon'|fetch-mock|from 'msw'" test/unit/   # forbidden
grep -rn "process\.env" test/unit/                  # forbidden
```

Pass: at least 1 test file + 3 `it()` cases, nock default-import, no env access. Full checklist: `@.claude/skills/gate-unit-tests/SKILL.md`.

## Gate 4b — e2e tests (creation)

```bash
ls test/e2e/*.test.ts >/dev/null
test -f test/e2e/constants.ts
grep -rn "process\.env" test/e2e/ | grep -v "/constants.ts:"   # forbidden
grep -rn "from 'nock'" test/e2e/                                # forbidden
grep -rn "CoreError\.deserialize" test/e2e/                     # required
test -d test/integration                                        # MUST NOT EXIST
```

Pass: `describeModule<T>` skeleton wired correctly, no nock/dotenv, `constants.ts` is the sole env reader. Full checklist: `@.claude/skills/gate-integration-tests/SKILL.md`.

## Gate 5 — Test execution

```bash
zbb test       --slot local           # unit
zbb testDirect --slot local           # e2e direct
zbb testDocker --slot local           # e2e docker (connectors only)
```

Pass: all three exit 0. Full checklist: `@.claude/skills/gate-test-execution/SKILL.md`.

## Gate 6 — Build & stamp

```bash
zbb gate --slot local                                          # full lifecycle + stamp
zbb gateCheck                                                  # cheap re-validation
test -f gate-stamp.json
test -f dist/src/index.js && test -f dist/src/index.d.ts
ls dist/module-*.yml >/dev/null
test -f generated/api/manifest.json
test -f hub-sdk/generated/api/index.ts                         # typed client (produced by buildHubSdk inside build)
[ "$(jq -r .type package.json)" = "module" ] || echo "✗ package.json missing type=module"
jq -e '.zerobias.package' package.json >/dev/null || echo "✗ no zerobias.package"
test -f npm-shrinkwrap.json && { echo "✗ npm-shrinkwrap.json present (forbidden)"; exit 1; }
test -f package-lock.json || echo "✗ missing package-lock.json"
```

Pass: `zbb gate` exits 0, `gate-stamp.json` exists and matches source. Full checklist: `@.claude/skills/gate-build/SKILL.md`.

## Output format

```
VALIDATION RESULTS: <module-id>

Gate 1: API spec            [✓ PASS | ✗ FAIL]   bundleSpec
Gate 2: Type generation     [✓ PASS | ✗ FAIL]   generate
Gate 3: Implementation      [✓ PASS | ✗ FAIL]   compile + lint
Gate 4a: Unit tests         [✓ PASS | ✗ FAIL]   test/unit/ shape
Gate 4b: e2e tests          [✓ PASS | ✗ FAIL]   test/e2e/ shape
Gate 5: Test execution      [✓ PASS | ✗ FAIL]   zbb test/testDirect[/testDocker]
Gate 6: Build + stamp       [✓ PASS | ✗ FAIL]   zbb gate + gate-stamp.json

Overall: [✓ ALL GATES PASSED | ✗ FAILURES]

[Per-failure remediation pointing at the responsible skill.]
```

## Examples

```
/validate-gates github-github
/validate-gates amazon-aws-s3
```
