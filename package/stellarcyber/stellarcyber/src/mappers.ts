import { Case, CaseAlert } from '../generated/model/index.js';

/**
 * Stellar Cyber returns underscore-prefixed/snake_case JSON (`_id`, `_source`,
 * `xdr_event`, `aws_guardduty`, `AccountId`, …). The generated models are
 * camelCase, so these mappers normalise the raw API shapes into model instances.
 */

export function toCase(raw: any): Case {
  const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
  return Case.newInstance({
    id: data._id ?? data.id,
    name: data.name,
    severity: data.severity,
    score: data.score,
    status: data.status,
    size: data.size,
    createdAt: data.created_at ?? data.createdAt ?? data.start_timestamp,
    tenantName: data.tenant_name ?? data.tenantName,
  });
}

export function toCaseAlert(raw: any): CaseAlert {
  const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
  const src = data._source ?? data.source ?? {};
  const xdr = src.xdr_event ?? src.xdrEvent ?? {};
  const gd = src.aws_guardduty ?? src.awsGuardduty ?? {};
  const res = gd.Resource ?? gd.resource ?? {};

  return CaseAlert.newInstance({
    id: data._id ?? data.id,
    source: {
      timestamp: src['@timestamp'] ?? src.timestamp,
      severity: src.severity,
      username: src.username,
      xdrEvent: {
        displayName: xdr.display_name ?? xdr.displayName,
        description: xdr.description,
        tactic: xdr.tactic && { id: xdr.tactic.id, name: xdr.tactic.name },
        technique: xdr.technique && { id: xdr.technique.id, name: xdr.technique.name },
      },
      awsGuardduty: {
        accountId: gd.AccountId ?? gd.accountId,
        arn: gd.Arn ?? gd.arn,
        type: gd.Type ?? gd.type,
        resource: { resourceType: res.ResourceType ?? res.resourceType },
      },
    },
  });
}
