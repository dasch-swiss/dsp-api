---
title: "API Model Changes Must Propagate Through Two Independent Type Channels to dsp-das"
date: 2026-03-08
category: "integration-issues"
component: "typescript_module"
module: "dsp-das/OpenAPI-Client, @dasch-swiss/dsp-js"
problem_type: "integration"
severity: "high"
symptoms: "TS2559 compilation errors when API model fields are added; structural type incompatibility between OpenAPI-generated interfaces and dsp-js-lib classes"
root_cause: "dsp-das consumes API models through two independent channels (OpenAPI codegen and dsp-js-lib) that must be updated separately when dsp-api changes"
tags:
  - openapi
  - typescript
  - type-mismatch
  - client-generation
  - dsp-api
  - dsp-das
  - dsp-js-lib
  - yalc
  - angular
  - npm-dependency-management
related:
  - learnings/best-practices/prefer-openapi-client-over-httpclient.md
---

# API Model Changes Must Propagate Through Two Independent Type Channels to dsp-das

## Problem

When `dsp-api` adds or changes a field on a model (e.g., adding `readOnlyStatus` to `Project`), `dsp-das` fails to compile with TypeScript errors like:

```
TS2559: Type 'Project' has no properties in common with type '{ readOnlyStatus?: string }'.
```

The confusing part: there are **two different `Project` types** in `dsp-das`, from two independent sources, and both need updating separately.

## Investigation

1. After adding `readOnlyStatus` to `dsp-api` and referencing it in `dsp-das`, 4 compilation errors appeared.
2. Attempted to widen the consuming function's parameter type to `Record<string, unknown>` — failed because class instances lack index signatures.
3. Tried `object` — compiled but is a type-safety anti-pattern.
4. Used structural type `{ readOnlyStatus?: string }` — fixed errors from dsp-js-lib `Project` (after yalc update) but OpenAPI `Project` still failed because the generated type literally did not have the property.
5. **Breakthrough:** Realized the two `Project` types come from independent sources that each need updating.

## Root Cause

`dsp-das` consumes `dsp-api` through **two independent type channels**:

1. **OpenAPI-generated client** — TypeScript interfaces generated from a local copy of the API's OpenAPI YAML spec (`libs/vre/3rd-party-services/open-api/dsp-api_spec.yaml`). Updated via `npm run update-openapi` which fetches from a running API instance.

2. **`@dasch-swiss/dsp-js-lib`** — A separate TypeScript library with hand-written model classes, published as an npm package. Updated by bumping the version or using `yalc` for local development.

Neither channel updates automatically when `dsp-api` changes. When a field is added to the API, **both** must be updated independently or TypeScript compilation breaks.

## Solution

### For dsp-js-lib (local development):

```bash
# In dsp-js-lib repo: build and publish locally
npm run build-local   # builds + yalc publish

# In dsp-das repo: consume the local version
yalc add @dasch-swiss/dsp-js && npm install
```

### For the OpenAPI channel:

**Option A — From running API** (normal workflow):
```bash
# In dsp-das repo: fetch spec from running API and regenerate
npm run update-openapi
```

**Option B — Manual spec update** (when API isn't deployed yet):
Add the new field to `libs/vre/3rd-party-services/open-api/dsp-api_spec.yaml`, then:
```bash
npm run generate-openapi-module
```

### Clean up yalc artifacts before committing:
```bash
# In dsp-das repo
rm -rf .yalc yalc.lock
git checkout -- package.json package-lock.json
```

## Prevention

### Checklist for API model changes

When a `dsp-api` PR adds/removes/renames a field on any model:
- [ ] Update the corresponding dsp-js-lib model class
- [ ] Run `npm run update-openapi` in dsp-das against the updated API
- [ ] Verify dsp-das compiles with both updated type sources
- [ ] Clean up any yalc artifacts before committing

### Longer-term improvements
- **Publish OpenAPI spec as CI artifact** from dsp-api on every merge to main
- **Add a cross-compile CI check** in dsp-das that validates both type channels
- **Consider generating dsp-js-lib models from the OpenAPI spec** to eliminate the dual-source problem entirely

## References

- Related: [Prefer OpenAPI client over HttpClient](../best-practices/prefer-openapi-client-over-httpclient.md)
- OpenAPI spec: `dsp-das/libs/vre/3rd-party-services/open-api/dsp-api_spec.yaml`
- Regeneration command: `npm run update-openapi` / `npm run generate-openapi-module`
- yalc local dev: `npm run build-local` (dsp-js-lib) → `yalc add` (dsp-das)
