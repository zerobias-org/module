import * as cheerio from 'cheerio';
import { map, toEnum } from '@zerobias-org/util-hub-module-utils';
import { DateFormat, DateTime, URL, UnexpectedError } from '@zerobias-org/types-core-js';
import {
  Certificate,
  CertificateSummary,
  CmvpModuleType,
  CmvpModuleTypeDef,
  CmvpStandard,
  CmvpStandardDef,
  CmvpStatus,
  CmvpStatusDef,
} from '../generated/model/index.js';
import { DETAIL_PATH_PREFIX } from './CmvpClient.js';
import { ensureProperties, optional } from './util.js';

// ── Value helpers ──────────────────────────────────────────────────────────

// CMVP's own casing is inconsistent in the wild (e.g. "Software-hybrid" vs
// "Software-Hybrid") — normalize to UPPER_SNAKE before the enum lookup.
function toEnumKey(raw: string): string {
  return raw.trim().toUpperCase().replace(/[\s-]+/g, '_');
}

// e.g. "FIPS\n\t140-3" -> "FIPS_140_3"
function toStandardKey(raw: string): string {
  const match = raw.replace(/\s+/g, ' ').trim().match(/140[\s-](\d)/);
  return match ? `FIPS_140_${match[1]}` : raw.trim();
}

// CMVP prints calendar dates as MM/DD/YYYY (sometimes without leading zeros).
// They are rebuilt as ISO strings rather than parsed through Date(), which
// would anchor them to local midnight and shift the day east of UTC.
function toIsoDate(raw: string | undefined): string | undefined {
  const match = raw?.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!match) return undefined;
  const [, month, day, year] = match;
  return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
}

function toOverallLevel(raw: string | undefined): number | undefined {
  const parsed = raw ? Number.parseInt(raw, 10) : Number.NaN;
  return Number.isNaN(parsed) ? undefined : parsed;
}

function toModuleType(raw: string): CmvpModuleTypeDef {
  return toEnum(CmvpModuleType, raw, toEnumKey) as CmvpModuleTypeDef;
}

function toStandard(raw: string): CmvpStandardDef {
  return toEnum(CmvpStandard, raw, toStandardKey) as CmvpStandardDef;
}

function toStatus(raw: string): CmvpStatusDef {
  return toEnum(CmvpStatus, raw, toEnumKey) as CmvpStatusDef;
}

export function certificateNumberFromHref(href: string): string {
  const match = href.match(/certificate\/(\d+)/);
  if (!match) {
    throw new UnexpectedError(`Could not extract certificate number from href: ${href}`);
  }
  return match[1];
}

export function detailUrlFor(baseUrl: string, certificateNumber: string): string {
  return `${baseUrl}${DETAIL_PATH_PREFIX}${certificateNumber}`;
}

// ── Mappers (scraped record -> generated model) ────────────────────────────

export function toCertificate(raw: any): Certificate {
  // 1. Check for required fields
  ensureProperties(raw, [
    'certificateNumber', 'standard', 'status', 'overallLevel', 'moduleType',
    'detailUrl', 'rawEntryHtml', 'lastAcquired',
  ]);

  // 2. Create output object
  const output: Certificate = {
    certificateNumber: raw.certificateNumber,
    moduleName: optional(raw.moduleName),
    standard: toStandard(raw.standard),
    status: toStatus(raw.status),
    sunsetDate: map(DateFormat, toIsoDate(raw.sunsetDate)),
    overallLevel: raw.overallLevel,
    moduleType: toModuleType(raw.moduleType),
    embodiment: optional(raw.embodiment),
    caveatText: optional(raw.caveatText),
    overview: optional(raw.overview),
    securityLevelExceptions: optional(raw.securityLevelExceptions),
    vendorName: optional(raw.vendorName),
    vendorAddress: optional(raw.vendorAddress),
    vendorContact: optional(raw.vendorContact),
    validationType: optional(raw.validationType),
    validationLab: optional(raw.validationLab),
    validationDate: map(DateFormat, toIsoDate(raw.validationDate)),
    securityPolicyUrl: map(URL, raw.securityPolicyUrl),
    detailUrl: map(URL, raw.detailUrl),
    rawEntryHtml: raw.rawEntryHtml,
    lastAcquired: map(DateTime, raw.lastAcquired),
    // Catalog matching (productId / vendorId) is deferred to a follow-up.
    productId: undefined,
    vendorId: undefined,
  };

  return output;
}

export function toCertificateSummary(raw: any): CertificateSummary {
  // 1. Check for required fields
  ensureProperties(raw, ['certificateNumber', 'detailUrl']);

  // 2. Create output object
  const output: CertificateSummary = {
    certificateNumber: raw.certificateNumber,
    moduleName: optional(raw.moduleName),
    vendorName: optional(raw.vendorName),
    moduleType: raw.moduleType ? toModuleType(raw.moduleType) : undefined,
    validationDate: map(DateFormat, toIsoDate(raw.validationDate)),
    detailUrl: map(URL, raw.detailUrl),
  };

  return output;
}

