# Change Log

All notable changes to this project will be documented in this file.
See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

# 1.0.0 (2026-08-04)

### Features

* add Azure Resource Graph module serving the microsoft.azure.azureresourcegraph
  and microsoft.azure.subscription products (msgraph many-products precedent)
* `listResources` — queries the Resource Graph `Resources` table at tenant or
  subscription scope (api-version 2024-04-01, $skipToken paging)
* `listResourceContainers` — queries the `ResourceContainers` table
  (subscriptions, resource groups, management groups)

**Note:** implements 2 of 3 planned operations — `listSubscriptions` is
deliberately deferred to a later step.
