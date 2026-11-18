// Minimum DataProducer blocks for the module's e2e suite (test/e2e/*.test.ts).
// Paste inside the existing describeModule<T>(..., (client) => { ... }) callback.
describe('DataProducer', () => {
  describe('ObjectsApi', () => {
    it('returns the root object', async () => {
      const root = await client.getObjectsApi().getObject('/');
      expect(root.id).to.equal('/');
    });
    it('rejects unknown ids', async () => {
      const err = await client.getObjectsApi().getObject('invalid_id').catch((e) => e);
      expect(err.message).to.include('No such');
    });
    // Required: this is the only test that catches an ambiguous ID grammar
    // before a consumer does.
    it('round-trips child ids', async () => {
      const children = await client.getObjectsApi().getChildren('/', 1, 5);
      expect(children.items).to.not.be.empty;
      const again = await client.getObjectsApi().getObject(children.items[0].id);
      expect(again.id).to.equal(children.items[0].id); // every emitted id must parse back
    });
  });
  describe('CollectionsApi', () => {
    it('lists elements', /* build a collection_... id from the constants file */);
    it('returns empty (not an error) when the vendor 404s an empty listing', /* ... */);
  });
  describe('SchemasApi', () => {
    it('round-trips schema ids', async () => {
      const s = await client.getSchemasApi().getSchema('schema:shared:acme.acme.organization');
      expect(s.id).to.equal('schema:shared:acme.acme.organization');
    });
    it('rejects unknown schema ids', /* message includes 'No such' */);
  });
  describe('Write operations', () => {
    // One assertion per write the implementation declines: all of them, not a sample.
    it('createChildObject throws not-implemented', /* message includes 'not implemented' */);
    // updateObject, deleteObject, addCollectionElement, updateCollectionElement,
    // deleteCollectionElement, executeBulkOperations, updateDocumentData,
    // uploadBinaryContent, ...
  });
});
