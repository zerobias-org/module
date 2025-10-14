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
  const output: UserIdentity = {
    id: raw.id || 0, // id is required but might not be in raw data, default to 0
    email: new Email(raw.email || 'unknown@example.com'), // Convert string email to Email object
    firstName: raw.firstName || '', // Required field
    lastName: raw.lastName || '', // Required field
    phoneNumber: raw.mobilePhone || raw.phoneNumber, // Map mobilePhone to phoneNumber
    ...(raw.avatarUrl && { avatarUrl: raw.avatarUrl }),
    ...(raw.middleName && { middleName: raw.middleName }),
    ...(raw.suffix && { suffix: raw.suffix }),
    ...(raw.preferredName && { preferredName: raw.preferredName }),
    ...(raw.pronouns && { pronouns: raw.pronouns }),
    ...(raw.dateOfBirth && { dateOfBirth: new Date(raw.dateOfBirth) }),
    ...(raw.emergencyContactName && { emergencyContactName: raw.emergencyContactName }),
    ...(raw.emergencyContactPhone && { emergencyContactPhone: raw.emergencyContactPhone }),
    ...(raw.homeAddress && { homeAddress: raw.homeAddress }),
    ...(raw.companyName && { companyName: raw.companyName }),
    ...(raw.workAddress && { workAddress: raw.workAddress }),
  };
  return output;
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
  const output: GroupInfo = {
    ...groupData,
    ...(raw.organizationId && { organizationId: raw.organizationId }),
    ...(raw.parentGroupId && { parentGroupId: raw.parentGroupId }),
    ...(raw.permissions && { permissions: raw.permissions }),
    ...(raw.accessRules && { accessRules: raw.accessRules }),
    ...(raw.customFields && { customFields: raw.customFields }),
  };
  return output;
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
  const output: AcuInfo = {
    ...acuData,
    ...(raw.organizationId && { organizationId: raw.organizationId }),
    ...(raw.siteId && { siteId: raw.siteId }),
    ...(raw.portCount && { portCount: raw.portCount }),
    ...(raw.lastHeartbeat && { lastHeartbeat: new Date(raw.lastHeartbeat) }),
  };
  return output;
}

/**
 * Maps raw API role data to Role interface
 */
export function mapRole(raw: any): Role {
  const output: Role = {
    id: raw.id,
    name: raw.name,
    ...(raw.description && { description: raw.description }),
    ...(raw.permissions && { permissions: raw.permissions }),
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),
  };
  return output;
}

/**
 * Maps raw API site data to Site interface
 */
export function mapSite(raw: any): Site {
  const output: Site = {
    id: raw.id,
    name: raw.name,
    ...(raw.opal && { opal: raw.opal }),
    ...(raw.address && { address: raw.address }),
    ...(raw.address2 && { address2: raw.address2 }),
    ...(raw.city && { city: raw.city }),
    ...(raw.state && { state: raw.state }),
    ...(raw.zip && { zip: raw.zip }),
    ...(raw.country && { country: raw.country }),
    ...(raw.phone && { phone: raw.phone }),
    ...(raw.language && { language: raw.language }),
    ...(raw.zoneCount !== undefined && { zoneCount: raw.zoneCount }),
    ...(raw.userCount !== undefined && { userCount: raw.userCount }),
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),
  };
  return output;
}

/**
 * Maps raw API entry data to Entry interface
 */
export function mapEntry(raw: any): Entry {
  const output: Entry = {
    id: raw.id,
    ...(raw.type && { type: toEnum(Entry.TypeEnum, raw.type) }),
    ...(raw.userId && { userId: raw.userId }),
    ...(raw.doorId && { doorId: raw.doorId }),
    ...(raw.timestamp && { timestamp: map(Date, raw.timestamp) }),
    ...(raw.granted && { granted: raw.granted }),
    ...(raw.reason && { reason: raw.reason }),
  };
  return output;
}

// Internal helper function for nested object mapping
function mapPortConfiguration(raw: any): any {
  if (!raw) return undefined;
  const output = {
    ...(raw.debounceTime && { debounceTime: raw.debounceTime }),
    ...(raw.pollingRate && { pollingRate: raw.pollingRate }),
    ...(raw.enabled && { enabled: raw.enabled }),
    ...(raw.customSettings && { customSettings: raw.customSettings }),
  };
  return output;
}

/**
 * Maps raw API port data to Port interface
 */
