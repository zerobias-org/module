import * as cheerio from 'cheerio';
import { URL, UnexpectedError } from '@zerobias-org/types-core-js';
import { Certificate, CertificateSummary, CmvpModuleType, CmvpModuleTypeDef, CmvpStandard, CmvpStandardDef, CmvpStatus, CmvpStatusDef } from '../generated/model/index.js';
import { DETAIL_PATH_PREFIX } from './CmvpClient.js';

// CMVP's own casing is inconsistent in the wild (e.g. "Software-hybrid" vs
// "Software-Hybrid") — normalize to uppercase-with-underscores before matching.
function normalizeKey(raw: string): string {
  return raw.trim().toUpperCase().replace(/[\s-]+/g, '_');
}

export function toModuleType(raw: string | undefined): CmvpModuleTypeDef | undefined {
  if (!raw) return undefined;
  try {
    return CmvpModuleType.from(normalizeKey(raw));
  } catch {
    return undefined;
  }
}

export function toStandard(raw: string | undefined): CmvpStandardDef | undefined {
  if (!raw) return undefined;
  // e.g. "FIPS\n\t\t\t\t\t\t140-3" (whitespace-collapsed) -> "FIPS_140_3"
  const collapsed = raw.replace(/\s+/g, ' ').trim();
  const match = collapsed.match(/140[\s-](\d)/);
  if (!match) return undefined;
  try {
    return CmvpStandard.from(`FIPS_140_${match[1]}`);
  } catch {
    return undefined;
  }
}

export function toStatus(raw: string | undefined): CmvpStatusDef | undefined {
  if (!raw) return undefined;
  try {
    return CmvpStatus.from(normalizeKey(raw));
  } catch {
    return undefined;
  }
}

export function toDate(raw: string | undefined): Date | undefined {
  if (!raw) return undefined;
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const parsed = new Date(trimmed);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed;
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
    const link = $row.find('a[href*="/certificate/"]').first();
    const href = link.attr('href');
    if (!href) {
      throw new UnexpectedError('CMVP listing row missing certificate link');
    }
    const certificateNumber = certificateNumberFromHref(href);
    const cells = $row.find('td');
    const vendorName = $(cells.get(1)).text().trim();
    const moduleName = $(cells.get(2)).text().trim();
    const moduleType = toModuleType($(cells.get(3)).text().trim());
    const validationDate = toDate($(cells.get(4)).text().trim());

    return new CertificateSummary(
      certificateNumber,
      moduleName,
      vendorName,
      new URL(detailUrlFor(baseUrl, certificateNumber)),
      moduleType,
      validationDate,
    );
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

  const detailsPanel = $('h4.panel-title')
    .filter((_i, el) => $(el).text().trim() === 'Details')
    .closest('.panel')
    .find('.panel-body')
    .first();

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

  const moduleName = textOf('Module Name');
  const standard = toStandard(textOf('Standard'));
  const status = toStatus(textOf('Status'));
  const overallLevelRaw = textOf('Overall Level');
  const overallLevel = overallLevelRaw ? Number.parseInt(overallLevelRaw, 10) : undefined;
  const moduleType = toModuleType(textOf('Module Type'));
  const caveatText = textOf('Caveat');

  const vendorPanel = $('h4.panel-title')
    .filter((_i, el) => $(el).text().trim() === 'Vendor')
    .closest('.panel')
    .find('.panel-body')
    .first();
  const vendorName = vendorPanel.find('a').first().text().trim() || vendorPanel.clone().children().remove().end().text().trim().split('\n')[0]?.trim();
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

  if (!moduleName || !standard || !status || !overallLevel || !moduleType || !caveatText || !vendorName) {
    throw new UnexpectedError(
      `CMVP detail page for certificate ${certificateNumber} is missing one or more required fields`,
    );
  }

  const securityPolicyHref = $('a[href*="security-policies"]').first().attr('href');

  const historyRow = $('#validation-history-table tbody tr').first();
  const historyCells = historyRow.find('td');
  const validationDate = historyCells.length ? toDate($(historyCells.get(0)).text().trim()) : undefined;
  const validationType = historyCells.length ? $(historyCells.get(1)).text().trim() || undefined : undefined;
  const validationLab = historyCells.length ? $(historyCells.get(2)).text().trim() || undefined : undefined;

  const detailUrl = new URL(detailUrlFor(baseUrl, certificateNumber));

  return new Certificate(
    certificateNumber,
    moduleName,
    standard,
    status,
    overallLevel,
    moduleType,
    caveatText,
    vendorName,
    detailUrl,
    detailsPanel.html() ?? html,
    lastAcquired,
    toDate(textOf('Sunset Date')),
    textOf('Embodiment'),
    textOf('Description'),
    textOf('Security Level Exceptions'),
    vendorAddress,
    vendorContact,
    validationType,
    validationLab,
    validationDate,
    securityPolicyHref ? new URL(`${baseUrl}${securityPolicyHref}`) : undefined,
    // productId/vendorId: catalog matching is deferred to a follow-up (see Plan §"User decisions locked in" #5)
    undefined,
    undefined,
  );
}
