/* eslint-disable */
import { toEnum, map } from '@auditmation/util-hub-module-utils';
import { URL, Email } from '@auditmation/types-core-js';
import { mapWith, ensureProperties, optional } from './util';
import {
  User,
  UserInfo,
  UserIdentity,
  IdentityNamespace,
  NamespaceType,
  UserGroup,
  UserCustomField,
  BuildingFloorUnit,
  Group,
  GroupInfo,
  Role,
  Site,
  Entry,
  TokenProperties,
  Zone,
  ZoneEntry,
  ZoneEntryAcu,
  ZoneSiteRef,
  OrganizationRef,
  EntryZone,
  EntryZoneSite,
  EntryAcu,
  EntryState,
  EntrySchedule,
  EntryCamera,
  VideoProvider,
  UserZone,
  MfaCredential,
  MfaCredentialType,
  TotpSoftDevice,
  UserPicture,
  PictureInfo,
  UserEntry,
  WirelessLock,
  WirelessLockDbEntry,
  UserEntryZone,
  GroupZone,
  GroupZoneGroup,
  Schedule,
  CredentialType,
  CredentialActionType,
  CredentialAction,
  OrgCredential,
  CardFormat,
  ZoneShare,
  ZoneShareSharedWithOrg,
  ZoneZoneUser,
  ZoneZoneUserUser,
  IdentityProviderType,
  IdentityProviderTypeInfo,
  IdentityProviderTypeSummary,
  IdentityProvider,
  IdentityProviderGroup,
  IdentityProviderGroupRelation,
  GroupSummary,
  AuthStrategyType,
  EntryInfo,
  EntryActivityEvent,
  EntryActivityEventUserGroups,
  EntryActivityEventUserSites,
  EntryStateInfo,
  EntryUser,
  EntryUserSchedule,
  UserSite,
  AuditLogEntry,
  ScheduleType,
  ScheduleEvent,
  SiteSummary,
  RoleInfo,
  RoleUser,
  UserActivityEvent,
  OrgIdentity,
  OrgPicture,
  SharedUser,
  UserCredential,
  UserZoneUser
} from '../generated/model';

// ============================================================================
// HELPER FUNCTIONS (NON-EXPORTED) - Declared before exported mappers
// ============================================================================

// Helper for OrganizationRef mapping
function toOrganizationRef(raw: any): OrganizationRef {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: OrganizationRef = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

// Helper for NamespaceType mapping
function toNamespaceType(raw: any): NamespaceType {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name', 'modelName']);

  // 2. Create output object
  const output: NamespaceType = {
    id: Number(raw.id),
    name: raw.name,
    modelName: raw.modelName,
  };

  return output;
}

// Helper for IdentityNamespace mapping
function toIdentityNamespace(raw: any): IdentityNamespace {
  // Create output object (no required fields for IdentityNamespace)
  const output: IdentityNamespace = {
    id: optional(raw.id),
    nickname: optional(raw.nickname),
    namespaceType: mapWith(toNamespaceType, raw.namespaceType),
    org: mapWith(toOrganizationRef, raw.org),
  };

  return output;
}

// Helper for UserIdentity mapping
function toUserIdentity(raw: any): UserIdentity {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'email']);

  // 2. Create output object
  const output: UserIdentity = {
    id: String(raw.id),
    email: map(Email, raw.email),
    firstName: optional(raw.firstName),
    lastName: optional(raw.lastName),
    fullName: optional(raw.fullName),
    initials: optional(raw.initials),
    opal: optional(raw.opal),
    phoneNumber: optional(raw.mobilePhone), // API uses 'mobilePhone', spec expects 'phoneNumber'
    mobilePhone: optional(raw.mobilePhone),
    avatarUrl: optional(raw.avatarUrl),
    middleName: optional(raw.middleName),
    suffix: optional(raw.suffix),
    preferredName: optional(raw.preferredName),
    pronouns: optional(raw.pronouns),
    dateOfBirth: map(Date, raw.dateOfBirth),
    emergencyContactName: optional(raw.emergencyContactName),
    emergencyContactPhone: optional(raw.emergencyContactPhone),
    homeAddress: optional(raw.homeAddress),
    companyName: optional(raw.companyName),
    workAddress: optional(raw.workAddress),
    isEmailVerified: optional(raw.isEmailVerified),
    idpUniqueIdentifier: optional(raw.idpUniqueIdentifier),
    language: optional(raw.language),
    nicknames: optional(raw.nicknames),
    needsPasswordChange: optional(raw.needsPasswordChange),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
    namespace: mapWith(toIdentityNamespace, raw.namespace),
  };

  return output;
}

// Helpers for Zone mapping
function toZoneEntryAcu(raw: any): ZoneEntryAcu {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ZoneEntryAcu = { id: String(raw.id) };

  return output;
}

function toZoneEntry(raw: any): ZoneEntry {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ZoneEntry = {
    id: String(raw.id),
    name: raw.name,
    wirelessLock: optional(raw.wirelessLock),
    acu: mapWith(toZoneEntryAcu, raw.acu),
  };

  return output;
}