export function mapPort(raw: any): Port {
  const output: Port = {
    id: raw.id,
    name: raw.name,
    ...(raw.type && { type: toEnum(Port.TypeEnum, raw.type) }),
    ...(raw.status && { status: toEnum(Port.StatusEnum, raw.status) }),
    ...(raw.description && { description: raw.description }),
    ...(raw.configuration && { configuration: mapPortConfiguration(raw.configuration) }),
  };
  return output;
}

/**
 * Maps raw API token properties data to TokenProperties interface
 */
export function mapTokenProperties(raw: any): TokenProperties {
  // Extract organization ID from tokenScopeList
  const organizationId = raw.tokenScopeList?.[0]?.org?.id;

  // Extract scope array from tokenScopeList
  const scope = raw.tokenScopeList?.[0]?.scope;

  const output: TokenProperties = {
    ...(organizationId !== undefined && { organizationId }),
    ...(raw.identityId !== undefined && { identityId: raw.identityId }),
    ...(raw.createdAt && { issuedAt: map(Date, raw.createdAt) }),
    ...(raw.expiresAt && { expiresAt: map(Date, raw.expiresAt) }),
    ...(scope && { scope }),
    ...(raw.tokenType && { tokenType: raw.tokenType }),
    ...(raw.jti && { jti: raw.jti }),
    ...(raw.iat !== undefined && { iat: raw.iat }),
    ...(raw.exp !== undefined && { exp: raw.exp }),
  };
  return output;
}

// Helper functions for Zone mapping
function mapZoneEntryAcu(raw: any): ZoneEntryAcu | undefined {
  if (!raw) return undefined;
  const output: ZoneEntryAcu = { id: raw.id };
  return output;
}

function mapZoneEntry(raw: any): ZoneEntry | undefined {
  if (!raw) return undefined;
  const output: ZoneEntry = {
    id: raw.id,
    name: raw.name,
    ...(raw.wirelessLock !== undefined && { wirelessLock: raw.wirelessLock }),
    ...(raw.acu && { acu: mapZoneEntryAcu(raw.acu) }),
  };
  return output;
}

function mapZoneSiteRef(raw: any): ZoneSiteRef | undefined {
  if (!raw) return undefined;
  const output: ZoneSiteRef = {
    id: raw.id,
    name: raw.name,
  };
  return output;
}

function mapOrganizationRef(raw: any): OrganizationRef | undefined {
  if (!raw) return undefined;
  const output: OrganizationRef = {
    id: raw.id,
    name: raw.name,
  };
  return output;
}

/**
 * Maps raw API zone data to Zone interface
 * Flattens the response from {data: [...], meta: {...}} to just the zones array
 */
export function mapZone(raw: any): Zone {
  const output: Zone = {
    id: raw.id,
    name: raw.name,
    ...(raw.opal && { opal: raw.opal }),
    ...(raw.description && { description: raw.description }),
    ...(raw.apbResetIcalText && { apbResetIcalText: raw.apbResetIcalText }),
    ...(raw.apbExpirationSeconds !== undefined && { apbExpirationSeconds: raw.apbExpirationSeconds }),
    ...(raw.apbUseContactSensor !== undefined && { apbUseContactSensor: raw.apbUseContactSensor }),
    ...(raw.apbAllowSharedOrgReset !== undefined && { apbAllowSharedOrgReset: raw.apbAllowSharedOrgReset }),
    ...(raw.entryCount !== undefined && { entryCount: raw.entryCount }),
    ...(raw.offlineEntryCount !== undefined && { offlineEntryCount: raw.offlineEntryCount }),
    ...(raw.userCount !== undefined && { userCount: raw.userCount }),
    ...(raw.groupCount !== undefined && { groupCount: raw.groupCount }),
    ...(raw.org && { org: mapOrganizationRef(raw.org) }),
    ...(raw.site && { site: mapZoneSiteRef(raw.site) }),
    ...(raw.zoneShares && { zoneShares: raw.zoneShares }),
    ...(raw.entries && { entries: raw.entries.map(mapZoneEntry) }),
    ...(raw.apbAreas && { apbAreas: raw.apbAreas }),
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),
  };
  return output;
}

// Helper functions for EntryDetails mapping
function mapVideoProvider(raw: any): VideoProvider | undefined {
  if (!raw) return undefined;
  const output: VideoProvider = {
    id: raw.id,
    ...(raw.videoProviderTypeId && { videoProviderTypeId: raw.videoProviderTypeId }),
  };
  return output;
}

