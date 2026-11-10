import { expect, assert } from 'chai';
import nock from 'nock';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { DateFormat, DateTime, EnumValue, IllegalArgumentError, NoSuchObjectError, UnexpectedError } from '@zerobias-org/types-core-js';
import { CmvpModuleType, CmvpStandard, CmvpStatus } from '../../generated/model/index.js';
import { DETAIL_PATH_PREFIX, LISTING_PATH } from '../../src/CmvpClient.js';
import { CmvpImpl } from '../../src/CmvpImpl.js';
import { MOCK_BASE_URL } from './Common.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixture = (name: string) => readFileSync(path.join(__dirname, 'fixtures', name), 'utf8');

before(() => {
  nock.disableNetConnect();
});

afterEach(() => {
  nock.cleanAll();
});

after(() => {
  nock.enableNetConnect();
});

describe('CertificateApi.list', () => {
  it('parses listing rows and cross-checks the reported total', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, fixture('listing-3-rows.html'));

    const page = await client.getCertificateApi().list(1, 50);

    expect(page.count).to.equal(3);
    expect(page.items).to.have.lengthOf(3);
    expect(page.items[0].certificateNumber).to.equal('5429');
    expect(page.items[0].vendorName).to.equal('Amazon Web Services Inc.');
    expect(page.items[0].detailUrl.toString()).to.equal(`${MOCK_BASE_URL}${DETAIL_PATH_PREFIX}5429`);
    expect(page.items[0].validationDate?.toString()).to.equal('2026-07-20');
    expect(page.items[2].validationDate?.toString()).to.equal('2026-07-19');
    expect((page.items[0].moduleType as EnumValue | undefined)?.eq(CmvpModuleType.Software)).to.be.true;
    expect((page.items[1].moduleType as EnumValue | undefined)?.eq(CmvpModuleType.SoftwareHybrid)).to.be.true;
    expect(nock.pendingMocks()).to.be.empty;
  });

  it('pages client-side (the registry has no native pagination)', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, fixture('listing-3-rows.html'));

    const page = await client.getCertificateApi().list(2, 1);

    expect(page.count).to.equal(3);
    expect(page.pageNumber).to.equal(2);
    expect(page.pageSize).to.equal(1);
    expect(page.items).to.have.lengthOf(1);
    expect(page.items[0].certificateNumber).to.equal('5426');
  });

  it('rejects a page past the end of the listing', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, fixture('listing-3-rows.html'));

    try {
      await client.getCertificateApi().list(5, 2);
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(IllegalArgumentError);
    }
  });

  it('throws UnexpectedError when the parsed count does not match the page-reported total', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(200, fixture('listing-count-mismatch.html'));

    try {
      await client.getCertificateApi().list();
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(UnexpectedError);
    }
  });

  it('throws UnexpectedError when the registry is unreachable', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(LISTING_PATH).reply(503);

    try {
      await client.getCertificateApi().list();
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(UnexpectedError);
    }
  });
});

describe('CertificateApi.get', () => {
  it('parses a full certificate detail page', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(`${DETAIL_PATH_PREFIX}5425`).reply(200, fixture('detail-5425.html'));

    const cert = await client.getCertificateApi().get('5425');

    expect(cert.certificateNumber).to.equal('5425');
    expect(cert.moduleName).to.equal('CorSSL');
    expect((cert.standard as EnumValue).eq(CmvpStandard._3)).to.be.true;
    expect((cert.status as EnumValue).eq(CmvpStatus.Active)).to.be.true;
    expect(cert.overallLevel).to.equal(1);
    expect((cert.moduleType as EnumValue).eq(CmvpModuleType.Software)).to.be.true;
    expect(cert.caveatText).to.include('No assurance of the minimum strength');
    expect(cert.vendorName).to.equal('Corsec Security, Inc.');
    expect(cert.vendorAddress).to.include('Fairfax, VA 22033');
    expect(cert.validationType).to.equal('Initial');
    expect(cert.validationLab).to.equal('Teron Labs');
    expect(cert.securityPolicyUrl?.toString()).to.include('140sp5425.pdf');
    expect(cert.detailUrl.toString()).to.equal(`${MOCK_BASE_URL}${DETAIL_PATH_PREFIX}5425`);
    expect(cert.rawEntryHtml).to.be.a('string').and.not.empty;
    expect(cert.lastAcquired).to.be.instanceOf(DateTime);
    expect(cert.validationDate).to.be.instanceOf(DateFormat);
    expect(cert.validationDate?.toString()).to.equal('2026-07-19');
  });

  it('throws NoSuchObjectError on a 404', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(`${DETAIL_PATH_PREFIX}999999`).reply(404);

    try {
      await client.getCertificateApi().get('999999');
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(NoSuchObjectError);
    }
  });

  it('throws NoSuchObjectError when the site returns a 200 non-certificate page', async () => {
    const client = new CmvpImpl();
    nock(MOCK_BASE_URL).get(`${DETAIL_PATH_PREFIX}0`).reply(200, fixture('detail-not-found.html'));

    try {
      await client.getCertificateApi().get('0');
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(NoSuchObjectError);
    }
  });
});
