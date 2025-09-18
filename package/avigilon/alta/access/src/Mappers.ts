import { toEnum, map } from '@auditmation/util-hub-module-utils';
import { URL, Email } from '@auditmation/types-core-js';
import {
  User,
  UserInfo,
  UserIdentity,
  Group,
  GroupInfo,
  Acu,
  AcuInfo,
  Role,
  Site,
  Entry,
  Port,
  TokenProperties,
  Zone,
  ZoneEntry,
  ZoneEntryAcu,
  ZoneSiteRef,
  OrganizationRef,
  EntryDetails,
  EntryZone,
  EntryZoneSite,
  EntryAcu,
  EntryState,
  EntrySchedule,
  EntryCamera,
  VideoProvider
} from '../generated/model';

export function mapUserIdentity(raw: any): UserIdentity {
  const result = new UserIdentity(
    raw.id || 0, // id is required but might not be in raw data, default to 0
    new Email(raw.email || 'unknown@example.com'), // Convert string email to Email object
    raw.firstName || '', // Required field
    raw.lastName || '', // Required field
    raw.mobilePhone || raw.phoneNumber, // Map mobilePhone to phoneNumber
    raw.avatarUrl,
    raw.middleName,
    raw.suffix,
    raw.preferredName,
    raw.pronouns,
    raw.dateOfBirth ? new Date(raw.dateOfBirth) : undefined,
    raw.emergencyContactName,
    raw.emergencyContactPhone,
    raw.homeAddress,
    raw.companyName,
    raw.workAddress
  );
  return result;
}

/**
 * Maps raw API user data to User interface
 * Field count validation: User interface has 8 fields (4 required + 4 optional)
 */
export function mapUser(raw: any): User {
  const output: User = {
    // ✅ REQUIRED FIELD 1: id exists in both User interface AND api.yml response - numeric ID
    id: raw.id,

    // ✅ REQUIRED FIELD 2: status - Handle status enum mapping (API: A/S/I -> active/suspended/inactive)
    status: toEnum(User.StatusEnum, raw.status, (apiValue: string) => {
      const statusMap = { A: 'active', S: 'suspended', I: 'inactive' };
      return statusMap[apiValue] || 'active';
    }),

    // ✅ OPTIONAL FIELDS: Map all the new User properties from API response
    ...(raw.opal && { opal: raw.opal }),
    ...(raw.identity && { identity: mapUserIdentity(raw.identity) }),
    ...(raw.groups && { groups: raw.groups }), // Will need proper mapper
    ...(raw.sites && { sites: raw.sites }), // Will need proper mapper
    ...(raw.buildingFloorUnits && { buildingFloorUnits: raw.buildingFloorUnits }),
    ...(raw.hasRemoteUnlock !== undefined && { hasRemoteUnlock: raw.hasRemoteUnlock }),
    ...(raw.isOverrideAllowed !== undefined && { isOverrideAllowed: raw.isOverrideAllowed }),
    ...(raw.startDate && { startDate: new Date(raw.startDate) }),
    ...(raw.endDate && { endDate: new Date(raw.endDate) }),
    ...(raw.startAndEndDateTimeZoneId && { startAndEndDateTimeZoneId: raw.startAndEndDateTimeZoneId }),
    ...(raw.externalId && { externalId: raw.externalId }),
    ...(raw.personId && { personId: raw.personId }),
    ...(raw.title && { title: raw.title }),
    ...(raw.department && { department: raw.department }),
    ...(raw.lastActivityAt && { lastActivityAt: new Date(raw.lastActivityAt) }),
    ...(raw.lastParcelReminderAt && { lastParcelReminderAt: new Date(raw.lastParcelReminderAt) }),
    ...(raw.manuallyInactivatedAt && { manuallyInactivatedAt: new Date(raw.manuallyInactivatedAt) }),
    ...(raw.statusLastUpdatedAt && { statusLastUpdatedAt: new Date(raw.statusLastUpdatedAt) }),
    ...(raw.userCustomFields && { userCustomFields: raw.userCustomFields }),
    ...(raw.createdAt && { createdAt: new Date(raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: new Date(raw.updatedAt) }),
  };

  return output;
}

/**
 * Maps raw API user data to UserInfo interface (extended User with additional fields)
 * Field count validation: UserInfo interface has 13 fields (4 required + 9 optional)
 */
export function mapUserInfo(raw: any): UserInfo {
  const output: UserInfo = {
    // ✅ REQUIRED FIELD 1: id exists in both UserInfo interface AND api.yml response - numeric ID
    id: raw.id,

    // ✅ REQUIRED FIELD 2: status - Handle status enum mapping safely (API: A/S/I -> Enum values)
    status: toEnum(UserInfo.StatusEnum, raw.status, (apiValue: string) => {
      const statusMap = { A: 'active', S: 'suspended', I: 'inactive' };
      return statusMap[apiValue] || 'active';
    }),

    // ✅ OPTIONAL FIELD: identity - Map to UserIdentity object
    ...(raw.identity && { identity: mapUserIdentity(raw.identity) }),

    // ✅ OPTIONAL FIELD: createdAt - MUST be included in mapping
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),

    // ✅ OPTIONAL FIELD: updatedAt - MUST be included in mapping
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),

    // ✅ OPTIONAL FIELD: organizationId - MUST be included in mapping
    ...(raw.identity?.namespace?.org?.id && { organizationId: raw.identity.namespace.org.id }),

    // ✅ OPTIONAL FIELD: avatarUrl - MUST be included in mapping
    ...(raw.avatarUrl && { avatarUrl: map(URL, raw.avatarUrl) }),

    // ✅ OPTIONAL FIELD: lastLoginAt - MUST be included in mapping
    ...(raw.lastLoginAt && { lastLoginAt: map(Date, raw.lastLoginAt) }),

    // ✅ OPTIONAL FIELD: permissions - MUST be included in mapping
    ...(raw.permissions && { permissions: raw.permissions }),

    // ✅ OPTIONAL FIELD: customFields - MUST be included in mapping
    ...(raw.userCustomFields && { customFields: raw.userCustomFields }),
  };

  return output;
}

