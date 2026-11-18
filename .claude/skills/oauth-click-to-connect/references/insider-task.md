# The insider task: external OAuth app and secret

Two things cannot be done as content, because they involve an app registered with the vendor and a
secret. Ask the user to open one task for exactly these when the authorization-code flow is chosen.
Fill in the placeholders; keep the scope to the two steps.

```
Title: Register <vendor> OAuth app + Secrets Manager entry for click-to-connect

The <module-package> connector is OAuth-capable. Its oauth provider package and the
connectionProfile link (x-oauth-providers: <vendor>.oauth) are written and loaded as content.
Two insider steps remain before the "Connect" button works:

  1. Register (or confirm) the <vendor> OAuth app with the provider and obtain client_id and
     client_secret; allow-list the ZeroBias shared app redirect URI; grant the scopes and any
     admin consent the module needs. For a reused provider such as Microsoft, the shared app
     may already exist.
  2. Store client_id and client_secret in AWS Secrets Manager (dev and prod), keyed to the
     <vendor>.oauth OAuth provider. Secrets cannot live in loadable content.

Scopes the module requests: <list from the module's client>
Module pull request: <link>
Provider pull request (if a new provider was added): <link>

Outcome: OAuth-enabled <vendor> connections show the click-to-connect button in Hub.
```

## What is not part of this task

- **The provider package.** If `@zerobias-org/oauth-<vendor>` does not exist, writing it is the
  contributor's job, a normal content pull request to `zerobias-org/oauth`
  (see `provider-package.md`).
- **OAuth client credentials.** A module where the user supplies a client id and secret directly
  (a service account, machine to machine) has no popup, no provider link and no insider task. It
  extends `oauthClientProfile.yml` and does not set `x-oauth-providers`. Many products expose such
  a service account from their admin console, exchanged at the product's own token endpoint. See
  the `connection-profile` skill.
