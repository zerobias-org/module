# SDI Petstore Example Module

Hub module for the SDI Petstore example integration — the OpenAPI-defined
connector over the Swagger Petstore API. Paired with
`@zerobias-org/collectorbot-sdi-petstore-example`.

## Operations

| Tag | Operation | Method | Path |
|---|---|---|---|
| pet | `petList` | `list` | `GET /pet/findByStatus` |
| pet | `petGet` | `get` | `GET /pet/{petId}` |
| store | `storeGetOrder` | `getOrder` | `GET /store/order/{orderId}` |
| store | `storeGetInventory` | `getInventory` | `GET /store/inventory` |
| user | `userGet` | `get` | `GET /user/{username}` |

## Connection

`connectionProfile.yml` declares an optional `apiKey` — the Petstore demo
accepts but does not validate it; it is included to demonstrate the apiKey
auth pattern.

## Development

```bash
npm install
./gradlew :sdi:petstore:example:compile   # generate + tsc
./gradlew :sdi:petstore:example:gate       # full lifecycle + dataloader
# Local gate requires dataloader 1.x: npm i -g @zerobias-com/platform-dataloader@1.0.114
```

Source of truth is `api.yml`; generated TypeScript is not edited directly.