/**
 * Maps raw API group data to Group interface
 * Field count validation: Group interface has 7 fields (2 required + 5 optional)
 */
export function mapGroup(raw: any): Group {
  const output: Group = {
    // ✅ REQUIRED FIELD 1: id exists in both Group interface AND api.yml response - numeric ID
    id: raw.id,

    // ✅ REQUIRED FIELD 2: name exists in both Group interface AND api.yml response
    name: raw.name,

    // ✅ OPTIONAL FIELD: badgeConfig - new field from API response
    ...(raw.badgeConfig && { badgeConfig: raw.badgeConfig }),

    // ✅ OPTIONAL FIELD 1: description - MUST be mapped with conditional logic
    ...(raw.description && { description: raw.description }),

    // ✅ OPTIONAL FIELD 2: userCount - MUST be included in mapping
    ...(raw.userCount && { userCount: raw.userCount }),

    // ✅ OPTIONAL FIELD 3: createdAt - MUST be included in mapping
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),

    // ✅ OPTIONAL FIELD 4: updatedAt - MUST be included in mapping
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),

    // ✅ FIELD COUNT VALIDATION: 7 fields total = 2 required + 5 optional ✓
  };

  return output;
}

/**
 * Maps raw API group data to GroupInfo interface (extended Group with additional fields)
 */
export function mapGroupInfo(groupData: Group, raw: any): GroupInfo {
  return {
    ...groupData,
    ...(raw.organizationId && { organizationId: raw.organizationId }),
    ...(raw.parentGroupId && { parentGroupId: raw.parentGroupId }),
    ...(raw.permissions && { permissions: raw.permissions }),
    ...(raw.accessRules && { accessRules: raw.accessRules }),
    ...(raw.customFields && { customFields: raw.customFields }),
  };
}

/**
 * Maps raw API ACU data to Acu interface
 * Field count validation: Acu interface has 11 fields (3 required + 8 optional)
 */
