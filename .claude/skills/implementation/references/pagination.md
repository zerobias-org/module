# Pagination of list operations

Read this before implementing or reviewing a list operation in a producer.

## Contents

- [When a list is paged at all](#when-a-list-is-paged-at-all)
- [What the producer receives](#what-the-producer-receives)
- [Choosing the mode](#choosing-the-mode)
- [Mode 1: offset/limit](#mode-1-offsetlimit)
- [Mode 2: client-side with `ingest`](#mode-2-client-side-with-ingest)
- [Mode 3: token](#mode-3-token)
- [Mistakes that produce wrong pages](#mistakes-that-produce-wrong-pages)
- [Review checklist](#review-checklist)

## When a list is paged at all

The producer signature is generated from `api.yml`. A list operation is generated as

```typescript
list(results: PagedResults<T>, ...otherParams): Promise<void>
```

only when its `200` response declares the `link` header (a `$ref` to the core `pagedLinkHeader`). Without
that header the same operation is generated as `list(...): Promise<Array<T>>` and the caller cannot page it.
So the first check is in `generated/api/index.ts`: if the signature has no `PagedResults`, the fix is in
`api.yml` (see the `api-spec` skill), followed by a regenerate. Do not work around it in `src/`.

Extra parameters (path ids, filters) come after `results`, in the order codegen emits them.

## What the producer receives

`PagedResults<T>` comes from `@zerobias-org/types-core-js`. The members a producer uses:

| Member | Direction | Meaning |
|---|---|---|
| `pageNumber` | in | 1-based page requested; defaults to 1 |
| `pageSize` | in | items per page; defaults to 50 |
| `pageToken` | in and out | cursor from the previous page; set it to the next cursor, or leave it undefined on the last page |
| `items` | out | the mapped items of this page |
| `count` | out | total number of items across all pages, when the API reports it |
| `ingest(all, pageNumber?, pageSize?, pageToken?)` | out | slices a full array into the requested page |
| `pageCount` | derived | computed from `count` and `pageSize`; read-only |

The method returns `void`: the result is what you wrote into `results`.

The `items` setter throws `ResultLimitExceededError` when it is given more items than `pageSize`. An
upstream API that ignores the requested limit therefore fails loudly instead of returning an oversized page;
if that happens, the API does not really support the limit parameter and mode 2 is the right one.

## Choosing the mode

Use exactly one mode per operation. The mode follows from what the upstream API can do, not from preference.

| Upstream API | Mode |
|---|---|
| accepts offset/limit, skip/top or page/per_page parameters | 1 offset/limit |
| returns the whole collection in one response and has no paging parameters | 2 client-side |
| returns a next-page cursor (body field, header, or next link) | 3 token |

Mixing modes gives inconsistent results: an offset request that also stores a `pageToken` makes the caller
switch to cursor paging on the second page, and a manual `items` assignment after `ingest` overwrites the
slice with the full array (which then throws, see above).

## Mode 1: offset/limit

Reference: `package/avigilon/alta/access/src/SiteProducerApiImpl.ts` (every list in that module has this
shape).

```typescript
async list(results: PagedResults<Site>, organizationId: string): Promise<void> {
  const params: Record<string, number> = {};

  if (results.pageNumber && results.pageSize) {
    params.offset = (results.pageNumber - 1) * results.pageSize;
    params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
  } else {
    params.offset = 0;
  }

  const response = await this.httpClient.get(`/orgs/${organizationId}/sites`, { params });

  if (!response.data || !Array.isArray(response.data.data)) {
    throw new UnexpectedError('Invalid response format: expected data array');
  }

  results.items = response.data.data.map((item) => toSite(item));
  results.count = response.data.totalCount || 0;
}
```

- `pageNumber` is 1-based, so the offset is `(pageNumber - 1) * pageSize`. Using `pageNumber * pageSize`
  skips the first page.
- Clamp the limit to the maximum the upstream API documents (1000 in this module). Sending more than the
  API accepts is either rejected or silently truncated.
- Use the parameter names of the upstream API (`offset`/`limit`, `$skip`/`$top`, `page`/`per_page`).
- Validate that the payload is an array before mapping, and throw `UnexpectedError` when it is not. A
  `?.map(...) || []` fallback turns a broken response into an empty page that looks like a valid result.
- Set `count` from the total the API reports. Do not use the length of the current page as the total: the
  caller would conclude there is only one page.
- Do not set `pageToken` in this mode.

## Mode 2: client-side with `ingest`

Reference: `package/nist/cmvp/src/CertificateProducerImpl.ts`. The CMVP registry returns one flat listing,
so the producer fetches it, maps all rows, and lets `PagedResults` cut the page:

```typescript
async list(results: PagedResults<CertificateSummary>): Promise<void> {
  let html: string;
  try {
    const response = await this.client.httpClient.get<string>(LISTING_PATH);
    html = response.data;
  } catch (error: any) {
    handleAxiosError(error, 'cmvp-listing', 'all');
  }

  const all = parseListingPage(html, this.client.baseUrl);

  results.ingest(all, results.pageNumber, results.pageSize);
}
```

What `ingest(all, pageNumber, pageSize)` does:

1. Throws `InvalidInputError` when `pageNumber` is below 1.
2. Computes the number of pages from `all.length` and `pageSize`, and throws `IllegalArgumentError` when the
   requested page is past the last one. An empty array counts as one (empty) page.
3. Sets `pageSize`, `pageNumber` and `pageToken` on the results.
4. Sets `count` to `all.length` and `items` to the slice for the requested page.

Rules:

- Pass the fully mapped array, not raw rows, so that `items` holds generated model objects.
- Pass `results.pageNumber` and `results.pageSize` through. Calling `ingest(all)` with no page arguments
  always returns page 1 with size 50, whatever the caller asked for.
- Do not assign `results.items` or `results.count` afterwards; `ingest` has set both.
- This mode downloads the whole collection on every page request. It is right for a source with no paging
  parameters and a bounded size. When the upstream API can page, use mode 1 or 3 instead.

The unit tests in `package/nist/cmvp/test/unit/CertificateProducerTest.test.ts` cover client-side paging, a
page past the end and the total count; reuse their shape (`testing` skill).

## Mode 3: token

Reference: `package/microsoft/azure/azureresourcegraph/src/ResourceProducerApiImpl.ts`. Azure Resource Graph
returns a `$skipToken` for the next page:

```typescript
async list(results: PagedResults<Resource>, subscriptionIds?: string[]): Promise<void> {
  const options: ArgQueryOptions = {};

  if (results.pageToken) {
    options.$skipToken = results.pageToken;
  } else if (results.pageSize) {
    options.$top = Math.min(Math.max(results.pageSize, 1), MAX_PAGE_SIZE);
  }

  const response = await this.client.query({ query: 'Resources', options /* plus scope */ });

  results.items = response.data.map(toResource);
  results.count = response.totalRecords || 0;
  results.pageToken = response.$skipToken;
}
```

- Send the incoming `results.pageToken` using the parameter name the upstream API expects.
- Always write the next cursor back to `results.pageToken`, including when the API returns none: leaving
  the old token in place on the last page makes the caller request the same page forever.
- Do not compute an offset from `pageNumber` in this mode; a cursor API ignores it or rejects it.
- Send the page size on the first request if the API accepts one. Whether it may be repeated together with
  the cursor depends on the API (Resource Graph ignores it once a token is present).
- Set `count` only when the API reports a total. Many cursor APIs do not; leave `count` unset rather than
  inventing one.
- In `api.yml` this mode uses the core `pageTokenParam` instead of `pageNumberParam` (`api-spec` skill).

## Mistakes that produce wrong pages

| Mistake | Effect | Fix |
|---|---|---|
| offset computed as `pageNumber * pageSize` | first page never returned | `(pageNumber - 1) * pageSize` |
| no upper bound on the limit | upstream rejects or truncates large pages | clamp to the documented maximum |
| `results.count = items.length` in mode 1 | caller sees a single page | use the API's total |
| `data?.map(...) \|\| []` | malformed response looks like an empty list | validate, throw `UnexpectedError` |
| assigning `items`/`count` after `ingest` | full array assigned, `ResultLimitExceededError` | let `ingest` set them |
| `ingest(all)` without page arguments | always page 1, size 50 | pass `results.pageNumber`, `results.pageSize` |
| old `pageToken` left set on the last page | caller loops | assign the new token even when undefined |
| paging by hand on a `Promise<Array<T>>` signature | caller still cannot page | add the `link` header in `api.yml`, regenerate |

## Review checklist

- The signature in the producer is the generated one (`PagedResults<T>` first, `Promise<void>`).
- One mode only; `pageToken` is touched only in mode 3, `ingest` only in mode 2.
- Items are mapped through `mappers.ts` before they are stored.
- The response shape is validated before mapping (modes 1 and 3).
- Request failures go through the module's error handling (`errors.md` in this folder).
- A unit test requests a page other than the first.