function toZoneSiteRef(raw: any): ZoneSiteRef {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ZoneSiteRef = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

// Helpers for Entry mapping
function toVideoProvider(raw: any): VideoProvider {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: VideoProvider = {
    id: String(raw.id),
    videoProviderTypeId: raw.videoProviderTypeId,
  };

  return output;
}

/**
 * Maps raw API entry camera data to EntryCamera interface
 */
export function toEntryCamera(raw: any): EntryCamera {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntryCamera = {
    id: String(raw.id),
    name: raw.name,
    nameExt: optional(raw.nameExt),
    idExt: optional(raw.idExt),
    supportsSnapshot: optional(raw.supportsSnapshot),
    supportsDeeplink: optional(raw.supportsDeeplink),
    supportsMotionSnapshot: optional(raw.supportsMotionSnapshot),
    supportsFaceCrop: optional(raw.supportsFaceCrop),
    supportsFaceDetection: optional(raw.supportsFaceDetection),
    supportsPeopleDetection: optional(raw.supportsPeopleDetection),
    supportsMotionRecap: optional(raw.supportsMotionRecap),
    supportsLivestream: optional(raw.supportsLivestream),
    videoProviderId: optional(raw.videoProviderId),
    videoProvider: mapWith(toVideoProvider, raw.videoProvider),
  };

  return output;
}

function toEntrySchedule(raw: any): EntrySchedule {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntrySchedule = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

function toEntryState(raw: any): EntryState {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntryState = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

function toEntryAcu(raw: any): EntryAcu {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntryAcu = {
    id: String(raw.id),
    name: raw.name,
    isGatewayMode: optional(raw.isGatewayMode),
  };

  return output;
}

function toEntryZoneSite(raw: any): EntryZoneSite {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntryZoneSite = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

function toEntryZone(raw: any): EntryZone {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntryZone = {
    id: String(raw.id),
    name: raw.name,
    site: mapWith(toEntryZoneSite, raw.site),
  };

  return output;
}

// Helpers for MFA credential mapping
function toMfaCredentialType(raw: any): MfaCredentialType {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: MfaCredentialType = {
    id: String(raw.id),
    name: raw.name,
    modelName: optional(raw.modelName),
  };

  return output;
}

function toTotpSoftDevice(raw: any): TotpSoftDevice {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: TotpSoftDevice = { id: String(raw.id) };

  return output;
}

// Helpers for UserPicture mapping
function toPictureInfo(raw: any): PictureInfo {
  // 2. Create output object (no required fields for PictureInfo)
  const output: PictureInfo = {
    contentType: optional(raw.contentType),
    fileName: optional(raw.fileName),
    url: map(URL, raw.url),
  };

  return output;
}

// Helpers for UserEntry mapping
function toWirelessLockDbEntry(raw: any): WirelessLockDbEntry {
  // 2. Create output object (no required fields for WirelessLockDbEntry)
  const output: WirelessLockDbEntry = { credentialNumberHash: optional(raw.credentialNumberHash) };

  return output;
}

function toWirelessLock(raw: any): WirelessLock {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: WirelessLock = {
    id: String(raw.id),
    noTourLockId: optional(raw.noTourLockId),
    isOffline: optional(raw.isOffline),
    wirelessLockDbEntries: Array.isArray(raw.wirelessLockDbEntries) ? (raw.wirelessLockDbEntries).map(s => toWirelessLockDbEntry(s)) : undefined,
  };

  return output;
}

function toUserEntryZone(raw: any): UserEntryZone {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: UserEntryZone = {
    id: String(raw.id),
    name: raw.name,
    offlineEntryCount: optional(raw.offlineEntryCount),
    site: mapWith(toZoneSiteRef, raw.site),
  };

  return output;
}

// ============================================================================
// EXPORTED MAPPER FUNCTIONS - Alphabetical order
// ============================================================================

/**
 * Maps raw API entry data to Entry interface
 */
export function toEntry(raw: any): Entry {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: Entry = {
    id: String(raw.id),
    name: raw.name,
    opal: optional(raw.opal),
    pincode: optional(raw.pincode),
    isPincodeEnabled: optional(raw.isPincodeEnabled),
    color: optional(raw.color),
    isMusterPoint: optional(raw.isMusterPoint),
    notes: optional(raw.notes),
    externalUuid: optional(raw.externalUuid),
    isReaderless: optional(raw.isReaderless),
    isIntercomEntry: optional(raw.isIntercomEntry),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
    zone: mapWith(toEntryZone, raw.zone),
    acu: mapWith(toEntryAcu, raw.acu),
    wirelessLock: optional(raw.wirelessLock),
    entryState: mapWith(toEntryState, raw.entryState),
    schedule: mapWith(toEntrySchedule, raw.schedule),
    cameras: Array.isArray(raw.cameras) ? (raw.cameras).map(s => toEntryCamera(s)) : undefined,
  };

  return output;
}

/**
 * Maps raw API group data to Group interface
 */
export function toGroup(raw: any): Group {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: Group = {
    id: String(raw.id),
    name: raw.name,
    badgeConfig: optional(raw.badgeConfig),
    description: optional(raw.description),
    userCount: optional(raw.userCount),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API group data to GroupInfo interface (extended Group with additional fields)
 */
export function toGroupInfo(groupData: Group, raw: any): GroupInfo {
  // 2. Create output object (using pre-validated groupData)
  const output: GroupInfo = {
    id: groupData.id,
    name: groupData.name,
    description: groupData.description,
    userCount: groupData.userCount,
    badgeConfig: groupData.badgeConfig,
    createdAt: groupData.createdAt,
    updatedAt: groupData.updatedAt,
    organizationId: optional(raw.organizationId),
    parentGroupId: optional(raw.parentGroupId),
    permissions: optional(raw.permissions),
    accessRules: optional(raw.accessRules),
    customFields: optional(raw.customFields),
  };

  return output;
}

/**
 * Maps raw API MFA credential data to MfaCredential interface
 */
export function toMfaCredential(raw: any): MfaCredential {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: MfaCredential = {
    id: String(raw.id),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
    name: raw.name,
    status: optional(raw.status),
    mfaCredentialType: mapWith(toMfaCredentialType, raw.mfaCredentialType),
    totpSoftDevice: mapWith(toTotpSoftDevice, raw.totpSoftDevice),
  };

  return output;
}

/**
 * Maps raw API role data to Role interface
 */
export function toRole(raw: any): Role {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: Role = {
    id: String(raw.id),
    name: raw.name,
    description: optional(raw.description),
    permissions: optional(raw.permissions),
    assignedAt: map(Date, raw.assignedAt),
  };

  return output;
}

// Helper for SiteSummary mapping
function toSiteSummary(raw: any): SiteSummary {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: SiteSummary = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

/**
 * Maps raw API role data to RoleInfo interface
 */
export function toRoleInfo(raw: any): RoleInfo {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: RoleInfo = {
    id: String(raw.id),
    name: raw.name,
    description: optional(raw.description),
    isEditable: optional(raw.isEditable),
    isSiteSpecific: optional(raw.isSiteSpecific),
    isMfaRequired: optional(raw.isMfaRequired),
    userCount: optional(raw.userCount),
    sites: Array.isArray(raw.sites) ? (raw.sites).map(s => toSiteSummary(s)) : undefined,
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API role user data to RoleUser interface
 */
export function toRoleUser(raw: any): RoleUser {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: RoleUser = {
    id: String(raw.id),
    status: optional(raw.status),
    identity: mapWith(toUserIdentity, raw.identity),
    assignedAt: map(Date, raw.assignedAt),
  };

  return output;
}

/**
 * Maps raw API site data to Site interface
 */
export function toSite(raw: any): Site {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: Site = {
    id: String(raw.id),
    name: raw.name,
    opal: optional(raw.opal),
    address: optional(raw.address),
    address2: optional(raw.address2),
    city: optional(raw.city),
    state: optional(raw.state),
    zip: optional(raw.zip),
    country: optional(raw.country),
    phone: optional(raw.phone),
    language: optional(raw.language),
    zoneCount: optional(raw.zoneCount),
    userCount: optional(raw.userCount),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API token properties data to TokenProperties interface
 */
export function toTokenProperties(raw: any): TokenProperties {
  // 2. Create output object (no required fields for TokenProperties)
  // Extract organization ID from tokenScopeList
  const tokenScopeList = raw.tokenScopeList;
  const organizationId = tokenScopeList?.[0]?.org?.id ? String(tokenScopeList[0].org.id) : undefined;

  // Extract scope array from tokenScopeList
  const scope = tokenScopeList?.[0]?.scope;

  const output: TokenProperties = {
    tokenType: optional(raw.tokenType),
    jti: optional(raw.jti),
    organizationId,
    identityId: raw.identityId ? String(raw.identityId) : undefined,
    // API provides expiresAt/createdAt as ISO strings, not iat/exp Unix timestamps
    issuedAt: map(Date, raw.createdAt),
    expiresAt: map(Date, raw.expiresAt),
    iat: optional(raw.iat),
    exp: optional(raw.exp),
    scope: optional(scope),
    tokenScopeList: optional(raw.tokenScopeList),
  };

  return output;
}

/**
 * Maps raw API user data to User interface
 */
export function toUser(raw: any): User {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'status']);

  // 2. Create output object
  const output: User = {
    id: String(raw.id),
    status: toEnum(User.StatusEnum, raw.status),
    opal: optional(raw.opal),
    identity: mapWith(toUserIdentity, raw.identity),
    groups: optional(raw.groups),
    sites: optional(raw.sites),
    buildingFloorUnits: optional(raw.buildingFloorUnits),
    hasRemoteUnlock: optional(raw.hasRemoteUnlock),
    isOverrideAllowed: optional(raw.isOverrideAllowed),
    startDate: map(Date, raw.startDate),
    endDate: map(Date, raw.endDate),
    startAndEndDateTimeZoneId: optional(raw.startAndEndDateTimeZoneId),
    externalId: optional(raw.externalId),
    personId: optional(raw.personId),
    title: optional(raw.title),
    department: optional(raw.department),
    lastActivityAt: map(Date, raw.lastActivityAt),
    lastParcelReminderAt: map(Date, raw.lastParcelReminderAt),
    manuallyInactivatedAt: map(Date, raw.manuallyInactivatedAt),
    statusLastUpdatedAt: map(Date, raw.statusLastUpdatedAt),
    userCustomFields: optional(raw.userCustomFields),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API user entry data to UserEntry interface
 */
export function toUserEntry(raw: any): UserEntry {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: UserEntry = {
    id: String(raw.id),
    name: raw.name,
    wirelessLock: mapWith(toWirelessLock, raw.wirelessLock),
    zone: mapWith(toUserEntryZone, raw.zone),
  };

  return output;
}

/**
 * Maps raw API user data to UserInfo interface (extended User with additional fields)
 */
export function toUserInfo(raw: any): UserInfo {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'status']);

  // 2. Create output object (includes all User fields + UserInfo-specific fields)
  const output: UserInfo = {
    // User fields (inherited via allOf)
    id: String(raw.id),
    status: toEnum(UserInfo.StatusEnum, raw.status),
    opal: optional(raw.opal),
    identity: mapWith(toUserIdentity, raw.identity),
    groups: optional(raw.groups),
    sites: optional(raw.sites),
    buildingFloorUnits: optional(raw.buildingFloorUnits),
    hasRemoteUnlock: optional(raw.hasRemoteUnlock),
    isOverrideAllowed: optional(raw.isOverrideAllowed),
    startDate: map(Date, raw.startDate),
    endDate: map(Date, raw.endDate),
    startAndEndDateTimeZoneId: optional(raw.startAndEndDateTimeZoneId),
    externalId: optional(raw.externalId),
    personId: optional(raw.personId),
    title: optional(raw.title),
    department: optional(raw.department),
    lastActivityAt: map(Date, raw.lastActivityAt),
    lastParcelReminderAt: map(Date, raw.lastParcelReminderAt),
    manuallyInactivatedAt: map(Date, raw.manuallyInactivatedAt),
    statusLastUpdatedAt: map(Date, raw.statusLastUpdatedAt),
    userCustomFields: optional(raw.userCustomFields),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
    // UserInfo-specific fields
    organizationId: optional(raw.organizationId),
    avatarUrl: map(URL, raw.avatarUrl),
    lastLoginAt: map(Date, raw.lastLoginAt),
    permissions: optional(raw.permissions),
    customFields: optional(raw.customFields),
  };

  return output;
}

/**
 * Maps raw API user picture data to UserPicture interface
 */
export function toUserPicture(raw: any): UserPicture {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: UserPicture = {
    id: String(raw.id),
    isAvatar: optional(raw.isAvatar),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
    picture: mapWith(toPictureInfo, raw.picture),
  };

  return output;
}

/**
 * Maps raw API user zone data to UserZone interface
 */
export function toUserZone(raw: any): UserZone {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: UserZone = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

/**
 * Maps raw API zone data to Zone interface
 * Flattens the response from {data: [...], meta: {...}} to just the zones array
 */
export function toZone(raw: any): Zone {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: Zone = {
    id: String(raw.id),
    name: raw.name,
    opal: optional(raw.opal),
    description: optional(raw.description),
    apbResetIcalText: optional(raw.apbResetIcalText),
    apbExpirationSeconds: optional(raw.apbExpirationSeconds),
    apbUseContactSensor: optional(raw.apbUseContactSensor),
    apbAllowSharedOrgReset: optional(raw.apbAllowSharedOrgReset),
    entryCount: optional(raw.entryCount),
    offlineEntryCount: optional(raw.offlineEntryCount),
    userCount: optional(raw.userCount),
    groupCount: optional(raw.groupCount),
    org: mapWith(toOrganizationRef, raw.org),
    site: mapWith(toZoneSiteRef, raw.site),
    zoneShares: optional(raw.zoneShares),
    entries: Array.isArray(raw.entries) ? (raw.entries).map(s => toZoneEntry(s)) : undefined,
    apbAreas: optional(raw.apbAreas),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API group zone data to GroupZone interface
 */
export function toGroupZone(raw: any): GroupZone {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: GroupZone = {
    id: String(raw.id),
    name: raw.name,
  };

  return output;
}

/**
 * Helper for Schedule mapping
 */
function toSchedule(raw: any): Schedule {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: Schedule = {
    id: String(raw.id),
    name: raw.name,
    description: optional(raw.description),
    scheduleType: mapWith(toScheduleType, raw.scheduleType),
    isActive: optional(raw.isActive),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API group zone group data to GroupZoneGroup interface
 */
export function toGroupZoneGroup(raw: any): GroupZoneGroup {
  // No required fields at top level, but zone.id is required if zone exists
  if (raw.zone) {
    ensureProperties(raw.zone, ['id']);
  }

  // 2. Create output object
  const output: GroupZoneGroup = {
    zone: raw.zone ? {
      id: String((raw.zone).id),
      name: optional((raw.zone).name),
    } : undefined,
    schedule: mapWith(toSchedule, raw.schedule),
  };

  return output;
}

// ============================================================================
// ENTRY-SPECIFIC MAPPERS
// ============================================================================

/**
 * Helper for EntryActivityEventUserGroups mapping
 */
function toEntryActivityEventUserGroups(raw: any): EntryActivityEventUserGroups {
  const output: EntryActivityEventUserGroups = {
    id: raw.id,
    name: raw.name  };
  return output;
}

/**
 * Helper for EntryActivityEventUserSites mapping
 */
function toEntryActivityEventUserSites(raw: any): EntryActivityEventUserSites {
  const output: EntryActivityEventUserSites = {
    id: raw.id,
    name: raw.name  };
  return output;
}

/**
 * Helper for UserSite mapping
 */
function toUserSite(raw: any): UserSite {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name']);

  // 2. Create output object
  const output: UserSite = {
    id: String(raw.id),
    name: raw.name,
    address: optional(raw.address),
  };

  return output;
}

/**
 * Maps raw API entry info data to EntryInfo interface (extended Entry with org)
 */
export function toEntryInfo(raw: any): EntryInfo {
  // EntryInfo is Entry + org field
  const entryData = toEntry(raw);

  const output: EntryInfo = {
    ...entryData,
    org: mapWith(toOrganizationRef, raw.org),
  };

  return output;
}

/**
 * Maps raw API entry activity event data to EntryActivityEvent interface
 */
export function toEntryActivityEvent(raw: any): EntryActivityEvent {
  // 1. Check for required fields
  ensureProperties(raw, ['time', 'sourceName']);

  // 2. Create output object
  const output: EntryActivityEvent = {
    time: raw.time,
    timeIsoString: optional(raw.timeIsoString),
    userId: optional(raw.userId),
    userFirstName: optional(raw.userFirstName),
    userMiddleName: optional(raw.userMiddleName),
    userLastName: optional(raw.userLastName),
    userName: optional(raw.userName),
    userEmail: map(Email, raw.userEmail),
    userMobilePhone: optional(raw.userMobilePhone),
    userOrgId: optional(raw.userOrgId),
    userOrgName: optional(raw.userOrgName),
    userExternalId: optional(raw.userExternalId),
    userIdpName: optional(raw.userIdpName),
    userGroups: Array.isArray(raw.userGroups) ? (raw.userGroups).map(s => toEntryActivityEventUserGroups(s)) : undefined,
    userSites: Array.isArray(raw.userSites) ? (raw.userSites).map(s => toEntryActivityEventUserSites(s)) : undefined,
    sourceName: raw.sourceName,
    category: optional(raw.category),
    subCategory: optional(raw.subCategory),
    credentialSubtype: optional(raw.credentialSubtype),
    credentialTypeModelName: optional(raw.credentialTypeModelName),
    credentialTypeName: optional(raw.credentialTypeName),
    credentialDetail: optional(raw.credentialDetail),
    entryId: optional(raw.entryId),
    entryName: optional(raw.entryName),
    zoneId: optional(raw.zoneId),
    zoneName: optional(raw.zoneName),
    siteId: optional(raw.siteId),
    siteName: optional(raw.siteName),
    requestType: optional(raw.requestType),
    result: optional(raw.result),
    resultDescription: optional(raw.resultDescription),
    deniedReason: optional(raw.deniedReason),
    location: optional(raw.location),
  };

  return output;
}

/**
 * Maps raw API entry state info data to EntryStateInfo interface
 */
export function toEntryStateInfo(raw: any): EntryStateInfo {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name', 'code']);

  // 2. Create output object
  const output: EntryStateInfo = {
    id: String(raw.id),
    ordinal: optional(raw.ordinal),
    name: raw.name,
    code: raw.code,
    description: optional(raw.description),
    isLocked: optional(raw.isLocked),
    isToggle: optional(raw.isToggle),
    orgId: optional(raw.orgId),
    isMultiFactor: optional(raw.isMultiFactor),
    triggerMethods: optional(raw.triggerMethods),
  };

  return output;
}

/**
 * Maps raw API entry user data to EntryUser interface
 */
export function toEntryUser(raw: any): EntryUser {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: EntryUser = {
    id: String(raw.id),
    status: optional(raw.status),
    identity: raw.identity ? {
      id: (raw.identity).id ? String((raw.identity).id) : undefined,
      firstName: optional((raw.identity).firstName),
      middleName: optional((raw.identity).middleName),
      lastName: optional((raw.identity).lastName),
      fullName: optional((raw.identity).fullName),
      initials: optional((raw.identity).initials),
      email: map(Email, (raw.identity).email),
    } : undefined,
    sites: Array.isArray(raw.sites) ? (raw.sites).map(s => toUserSite(s)) : undefined,
  };

  return output;
}

/**
 * Maps raw API entry user schedule data to EntryUserSchedule interface
 */
export function toEntryUserSchedule(raw: any): EntryUserSchedule {
  // No required fields at top level, but user.id is required if user exists
  if (raw.user) {
    ensureProperties(raw.user, ['id']);
  }

  // 2. Create output object
  const output: EntryUserSchedule = {
    user: raw.user ? {
      id: String((raw.user).id),
      status: optional((raw.user).status),
      identity: (raw.user).identity ? {
        id: ((raw.user).identity).id ? String(((raw.user).identity).id) : undefined,
        firstName: optional(((raw.user).identity).firstName),
        middleName: optional(((raw.user).identity).middleName),
        lastName: optional(((raw.user).identity).lastName),
        fullName: optional(((raw.user).identity).fullName),
        initials: optional(((raw.user).identity).initials),
        email: map(Email, ((raw.user).identity).email),
      } : undefined,
      sites: Array.isArray((raw.user).sites) ? ((raw.user).sites).map(s => toUserSite(s)) : undefined,
    } : undefined,
    schedules: Array.isArray(raw.schedules) ? (raw.schedules).map(s => toEntrySchedule(s)) : undefined,
  };

  return output;
}

/**
 * Helper for ZoneShareSharedWithOrg mapping
 */
function toZoneShareSharedWithOrg(raw: any): ZoneShareSharedWithOrg {
  // 2. Create output object (no required fields for ZoneShareSharedWithOrg)
  const output: ZoneShareSharedWithOrg = {
    id: raw.id ? String(raw.id) : undefined,
    name: raw.name,
  };

  return output;
}

/**
 * Maps raw API zone share data to ZoneShare interface
 */
export function toZoneShare(raw: any): ZoneShare {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ZoneShare = {
    id: String(raw.id),
    zoneId: optional(raw.zoneId),
    sharedWithOrgId: optional(raw.sharedWithOrgId),
    sharedWithOrg: mapWith(toZoneShareSharedWithOrg, raw.sharedWithOrg),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Helper for ZoneZoneUserUser mapping
 */
function toZoneZoneUserUser(raw: any): ZoneZoneUserUser {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ZoneZoneUserUser = {
    id: String(raw.id),
    status: optional(raw.status),
    identity: mapWith(toUserIdentity, raw.identity),
  };

  return output;
}

/**
 * Maps raw API zone zone user data to ZoneZoneUser interface
 */
export function toZoneZoneUser(raw: any): ZoneZoneUser {
  // 2. Create output object (no required fields at top level)
  const output: ZoneZoneUser = {
    user: mapWith(toZoneZoneUserUser, raw.user),
    schedule: mapWith(toSchedule, raw.schedule),
  };

  return output;
}

/**
 * Maps raw API audit log entry data to AuditLogEntry interface
 */
export function toAuditLogEntry(raw: any): AuditLogEntry {
  // 1. Check for required fields - only timestamp is truly required
  ensureProperties(raw, ['timestamp']);

  // 2. Create output object
  const output: AuditLogEntry = {
    timestamp: raw.timestamp,
    timestampIso: optional(raw.timestampIso),
    action: optional(raw.action),
    category: optional(raw.category),
    actorId: optional(raw.actorId),
    actorName: optional(raw.actorName),
    actorEmail: map(Email, raw.actorEmail),
    targetId: optional(raw.targetId),
    targetType: optional(raw.targetType),
    targetName: optional(raw.targetName),
    details: optional(raw.details),
    ipAddress: optional(raw.ipAddress),
    userAgent: optional(raw.userAgent),
  };

  return output;
}

// ============================================================================
// CREDENTIAL-SPECIFIC MAPPERS
// ============================================================================

/**
 * Maps raw API credential type data to CredentialType interface
 */
export function toCredentialType(raw: any): CredentialType {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: CredentialType = {
    id: String(raw.id),
    name: raw.name,
    description: optional(raw.description),
    modelName: optional(raw.modelName),
  };

  return output;
}

/**
 * Maps raw API credential action type data to CredentialActionType interface
 */
export function toCredentialActionType(raw: any): CredentialActionType {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: CredentialActionType = {
    id: String(raw.id),
    name: raw.name,
    code: optional(raw.code),
    description: optional(raw.description),
  };

  return output;
}

/**
 * Maps raw API credential action data to CredentialAction interface
 */
export function toCredentialAction(raw: any): CredentialAction {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: CredentialAction = {
    id: String(raw.id),
    credentialId: optional(raw.credentialId),
    credentialActionType: mapWith(toCredentialActionType, raw.credentialActionType),
    performedBy: raw.performedBy ? {
      id: (raw.performedBy).id ? String((raw.performedBy).id) : undefined,
      identity: mapWith(toUserIdentity, (raw.performedBy).identity),
    } : undefined,
    performedAt: map(Date, raw.performedAt),
    details: optional(raw.details),
  };

  return output;
}

/**
 * Maps raw API organization credential data to OrgCredential interface
 */
export function toOrgCredential(raw: any): OrgCredential {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: OrgCredential = {
    id: String(raw.id),
    userId: optional(raw.userId),
    user: raw.user ? {
      id: (raw.user).id ? String((raw.user).id) : undefined,
      identity: mapWith(toUserIdentity, (raw.user).identity),
    } : undefined,
    credentialType: mapWith(toCredentialType, raw.credentialType),
    startDate: map(Date, raw.startDate),
    endDate: map(Date, raw.endDate),
    mobile: optional(raw.mobile),
    card: optional(raw.card),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API card format data to CardFormat interface
 */
export function toCardFormat(raw: any): CardFormat {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: CardFormat = {
    id: String(raw.id),
    name: raw.name,
    code: optional(raw.code),
    description: optional(raw.description),
    bitLength: optional(raw.bitLength),
    facilityCodeStart: optional(raw.facilityCodeStart),
    facilityCodeLength: optional(raw.facilityCodeLength),
    cardNumberStart: optional(raw.cardNumberStart),
    cardNumberLength: optional(raw.cardNumberLength),
  };

  return output;
}

// ============================================================================
// SCHEDULE MAPPERS
// ============================================================================

/**
 * Helper for ScheduleType mapping
 */
function toScheduleType(raw: any): ScheduleType {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ScheduleType = {
    id: String(raw.id),
    name: raw.name,
    code: optional(raw.code),
    description: optional(raw.description),
  };

  return output;
}

/**
 * Maps raw API schedule type data to ScheduleType interface (exported for use in producers)
 */
export function toScheduleTypeExported(raw: any): ScheduleType {
  return toScheduleType(raw);
}

/**
 * Maps raw API schedule data to Schedule interface (exported for use in producers)
 */
export function toScheduleExported(raw: any): Schedule {
  return toSchedule(raw);
}

/**
 * Maps raw API schedule event data to ScheduleEvent interface
 */
export function toScheduleEvent(raw: any): ScheduleEvent {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: ScheduleEvent = {
    id: String(raw.id),
    scheduleId: optional(raw.scheduleId),
    startTime: map(Date, raw.startTime),
    endTime: map(Date, raw.endTime),
    daysOfWeek: Array.isArray(raw.daysOfWeek) ? (raw.daysOfWeek).map((d: any) => toEnum(ScheduleEvent.DaysOfWeekEnum, d)) : undefined,
    isRecurring: optional(raw.isRecurring),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

// ============================================================================
// IDENTITY PROVIDER MAPPER HELPERS
// ============================================================================

/**
 * Helper for AuthStrategyType mapping
 */
function toAuthStrategyType(raw: any): AuthStrategyType {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: AuthStrategyType = {
    id: String(raw.id),
    name: raw.name,
    code: optional(raw.code),
  };

  return output;
}

/**
 * Helper for IdentityProviderTypeSummary mapping
 */
function toIdentityProviderTypeSummary(raw: any): IdentityProviderTypeSummary {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: IdentityProviderTypeSummary = {
    id: Number(raw.id),
    name: raw.name,
    code: optional(raw.code),
  };

  return output;
}

/**
 * Helper for GroupSummary mapping
 */
function toGroupSummary(raw: any): GroupSummary {
  // No required fields for GroupSummary
  const output: GroupSummary = {
    id: optional(raw.id),
    name: raw.name,
  };

  return output;
}

// ============================================================================
// IDENTITY PROVIDER MAPPER EXPORTS
// ============================================================================

/**
 * Maps raw API identity provider type data to IdentityProviderType interface
 */
export function toIdentityProviderType(raw: any): IdentityProviderType {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name', 'code']);

  // 2. Create output object
  const output: IdentityProviderType = {
    id: String(raw.id),
    name: raw.name,
    code: raw.code,
    featureCode: optional(raw.featureCode),
    supportsIdpInitiatedSso: optional(raw.supportsIdpInitiatedSso),
    authStrategyTypes: Array.isArray(raw.authStrategyTypes) ? (raw.authStrategyTypes).map(s => toAuthStrategyType(s)) : undefined,
  };

  return output;
}

/**
 * Maps raw API identity provider type data to IdentityProviderTypeInfo interface
 */
export function toIdentityProviderTypeInfo(raw: any): IdentityProviderTypeInfo {
  // 1. Check for required fields
  ensureProperties(raw, ['id', 'name', 'code']);

  // 2. Create output object (IdentityProviderTypeInfo extends IdentityProviderType)
  const output: IdentityProviderTypeInfo = {
    id: String(raw.id),
    name: raw.name,
    code: raw.code,
    featureCode: optional(raw.featureCode),
    supportsIdpInitiatedSso: optional(raw.supportsIdpInitiatedSso),
    authStrategyTypes: Array.isArray(raw.authStrategyTypes) ? (raw.authStrategyTypes).map(s => toAuthStrategyType(s)) : undefined,
  };

  return output;
}

/**
 * Maps raw API identity provider data to IdentityProvider interface
 */
export function toIdentityProvider(raw: any): IdentityProvider {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: IdentityProvider = {
    id: raw.id,
    orgId: optional(raw.orgId),
    identityProviderType: mapWith(toIdentityProviderTypeSummary, raw.identityProviderType),
    isSyncUsersEnabled: optional(raw.isSyncUsersEnabled),
    isMobileCredentialEnabled: optional(raw.isMobileCredentialEnabled),
    isSsoEnabled: optional(raw.isSsoEnabled),
    isMobileSsoEnabled: optional(raw.isMobileSsoEnabled),
    lastSyncedAt: map(Date, raw.lastSyncedAt),
    status: optional(raw.status),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API identity provider group data to IdentityProviderGroup interface
 */
export function toIdentityProviderGroup(raw: any): IdentityProviderGroup {
  // No required fields for IdentityProviderGroup
  const output: IdentityProviderGroup = {
    idpGroupUniqueIdentifier: optional(raw.idpGroupUniqueIdentifier),
    name: raw.name,
    description: optional(raw.description),
    email: map(Email, raw.email),
  };

  return output;
}

/**
 * Maps raw API identity provider group relation data to IdentityProviderGroupRelation interface
 */
export function toIdentityProviderGroupRelation(raw: any): IdentityProviderGroupRelation {
  // No required fields for IdentityProviderGroupRelation
  const output: IdentityProviderGroupRelation = {
    idpGroupUniqueIdentifier: optional(raw.idpGroupUniqueIdentifier),
    identityProviderGroup: mapWith(toIdentityProviderGroup, raw.identityProviderGroup),
    groupId: optional(raw.groupId),
    group: mapWith(toGroupSummary, raw.group),
  };

  return output;
}

// ============================================================================
// USER-SPECIFIC MAPPERS
// ============================================================================

/**
 * Maps raw API user activity event data to UserActivityEvent interface
 */
export function toUserActivityEvent(raw: any): UserActivityEvent {
  // 1. Check for required fields
  ensureProperties(raw, ['time', 'sourceName']);

  // 2. Create output object
  const output: UserActivityEvent = {
    time: raw.time,
    timeIsoString: optional(raw.timeIsoString),
    sourceName: raw.sourceName,
    userId: optional(raw.userId),
    userFirstName: optional(raw.userFirstName),
    userLastName: optional(raw.userLastName),
    userName: optional(raw.userName),
    userEmail: map(Email, raw.userEmail),
    category: optional(raw.category),
    subCategory: optional(raw.subCategory),
    credentialSubtype: optional(raw.credentialSubtype),
    credentialTypeModelName: optional(raw.credentialTypeModelName),
    credentialTypeName: optional(raw.credentialTypeName),
    credentialDetail: optional(raw.credentialDetail),
    entryId: optional(raw.entryId),
    entryName: optional(raw.entryName),
    zoneId: optional(raw.zoneId),
    zoneName: optional(raw.zoneName),
    siteId: optional(raw.siteId),
    siteName: optional(raw.siteName),
    requestType: optional(raw.requestType),
    result: optional(raw.result),
    resultDescription: optional(raw.resultDescription),
    deniedReason: optional(raw.deniedReason),
    location: optional(raw.location),
  };

  return output;
}

/**
 * Maps raw API organization identity data to OrgIdentity interface
 */
export function toOrgIdentity(raw: any): OrgIdentity {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: OrgIdentity = {
    id: String(raw.id),
    email: map(Email, raw.email),
    firstName: optional(raw.firstName),
    lastName: optional(raw.lastName),
    phoneNumber: optional(raw.phoneNumber),
    avatarUrl: map(URL, raw.avatarUrl),
    isVerified: optional(raw.isVerified),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API organization picture data to OrgPicture interface
 */
export function toOrgPicture(raw: any): OrgPicture {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: OrgPicture = {
    id: Number(raw.id),
    isAvatar: optional(raw.isAvatar),
    picture: mapWith(toPictureInfo, raw.picture),
    createdAt: map(Date, raw.createdAt),
    updatedAt: map(Date, raw.updatedAt),
  };

  return output;
}

/**
 * Maps raw API shared user data to SharedUser interface
 */
export function toSharedUser(raw: any): SharedUser {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: SharedUser = {
    id: String(raw.id),
    userId: optional(raw.userId),
    user: optional(raw.user),
    sharedFromOrgId: optional(raw.sharedFromOrgId),
    sharedFromOrg: optional(raw.sharedFromOrg),
    sharedAt: map(Date, raw.sharedAt),
  };

  return output;
}

/**
 * Maps raw API user credential data to UserCredential interface
 */
export function toUserCredential(raw: any): UserCredential {
  // 1. Check for required fields
  ensureProperties(raw, ['id']);

  // 2. Create output object
  const output: UserCredential = {
    id: String(raw.id),
    startDate: map(Date, raw.startDate),
    endDate: map(Date, raw.endDate),
    credentialType: mapWith(toCredentialType, raw.credentialType),
    mobile: optional(raw.mobile),
    card: optional(raw.card),
    pincode: optional(raw.pincode),
  };

  return output;
}

/**
 * Maps raw API user zone user data to UserZoneUser interface
 */
export function toUserZoneUser(raw: any): UserZoneUser {
  // 2. Create output object (no required fields for UserZoneUser)
  const output: UserZoneUser = {
    zone: optional(raw.zone),
    schedule: mapWith(toSchedule, raw.schedule),
  };

  return output;
}