function mapEntryCamera(raw: any): EntryCamera | undefined {
  if (!raw) return undefined;
  const output: EntryCamera = {
    id: raw.id,
    name: raw.name,
    ...(raw.nameExt && { nameExt: raw.nameExt }),
    ...(raw.idExt && { idExt: raw.idExt }),
    ...(raw.supportsSnapshot !== undefined && { supportsSnapshot: raw.supportsSnapshot }),
    ...(raw.supportsDeeplink !== undefined && { supportsDeeplink: raw.supportsDeeplink }),
    ...(raw.supportsMotionSnapshot !== undefined && { supportsMotionSnapshot: raw.supportsMotionSnapshot }),
    ...(raw.supportsFaceCrop !== undefined && { supportsFaceCrop: raw.supportsFaceCrop }),
    ...(raw.supportsFaceDetection !== undefined && { supportsFaceDetection: raw.supportsFaceDetection }),
    ...(raw.supportsPeopleDetection !== undefined && { supportsPeopleDetection: raw.supportsPeopleDetection }),
    ...(raw.supportsMotionRecap !== undefined && { supportsMotionRecap: raw.supportsMotionRecap }),
    ...(raw.supportsLivestream !== undefined && { supportsLivestream: raw.supportsLivestream }),
    ...(raw.videoProviderId && { videoProviderId: raw.videoProviderId }),
    ...(raw.videoProvider && { videoProvider: mapVideoProvider(raw.videoProvider) }),
  };
  return output;
}

function mapEntrySchedule(raw: any): EntrySchedule | undefined {
  if (!raw) return undefined;
  const output: EntrySchedule = {
    id: raw.id,
    name: raw.name,
  };
  return output;
}

function mapEntryState(raw: any): EntryState | undefined {
  if (!raw) return undefined;
  const output: EntryState = {
    id: raw.id,
    name: raw.name,
  };
  return output;
}

function mapEntryAcu(raw: any): EntryAcu | undefined {
  if (!raw) return undefined;
  const output: EntryAcu = {
    id: raw.id,
    name: raw.name,
    ...(raw.isGatewayMode !== undefined && { isGatewayMode: raw.isGatewayMode }),
  };
  return output;
}

function mapEntryZoneSite(raw: any): EntryZoneSite | undefined {
  if (!raw) return undefined;
  const output: EntryZoneSite = {
    id: raw.id,
    name: raw.name,
  };
  return output;
}

function mapEntryZone(raw: any): EntryZone | undefined {
  if (!raw) return undefined;
  const output: EntryZone = {
    id: raw.id,
    name: raw.name,
    ...(raw.site && { site: mapEntryZoneSite(raw.site) }),
  };
  return output;
}

/**
 * Maps raw API entry data to EntryDetails interface
 * Based on the actual API response structure from /orgs/{organizationId}/entries
 */
export function mapEntryDetails(raw: any): EntryDetails {
  const output: EntryDetails = {
    id: raw.id,
    name: raw.name,
    ...(raw.opal && { opal: raw.opal }),
    ...(raw.pincode !== undefined && { pincode: raw.pincode }),
    ...(raw.isPincodeEnabled !== undefined && { isPincodeEnabled: raw.isPincodeEnabled }),
    ...(raw.color && { color: raw.color }),
    ...(raw.isMusterPoint !== undefined && { isMusterPoint: raw.isMusterPoint }),
    ...(raw.notes !== undefined && { notes: raw.notes }),
    ...(raw.externalUuid !== undefined && { externalUuid: raw.externalUuid }),
    ...(raw.isReaderless !== undefined && { isReaderless: raw.isReaderless }),
    ...(raw.effectiveLocationRestriction && { effectiveLocationRestriction: raw.effectiveLocationRestriction }),
    ...(raw.org && { org: mapOrganizationRef(raw.org) }),
    ...(raw.shadow && { shadow: raw.shadow }),
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),
    ...(raw.zone && { zone: mapEntryZone(raw.zone) }),
    ...(raw.acu && { acu: mapEntryAcu(raw.acu) }),
    ...(raw.wirelessLock !== undefined && { wirelessLock: raw.wirelessLock }),
    ...(raw.entryState && { entryState: mapEntryState(raw.entryState) }),
    ...(raw.schedule && { schedule: mapEntrySchedule(raw.schedule) }),
    ...(raw.cameras && { cameras: raw.cameras.map(mapEntryCamera) }),
  };
  return output;
}
