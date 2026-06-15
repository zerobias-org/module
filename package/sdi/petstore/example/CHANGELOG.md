# Changelog

## 2.0.0

- Rebuilt on the Gradle + zbb toolchain (`zb.typescript-connector` plugin):
  ESM, `@zerobias-org/*` dependency stack (axios 1.x), `test/e2e/` layout,
  hub-sdk subpackage, Docker module image gate.
- api.yml hardening for the v2 codegen + dataloader: the `findByStatus`
  status filter now references a named `PetStatus` component enum (was an
  inline parameter enum), and operationIds are dot-free camelCase
  (`petList`, `petGet`, `storeGetOrder`, `storeGetInventory`, `userGet`).
- Producer/mapper/client logic carried over from 1.0.0-rc.1.

## 1.0.0-rc.1

- Initial release on the @auditmation/Lerna toolchain.