export function mapAcu(raw: any): Acu {
  const output: Acu = {
    // ✅ REQUIRED FIELD 1: id exists in both Acu interface AND api.yml response - numeric ID
    id: raw.id,

    // ✅ REQUIRED FIELD 2: name exists in both Acu interface AND api.yml response
    name: raw.name,

    // ✅ REQUIRED FIELD 3: status exists in both Acu interface AND api.yml response
    status: toEnum(Acu.StatusEnum, raw.status, (apiValue: string) => {
      const statusMap = { A: 'active', I: 'inactive', E: 'error', M: 'maintenance' };
      return statusMap[apiValue] || 'active';
    }),

    // ✅ OPTIONAL FIELD 1: modelNumber - MUST be mapped with conditional logic
    ...(raw.modelNumber && { modelNumber: raw.modelNumber }),

    // ✅ OPTIONAL FIELD 2: serialNumber - MUST be included in mapping
    ...(raw.serialNumber && { serialNumber: raw.serialNumber }),

    // ✅ OPTIONAL FIELD 3: firmwareVersion - MUST be included in mapping
    ...(raw.firmwareVersion && { firmwareVersion: raw.firmwareVersion }),

    // ✅ OPTIONAL FIELD 4: ipAddress - MUST be included in mapping
    ...(raw.ipAddress && { ipAddress: raw.ipAddress }),

    // ✅ OPTIONAL FIELD 5: macAddress - MUST be included in mapping
    ...(raw.macAddress && { macAddress: raw.macAddress }),

    // ✅ OPTIONAL FIELD 6: location - MUST be included in mapping
    ...(raw.location && { location: raw.location }),

    // ✅ OPTIONAL FIELD 7: createdAt - MUST be included in mapping
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),

    // ✅ OPTIONAL FIELD 8: updatedAt - MUST be included in mapping
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),

    // ✅ FIELD COUNT VALIDATION: 11 fields mapped = 11 fields in Acu interface ✓
  };

  return output;
}

/**
 * Maps raw API ACU data to AcuInfo interface (extended Acu with additional fields)
 */
export function mapAcuInfo(acuData: Acu, raw: any): AcuInfo {
  return {
    ...acuData,
    ...(raw.organizationId && { organizationId: raw.organizationId }),
    ...(raw.siteId && { siteId: raw.siteId }),
    ...(raw.portCount && { portCount: raw.portCount }),
    ...(raw.lastHeartbeat && { lastHeartbeat: new Date(raw.lastHeartbeat) }),
  };
}

/**
 * Maps raw API role data to Role interface
 */
export function mapRole(raw: any): Role {
  return {
    id: raw.id,
    name: raw.name,
    ...(raw.description && { description: raw.description }),
    ...(raw.permissions && { permissions: raw.permissions }),
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),
  };
}

/**
 * Maps raw API site data to Site interface
 */
export function mapSite(raw: any): Site {
  return new Site(
    raw.id,
    raw.name,
    raw.opal,
    raw.address,
    raw.address2,
    raw.city,
    raw.state,
    raw.zip,
    raw.country,
    raw.phone,
    raw.language,
    raw.zoneCount,
    raw.userCount,
    raw.createdAt ? map(Date, raw.createdAt) : undefined,
    raw.updatedAt ? map(Date, raw.updatedAt) : undefined
  );
}

/**
 * Maps raw API entry data to Entry interface
 */
export function mapEntry(raw: any): Entry {
  return {
    id: raw.id,
    ...(raw.type && { type: toEnum(Entry.TypeEnum, raw.type) }),
    ...(raw.userId && { userId: raw.userId }),
    ...(raw.doorId && { doorId: raw.doorId }),
    ...(raw.timestamp && { timestamp: map(Date, raw.timestamp) }),
    ...(raw.granted && { granted: raw.granted }),
    ...(raw.reason && { reason: raw.reason }),
  };
}

// Internal helper function for nested object mapping
function mapPortConfiguration(raw: any): any {
  if (!raw) return undefined;
  return {
    ...(raw.debounceTime && { debounceTime: raw.debounceTime }),
    ...(raw.pollingRate && { pollingRate: raw.pollingRate }),
    ...(raw.enabled && { enabled: raw.enabled }),
    ...(raw.customSettings && { customSettings: raw.customSettings }),
  };
}

/**
 * Maps raw API port data to Port interface
 */
export function mapPort(raw: any): Port {
  return {
    id: raw.id,
    name: raw.name,
    ...(raw.type && { type: toEnum(Port.TypeEnum, raw.type) }),
    ...(raw.status && { status: toEnum(Port.StatusEnum, raw.status) }),
    ...(raw.description && { description: raw.description }),
    ...(raw.configuration && { configuration: mapPortConfiguration(raw.configuration) }),
  };
}

/**
 * Maps raw API token properties data to TokenProperties interface
 */
export function mapTokenProperties(raw: any): TokenProperties {
  // Extract organization ID from tokenScopeList
  const organizationId = raw.tokenScopeList?.[0]?.org?.id;

  // Extract scope array from tokenScopeList
  const scope = raw.tokenScopeList?.[0]?.scope;

  return new TokenProperties(
    organizationId,
    raw.identityId,
    map(Date, raw.createdAt),
    map(Date, raw.expiresAt),
    scope,
    raw.tokenType,
    raw.jti,
    raw.iat,
    raw.exp
  );
}

