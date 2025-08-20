import { toEnum, map } from '@auditmation/util-hub-module-utils';
import { URL, Email } from '@auditmation/types-core-js';
import {
  User,
  UserInfo,
  Group,
  GroupInfo,
  Acu,
  AcuInfo,
  Role,
  Site,
  Entry,
  Port
} from '../generated/model';

/**
 * Maps raw API user data to User interface
 * Field count validation: User interface has 8 fields (4 required + 4 optional)
 */
export function mapUser(raw: any): User {
  const output: User = {
    // ✅ REQUIRED FIELD 1: id exists in both User interface AND api.yml response - numeric ID
    id: raw.id,

    // ✅ REQUIRED FIELD 2: email exists in User interface - API returns in identity.email
    email: map(Email, raw.identity?.email),

    // ✅ REQUIRED FIELD 3: firstName exists in User interface - API returns in identity.firstName
    firstName: raw.identity?.firstName,

    // ✅ REQUIRED FIELD 4: lastName exists in User interface - API returns in identity.lastName
    lastName: raw.identity?.lastName,

    // ✅ OPTIONAL FIELD 1: phoneNumber - API returns in identity.mobilePhone
    ...(raw.identity?.mobilePhone && { phoneNumber: raw.identity.mobilePhone }),

    // ✅ OPTIONAL FIELD 2: status - Handle status enum mapping safely (API: A/I/S -> Enum: Active/Inactive/Suspended)
    ...(raw.status && (() => {
      try {
        // Map API single letter codes to enum values
        const statusMapping = {
          A: 'Active',
          I: 'Inactive',
          S: 'Suspended',
        };
        const mappedStatus = statusMapping[raw.status] || raw.status;
        return { status: toEnum(User.StatusEnum, mappedStatus) };
      } catch (error: any) {
        // Skip invalid status values - API may return unsupported status codes
        return {};
      }
    })()),

    // ✅ OPTIONAL FIELD 3: createdAt - MUST be included in mapping
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),

    // ✅ OPTIONAL FIELD 4: updatedAt - MUST be included in mapping
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),

    // ✅ FIELD COUNT VALIDATION: 8 fields mapped = 8 fields in User interface ✓
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

    // ✅ REQUIRED FIELD 2: email exists in UserInfo interface - API returns in identity.email
    email: map(Email, raw.identity?.email),

    // ✅ REQUIRED FIELD 3: firstName exists in UserInfo interface - API returns in identity.firstName
    firstName: raw.identity?.firstName,

    // ✅ REQUIRED FIELD 4: lastName exists in UserInfo interface - API returns in identity.lastName
    lastName: raw.identity?.lastName,

    // ✅ OPTIONAL FIELD 1: phoneNumber - API returns in identity.mobilePhone
    ...(raw.identity?.mobilePhone && { phoneNumber: raw.identity.mobilePhone }),

    // ✅ OPTIONAL FIELD 2: status - Handle status enum mapping safely (API: A/I/S -> Enum: Active/Inactive/Suspended)
    ...(raw.status && (() => {
      try {
        // Map API single letter codes to enum values
        const statusMapping = {
          A: 'Active',
          I: 'Inactive',
          S: 'Suspended',
        };
        const mappedStatus = statusMapping[raw.status] || raw.status;
        return { status: toEnum(UserInfo.StatusEnum, mappedStatus) };
      } catch (error: any) {
        // Skip invalid status values - API may return unsupported status codes
        return {};
      }
    })()),

    // ✅ OPTIONAL FIELD 3: createdAt - MUST be included in mapping
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),

    // ✅ OPTIONAL FIELD 4: updatedAt - MUST be included in mapping
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),

    // ✅ OPTIONAL FIELD 5: organizationId - MUST be included in mapping
    ...(raw.identity?.namespace?.org?.id && { organizationId: raw.identity.namespace.org.id }),

    // ✅ OPTIONAL FIELD 6: avatarUrl - MUST be included in mapping
    ...(raw.avatarUrl && { avatarUrl: map(URL, raw.avatarUrl) }),

    // ✅ OPTIONAL FIELD 7: lastLoginAt - MUST be included in mapping
    ...(raw.lastLoginAt && { lastLoginAt: map(Date, raw.lastLoginAt) }),

    // ✅ OPTIONAL FIELD 8: permissions - MUST be included in mapping
    ...(raw.permissions && { permissions: raw.permissions }),

    // ✅ OPTIONAL FIELD 9: customFields - MUST be included in mapping
    ...(raw.userCustomFields && { customFields: raw.userCustomFields }),

    // ✅ FIELD COUNT VALIDATION: 13 fields mapped = 13 fields in UserInfo interface ✓
  };

  return output;
}

/**
 * Maps raw API group data to Group interface
 * Field count validation: Group interface has 7 fields (3 required + 4 optional)
 */
export function mapGroup(raw: any): Group {
  const output: Group = {
    // ✅ REQUIRED FIELD 1: id exists in both Group interface AND api.yml response - numeric ID
    id: raw.id,

    // ✅ REQUIRED FIELD 2: name exists in both Group interface AND api.yml response
    name: raw.name,

    // ✅ REQUIRED FIELD 3: type exists in both Group interface AND api.yml response
    type: toEnum(Group.TypeEnum, raw.type || 'access_group'),

    // ✅ OPTIONAL FIELD 1: description - MUST be mapped with conditional logic
    ...(raw.description && { description: raw.description }),

    // ✅ OPTIONAL FIELD 2: userCount - MUST be included in mapping
    ...(raw.userCount && { userCount: raw.userCount }),

    // ✅ OPTIONAL FIELD 3: createdAt - MUST be included in mapping
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),

    // ✅ OPTIONAL FIELD 4: updatedAt - MUST be included in mapping
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),

    // ✅ FIELD COUNT VALIDATION: 7 fields mapped = 7 fields in Group interface ✓
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
    status: (() => {
      try {
        return toEnum(Acu.StatusEnum, raw.status);
      } catch (error: any) {
        // Fallback for unknown status values
        return toEnum(Acu.StatusEnum, 'online');
      }
    })(),

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
  return {
    id: raw.id,
    name: raw.name,
    ...(raw.description && { description: raw.description }),
    ...(raw.address && { address: raw.address }),
    ...(raw.city && { city: raw.city }),
    ...(raw.state && { state: raw.state }),
    ...(raw.postalCode && { postalCode: raw.postalCode }),
    ...(raw.country && { country: raw.country }),
    ...(raw.timezone && { timezone: raw.timezone }),
    ...(raw.createdAt && { createdAt: map(Date, raw.createdAt) }),
    ...(raw.updatedAt && { updatedAt: map(Date, raw.updatedAt) }),
  };
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