// ── HTML extraction (page -> scraped record) ───────────────────────────────

/**
 * Parses the CMVP listing page (#searchResultsTable) into row summaries, and
 * cross-checks the parsed count against the page's own reported total
 * (#divResults[data-total-records]) — per the connector's design requirement
 * to fail loudly on a scrape/parse mismatch rather than silently under-report.
 */
export function parseListingPage(html: string, baseUrl: string): CertificateSummary[] {
  const $ = cheerio.load(html);

  const reportedTotal = $('#divResults').attr('data-total-records');
  const rows = $('#searchResultsTable tbody tr').toArray();

  if (reportedTotal !== undefined && rows.length !== Number(reportedTotal)) {
    throw new UnexpectedError(
      `CMVP listing parse mismatch: page reports ${reportedTotal} certificates but parsed ${rows.length} rows`,
    );
  }

  return rows.map((row) => {
    const $row = $(row);
    const href = $row.find('a[href*="/certificate/"]').first().attr('href');
    if (!href) {
      throw new UnexpectedError('CMVP listing row missing certificate link');
    }
    const certificateNumber = certificateNumberFromHref(href);
    const cells = $row.find('td');
    const text = (index: number): string | undefined => $(cells.get(index)).text().trim() || undefined;

    return toCertificateSummary({
      certificateNumber,
      vendorName: text(1),
      moduleName: text(2),
      moduleType: text(3),
      validationDate: text(4),
      detailUrl: detailUrlFor(baseUrl, certificateNumber),
    });
  });
}

/**
 * Parses a CMVP certificate detail page into a full Certificate record.
 * The "Details" panel is a sequence of label/value row pairs (.row.padrow)
 * rather than stable per-field ids, so fields are matched by label text.
 */
export function parseDetailPage(
  html: string,
  certificateNumber: string,
  baseUrl: string,
  lastAcquired: Date,
): Certificate {
  const $ = cheerio.load(html);

  const panelBody = (title: string) => $('h4.panel-title')
    .filter((_i, el) => $(el).text().trim() === title)
    .closest('.panel')
    .find('.panel-body')
    .first();

  const detailsPanel = panelBody('Details');
  const details = new Map<string, string>();
  detailsPanel.find('> .row.padrow').each((_i, el) => {
    const $row = $(el);
    const label = $row.find('.col-md-3').first().text().trim();
    const value = $row.find('.col-md-9').first().text().trim();
    if (label) {
      details.set(label, value);
    }
  });
  const textOf = (label: string): string | undefined => details.get(label) || undefined;

  const vendorPanel = panelBody('Vendor');
  const vendorName = vendorPanel.find('a').first().text().trim()
    || vendorPanel.clone().children().remove().end().text().trim().split('\n')[0]?.trim();
  const contactBlock = vendorPanel.find('div[style*="font-size"]');
  // Address and contact both use <span class="indent"> — scope address to
  // spans NOT nested inside the contact block (name/email/phone).
  const vendorAddress = vendorPanel.find('span.indent')
    .filter((_i, el) => $(el).closest('div[style*="font-size"]').length === 0)
    .map((_i, el) => $(el).text().trim())
    .toArray()
    .filter((line) => line)
    .join(', ') || undefined;
  const vendorContact = contactBlock.length ? contactBlock.text().replace(/\s+/g, ' ').trim() || undefined : undefined;

  const securityPolicyHref = $('a[href*="security-policies"]').first().attr('href');

  const historyCells = $('#validation-history-table tbody tr').first().find('td');
  const historyCell = (index: number): string | undefined => (
    historyCells.length ? $(historyCells.get(index)).text().trim() || undefined : undefined
  );

  return toCertificate({
    certificateNumber,
    moduleName: textOf('Module Name'),
    standard: textOf('Standard'),
    status: textOf('Status'),
    sunsetDate: textOf('Sunset Date'),
    overallLevel: toOverallLevel(textOf('Overall Level')),
    moduleType: textOf('Module Type'),
    embodiment: textOf('Embodiment'),
    caveatText: textOf('Caveat'),
    overview: textOf('Description'),
    securityLevelExceptions: textOf('Security Level Exceptions'),
    vendorName: vendorName || undefined,
    vendorAddress,
    vendorContact,
    validationDate: historyCell(0),
    validationType: historyCell(1),
    validationLab: historyCell(2),
    securityPolicyUrl: securityPolicyHref ? `${baseUrl}${securityPolicyHref}` : undefined,
    detailUrl: detailUrlFor(baseUrl, certificateNumber),
    rawEntryHtml: detailsPanel.html() ?? html,
    lastAcquired,
  });
}