// Helper functions for Zone mapping
function mapZoneEntryAcu(raw: any): ZoneEntryAcu | undefined {
  if (!raw) return undefined;
  return new ZoneEntryAcu(raw.id);
}

function mapZoneEntry(raw: any): ZoneEntry | undefined {
  if (!raw) return undefined;
  return new ZoneEntry(
    raw.id,
    raw.name,
    raw.wirelessLock,
    mapZoneEntryAcu(raw.acu)
  );
}

function mapZoneSiteRef(raw: any): ZoneSiteRef | undefined {
  if (!raw) return undefined;
  return new ZoneSiteRef(raw.id, raw.name);
}

function mapOrganizationRef(raw: any): OrganizationRef | undefined {
  if (!raw) return undefined;
  return new OrganizationRef(raw.id, raw.name);
}

/**
 * Maps raw API zone data to Zone interface
 * Flattens the response from {data: [...], meta: {...}} to just the zones array
 */
export function mapZone(raw: any): Zone {
  return new Zone(
    raw.id,
    raw.name,
    raw.opal,
    raw.description,
    raw.apbResetIcalText,
    raw.apbExpirationSeconds,
    raw.apbUseContactSensor,
    raw.apbAllowSharedOrgReset,
    raw.entryCount,
    raw.offlineEntryCount,
    raw.userCount,
    raw.groupCount,
    mapOrganizationRef(raw.org),
    mapZoneSiteRef(raw.site),
    raw.zoneShares,
    raw.entries?.map(mapZoneEntry),
    raw.apbAreas,
    raw.createdAt ? map(Date, raw.createdAt) : undefined,
    raw.updatedAt ? map(Date, raw.updatedAt) : undefined
  );
}

// Helper functions for EntryDetails mapping
function mapVideoProvider(raw: any): VideoProvider | undefined {
  if (!raw) return undefined;
  return new VideoProvider(raw.id, raw.videoProviderTypeId);
}

function mapEntryCamera(raw: any): EntryCamera | undefined {
  if (!raw) return undefined;
  return new EntryCamera(
    raw.id,
    raw.name,
    raw.nameExt,
    raw.idExt,
    raw.supportsSnapshot,
    raw.supportsDeeplink,
    raw.supportsMotionSnapshot,
    raw.supportsFaceCrop,
    raw.supportsFaceDetection,
    raw.supportsPeopleDetection,
    raw.supportsMotionRecap,
    raw.supportsLivestream,
    raw.videoProviderId,
    mapVideoProvider(raw.videoProvider)
  );
}

function mapEntrySchedule(raw: any): EntrySchedule | undefined {
  if (!raw) return undefined;
  return new EntrySchedule(raw.id, raw.name);
}

function mapEntryState(raw: any): EntryState | undefined {
  if (!raw) return undefined;
  return new EntryState(raw.id, raw.name);
}

function mapEntryAcu(raw: any): EntryAcu | undefined {
  if (!raw) return undefined;
  return new EntryAcu(raw.id, raw.name, raw.isGatewayMode);
}

function mapEntryZoneSite(raw: any): EntryZoneSite | undefined {
  if (!raw) return undefined;
  return new EntryZoneSite(raw.id, raw.name);
}

function mapEntryZone(raw: any): EntryZone | undefined {
  if (!raw) return undefined;
  return new EntryZone(raw.id, raw.name, mapEntryZoneSite(raw.site));
}

/**
 * Maps raw API entry data to EntryDetails interface
 * Based on the actual API response structure from /orgs/{organizationId}/entries
 */
export function mapEntryDetails(raw: any): EntryDetails {
  return new EntryDetails(
    raw.id,
    raw.name,
    raw.opal,
    raw.pincode,
    raw.isPincodeEnabled,
    raw.color,
    raw.isMusterPoint,
    raw.notes,
    raw.externalUuid,
    raw.isReaderless,
    raw.isIntercomEntry,
    raw.createdAt ? map(Date, raw.createdAt) : undefined,
    raw.updatedAt ? map(Date, raw.updatedAt) : undefined,
    mapEntryZone(raw.zone),
    mapEntryAcu(raw.acu),
    raw.wirelessLock,
    mapEntryState(raw.entryState),
    mapEntrySchedule(raw.schedule),
    raw.cameras?.map(mapEntryCamera)
  );
}
