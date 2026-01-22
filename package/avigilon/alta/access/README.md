# @zerobias-org/module-avigilon-alta-access

A TypeScript client library for integrating with the Avigilon Alta Access API. Provides type-safe operations for managing people, groups, and access control units with read-only access.

For credential setup, see the [User Guide](USER_GUIDE.md).

**Note**: API operations and data models are automatically appended to this README during the publishing process based on the OpenAPI specification.

## Installation

```bash
npm install @zerobias-org/module-avigilon-alta-access
```

Requires Node.js 18+ and an Avigilon Alta Access account with email/password credentials.

## Quick Start

```typescript
import { newAvigilonAltaAccess } from '@zerobias-org/module-avigilon-alta-access';
import { Email } from '@zerobias-org/types-core-js';

const client = newAvigilonAltaAccess();

await client.connect({
  email: new Email(process.env.AVIGILON_EMAIL!),
  password: process.env.AVIGILON_PASSWORD!,
  totpCode: process.env.AVIGILON_TOTP_CODE // Optional, for MFA
});

const users = await client.getUserProducerApi().listUsers();
console.log(users);

await client.disconnect();
```

See [User Guide](USER_GUIDE.md) for detailed setup instructions.

## Usage

```typescript
import { Email } from '@zerobias-org/types-core-js';

const client = newAvigilonAltaAccess();
await client.connect({
  email: new Email('your-email@domain.com'),
  password: 'your-password',
  totpCode: '123456' // Optional, if MFA is enabled
});

// Available APIs
const userApi = client.getUserProducerApi();
const groupApi = client.getGroupProducerApi();  
const acuApi = client.getAcuProducerApi();

// List operations
await userApi.listUsers();
await groupApi.listGroups();
await acuApi.listAcus();

// Get single item operations
await userApi.getUser('user-id');
await groupApi.getGroup('group-id');
await acuApi.getAcu('acu-id');
```


---

## External API Schema

[External API Schema Diagram](https://mermaid.live/view#pako:H4sIAMBOsGgCA9Ud23LbNvZXNH7c2f0Bvzm2s8k2TTy-tDM7nfFAICShJgkuADpW3fz7ngOQFEFcSCqS4vQhbYRDXM79BvT1jIqMnZ2fMXnFyVqS4o9yAf98zFipud4uXu3f8R-lJS_XC54tbn5Z_HH2jz_OvDFWEJ4vHmLDKy6V_kwK5o3kZDiQEc00L9iCSgb_mV3o4aQdRF1lYYilEDkj5YJQzZ9Zf_Sb_Zf984tck5L_RTQX5cwDl7Dn-HkzpqjkFc57qoP1Vscp_hKlj2xVLzMBlCr9CZUS1yVZ5izzSSTW4kHmu9__VIAuxbSGURVA64NiciY6ecN2H7PF-xiMkOvgcHeEWlWszFgWRWsH8QCL5YeljEEKrZUWxXvO8kx5-6841bVkDipbim3qYlmCCDmD7cG4utCa0A3L7sXHUjNJReHPvglRnBVVLraMfcwCPFoRqQvAe4CBdB7gHk10rWLYr4TUJL-glCnlj4pnJiUQ-YbJgivlyEWHnvISVJJ_hBc4cUny_hF2FNVwhiv4W2AMKB0ZQaXzSaxbOXCY9xY2PJN5Y4y5v6qwEkZFxaL45upOkzIjMs7uB9Azaqs0KxAng81VHSG9HXJg0TVogIIVSyYvRd1ymIPnf0tRVz8c0SfTyWs87v22YjEcG3xMQOIUBXzH9Y_n4WaMZJl0VELzOwVtH1QxvgageHrpQ1cC4HNXaWSM8oLkIOKgxOrgiCjXw6FTcUFYSfv2eozA_wXowxJYAcv8rLKFuIuI1obIvkfTTQf2_4YotST0yXN7WtGjpCLIpZ94wX0bCcYTTCcSwqBtOu2ukZePRLwgasIj-0s0wwMgvuOm6ZOgT-yohinMEAb3uaDGo79luGEaPINRNLGzCyFvBODU-bBdWLIXj2PasbqEtZ--lNcvfYZp-cmO3vMiwKiFKLkWuLw3uTkTICxj8i7OVReXD6fiqTTngO_PSf65RrMVhyoIvbCGAWD8bVcXEasBgWTxFSTvNyaDTiTIexYc7xzRMucli_IdeoZ3jJUTdPTJ1FsBBq5B6ESfvAvcrCAMeKlk-quQTy4z7UIY0HpLngP3syCf0fpSlCu-ruU-YTOhdZDpJmwsY8-cMn_wEHRoRbQSObDH2gRZz2QY8uIcotaR_SlGawnmIiWkA-R9dg88H5fObClpjkuUqpeA91-JevJ9VkDWV-L7XVmp7gA_fXZsGTnb0KrRYMfQ_v0NUIHRzzZERKmDyMfQ9LY-dHhXg8YLK0rj84eHjA2da7IVpgBg_4PRTonkufiawDswNM_ey37uwB07QlZkkoIzBmVudIzkNyZaBXUJod6Yww1ohnsOwmFZYjyXZSFCTlSnaZBT1UiGbx66DyiBJu_iujLdyTL3d6u8yVZ9Wf3O2NN3MqZDxBuQ9O-xRqgpUlSi_bmjxFKePTg-GVpdp1iJjuqzE0y3gxlbQuRMmUuPga27dSJuy30bsBQb4eQwe-nNhIG7NW7qYYUp5jKMa9JZjqo3bl3udJzzO2drUmZxd0LqZoHIFCPu6LE1byrMG3O1w65nxIdMCEvE9_YE_UCe5jhtesohSf3yQYUVgqM2jqJ-y1ApLUXMMLEc1F_Qvh5x0H8pmanPkHwmETrfaD_Rp926SIhpkFdEkz3NInupOGi-C32EylDBNIFxMnLGuLmNh3kUYt5IeLgiFMVwGyysFAJklGEaLFAdormos1_YNlSt8io1HQ4Mgi0WIlwGMhNItQGfxOoEly5-TpjV1nF6JN3SkOwf1yNwk_9NlPA2iljGkyXlmoUtBR7GDr9BH7dBZNJLvwEvQmRxzcpWK2Y2GvFvu_F7MZidvVBmMKqiwey9eGIhn1eb3_crtidKj76inI_9_g5T1nWkc-P6GY5wqjg-4WJaJzHyGe4xodRtiBpFIf4Ber-oJtqTXRuE4Y09PD4gaZ3rSbY4gohgi8-K8LyWEG0TFXSHKilww67RaE1NO_Yu6JeIUgMW7yAIEvLgIbzcfkdYMhZ74I5T_F8KWZA8336pWPkTBg_pvDQ6-jremnG5QYswvaJ2y3Ky_ZnIL3HD-1K_9ZkaXwszE_wH9c_dspefC-0v-yL9hEhtcwoHRWzPTs1DXZdLAlvzjutos0840jh2ODzVW3ipSIlJjHcC-6UOk0iYxHJLXDAV2MWSUD9Sxzu4axuCb6R45gdP7qWxVzVrRj1wh7VOGyi4jsiu7_MYlYQEA61wzc9JLBqQaSgMtTv8rwZfP_uBHPjJ7-d4O-Wanevc7i2KaUzGZ0TyeE_nCTgVO3My8bW8yUl5BFGe7ylWsJFw81aixUcTuWam-WqYXzYehHEuVSDnC7G6dEoldjbJ1zCsjoTyG0KfyJq9OYwHMbuC80Kwti-LdumMWE82Zg62V37NKccmO9JLd_RbNoFu1I8rl9wUsC63NGdHIt2vELpKIYof0Gs5n65-u88ESSJdV8RQkkTF0CiU6w-iljPKOAexp-A1vUd3XL8JiVmZrYRkptUsS64_sXKtN2GhQgt8xVa8DHcsTKDTkRH-G1fYf3hgu2ouhY3cCEvkCBLD4UZqKgpw9P2GpY0whYdInu7Znj2Y1hqpm5lP77AXIg1y7deIU3WWnmQObNxBif47W26EeJpJ9DHtVct8dpWgS5WqaJwEB9aAp6lC0womfCW3g-sTbsW5HlxZckbf29zlYLsbE00fiywXOZMH1nsEp0yGoc8MHaNou5HOJ-jNfYqXCowLZWHZM0Mfh63Q5ixXTup7lzO3nlySAODciPw5mG1uh95tp5LWIdxnofmKU3L4CCVem0iRpgC-BsczZgt7u9239BxxqP3ixO7CHgh6sDiMGarplfdBGrIS8m14CpLF2kbsrTkiYV4NuiNmFo2nMY8IK567l0o71K1ZiT5cpBx_hHYHN3OEKlgeQRoStOG7NWfz9HhGyUJ0hbBoE9O2pOGiBwzcmbWH3h0MXEspjmdTKg7G80hKaT6Zntj2A1FhZzlxs3SaU5bia78FpUeej9WxsF9nHC8cn8oooB1rzWo8YeWZ12RdOlkjtvktBtqLDQp71mSvQOXdBe92xi8m4PEu1izouM2pjbuUAGrOpELXj38gSzFyvbAbriVeMPxCaQ0xDd2mLzFGS8Tt57HHHQggxPW-dmm0GwGb8RJsL1wHB5oNY5x14mxBh6N7CajAU82s8MAekr2izdFMGBG97U5eLhuKhnBpvg3hcjjgqip79iCXb7jCG4PUtjyGeL0k-VZzqg6rdkC6YNWEaqlMS1RMMuHbUIvmrhvEfJ2OqS1MIKjesRXJaZ1P7sccdJUUVc6Bn9iNyDndnsx5qcxyUeexKcXgix37ZJBod6xBtu-7krcHFeR3IL51ZW90nAzrS7NoqsSGiGegXoJo5SXN64xlt41BjTstQkIw9imapGUj1-ZQH1gEBZ0XOxT0LiVEHCUuavn5SMT7AJNn5ATSghtZbOxqV4Fr3LvSJeptmCXgxFiNDhJONL4uM81yn7BMd42p03t8vYcc5SmPCI_Wyz9BA0R7GbLtB13kieF79hJ4Tag5RlSxPRPJkffVj-mXLtSeF3BHEN12L4SjnGawUXYRFo7ohF0XoWnqGZYzG3wfy91631wMss7JYXFmL3knlHHjisUuJdnCcDM6P_q3n1-ZTYTQ17aEhxMq5lJkZMxYXzYYNWtahN-auErtlf-y-_3ASK43M8lh8f1dFAmjc5ULohe0qh8wBTn8vWCFkNvgEHIvJq2crHvXomcvzIPQlKCnWBYxk4AKqZeAtoQTqkJpZRVMTFIhs3Ti97vu8Y_T4NC3928kL4jcTrxXP-Fuv3_1fuRZhLktFF1O6I1keUfzK8kkirleEXsILPRE3mEN3vdZuxkvxTRxP2YeYxbMAPxG8jpKBwjSUvYADrsWIV5OeXAW0w0i9kIi9lzv8QpXguH0X7bVcsV9l6G7J6fpl9UKsBZzGDKlY5memS6tyzLbkv5HLA8rfL1cfZh9_hTL2XZnf2O8qxijvgcd2dw8iUJgbdh7qIlhFv-KaRgbWhg4TqRsOW7im1L9_BzyV_thsk0Di-8RAQ7iuMXDRmsv4OxuJ2g05Okb1qBGK1Eq9g4iB39XiMhfbQUzdNPUzp9UhSVEI7dY_7_Q4ZLIwVPyxJRZYrfIsgrzpvG02kZEDaslBDbXxxsdLDKDj3LMyrJ3ySamBnX2ZsQuFCvBh1Lz_vPA3_7-17_-_nv33PT5YkPUY3sz0Qd0nmo-XywZPuOoHrXovdo6DdQ-PDoN1jykOQ3UGIMG1HyWBhmZzT4SuAdsZGkHxuzDh8En5Kat2IOMrNc8BNIAIfw5llat1x6BGiOxA2wPZCbVUrS61rwT4Sw6nKX3lEEDZ7hsCuDI_vwPBlfXLYvrrtgyGJ7Ike3t7omU6t0T9kUO3My6Rw57qXYi0_VhGxTy8hnbZ1QAoiUX1qOkemxbHByYhsDnuy6ex-U2dNFzMGfzdqJKgMYYMAg8yod42zDJhg5InPl7QKNrvgxXVPgCm_KG46u9TFyrvX82pErbGdEuOoAb4xf3CpazW3zhu2wfG_LuG02Uvt4NnGlfhK6VTPtyZ5deG2ty3rzT_ChWPoixTVb4ZfeQdh-g0aGUlI-k14zYM1SRSRyIjhVT0zSqPwnTqZjzxUrgm3IqsOUITKdcGjiPmucLUusN_oiR207CeybFe2LSHjtwUcmDjMwwzM_YCZtk0qNbne49EzhHxfY_aNQhqaqcMwR8RC8rBt1yUA98vXsL3Idv6dyDZ7vnsX34huQ98N07pj60T9jHNukZfjhvGpL8l9qa74zF9pWLc01psjwPPunYnZjCpvKv40ybubsFsh94I97megZwfGv7enccps3b9ujvBd3wJHbC96TOAYpqh7ZPPO3y9ULUBrD9Ds--VjEo6wOErL5thJ52XKf3dv9PGjRhl-pOwPvbcD46byJ35fShTlu93x050fVrkjCtH9ebAG0oo3WftAPoMSVm-wP3AW5QBpLXJ50NrifP1wNuZj_fda_uZm376CbOOwBvdloxiW22_XmxKywdHPk9Ru128VvkXvhZDad7bde0w48VKDgsO_twQ0fycQW6d9jHM9EVGvavTPvMab6Y9klb8t8ndHntvkbmNT0UKlT7njh3r4g77YtBFXPaR06lzfFh3SAkBNc50uOgblgy_GLozQw9f8eQRoCjXn2v0jI38_La__i8bYxRg6LDRHoOwCM2p8vC-27pzt85--dZARsjPDs7fz0D57PA_-9ZxlakzvXZt2__Bx4sHMQLbQAA)
<!-- external-api-hash: f023c3a1 -->

## Development

### Testing

Run integration tests:

```bash
npm run test:integration
```

### Test Fixtures

Integration tests use fixtures stored in `test/fixtures/integration/` for consistent test data. These fixtures are **not automatically updated** during test runs.

To update fixtures when API responses change:

```bash
SAVE_FIXTURES=true npm run test:integration
```

**When to update fixtures:**
- API response format changes
- New test scenarios require different data
- Debugging integration issues

**⚠️ Important:** Only update fixtures intentionally. Review changes before committing to ensure they reflect expected API behavior.

---

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Apis](#apis)
  - [AuditApi](#auditapi)
  - [**listAuditLogs**](#listauditlogs)
  - [AuthApi](#authapi)
  - [**getTokenProperties**](#gettokenproperties)
  - [CredentialApi](#credentialapi)
  - [**listCardFormats**](#listcardformats)
  - [**listCredentialActionTypes**](#listcredentialactiontypes)
  - [**listCredentialActions**](#listcredentialactions)
  - [**listCredentialTypes**](#listcredentialtypes)
  - [**listOrgCredentials**](#listorgcredentials)
  - [EntryApi](#entryapi)
  - [**getEntry**](#getentry)
  - [**listEntries**](#listentries)
  - [**listEntryActivity**](#listentryactivity)
  - [**listEntryCameras**](#listentrycameras)
  - [**listEntryStates**](#listentrystates)
  - [**listEntryUserSchedules**](#listentryuserschedules)
  - [**listEntryUsers**](#listentryusers)
  - [GroupApi](#groupapi)
  - [**getGroup**](#getgroup)
  - [**listGroupEntries**](#listgroupentries)
  - [**listGroupUsers**](#listgroupusers)
  - [**listGroupZoneGroups**](#listgroupzonegroups)
  - [**listGroupZones**](#listgroupzones)
  - [**listGroups**](#listgroups)
  - [IdentityProviderApi](#identityproviderapi)
  - [**getIdentityProviderGroupRelations**](#getidentityprovidergrouprelations)
  - [**getIdentityProviderType**](#getidentityprovidertype)
  - [**listIdentityProviderGroups**](#listidentityprovidergroups)
  - [**listIdentityProviderTypes**](#listidentityprovidertypes)
  - [**listIdentityProviders**](#listidentityproviders)
  - [RoleApi](#roleapi)
  - [**listRoleUsers**](#listroleusers)
  - [**listRoles**](#listroles)
  - [ScheduleApi](#scheduleapi)
  - [**listScheduleEvents**](#listscheduleevents)
  - [**listScheduleTypes**](#listscheduletypes)
  - [**listSchedules**](#listschedules)
  - [SiteApi](#siteapi)
  - [**listSites**](#listsites)
  - [UserApi](#userapi)
  - [**getUser**](#getuser)
  - [**listOrgIdentities**](#listorgidentities)
  - [**listOrgPictures**](#listorgpictures)
  - [**listSharedUsers**](#listsharedusers)
  - [**listUserActivity**](#listuseractivity)
  - [**listUserCredentials**](#listusercredentials)
  - [**listUserEntries**](#listuserentries)
  - [**listUserGroups**](#listusergroups)
  - [**listUserMfaCredentials**](#listusermfacredentials)
  - [**listUserPictures**](#listuserpictures)
  - [**listUserRoles**](#listuserroles)
  - [**listUserSites**](#listusersites)
  - [**listUserZoneUsers**](#listuserzoneusers)
  - [**listUserZones**](#listuserzones)
  - [**listUsers**](#listusers)
  - [ZoneApi](#zoneapi)
  - [**listZoneShares**](#listzoneshares)
  - [**listZoneZoneUsers**](#listzonezoneusers)
  - [**listZones**](#listzones)
- [Models](#models)
  - [AccessRule](#accessrule)
    - [Properties](#properties)
  - [AuditLogEntry](#auditlogentry)
    - [Properties](#properties-1)
  - [AuthStrategyType](#authstrategytype)
    - [Properties](#properties-2)
  - [BuildingFloorUnit](#buildingfloorunit)
    - [Properties](#properties-3)
  - [CardFormat](#cardformat)
    - [Properties](#properties-4)
  - [CredentialAction](#credentialaction)
    - [Properties](#properties-5)
  - [CredentialActionType](#credentialactiontype)
    - [Properties](#properties-6)
  - [CredentialAction_performedBy](#credentialaction_performedby)
    - [Properties](#properties-7)
  - [CredentialType](#credentialtype)
    - [Properties](#properties-8)
  - [Entry](#entry)
    - [Properties](#properties-9)
  - [EntryActivityEvent](#entryactivityevent)
    - [Properties](#properties-10)
  - [EntryActivityEvent_userGroups](#entryactivityevent_usergroups)
    - [Properties](#properties-11)
  - [EntryActivityEvent_userSites](#entryactivityevent_usersites)
    - [Properties](#properties-12)
  - [EntryAcu](#entryacu)
    - [Properties](#properties-13)
  - [EntryCamera](#entrycamera)
    - [Properties](#properties-14)
  - [EntryInfo](#entryinfo)
    - [Properties](#properties-15)
  - [EntryInfo_allOf](#entryinfo_allof)
    - [Properties](#properties-16)
  - [EntrySchedule](#entryschedule)
    - [Properties](#properties-17)
  - [EntryState](#entrystate)
    - [Properties](#properties-18)
  - [EntryStateInfo](#entrystateinfo)
    - [Properties](#properties-19)
  - [EntryUser](#entryuser)
    - [Properties](#properties-20)
  - [EntryUserSchedule](#entryuserschedule)
    - [Properties](#properties-21)
  - [EntryUserSchedule_user](#entryuserschedule_user)
    - [Properties](#properties-22)
  - [EntryUser_identity](#entryuser_identity)
    - [Properties](#properties-23)
  - [EntryZone](#entryzone)
    - [Properties](#properties-24)
  - [EntryZoneSite](#entryzonesite)
    - [Properties](#properties-25)
  - [Group](#group)
    - [Properties](#properties-26)
  - [GroupInfo](#groupinfo)
    - [Properties](#properties-27)
  - [GroupInfo_allOf](#groupinfo_allof)
    - [Properties](#properties-28)
  - [GroupSummary](#groupsummary)
    - [Properties](#properties-29)
  - [GroupZone](#groupzone)
    - [Properties](#properties-30)
  - [GroupZoneGroup](#groupzonegroup)
    - [Properties](#properties-31)
  - [IdentityNamespace](#identitynamespace)
    - [Properties](#properties-32)
  - [IdentityProvider](#identityprovider)
    - [Properties](#properties-33)
  - [IdentityProviderGroup](#identityprovidergroup)
    - [Properties](#properties-34)
  - [IdentityProviderGroupRelation](#identityprovidergrouprelation)
    - [Properties](#properties-35)
  - [IdentityProviderType](#identityprovidertype)
    - [Properties](#properties-36)
  - [IdentityProviderTypeInfo](#identityprovidertypeinfo)
    - [Properties](#properties-37)
  - [IdentityProviderTypeSummary](#identityprovidertypesummary)
    - [Properties](#properties-38)
  - [MfaCredential](#mfacredential)
    - [Properties](#properties-39)
  - [MfaCredentialType](#mfacredentialtype)
    - [Properties](#properties-40)
  - [NamespaceType](#namespacetype)
    - [Properties](#properties-41)
  - [OrgCredential](#orgcredential)
    - [Properties](#properties-42)
  - [OrgCredential_card](#orgcredential_card)
    - [Properties](#properties-43)
  - [OrgCredential_mobile](#orgcredential_mobile)
    - [Properties](#properties-44)
  - [OrgCredential_user](#orgcredential_user)
    - [Properties](#properties-45)
  - [OrgIdentity](#orgidentity)
    - [Properties](#properties-46)
  - [OrgPicture](#orgpicture)
    - [Properties](#properties-47)
  - [OrganizationRef](#organizationref)
    - [Properties](#properties-48)
  - [PictureInfo](#pictureinfo)
    - [Properties](#properties-49)
  - [Role](#role)
    - [Properties](#properties-50)
  - [RoleInfo](#roleinfo)
    - [Properties](#properties-51)
  - [RoleUser](#roleuser)
    - [Properties](#properties-52)
  - [Schedule](#schedule)
    - [Properties](#properties-53)
  - [ScheduleEvent](#scheduleevent)
    - [Properties](#properties-54)
  - [ScheduleType](#scheduletype)
    - [Properties](#properties-55)
  - [SharedUser](#shareduser)
    - [Properties](#properties-56)
  - [SharedUser_sharedFromOrg](#shareduser_sharedfromorg)
    - [Properties](#properties-57)
  - [SharedUser_user](#shareduser_user)
    - [Properties](#properties-58)
  - [Site](#site)
    - [Properties](#properties-59)
  - [SiteSummary](#sitesummary)
    - [Properties](#properties-60)
  - [TimeRestriction](#timerestriction)
    - [Properties](#properties-61)
  - [TokenProperties](#tokenproperties)
    - [Properties](#properties-62)
  - [TokenScopeItem](#tokenscopeitem)
    - [Properties](#properties-63)
  - [TotpSoftDevice](#totpsoftdevice)
    - [Properties](#properties-64)
  - [User](#user)
    - [Properties](#properties-65)
  - [UserActivityEvent](#useractivityevent)
    - [Properties](#properties-66)
  - [UserCredential](#usercredential)
    - [Properties](#properties-67)
  - [UserCredential_card](#usercredential_card)
    - [Properties](#properties-68)
  - [UserCredential_credentialType](#usercredential_credentialtype)
    - [Properties](#properties-69)
  - [UserCredential_mobile](#usercredential_mobile)
    - [Properties](#properties-70)
  - [UserCredential_pincode](#usercredential_pincode)
    - [Properties](#properties-71)
  - [UserCustomField](#usercustomfield)
    - [Properties](#properties-72)
  - [UserEntry](#userentry)
    - [Properties](#properties-73)
  - [UserEntryZone](#userentryzone)
    - [Properties](#properties-74)
  - [UserGroup](#usergroup)
    - [Properties](#properties-75)
  - [UserIdentity](#useridentity)
    - [Properties](#properties-76)
  - [UserInfo](#userinfo)
    - [Properties](#properties-77)
  - [UserInfo_allOf](#userinfo_allof)
    - [Properties](#properties-78)
  - [UserPicture](#userpicture)
    - [Properties](#properties-79)
  - [UserSite](#usersite)
    - [Properties](#properties-80)
  - [UserZone](#userzone)
    - [Properties](#properties-81)
  - [UserZoneUser](#userzoneuser)
    - [Properties](#properties-82)
  - [UserZoneUser_zone](#userzoneuser_zone)
    - [Properties](#properties-83)
  - [VideoProvider](#videoprovider)
    - [Properties](#properties-84)
  - [WirelessLock](#wirelesslock)
    - [Properties](#properties-85)
  - [WirelessLockDbEntry](#wirelesslockdbentry)
    - [Properties](#properties-86)
  - [Zone](#zone)
    - [Properties](#properties-87)
  - [ZoneEntry](#zoneentry)
    - [Properties](#properties-88)
  - [ZoneEntryAcu](#zoneentryacu)
    - [Properties](#properties-89)
  - [ZoneShare](#zoneshare)
    - [Properties](#properties-90)
  - [ZoneShare_sharedWithOrg](#zoneshare_sharedwithorg)
    - [Properties](#properties-91)
  - [ZoneSiteRef](#zonesiteref)
    - [Properties](#properties-92)
  - [ZoneZoneUser](#zonezoneuser)
    - [Properties](#properties-93)
  - [ZoneZoneUser_user](#zonezoneuser_user)
    - [Properties](#properties-94)
  - [baseConnectionState](#baseconnectionstate)
    - [Properties](#properties-95)
  - [connectionProfile](#connectionprofile)
    - [Properties](#properties-96)
  - [connectionState](#connectionstate)
    - [Properties](#properties-97)
  - [connectionState_allOf](#connectionstate_allof)
    - [Properties](#properties-98)
- [Documentation for @zerobias-org/module-avigilon-alta-access](#documentation-for-zerobias-orgmodule-avigilon-alta-access)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)
    - [access-token](#access-token)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisauditapimd"></a>

## AuditApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listAuditLogs**](#listAuditLogs) | **GET** /organizations/{organizationId}/reports/auditLogs/ui | Retrieves audit logs for the organization


<a name="listAuditLogs"></a>
## **listAuditLogs**
> List listAuditLogs(organizationId, filter, options, pageNumber, pageSize)

Retrieves audit logs for the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **filter** | **String**| Filter criteria for audit logs (e.g., \&quot;timestamp:(1753282000--1753284000)\&quot;) | [optional] [default to null]
 **options** | **String**| Search options (e.g., \&quot;searchId:123-456-0\&quot;) | [optional] [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsauditlogentrymd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisauthapimd"></a>

## AuthApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getTokenProperties**](#getTokenProperties) | **GET** /accessToken | Retrieves properties and metadata for the current access token


<a name="getTokenProperties"></a>
## **getTokenProperties**
> TokenProperties getTokenProperties()

Retrieves properties and metadata for the current access token

#### Parameters
This endpoint does not need any parameter.

#### Return type

[**TokenProperties**](#modelstokenpropertiesmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apiscredentialapimd"></a>

## CredentialApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listCardFormats**](#listCardFormats) | **GET** /organizations/{organizationId}/cardFormats | Retrieves all card formats in the organization
[**listCredentialActionTypes**](#listCredentialActionTypes) | **GET** /organizations/{organizationId}/credentialActionTypes | Retrieves all credential action types in the organization
[**listCredentialActions**](#listCredentialActions) | **GET** /organizations/{organizationId}/credentials/{credentialId}/credentialActions | Retrieves all actions performed on a specific credential
[**listCredentialTypes**](#listCredentialTypes) | **GET** /organizations/{organizationId}/credentialTypes | Retrieves all credential types in the organization
[**listOrgCredentials**](#listOrgCredentials) | **GET** /organizations/{organizationId}/credentials | Retrieves all credentials in the organization


<a name="listCardFormats"></a>
## **listCardFormats**
> List listCardFormats(organizationId, pageNumber, pageSize)

Retrieves all card formats in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelscardformatmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listCredentialActionTypes"></a>
## **listCredentialActionTypes**
> List listCredentialActionTypes(organizationId, pageNumber, pageSize)

Retrieves all credential action types in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelscredentialactiontypemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listCredentialActions"></a>
## **listCredentialActions**
> List listCredentialActions(organizationId, credentialId, pageNumber, pageSize)

Retrieves all actions performed on a specific credential

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **credentialId** | **String**| Unique identifier for a credential | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelscredentialactionmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listCredentialTypes"></a>
## **listCredentialTypes**
> List listCredentialTypes(organizationId, pageNumber, pageSize)

Retrieves all credential types in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelscredentialtypemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listOrgCredentials"></a>
## **listOrgCredentials**
> List listOrgCredentials(organizationId, pageNumber, pageSize)

Retrieves all credentials in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsorgcredentialmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisentryapimd"></a>

## EntryApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getEntry**](#getEntry) | **GET** /organizations/{organizationId}/entries/{entryId} | Retrieves detailed information for a specific entry
[**listEntries**](#listEntries) | **GET** /organizations/{organizationId}/entries | Retrieves all entries in the organization
[**listEntryActivity**](#listEntryActivity) | **GET** /organizations/{organizationId}/entries/{entryId}/activity | Retrieves activity report for a specific entry
[**listEntryCameras**](#listEntryCameras) | **GET** /organizations/{organizationId}/entries/{entryId}/cameras | Retrieves all cameras associated with a specific entry
[**listEntryStates**](#listEntryStates) | **GET** /organizations/{organizationId}/entryStates | Retrieves all entry states in the organization
[**listEntryUserSchedules**](#listEntryUserSchedules) | **GET** /organizations/{organizationId}/entries/{entryId}/userSchedules | Retrieves all active users with their associated schedules that have access to a specific entry
[**listEntryUsers**](#listEntryUsers) | **GET** /organizations/{organizationId}/entries/{entryId}/users | Retrieves all active users that have access to a specific entry


<a name="getEntry"></a>
## **getEntry**
> EntryInfo getEntry(organizationId, entryId)

Retrieves detailed information for a specific entry

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **entryId** | **String**| Unique identifier for an entry | [default to null]

#### Return type

[**EntryInfo**](#modelsentryinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listEntries"></a>
## **listEntries**
> List listEntries(organizationId, pageNumber, pageSize)

Retrieves all entries in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentrymd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listEntryActivity"></a>
## **listEntryActivity**
> List listEntryActivity(organizationId, entryId, pageNumber, pageSize)

Retrieves activity report for a specific entry

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **entryId** | **String**| Unique identifier for an entry | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentryactivityeventmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listEntryCameras"></a>
## **listEntryCameras**
> List listEntryCameras(organizationId, entryId, pageNumber, pageSize)

Retrieves all cameras associated with a specific entry

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **entryId** | **String**| Unique identifier for an entry | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentrycameramd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listEntryStates"></a>
## **listEntryStates**
> List listEntryStates(organizationId, pageNumber, pageSize)

Retrieves all entry states in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentrystateinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listEntryUserSchedules"></a>
## **listEntryUserSchedules**
> List listEntryUserSchedules(organizationId, entryId, pageNumber, pageSize)

Retrieves all active users with their associated schedules that have access to a specific entry

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **entryId** | **String**| Unique identifier for an entry | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentryuserschedulemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listEntryUsers"></a>
## **listEntryUsers**
> List listEntryUsers(organizationId, entryId, pageNumber, pageSize)

Retrieves all active users that have access to a specific entry

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **entryId** | **String**| Unique identifier for an entry | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentryusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisgroupapimd"></a>

## GroupApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getGroup**](#getGroup) | **GET** /organizations/{organizationId}/groups/{groupId} | Retrieves single access group details by group ID
[**listGroupEntries**](#listGroupEntries) | **GET** /organizations/{organizationId}/groups/{groupId}/entries | Retrieves all entries/permissions for a specific group
[**listGroupUsers**](#listGroupUsers) | **GET** /organizations/{organizationId}/groups/{groupId}/users | Retrieves all users belonging to a specific group
[**listGroupZoneGroups**](#listGroupZoneGroups) | **GET** /organizations/{organizationId}/groups/{groupId}/zoneGroups | Retrieves all zones and their associated configurations for a specific group
[**listGroupZones**](#listGroupZones) | **GET** /organizations/{organizationId}/groups/{groupId}/zones | Retrieves all zones for a specific group
[**listGroups**](#listGroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization


<a name="getGroup"></a>
## **getGroup**
> GroupInfo getGroup(organizationId, groupId)

Retrieves single access group details by group ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **String**| Unique identifier for a group | [default to null]

#### Return type

[**GroupInfo**](#modelsgroupinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listGroupEntries"></a>
## **listGroupEntries**
> List listGroupEntries(organizationId, groupId, pageNumber, pageSize)

Retrieves all entries/permissions for a specific group

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **String**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsentrymd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listGroupUsers"></a>
## **listGroupUsers**
> List listGroupUsers(organizationId, groupId, pageNumber, pageSize)

Retrieves all users belonging to a specific group

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **String**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listGroupZoneGroups"></a>
## **listGroupZoneGroups**
> List listGroupZoneGroups(organizationId, groupId, pageNumber, pageSize)

Retrieves all zones and their associated configurations for a specific group

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **String**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsgroupzonegroupmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listGroupZones"></a>
## **listGroupZones**
> List listGroupZones(organizationId, groupId, pageNumber, pageSize)

Retrieves all zones for a specific group

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **String**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsgroupzonemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listGroups"></a>
## **listGroups**
> List listGroups(organizationId, pageNumber, pageSize)

Retrieves all access groups in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsgroupmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisidentityproviderapimd"></a>

## IdentityProviderApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getIdentityProviderGroupRelations**](#getIdentityProviderGroupRelations) | **GET** /organizations/{organizationId}/identityProviders/{identityProviderId}/groupRelations | Retrieves group relations for a specific identity provider
[**getIdentityProviderType**](#getIdentityProviderType) | **GET** /organizations/{organizationId}/identityProviderTypes/{identityProviderTypeId} | Retrieves detailed information for a specific identity provider type
[**listIdentityProviderGroups**](#listIdentityProviderGroups) | **GET** /organizations/{organizationId}/identityProviders/{identityProviderId}/groups | Retrieves all groups from a specific identity provider
[**listIdentityProviderTypes**](#listIdentityProviderTypes) | **GET** /organizations/{organizationId}/identityProviderTypes | Retrieves all identity provider types in the organization
[**listIdentityProviders**](#listIdentityProviders) | **GET** /organizations/{organizationId}/identityProviders | Retrieves all identity providers in the organization


<a name="getIdentityProviderGroupRelations"></a>
## **getIdentityProviderGroupRelations**
> List getIdentityProviderGroupRelations(organizationId, identityProviderId, pageNumber, pageSize)

Retrieves group relations for a specific identity provider

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **identityProviderId** | **String**| Unique identifier for an identity provider | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsidentityprovidergrouprelationmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="getIdentityProviderType"></a>
## **getIdentityProviderType**
> IdentityProviderTypeInfo getIdentityProviderType(organizationId, identityProviderTypeId)

Retrieves detailed information for a specific identity provider type

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **identityProviderTypeId** | **String**| Unique identifier for an identity provider type | [default to null]

#### Return type

[**IdentityProviderTypeInfo**](#modelsidentityprovidertypeinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listIdentityProviderGroups"></a>
## **listIdentityProviderGroups**
> List listIdentityProviderGroups(organizationId, identityProviderId, pageNumber, pageSize)

Retrieves all groups from a specific identity provider

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **identityProviderId** | **String**| Unique identifier for an identity provider | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsidentityprovidergroupmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listIdentityProviderTypes"></a>
## **listIdentityProviderTypes**
> List listIdentityProviderTypes(organizationId, pageNumber, pageSize)

Retrieves all identity provider types in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsidentityprovidertypemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listIdentityProviders"></a>
## **listIdentityProviders**
> List listIdentityProviders(organizationId, pageNumber, pageSize)

Retrieves all identity providers in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsidentityprovidermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisroleapimd"></a>

## RoleApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listRoleUsers**](#listRoleUsers) | **GET** /organizations/{organizationId}/roles/{roleId}/users | Retrieves all users assigned to a specific role
[**listRoles**](#listRoles) | **GET** /organizations/{organizationId}/roles | Retrieves all roles in the organization


<a name="listRoleUsers"></a>
## **listRoleUsers**
> List listRoleUsers(organizationId, roleId, pageNumber, pageSize)

Retrieves all users assigned to a specific role

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **roleId** | **String**| Unique identifier for a role | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsroleusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listRoles"></a>
## **listRoles**
> List listRoles(organizationId, pageNumber, pageSize)

Retrieves all roles in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsroleinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisscheduleapimd"></a>

## ScheduleApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listScheduleEvents**](#listScheduleEvents) | **GET** /organizations/{organizationId}/schedules/{scheduleId}/events | Retrieves all events for a specific schedule
[**listScheduleTypes**](#listScheduleTypes) | **GET** /organizations/{organizationId}/scheduleTypes | Retrieves all schedule types in the organization
[**listSchedules**](#listSchedules) | **GET** /organizations/{organizationId}/schedules | Retrieves all schedules in the organization


<a name="listScheduleEvents"></a>
## **listScheduleEvents**
> List listScheduleEvents(organizationId, scheduleId, pageNumber, pageSize)

Retrieves all events for a specific schedule

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **scheduleId** | **String**| Unique identifier for a schedule | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsscheduleeventmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listScheduleTypes"></a>
## **listScheduleTypes**
> List listScheduleTypes(organizationId, pageNumber, pageSize)

Retrieves all schedule types in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsscheduletypemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listSchedules"></a>
## **listSchedules**
> List listSchedules(organizationId, pageNumber, pageSize)

Retrieves all schedules in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsschedulemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apissiteapimd"></a>

## SiteApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listSites**](#listSites) | **GET** /organizations/{organizationId}/sites | Retrieves all sites in the organization


<a name="listSites"></a>
## **listSites**
> List listSites(organizationId, pageNumber, pageSize)

Retrieves all sites in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssitemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getUser**](#getUser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
[**listOrgIdentities**](#listOrgIdentities) | **GET** /organizations/{organizationId}/orgIdentities | Retrieves all identities in the organization
[**listOrgPictures**](#listOrgPictures) | **GET** /organizations/{organizationId}/orgPictures | Retrieves all organization pictures
[**listSharedUsers**](#listSharedUsers) | **GET** /organizations/{organizationId}/sharedUsers | Retrieves all users shared from other organizations
[**listUserActivity**](#listUserActivity) | **GET** /organizations/{organizationId}/users/{userId}/activity | Retrieves activity report for a specific user
[**listUserCredentials**](#listUserCredentials) | **GET** /organizations/{organizationId}/users/{userId}/credentials | Retrieves all credentials for a specific user
[**listUserEntries**](#listUserEntries) | **GET** /organizations/{organizationId}/users/{userId}/entries | Retrieves all entries/access permissions for a specific user
[**listUserGroups**](#listUserGroups) | **GET** /organizations/{organizationId}/users/{userId}/groups | Retrieves all groups a specific user belongs to
[**listUserMfaCredentials**](#listUserMfaCredentials) | **GET** /organizations/{organizationId}/users/{userId}/mfaCredentials | Retrieves all MFA credentials for a specific user
[**listUserPictures**](#listUserPictures) | **GET** /organizations/{organizationId}/users/{userId}/userPictures | Retrieves all pictures for a specific user
[**listUserRoles**](#listUserRoles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
[**listUserSites**](#listUserSites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
[**listUserZoneUsers**](#listUserZoneUsers) | **GET** /organizations/{organizationId}/users/{userId}/zoneUsers | Retrieves all zones and their associated configurations a user has access to
[**listUserZones**](#listUserZones) | **GET** /organizations/{organizationId}/users/{userId}/zones | Retrieves all zones associated with a specific user
[**listUsers**](#listUsers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="getUser"></a>
## **getUser**
> UserInfo getUser(organizationId, userId)

Retrieves single user details by user ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]

#### Return type

[**UserInfo**](#modelsuserinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listOrgIdentities"></a>
## **listOrgIdentities**
> List listOrgIdentities(organizationId, pageNumber, pageSize)

Retrieves all identities in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsorgidentitymd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listOrgPictures"></a>
## **listOrgPictures**
> List listOrgPictures(organizationId, pageNumber, pageSize)

Retrieves all organization pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsorgpicturemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listSharedUsers"></a>
## **listSharedUsers**
> List listSharedUsers(organizationId, pageNumber, pageSize)

Retrieves all users shared from other organizations

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssharedusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserActivity"></a>
## **listUserActivity**
> List listUserActivity(organizationId, userId, pageNumber, pageSize)

Retrieves activity report for a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsuseractivityeventmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserCredentials"></a>
## **listUserCredentials**
> List listUserCredentials(organizationId, userId, pageNumber, pageSize)

Retrieves all credentials for a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusercredentialmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserEntries"></a>
## **listUserEntries**
> List listUserEntries(organizationId, userId, pageNumber, pageSize)

Retrieves all entries/access permissions for a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsuserentrymd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserGroups"></a>
## **listUserGroups**
> List listUserGroups(organizationId, userId, pageNumber, pageSize)

Retrieves all groups a specific user belongs to

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsgroupmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserMfaCredentials"></a>
## **listUserMfaCredentials**
> List listUserMfaCredentials(organizationId, userId, pageNumber, pageSize)

Retrieves all MFA credentials for a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsmfacredentialmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserPictures"></a>
## **listUserPictures**
> List listUserPictures(organizationId, userId, pageNumber, pageSize)

Retrieves all pictures for a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsuserpicturemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserRoles"></a>
## **listUserRoles**
> List listUserRoles(organizationId, userId, pageNumber, pageSize)

Retrieves all roles assigned to a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsrolemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserSites"></a>
## **listUserSites**
> List listUserSites(organizationId, userId, pageNumber, pageSize)

Retrieves all sites associated with a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssitemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserZoneUsers"></a>
## **listUserZoneUsers**
> List listUserZoneUsers(organizationId, userId, pageNumber, pageSize)

Retrieves all zones and their associated configurations a user has access to

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsuserzoneusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUserZones"></a>
## **listUserZones**
> List listUserZones(organizationId, userId, pageNumber, pageSize)

Retrieves all zones associated with a specific user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **String**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsuserzonemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listUsers"></a>
## **listUsers**
> List listUsers(organizationId, pageNumber, pageSize)

Retrieves all users in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apiszoneapimd"></a>

## ZoneApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listZoneShares**](#listZoneShares) | **GET** /organizations/{organizationId}/zones/{zoneId}/zoneShares | Retrieves all zone shares for a specific zone
[**listZoneZoneUsers**](#listZoneZoneUsers) | **GET** /organizations/{organizationId}/zones/{zoneId}/zoneUsers | Retrieves all zone users with their access configurations for a specific zone
[**listZones**](#listZones) | **GET** /organizations/{organizationId}/zones | Retrieves all zones in the organization


<a name="listZoneShares"></a>
## **listZoneShares**
> List listZoneShares(organizationId, zoneId, pageNumber, pageSize)

Retrieves all zone shares for a specific zone

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **zoneId** | **String**| Unique identifier for a zone | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelszonesharemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listZoneZoneUsers"></a>
## **listZoneZoneUsers**
> List listZoneZoneUsers(organizationId, zoneId, pageNumber, pageSize)

Retrieves all zone users with their access configurations for a specific zone

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **zoneId** | **String**| Unique identifier for a zone | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelszonezoneusermd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listZones"></a>
## **listZones**
> List listZones(organizationId, pageNumber, pageSize)

Retrieves all zones in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelszonemd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaccessrulemd"></a>

## AccessRule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the access rule | [default to null]
**name** | **String** | Name of the access rule | [default to null]
**description** | **String** | Description of the access rule | [optional] [default to null]
**conditions** | **List** | Conditions that must be met for access | [optional] [default to null]
**actions** | **List** | Actions to take when rule is triggered | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsauditlogentrymd"></a>

## AuditLogEntry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**timestamp** | **BigDecimal** | Unix timestamp of the action | [default to null]
**timestampIso** | **Date** | ISO 8601 formatted timestamp | [optional] [default to null]
**action** | **String** | Action performed | [optional] [default to null]
**category** | **String** | Action category | [optional] [default to null]
**actorId** | **String** | User ID who performed the action | [optional] [default to null]
**actorName** | **String** | Name of user who performed the action | [optional] [default to null]
**actorEmail** | **String** | Email of user who performed the action | [optional] [default to null]
**targetId** | **String** | ID of the target resource | [optional] [default to null]
**targetType** | **String** | Type of the target resource | [optional] [default to null]
**targetName** | **String** | Name of the target resource | [optional] [default to null]
**details** | [**Object**](.md) | Additional action details | [optional] [default to null]
**ipAddress** | **String** | IP address of the actor | [optional] [default to null]
**userAgent** | **String** | User agent string | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsauthstrategytypemd"></a>

## AuthStrategyType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Auth strategy type identifier | [default to null]
**name** | **String** | Auth strategy type name | [optional] [default to null]
**code** | **String** | Auth strategy type code | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsbuildingfloorunitmd"></a>

## BuildingFloorUnit
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Building floor unit identifier | [optional] [default to null]
**name** | **String** | Building floor unit name | [optional] [default to null]
**buildingId** | **String** | Building identifier | [optional] [default to null]
**floorId** | **String** | Floor identifier | [optional] [default to null]
**unitNumber** | **String** | Unit number | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscardformatmd"></a>

## CardFormat
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Card format identifier | [default to null]
**name** | **String** | Card format name | [optional] [default to null]
**code** | **String** | Card format code | [optional] [default to null]
**description** | **String** | Card format description | [optional] [default to null]
**bitLength** | **Integer** | Bit length of the card format | [optional] [default to null]
**facilityCodeStart** | **Integer** | Facility code start position | [optional] [default to null]
**facilityCodeLength** | **Integer** | Facility code length | [optional] [default to null]
**cardNumberStart** | **Integer** | Card number start position | [optional] [default to null]
**cardNumberLength** | **Integer** | Card number length | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscredentialactionmd"></a>

## CredentialAction
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Credential action identifier | [default to null]
**credentialId** | **String** | Credential identifier | [optional] [default to null]
**credentialActionType** | [**CredentialActionType**](#modelscredentialactiontypemd) |  | [optional] [default to null]
**performedBy** | [**CredentialAction_performedBy**](#modelscredentialaction_performedbymd) |  | [optional] [default to null]
**performedAt** | **Date** | Timestamp when action was performed | [optional] [default to null]
**details** | [**Object**](.md) | Additional action details | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscredentialactiontypemd"></a>

## CredentialActionType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Credential action type identifier | [default to null]
**name** | **String** | Credential action type name | [optional] [default to null]
**code** | **String** | Credential action type code | [optional] [default to null]
**description** | **String** | Credential action type description | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscredentialaction_performedbymd"></a>

## CredentialAction_performedBy
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier who performed the action | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscredentialtypemd"></a>

## CredentialType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Credential type identifier | [default to null]
**name** | **String** | Credential type name | [optional] [default to null]
**description** | **String** | Credential type description | [optional] [default to null]
**modelName** | **String** | Model name of the credential type | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrymd"></a>

## Entry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**opal** | **String** | OPAL identifier for the entry | [optional] [default to null]
**id** | **String** | Unique identifier for the entry | [default to null]
**name** | **String** | Name of the entry | [default to null]
**pincode** | **String** | Entry pincode if enabled | [optional] [default to null]
**isPincodeEnabled** | **Boolean** | Whether pincode is enabled for this entry | [optional] [default to null]
**color** | **String** | Color code for the entry | [optional] [default to null]
**isMusterPoint** | **Boolean** | Whether this entry is a muster point | [optional] [default to null]
**notes** | **String** | Notes about the entry | [optional] [default to null]
**externalUuid** | **String** | External UUID identifier | [optional] [default to null]
**isReaderless** | **Boolean** | Whether this is a readerless entry | [optional] [default to null]
**isIntercomEntry** | **Boolean** | Whether this is an intercom entry | [optional] [default to null]
**createdAt** | **Date** | Timestamp when entry was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when entry was last updated | [optional] [default to null]
**zone** | [**EntryZone**](#modelsentryzonemd) |  | [optional] [default to null]
**acu** | [**EntryAcu**](#modelsentryacumd) |  | [optional] [default to null]
**wirelessLock** | [**Object**](.md) | Wireless lock information | [optional] [default to null]
**entryState** | [**EntryState**](#modelsentrystatemd) |  | [optional] [default to null]
**schedule** | [**EntrySchedule**](#modelsentryschedulemd) |  | [optional] [default to null]
**cameras** | [**List**](#modelsentrycameramd) | Cameras associated with the entry | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryactivityeventmd"></a>

## EntryActivityEvent
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**time** | **BigDecimal** | Unix timestamp of the event | [default to null]
**timeIsoString** | **Date** | ISO 8601 formatted timestamp | [optional] [default to null]
**userId** | **String** | User identifier | [optional] [default to null]
**userFirstName** | **String** | User&#39;s first name | [optional] [default to null]
**userMiddleName** | **String** | User&#39;s middle name | [optional] [default to null]
**userLastName** | **String** | User&#39;s last name | [optional] [default to null]
**userName** | **String** | User&#39;s full name | [optional] [default to null]
**userEmail** | **String** | User&#39;s email address | [optional] [default to null]
**userMobilePhone** | **String** | User&#39;s mobile phone | [optional] [default to null]
**userOrgId** | **String** | User&#39;s organization identifier | [optional] [default to null]
**userOrgName** | **String** | User&#39;s organization name | [optional] [default to null]
**userExternalId** | **String** | User&#39;s external identifier | [optional] [default to null]
**userIdpName** | **String** | Identity provider name | [optional] [default to null]
**userGroups** | [**List**](#modelsentryactivityevent_usergroupsmd) | User&#39;s groups | [optional] [default to null]
**userSites** | [**List**](#modelsentryactivityevent_usersitesmd) | User&#39;s sites | [optional] [default to null]
**sourceName** | **String** | Source of the activity event | [default to null]
**category** | **String** | Event category | [optional] [default to null]
**subCategory** | **String** | Event sub-category | [optional] [default to null]
**credentialSubtype** | **String** | Type of credential used | [optional] [default to null]
**credentialTypeModelName** | **String** | Model name of credential type | [optional] [default to null]
**credentialTypeName** | **String** | Name of credential type | [optional] [default to null]
**credentialDetail** | **String** | Detailed credential information | [optional] [default to null]
**entryId** | **String** | Entry identifier where event occurred | [optional] [default to null]
**entryName** | **String** | Entry name where event occurred | [optional] [default to null]
**zoneId** | **String** | Zone identifier where event occurred | [optional] [default to null]
**zoneName** | **String** | Zone name where event occurred | [optional] [default to null]
**siteId** | **String** | Site identifier where event occurred | [optional] [default to null]
**siteName** | **String** | Site name where event occurred | [optional] [default to null]
**requestType** | **String** | Type of access request | [optional] [default to null]
**result** | **String** | Result of the access attempt | [optional] [default to null]
**resultDescription** | **String** | Detailed description of the result | [optional] [default to null]
**deniedReason** | **String** | Reason for access denial if applicable | [optional] [default to null]
**location** | **String** | Location information for the event | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryactivityevent_usergroupsmd"></a>

## EntryActivityEvent_userGroups
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Group identifier | [default to null]
**name** | **String** | Group name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryactivityevent_usersitesmd"></a>

## EntryActivityEvent_userSites
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Site identifier | [default to null]
**name** | **String** | Site name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryacumd"></a>

## EntryAcu
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ACU identifier | [default to null]
**name** | **String** | ACU name | [default to null]
**isGatewayMode** | **Boolean** | Whether ACU is in gateway mode | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrycameramd"></a>

## EntryCamera
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Camera identifier | [default to null]
**name** | **String** | Camera name | [default to null]
**nameExt** | **String** | Extended camera name | [optional] [default to null]
**idExt** | **String** | External camera identifier | [optional] [default to null]
**supportsSnapshot** | **Boolean** | Whether camera supports snapshots | [optional] [default to null]
**supportsDeeplink** | **Boolean** | Whether camera supports deep linking | [optional] [default to null]
**supportsMotionSnapshot** | **Boolean** | Whether camera supports motion snapshots | [optional] [default to null]
**supportsFaceCrop** | **Boolean** | Whether camera supports face cropping | [optional] [default to null]
**supportsFaceDetection** | **Boolean** | Whether camera supports face detection | [optional] [default to null]
**supportsPeopleDetection** | **Boolean** | Whether camera supports people detection | [optional] [default to null]
**supportsMotionRecap** | **Boolean** | Whether camera supports motion recap | [optional] [default to null]
**supportsLivestream** | **Boolean** | Whether camera supports live streaming | [optional] [default to null]
**videoProviderId** | **Integer** | Video provider identifier | [optional] [default to null]
**videoProvider** | [**VideoProvider**](#modelsvideoprovidermd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryinfomd"></a>

## EntryInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**opal** | **String** | OPAL identifier for the entry | [optional] [default to null]
**id** | **String** | Unique identifier for the entry | [default to null]
**name** | **String** | Name of the entry | [default to null]
**pincode** | **String** | Entry pincode if enabled | [optional] [default to null]
**isPincodeEnabled** | **Boolean** | Whether pincode is enabled for this entry | [optional] [default to null]
**color** | **String** | Color code for the entry | [optional] [default to null]
**isMusterPoint** | **Boolean** | Whether this entry is a muster point | [optional] [default to null]
**notes** | **String** | Notes about the entry | [optional] [default to null]
**externalUuid** | **String** | External UUID identifier | [optional] [default to null]
**isReaderless** | **Boolean** | Whether this is a readerless entry | [optional] [default to null]
**isIntercomEntry** | **Boolean** | Whether this is an intercom entry | [optional] [default to null]
**createdAt** | **Date** | Timestamp when entry was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when entry was last updated | [optional] [default to null]
**zone** | [**EntryZone**](#modelsentryzonemd) |  | [optional] [default to null]
**acu** | [**EntryAcu**](#modelsentryacumd) |  | [optional] [default to null]
**wirelessLock** | [**Object**](.md) | Wireless lock information | [optional] [default to null]
**entryState** | [**EntryState**](#modelsentrystatemd) |  | [optional] [default to null]
**schedule** | [**EntrySchedule**](#modelsentryschedulemd) |  | [optional] [default to null]
**cameras** | [**List**](#modelsentrycameramd) | Cameras associated with the entry | [optional] [default to null]
**org** | [**OrganizationRef**](#modelsorganizationrefmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryinfo_allofmd"></a>

## EntryInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**org** | [**OrganizationRef**](#modelsorganizationrefmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryschedulemd"></a>

## EntrySchedule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Schedule identifier | [default to null]
**name** | **String** | Schedule name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrystatemd"></a>

## EntryState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Entry state identifier | [default to null]
**name** | **String** | Entry state name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrystateinfomd"></a>

## EntryStateInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Entry state identifier | [default to null]
**ordinal** | **BigDecimal** | Sort order for the entry state | [optional] [default to null]
**name** | **String** | Entry state name | [default to null]
**code** | **String** | Entry state code | [default to null]
**description** | **String** | Entry state description | [optional] [default to null]
**isLocked** | **Boolean** | Whether the entry is locked in this state | [optional] [default to null]
**isToggle** | **Boolean** | Whether this is a toggle state | [optional] [default to null]
**orgId** | **String** | Organization identifier if org-specific | [optional] [default to null]
**isMultiFactor** | **Boolean** | Whether multi-factor authentication is required | [optional] [default to null]
**triggerMethods** | **List** | Available trigger methods for this state | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryusermd"></a>

## EntryUser
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier | [default to null]
**status** | **String** | User status | [optional] [default to null]
**identity** | [**EntryUser_identity**](#modelsentryuser_identitymd) |  | [optional] [default to null]
**sites** | [**List**](#modelsusersitemd) | Sites the user has access to | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryuserschedulemd"></a>

## EntryUserSchedule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**user** | [**EntryUserSchedule_user**](#modelsentryuserschedule_usermd) |  | [optional] [default to null]
**schedules** | [**List**](#modelsentryschedulemd) | Schedules for entry access | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryuserschedule_usermd"></a>

## EntryUserSchedule_user
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier | [default to null]
**status** | **String** | User status | [optional] [default to null]
**identity** | [**EntryUser_identity**](#modelsentryuser_identitymd) |  | [optional] [default to null]
**sites** | [**List**](#modelsusersitemd) | Sites the user has access to | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryuser_identitymd"></a>

## EntryUser_identity
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Identity identifier | [optional] [default to null]
**firstName** | **String** | User&#39;s first name | [optional] [default to null]
**middleName** | **String** | User&#39;s middle name | [optional] [default to null]
**lastName** | **String** | User&#39;s last name | [optional] [default to null]
**fullName** | **String** | User&#39;s full name | [optional] [default to null]
**initials** | **String** | User&#39;s initials | [optional] [default to null]
**email** | **String** | User&#39;s email address | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryzonemd"></a>

## EntryZone
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Zone identifier | [default to null]
**name** | **String** | Zone name | [default to null]
**site** | [**EntryZoneSite**](#modelsentryzonesitemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentryzonesitemd"></a>

## EntryZoneSite
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Site identifier | [default to null]
**name** | **String** | Site name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupmd"></a>

## Group
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**badgeConfig** | [**Object**](.md) | Badge configuration for the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfomd"></a>

## GroupInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**badgeConfig** | [**Object**](.md) | Badge configuration for the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]
**organizationId** | **String** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **String** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfo_allofmd"></a>

## GroupInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **String** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **String** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupsummarymd"></a>

## GroupSummary
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Group identifier | [optional] [default to null]
**name** | **String** | Group name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupzonemd"></a>

## GroupZone
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Zone identifier | [default to null]
**name** | **String** | Zone name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupzonegroupmd"></a>

## GroupZoneGroup
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**zone** | [**UserZoneUser_zone**](#modelsuserzoneuser_zonemd) |  | [optional] [default to null]
**schedule** | [**Schedule**](#modelsschedulemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentitynamespacemd"></a>

## IdentityNamespace
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **BigDecimal** | Namespace identifier | [optional] [default to null]
**nickname** | **String** | Namespace nickname | [optional] [default to null]
**namespaceType** | [**NamespaceType**](#modelsnamespacetypemd) |  | [optional] [default to null]
**org** | [**OrganizationRef**](#modelsorganizationrefmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentityprovidermd"></a>

## IdentityProvider
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Integer** | Identity provider identifier | [default to null]
**orgId** | **Integer** | Organization ID | [optional] [default to null]
**identityProviderType** | [**IdentityProviderTypeSummary**](#modelsidentityprovidertypesummarymd) |  | [optional] [default to null]
**isSyncUsersEnabled** | **Boolean** | Whether user sync is enabled | [optional] [default to null]
**isMobileCredentialEnabled** | **Boolean** | Whether mobile credentials are enabled | [optional] [default to null]
**isSsoEnabled** | **Boolean** | Whether SSO is enabled | [optional] [default to null]
**isMobileSsoEnabled** | **Boolean** | Whether mobile SSO is enabled | [optional] [default to null]
**lastSyncedAt** | **Date** | Last sync timestamp | [optional] [default to null]
**status** | **String** | Provider status | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentityprovidergroupmd"></a>

## IdentityProviderGroup
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**idpGroupUniqueIdentifier** | **String** | Unique identifier from the identity provider | [optional] [default to null]
**name** | **String** | Group name | [optional] [default to null]
**description** | **String** | Group description | [optional] [default to null]
**email** | **String** | Group email | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentityprovidergrouprelationmd"></a>

## IdentityProviderGroupRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**idpGroupUniqueIdentifier** | **String** | Identity provider group identifier | [optional] [default to null]
**identityProviderGroup** | [**IdentityProviderGroup**](#modelsidentityprovidergroupmd) |  | [optional] [default to null]
**groupId** | **Integer** | Organization group ID | [optional] [default to null]
**group** | [**GroupSummary**](#modelsgroupsummarymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentityprovidertypemd"></a>

## IdentityProviderType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Identity provider type identifier | [default to null]
**name** | **String** | Identity provider type name | [default to null]
**code** | **String** | Identity provider type code | [default to null]
**featureCode** | **String** | Feature code for the identity provider type | [optional] [default to null]
**supportsIdpInitiatedSso** | **Boolean** | Whether IDP-initiated SSO is supported | [optional] [default to null]
**authStrategyTypes** | [**List**](#modelsauthstrategytypemd) | Authentication strategy types supported | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentityprovidertypeinfomd"></a>

## IdentityProviderTypeInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Identity provider type identifier | [default to null]
**name** | **String** | Identity provider type name | [default to null]
**code** | **String** | Identity provider type code | [default to null]
**featureCode** | **String** | Feature code for the identity provider type | [optional] [default to null]
**supportsIdpInitiatedSso** | **Boolean** | Whether IDP-initiated SSO is supported | [optional] [default to null]
**authStrategyTypes** | [**List**](#modelsauthstrategytypemd) | Authentication strategy types supported | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsidentityprovidertypesummarymd"></a>

## IdentityProviderTypeSummary
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Integer** | Type identifier | [default to null]
**name** | **String** | Type name | [optional] [default to null]
**code** | **String** | Type code | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsmfacredentialmd"></a>

## MfaCredential
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | MFA credential identifier | [default to null]
**createdAt** | **Date** | Timestamp when credential was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when credential was last updated | [optional] [default to null]
**name** | **String** | Name of the MFA credential | [optional] [default to null]
**status** | **String** | Status of the MFA credential | [optional] [default to null]
**mfaCredentialType** | [**MfaCredentialType**](#modelsmfacredentialtypemd) |  | [optional] [default to null]
**totpSoftDevice** | [**TotpSoftDevice**](#modelstotpsoftdevicemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsmfacredentialtypemd"></a>

## MfaCredentialType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | MFA credential type identifier | [default to null]
**name** | **String** | Name of the credential type | [optional] [default to null]
**modelName** | **String** | Model name of the credential type | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsnamespacetypemd"></a>

## NamespaceType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **BigDecimal** | Namespace type identifier | [default to null]
**name** | **String** | Namespace type name | [default to null]
**modelName** | **String** | Namespace model name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorgcredentialmd"></a>

## OrgCredential
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Credential identifier | [default to null]
**userId** | **String** | User identifier this credential belongs to | [optional] [default to null]
**user** | [**OrgCredential_user**](#modelsorgcredential_usermd) |  | [optional] [default to null]
**credentialType** | [**CredentialType**](#modelscredentialtypemd) |  | [optional] [default to null]
**startDate** | **date** | Credential validity start date | [optional] [default to null]
**endDate** | **date** | Credential validity end date | [optional] [default to null]
**mobile** | [**OrgCredential_mobile**](#modelsorgcredential_mobilemd) |  | [optional] [default to null]
**card** | [**OrgCredential_card**](#modelsorgcredential_cardmd) |  | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorgcredential_cardmd"></a>

## OrgCredential_card
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Card identifier | [optional] [default to null]
**number** | **String** | Card number | [optional] [default to null]
**facilityCode** | **String** | Card facility code | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorgcredential_mobilemd"></a>

## OrgCredential_mobile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Mobile credential identifier | [optional] [default to null]
**name** | **String** | Mobile device name | [optional] [default to null]
**provisionedAt** | **Date** | Provisioned timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorgcredential_usermd"></a>

## OrgCredential_user
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorgidentitymd"></a>

## OrgIdentity
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Identity identifier | [default to null]
**email** | **String** | Email address | [optional] [default to null]
**firstName** | **String** | First name | [optional] [default to null]
**lastName** | **String** | Last name | [optional] [default to null]
**phoneNumber** | **String** | Phone number | [optional] [default to null]
**avatarUrl** | **String** | Avatar URL | [optional] [default to null]
**isVerified** | **Boolean** | Whether identity is verified | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorgpicturemd"></a>

## OrgPicture
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Integer** | Picture identifier | [default to null]
**isAvatar** | **Boolean** | Whether this is an avatar image | [optional] [default to null]
**picture** | [**PictureInfo**](#modelspictureinfomd) |  | [optional] [default to null]
**createdAt** | **Date** | Upload timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsorganizationrefmd"></a>

## OrganizationRef
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Organization identifier | [default to null]
**name** | **String** | Organization name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelspictureinfomd"></a>

## PictureInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**contentType** | **String** | MIME type of the image | [optional] [default to null]
**fileName** | **String** | Name of the picture file | [optional] [default to null]
**url** | **String** | URL to the picture | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrolemd"></a>

## Role
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the role | [default to null]
**name** | **String** | Name of the role | [default to null]
**description** | **String** | Description of the role | [optional] [default to null]
**permissions** | **List** | List of permissions granted by this role | [optional] [default to null]
**assignedAt** | **Date** | Timestamp when the role was assigned | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsroleinfomd"></a>

## RoleInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Role identifier | [default to null]
**name** | **String** | Role name | [default to null]
**description** | **String** | Role description | [optional] [default to null]
**isEditable** | **Boolean** | Whether role can be edited | [optional] [default to null]
**isSiteSpecific** | **Boolean** | Whether role is site-specific | [optional] [default to null]
**isMfaRequired** | **Boolean** | Whether MFA is required for this role | [optional] [default to null]
**userCount** | **Integer** | Number of users with this role | [optional] [default to null]
**sites** | [**List**](#modelssitesummarymd) | Sites associated with the role | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsroleusermd"></a>

## RoleUser
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier | [default to null]
**status** | **String** | User status | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]
**assignedAt** | **Date** | Timestamp when role was assigned to user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsschedulemd"></a>

## Schedule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Schedule identifier | [default to null]
**name** | **String** | Schedule name | [default to null]
**description** | **String** | Schedule description | [optional] [default to null]
**scheduleType** | [**ScheduleType**](#modelsscheduletypemd) |  | [optional] [default to null]
**isActive** | **Boolean** | Whether schedule is active | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsscheduleeventmd"></a>

## ScheduleEvent
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Event identifier | [default to null]
**scheduleId** | **String** | Schedule identifier | [optional] [default to null]
**startTime** | **Date** | Event start time | [optional] [default to null]
**endTime** | **Date** | Event end time | [optional] [default to null]
**daysOfWeek** | **List** | Days of the week for recurring events | [optional] [default to null]
**isRecurring** | **Boolean** | Whether event recurs | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsscheduletypemd"></a>

## ScheduleType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Schedule type identifier | [default to null]
**name** | **String** | Schedule type name | [optional] [default to null]
**code** | **String** | Schedule type code | [optional] [default to null]
**description** | **String** | Schedule type description | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssharedusermd"></a>

## SharedUser
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Shared user identifier | [default to null]
**userId** | **String** | User identifier | [optional] [default to null]
**user** | [**SharedUser_user**](#modelsshareduser_usermd) |  | [optional] [default to null]
**sharedFromOrgId** | **String** | Organization ID the user is shared from | [optional] [default to null]
**sharedFromOrg** | [**SharedUser_sharedFromOrg**](#modelsshareduser_sharedfromorgmd) |  | [optional] [default to null]
**sharedAt** | **Date** | Timestamp when user was shared | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsshareduser_sharedfromorgmd"></a>

## SharedUser_sharedFromOrg
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Source organization identifier | [optional] [default to null]
**name** | **String** | Source organization name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsshareduser_usermd"></a>

## SharedUser_user
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier | [optional] [default to null]
**status** | **String** | User status | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssitemd"></a>

## Site
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the site | [default to null]
**opal** | **String** | OPAL identifier for the site | [optional] [default to null]
**name** | **String** | Name of the site | [default to null]
**address** | **String** | Physical address of the site | [optional] [default to null]
**address2** | **String** | Secondary address line | [optional] [default to null]
**city** | **String** | City where the site is located | [optional] [default to null]
**state** | **String** | State/province where the site is located | [optional] [default to null]
**zip** | **String** | Postal/zip code of the site | [optional] [default to null]
**country** | **String** | Country where the site is located | [optional] [default to null]
**phone** | **String** | Phone number for the site | [optional] [default to null]
**language** | **String** | Language setting for the site | [optional] [default to null]
**zoneCount** | **Integer** | Number of zones at the site | [optional] [default to null]
**userCount** | **Integer** | Number of users at the site | [optional] [default to null]
**createdAt** | **Date** | Timestamp when site was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when site was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssitesummarymd"></a>

## SiteSummary
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Site identifier | [default to null]
**name** | **String** | Site name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstimerestrictionmd"></a>

## TimeRestriction
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**startTime** | **Date** | Start time for access | [optional] [default to null]
**endTime** | **Date** | End time for access | [optional] [default to null]
**daysOfWeek** | **List** | Days of the week when access is allowed | [optional] [default to null]
**validFrom** | **date** | Start date for the time restriction | [optional] [default to null]
**validUntil** | **date** | End date for the time restriction | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenpropertiesmd"></a>

## TokenProperties
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **String** | Organization ID associated with the token | [optional] [default to null]
**identityId** | **String** | Identity ID of the token holder | [optional] [default to null]
**issuedAt** | **Date** | Token issued timestamp | [optional] [default to null]
**expiresAt** | **Date** | Token expiration timestamp | [optional] [default to null]
**scope** | **List** | Permissions granted by this token | [optional] [default to null]
**tokenType** | **String** | Type of the access token | [optional] [default to null]
**jti** | **String** | JWT token identifier | [optional] [default to null]
**iat** | **Integer** | Issued at timestamp (Unix epoch) | [optional] [default to null]
**exp** | **Integer** | Expiration timestamp (Unix epoch) | [optional] [default to null]
**tokenScopeList** | [**List**](#modelstokenscopeitemmd) | List of token scopes with organization context | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenscopeitemmd"></a>

## TokenScopeItem
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**org** | [**OrganizationRef**](#modelsorganizationrefmd) |  | [optional] [default to null]
**scope** | **List** | Permissions in this scope | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstotpsoftdevicemd"></a>

## TotpSoftDevice
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | TOTP device identifier | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusermd"></a>

## User
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the user | [default to null]
**status** | **String** | User&#39;s current status | [default to null]
**opal** | **String** | OPAL identifier for the user | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]
**groups** | [**List**](#modelsusergroupmd) | Groups the user belongs to | [optional] [default to null]
**sites** | [**List**](#modelsusersitemd) | Sites the user has access to | [optional] [default to null]
**buildingFloorUnits** | [**List**](#modelsbuildingfloorunitmd) | Building floor units associated with the user | [optional] [default to null]
**hasRemoteUnlock** | **Boolean** | Whether the user has remote unlock permissions | [optional] [default to null]
**isOverrideAllowed** | **Boolean** | Whether override is allowed for this user | [optional] [default to null]
**startDate** | **date** | Access start date | [optional] [default to null]
**endDate** | **date** | Access end date | [optional] [default to null]
**startAndEndDateTimeZoneId** | **String** | Timezone for start/end dates | [optional] [default to null]
**externalId** | **String** | External system identifier | [optional] [default to null]
**personId** | **String** | Person identifier | [optional] [default to null]
**title** | **String** | Job title | [optional] [default to null]
**department** | **String** | Department | [optional] [default to null]
**lastActivityAt** | **Date** | Last activity timestamp | [optional] [default to null]
**lastParcelReminderAt** | **Date** | Last parcel reminder timestamp | [optional] [default to null]
**manuallyInactivatedAt** | **Date** | Manual inactivation timestamp | [optional] [default to null]
**statusLastUpdatedAt** | **Date** | Status last updated timestamp | [optional] [default to null]
**userCustomFields** | [**List**](#modelsusercustomfieldmd) | Custom field values for the user | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuseractivityeventmd"></a>

## UserActivityEvent
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**time** | **BigDecimal** | Unix timestamp of the event | [default to null]
**timeIsoString** | **Date** | ISO 8601 formatted timestamp | [optional] [default to null]
**userId** | **String** | User identifier | [optional] [default to null]
**userFirstName** | **String** | User&#39;s first name | [optional] [default to null]
**userLastName** | **String** | User&#39;s last name | [optional] [default to null]
**userName** | **String** | User&#39;s full name | [optional] [default to null]
**userEmail** | **String** | User&#39;s email address | [optional] [default to null]
**sourceName** | **String** | Source of the activity event | [default to null]
**category** | **String** | Event category | [optional] [default to null]
**subCategory** | **String** | Event sub-category | [optional] [default to null]
**credentialSubtype** | **String** | Type of credential used | [optional] [default to null]
**credentialTypeModelName** | **String** | Model name of credential type | [optional] [default to null]
**credentialTypeName** | **String** | Name of credential type | [optional] [default to null]
**credentialDetail** | **String** | Detailed credential information | [optional] [default to null]
**entryId** | **String** | Entry identifier where event occurred | [optional] [default to null]
**entryName** | **String** | Entry name where event occurred | [optional] [default to null]
**zoneId** | **String** | Zone identifier where event occurred | [optional] [default to null]
**zoneName** | **String** | Zone name where event occurred | [optional] [default to null]
**siteId** | **String** | Site identifier where event occurred | [optional] [default to null]
**siteName** | **String** | Site name where event occurred | [optional] [default to null]
**requestType** | **String** | Type of access request | [optional] [default to null]
**result** | **String** | Result of the access attempt | [optional] [default to null]
**resultDescription** | **String** | Detailed description of the result | [optional] [default to null]
**deniedReason** | **String** | Reason for access denial if applicable | [optional] [default to null]
**location** | **String** | Location information for the event | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusercredentialmd"></a>

## UserCredential
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Credential identifier | [default to null]
**startDate** | **date** | Credential validity start date | [optional] [default to null]
**endDate** | **date** | Credential validity end date | [optional] [default to null]
**credentialType** | [**UserCredential_credentialType**](#modelsusercredential_credentialtypemd) |  | [optional] [default to null]
**mobile** | [**UserCredential_mobile**](#modelsusercredential_mobilemd) |  | [optional] [default to null]
**card** | [**UserCredential_card**](#modelsusercredential_cardmd) |  | [optional] [default to null]
**pincode** | [**UserCredential_pincode**](#modelsusercredential_pincodemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusercredential_cardmd"></a>

## UserCredential_card
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Card identifier | [optional] [default to null]
**number** | **String** | Card number | [optional] [default to null]
**facilityCode** | **String** | Card facility code | [optional] [default to null]
**cardId** | **String** | Card ID | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusercredential_credentialtypemd"></a>

## UserCredential_credentialType
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Credential type identifier | [default to null]
**name** | **String** | Credential type name | [optional] [default to null]
**modelName** | **String** | Credential type model name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusercredential_mobilemd"></a>

## UserCredential_mobile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Mobile credential identifier | [optional] [default to null]
**name** | **String** | Mobile device name | [optional] [default to null]
**provisionedAt** | **Date** | Timestamp when mobile credential was provisioned | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusercredential_pincodemd"></a>

## UserCredential_pincode
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | PIN code identifier | [optional] [default to null]
**number** | **String** | PIN code number | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusercustomfieldmd"></a>

## UserCustomField
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Custom field identifier | [default to null]
**name** | **String** | Custom field name | [default to null]
**value** | **String** | Custom field value | [default to null]
**type** | **String** | Custom field type | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserentrymd"></a>

## UserEntry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Entry identifier | [default to null]
**name** | **String** | Entry name | [optional] [default to null]
**wirelessLock** | [**WirelessLock**](#modelswirelesslockmd) |  | [optional] [default to null]
**zone** | [**UserEntryZone**](#modelsuserentryzonemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserentryzonemd"></a>

## UserEntryZone
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Zone identifier | [default to null]
**name** | **String** | Zone name | [optional] [default to null]
**offlineEntryCount** | **Integer** | Number of offline entries in the zone | [optional] [default to null]
**site** | [**ZoneSiteRef**](#modelszonesiterefmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusergroupmd"></a>

## UserGroup
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Group identifier | [default to null]
**name** | **String** | Group name | [default to null]
**description** | **String** | Group description | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuseridentitymd"></a>

## UserIdentity
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Identity identifier | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [optional] [default to null]
**lastName** | **String** | User&#39;s last name | [optional] [default to null]
**fullName** | **String** | User&#39;s full name | [optional] [default to null]
**initials** | **String** | User&#39;s initials | [optional] [default to null]
**opal** | **String** | OPAL identifier for this identity | [optional] [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**mobilePhone** | **String** | User&#39;s mobile phone number | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**middleName** | **String** | User&#39;s middle name | [optional] [default to null]
**suffix** | **String** | User&#39;s name suffix | [optional] [default to null]
**preferredName** | **String** | User&#39;s preferred name | [optional] [default to null]
**pronouns** | **String** | User&#39;s pronouns | [optional] [default to null]
**dateOfBirth** | **date** | User&#39;s date of birth | [optional] [default to null]
**emergencyContactName** | **String** | Emergency contact name | [optional] [default to null]
**emergencyContactPhone** | **String** | Emergency contact phone | [optional] [default to null]
**homeAddress** | **String** | User&#39;s home address | [optional] [default to null]
**companyName** | **String** | User&#39;s company name | [optional] [default to null]
**workAddress** | **String** | User&#39;s work address | [optional] [default to null]
**isEmailVerified** | **Boolean** | Whether the email has been verified | [optional] [default to null]
**idpUniqueIdentifier** | **String** | Identity provider unique identifier | [optional] [default to null]
**language** | **String** | User&#39;s language preference | [optional] [default to null]
**nicknames** | **List** | User&#39;s nicknames | [optional] [default to null]
**needsPasswordChange** | **Boolean** | Whether the user needs to change their password | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]
**namespace** | [**IdentityNamespace**](#modelsidentitynamespacemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfomd"></a>

## UserInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the user | [default to null]
**status** | **String** | User&#39;s current status | [default to null]
**opal** | **String** | OPAL identifier for the user | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]
**groups** | [**List**](#modelsusergroupmd) | Groups the user belongs to | [optional] [default to null]
**sites** | [**List**](#modelsusersitemd) | Sites the user has access to | [optional] [default to null]
**buildingFloorUnits** | [**List**](#modelsbuildingfloorunitmd) | Building floor units associated with the user | [optional] [default to null]
**hasRemoteUnlock** | **Boolean** | Whether the user has remote unlock permissions | [optional] [default to null]
**isOverrideAllowed** | **Boolean** | Whether override is allowed for this user | [optional] [default to null]
**startDate** | **date** | Access start date | [optional] [default to null]
**endDate** | **date** | Access end date | [optional] [default to null]
**startAndEndDateTimeZoneId** | **String** | Timezone for start/end dates | [optional] [default to null]
**externalId** | **String** | External system identifier | [optional] [default to null]
**personId** | **String** | Person identifier | [optional] [default to null]
**title** | **String** | Job title | [optional] [default to null]
**department** | **String** | Department | [optional] [default to null]
**lastActivityAt** | **Date** | Last activity timestamp | [optional] [default to null]
**lastParcelReminderAt** | **Date** | Last parcel reminder timestamp | [optional] [default to null]
**manuallyInactivatedAt** | **Date** | Manual inactivation timestamp | [optional] [default to null]
**statusLastUpdatedAt** | **Date** | Status last updated timestamp | [optional] [default to null]
**userCustomFields** | [**List**](#modelsusercustomfieldmd) | Custom field values for the user | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]
**organizationId** | **String** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | [**List**](#modelsusercustomfieldmd) | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfo_allofmd"></a>

## UserInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **String** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | [**List**](#modelsusercustomfieldmd) | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserpicturemd"></a>

## UserPicture
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Picture identifier | [default to null]
**isAvatar** | **Boolean** | Whether this picture is the user&#39;s avatar | [optional] [default to null]
**createdAt** | **Date** | Timestamp when picture was uploaded | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when picture was last updated | [optional] [default to null]
**picture** | [**PictureInfo**](#modelspictureinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusersitemd"></a>

## UserSite
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Site identifier | [default to null]
**name** | **String** | Site name | [default to null]
**address** | **String** | Site address | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserzonemd"></a>

## UserZone
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Zone identifier | [default to null]
**name** | **String** | Zone name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserzoneusermd"></a>

## UserZoneUser
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**zone** | [**UserZoneUser_zone**](#modelsuserzoneuser_zonemd) |  | [optional] [default to null]
**schedule** | [**Schedule**](#modelsschedulemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserzoneuser_zonemd"></a>

## UserZoneUser_zone
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Zone identifier | [default to null]
**name** | **String** | Zone name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsvideoprovidermd"></a>

## VideoProvider
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Video provider identifier | [default to null]
**videoProviderTypeId** | **String** | Video provider type identifier | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelswirelesslockmd"></a>

## WirelessLock
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Wireless lock identifier | [default to null]
**noTourLockId** | **Integer** | NoTour lock identifier | [optional] [default to null]
**isOffline** | **Boolean** | Whether the wireless lock is offline | [optional] [default to null]
**wirelessLockDbEntries** | [**List**](#modelswirelesslockdbentrymd) | Database entries for the wireless lock | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelswirelesslockdbentrymd"></a>

## WirelessLockDbEntry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**credentialNumberHash** | **String** | Hashed credential number | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszonemd"></a>

## Zone
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Unique identifier for the zone | [default to null]
**opal** | **String** | OPAL identifier for the zone | [optional] [default to null]
**name** | **String** | Name of the zone | [default to null]
**description** | **String** | Description of the zone | [optional] [default to null]
**apbResetIcalText** | **String** | Anti-passback reset iCal text | [optional] [default to null]
**apbExpirationSeconds** | **Integer** | Anti-passback expiration in seconds | [optional] [default to null]
**apbUseContactSensor** | **Boolean** | Whether to use contact sensor for anti-passback | [optional] [default to null]
**apbAllowSharedOrgReset** | **Boolean** | Whether to allow shared organization reset for anti-passback | [optional] [default to null]
**entryCount** | **Integer** | Number of entries in the zone | [optional] [default to null]
**offlineEntryCount** | **Integer** | Number of offline entries in the zone | [optional] [default to null]
**userCount** | **Integer** | Number of users in the zone | [optional] [default to null]
**groupCount** | **Integer** | Number of groups in the zone | [optional] [default to null]
**org** | [**OrganizationRef**](#modelsorganizationrefmd) |  | [optional] [default to null]
**site** | [**ZoneSiteRef**](#modelszonesiterefmd) |  | [optional] [default to null]
**zoneShares** | **List** | Zone shares array | [optional] [default to null]
**entries** | [**List**](#modelszoneentrymd) | Entries in the zone | [optional] [default to null]
**apbAreas** | **List** | Anti-passback areas | [optional] [default to null]
**createdAt** | **Date** | Timestamp when zone was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when zone was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszoneentrymd"></a>

## ZoneEntry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Entry identifier | [default to null]
**name** | **String** | Entry name | [default to null]
**wirelessLock** | [**Object**](.md) | Wireless lock information | [optional] [default to null]
**acu** | [**ZoneEntryAcu**](#modelszoneentryacumd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszoneentryacumd"></a>

## ZoneEntryAcu
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ACU identifier | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszonesharemd"></a>

## ZoneShare
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Zone share identifier | [default to null]
**zoneId** | **String** | Zone identifier | [optional] [default to null]
**sharedWithOrgId** | **String** | Organization ID that the zone is shared with | [optional] [default to null]
**sharedWithOrg** | [**ZoneShare_sharedWithOrg**](#modelszoneshare_sharedwithorgmd) |  | [optional] [default to null]
**createdAt** | **Date** | Creation timestamp | [optional] [default to null]
**updatedAt** | **Date** | Last update timestamp | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszoneshare_sharedwithorgmd"></a>

## ZoneShare_sharedWithOrg
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Shared organization identifier | [optional] [default to null]
**name** | **String** | Shared organization name | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszonesiterefmd"></a>

## ZoneSiteRef
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Site identifier | [default to null]
**name** | **String** | Site name | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszonezoneusermd"></a>

## ZoneZoneUser
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**user** | [**ZoneZoneUser_user**](#modelszonezoneuser_usermd) |  | [optional] [default to null]
**schedule** | [**Schedule**](#modelsschedulemd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelszonezoneuser_usermd"></a>

## ZoneZoneUser_user
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | User identifier | [default to null]
**status** | **String** | User status | [optional] [default to null]
**identity** | [**UserIdentity**](#modelsuseridentitymd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsbaseconnectionstatemd"></a>

## baseConnectionState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## connectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**email** | **String** | The email address to use for authentication | [default to null]
**password** | **String** | The password to provide for authentication | [default to null]
**totpCode** | **String** | Time-based One-Time Password for MFA | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstatemd"></a>

## connectionState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allofmd"></a>

## connectionState_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-avigilon-alta-access

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AuditApi* | [**listAuditLogs**](#listauditlogs) | **GET** /organizations/{organizationId}/reports/auditLogs/ui | Retrieves audit logs for the organization
*AuthApi* | [**getTokenProperties**](#gettokenproperties) | **GET** /accessToken | Retrieves properties and metadata for the current access token
*CredentialApi* | [**listCardFormats**](#listcardformats) | **GET** /organizations/{organizationId}/cardFormats | Retrieves all card formats in the organization
*CredentialApi* | [**listCredentialActionTypes**](#listcredentialactiontypes) | **GET** /organizations/{organizationId}/credentialActionTypes | Retrieves all credential action types in the organization
*CredentialApi* | [**listCredentialActions**](#listcredentialactions) | **GET** /organizations/{organizationId}/credentials/{credentialId}/credentialActions | Retrieves all actions performed on a specific credential
*CredentialApi* | [**listCredentialTypes**](#listcredentialtypes) | **GET** /organizations/{organizationId}/credentialTypes | Retrieves all credential types in the organization
*CredentialApi* | [**listOrgCredentials**](#listorgcredentials) | **GET** /organizations/{organizationId}/credentials | Retrieves all credentials in the organization
*EntryApi* | [**getEntry**](#getentry) | **GET** /organizations/{organizationId}/entries/{entryId} | Retrieves detailed information for a specific entry
*EntryApi* | [**listEntries**](#listentries) | **GET** /organizations/{organizationId}/entries | Retrieves all entries in the organization
*EntryApi* | [**listEntryActivity**](#listentryactivity) | **GET** /organizations/{organizationId}/entries/{entryId}/activity | Retrieves activity report for a specific entry
*EntryApi* | [**listEntryCameras**](#listentrycameras) | **GET** /organizations/{organizationId}/entries/{entryId}/cameras | Retrieves all cameras associated with a specific entry
*EntryApi* | [**listEntryStates**](#listentrystates) | **GET** /organizations/{organizationId}/entryStates | Retrieves all entry states in the organization
*EntryApi* | [**listEntryUserSchedules**](#listentryuserschedules) | **GET** /organizations/{organizationId}/entries/{entryId}/userSchedules | Retrieves all active users with their associated schedules that have access to a specific entry
*EntryApi* | [**listEntryUsers**](#listentryusers) | **GET** /organizations/{organizationId}/entries/{entryId}/users | Retrieves all active users that have access to a specific entry
*GroupApi* | [**getGroup**](#getgroup) | **GET** /organizations/{organizationId}/groups/{groupId} | Retrieves single access group details by group ID
*GroupApi* | [**listGroupEntries**](#listgroupentries) | **GET** /organizations/{organizationId}/groups/{groupId}/entries | Retrieves all entries/permissions for a specific group
*GroupApi* | [**listGroupUsers**](#listgroupusers) | **GET** /organizations/{organizationId}/groups/{groupId}/users | Retrieves all users belonging to a specific group
*GroupApi* | [**listGroupZoneGroups**](#listgroupzonegroups) | **GET** /organizations/{organizationId}/groups/{groupId}/zoneGroups | Retrieves all zones and their associated configurations for a specific group
*GroupApi* | [**listGroupZones**](#listgroupzones) | **GET** /organizations/{organizationId}/groups/{groupId}/zones | Retrieves all zones for a specific group
*GroupApi* | [**listGroups**](#listgroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization
*IdentityProviderApi* | [**getIdentityProviderGroupRelations**](#getidentityprovidergrouprelations) | **GET** /organizations/{organizationId}/identityProviders/{identityProviderId}/groupRelations | Retrieves group relations for a specific identity provider
*IdentityProviderApi* | [**getIdentityProviderType**](#getidentityprovidertype) | **GET** /organizations/{organizationId}/identityProviderTypes/{identityProviderTypeId} | Retrieves detailed information for a specific identity provider type
*IdentityProviderApi* | [**listIdentityProviderGroups**](#listidentityprovidergroups) | **GET** /organizations/{organizationId}/identityProviders/{identityProviderId}/groups | Retrieves all groups from a specific identity provider
*IdentityProviderApi* | [**listIdentityProviderTypes**](#listidentityprovidertypes) | **GET** /organizations/{organizationId}/identityProviderTypes | Retrieves all identity provider types in the organization
*IdentityProviderApi* | [**listIdentityProviders**](#listidentityproviders) | **GET** /organizations/{organizationId}/identityProviders | Retrieves all identity providers in the organization
*RoleApi* | [**listRoleUsers**](#listroleusers) | **GET** /organizations/{organizationId}/roles/{roleId}/users | Retrieves all users assigned to a specific role
*RoleApi* | [**listRoles**](#listroles) | **GET** /organizations/{organizationId}/roles | Retrieves all roles in the organization
*ScheduleApi* | [**listScheduleEvents**](#listscheduleevents) | **GET** /organizations/{organizationId}/schedules/{scheduleId}/events | Retrieves all events for a specific schedule
*ScheduleApi* | [**listScheduleTypes**](#listscheduletypes) | **GET** /organizations/{organizationId}/scheduleTypes | Retrieves all schedule types in the organization
*ScheduleApi* | [**listSchedules**](#listschedules) | **GET** /organizations/{organizationId}/schedules | Retrieves all schedules in the organization
*SiteApi* | [**listSites**](#listsites) | **GET** /organizations/{organizationId}/sites | Retrieves all sites in the organization
*UserApi* | [**getUser**](#getuser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
*UserApi* | [**listOrgIdentities**](#listorgidentities) | **GET** /organizations/{organizationId}/orgIdentities | Retrieves all identities in the organization
*UserApi* | [**listOrgPictures**](#listorgpictures) | **GET** /organizations/{organizationId}/orgPictures | Retrieves all organization pictures
*UserApi* | [**listSharedUsers**](#listsharedusers) | **GET** /organizations/{organizationId}/sharedUsers | Retrieves all users shared from other organizations
*UserApi* | [**listUserActivity**](#listuseractivity) | **GET** /organizations/{organizationId}/users/{userId}/activity | Retrieves activity report for a specific user
*UserApi* | [**listUserCredentials**](#listusercredentials) | **GET** /organizations/{organizationId}/users/{userId}/credentials | Retrieves all credentials for a specific user
*UserApi* | [**listUserEntries**](#listuserentries) | **GET** /organizations/{organizationId}/users/{userId}/entries | Retrieves all entries/access permissions for a specific user
*UserApi* | [**listUserGroups**](#listusergroups) | **GET** /organizations/{organizationId}/users/{userId}/groups | Retrieves all groups a specific user belongs to
*UserApi* | [**listUserMfaCredentials**](#listusermfacredentials) | **GET** /organizations/{organizationId}/users/{userId}/mfaCredentials | Retrieves all MFA credentials for a specific user
*UserApi* | [**listUserPictures**](#listuserpictures) | **GET** /organizations/{organizationId}/users/{userId}/userPictures | Retrieves all pictures for a specific user
*UserApi* | [**listUserRoles**](#listuserroles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
*UserApi* | [**listUserSites**](#listusersites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
*UserApi* | [**listUserZoneUsers**](#listuserzoneusers) | **GET** /organizations/{organizationId}/users/{userId}/zoneUsers | Retrieves all zones and their associated configurations a user has access to
*UserApi* | [**listUserZones**](#listuserzones) | **GET** /organizations/{organizationId}/users/{userId}/zones | Retrieves all zones associated with a specific user
*UserApi* | [**listUsers**](#listusers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization
*ZoneApi* | [**listZoneShares**](#listzoneshares) | **GET** /organizations/{organizationId}/zones/{zoneId}/zoneShares | Retrieves all zone shares for a specific zone
*ZoneApi* | [**listZoneZoneUsers**](#listzonezoneusers) | **GET** /organizations/{organizationId}/zones/{zoneId}/zoneUsers | Retrieves all zone users with their access configurations for a specific zone
*ZoneApi* | [**listZones**](#listzones) | **GET** /organizations/{organizationId}/zones | Retrieves all zones in the organization


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AccessRule](#modelsaccessrulemd)
 - [AuditLogEntry](#modelsauditlogentrymd)
 - [AuthStrategyType](#modelsauthstrategytypemd)
 - [BuildingFloorUnit](#modelsbuildingfloorunitmd)
 - [CardFormat](#modelscardformatmd)
 - [CredentialAction](#modelscredentialactionmd)
 - [CredentialActionType](#modelscredentialactiontypemd)
 - [CredentialAction_performedBy](#modelscredentialaction_performedbymd)
 - [CredentialType](#modelscredentialtypemd)
 - [Entry](#modelsentrymd)
 - [EntryActivityEvent](#modelsentryactivityeventmd)
 - [EntryActivityEvent_userGroups](#modelsentryactivityevent_usergroupsmd)
 - [EntryActivityEvent_userSites](#modelsentryactivityevent_usersitesmd)
 - [EntryAcu](#modelsentryacumd)
 - [EntryCamera](#modelsentrycameramd)
 - [EntryInfo](#modelsentryinfomd)
 - [EntryInfo_allOf](#modelsentryinfo_allofmd)
 - [EntrySchedule](#modelsentryschedulemd)
 - [EntryState](#modelsentrystatemd)
 - [EntryStateInfo](#modelsentrystateinfomd)
 - [EntryUser](#modelsentryusermd)
 - [EntryUserSchedule](#modelsentryuserschedulemd)
 - [EntryUserSchedule_user](#modelsentryuserschedule_usermd)
 - [EntryUser_identity](#modelsentryuser_identitymd)
 - [EntryZone](#modelsentryzonemd)
 - [EntryZoneSite](#modelsentryzonesitemd)
 - [Group](#modelsgroupmd)
 - [GroupInfo](#modelsgroupinfomd)
 - [GroupInfo_allOf](#modelsgroupinfo_allofmd)
 - [GroupSummary](#modelsgroupsummarymd)
 - [GroupZone](#modelsgroupzonemd)
 - [GroupZoneGroup](#modelsgroupzonegroupmd)
 - [IdentityNamespace](#modelsidentitynamespacemd)
 - [IdentityProvider](#modelsidentityprovidermd)
 - [IdentityProviderGroup](#modelsidentityprovidergroupmd)
 - [IdentityProviderGroupRelation](#modelsidentityprovidergrouprelationmd)
 - [IdentityProviderType](#modelsidentityprovidertypemd)
 - [IdentityProviderTypeInfo](#modelsidentityprovidertypeinfomd)
 - [IdentityProviderTypeSummary](#modelsidentityprovidertypesummarymd)
 - [MfaCredential](#modelsmfacredentialmd)
 - [MfaCredentialType](#modelsmfacredentialtypemd)
 - [NamespaceType](#modelsnamespacetypemd)
 - [OrgCredential](#modelsorgcredentialmd)
 - [OrgCredential_card](#modelsorgcredential_cardmd)
 - [OrgCredential_mobile](#modelsorgcredential_mobilemd)
 - [OrgCredential_user](#modelsorgcredential_usermd)
 - [OrgIdentity](#modelsorgidentitymd)
 - [OrgPicture](#modelsorgpicturemd)
 - [OrganizationRef](#modelsorganizationrefmd)
 - [PictureInfo](#modelspictureinfomd)
 - [Role](#modelsrolemd)
 - [RoleInfo](#modelsroleinfomd)
 - [RoleUser](#modelsroleusermd)
 - [Schedule](#modelsschedulemd)
 - [ScheduleEvent](#modelsscheduleeventmd)
 - [ScheduleType](#modelsscheduletypemd)
 - [SharedUser](#modelssharedusermd)
 - [SharedUser_sharedFromOrg](#modelsshareduser_sharedfromorgmd)
 - [SharedUser_user](#modelsshareduser_usermd)
 - [Site](#modelssitemd)
 - [SiteSummary](#modelssitesummarymd)
 - [TimeRestriction](#modelstimerestrictionmd)
 - [TokenProperties](#modelstokenpropertiesmd)
 - [TokenScopeItem](#modelstokenscopeitemmd)
 - [TotpSoftDevice](#modelstotpsoftdevicemd)
 - [User](#modelsusermd)
 - [UserActivityEvent](#modelsuseractivityeventmd)
 - [UserCredential](#modelsusercredentialmd)
 - [UserCredential_card](#modelsusercredential_cardmd)
 - [UserCredential_credentialType](#modelsusercredential_credentialtypemd)
 - [UserCredential_mobile](#modelsusercredential_mobilemd)
 - [UserCredential_pincode](#modelsusercredential_pincodemd)
 - [UserCustomField](#modelsusercustomfieldmd)
 - [UserEntry](#modelsuserentrymd)
 - [UserEntryZone](#modelsuserentryzonemd)
 - [UserGroup](#modelsusergroupmd)
 - [UserIdentity](#modelsuseridentitymd)
 - [UserInfo](#modelsuserinfomd)
 - [UserInfo_allOf](#modelsuserinfo_allofmd)
 - [UserPicture](#modelsuserpicturemd)
 - [UserSite](#modelsusersitemd)
 - [UserZone](#modelsuserzonemd)
 - [UserZoneUser](#modelsuserzoneusermd)
 - [UserZoneUser_zone](#modelsuserzoneuser_zonemd)
 - [VideoProvider](#modelsvideoprovidermd)
 - [WirelessLock](#modelswirelesslockmd)
 - [WirelessLockDbEntry](#modelswirelesslockdbentrymd)
 - [Zone](#modelszonemd)
 - [ZoneEntry](#modelszoneentrymd)
 - [ZoneEntryAcu](#modelszoneentryacumd)
 - [ZoneShare](#modelszonesharemd)
 - [ZoneShare_sharedWithOrg](#modelszoneshare_sharedwithorgmd)
 - [ZoneSiteRef](#modelszonesiterefmd)
 - [ZoneZoneUser](#modelszonezoneusermd)
 - [ZoneZoneUser_user](#modelszonezoneuser_usermd)
 - [baseConnectionState](#modelsbaseconnectionstatemd)
 - [connectionProfile](#modelsconnectionprofilemd)
 - [connectionState](#modelsconnectionstatemd)
 - [connectionState_allOf](#modelsconnectionstate_allofmd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

<a name="access-token"></a>
### access-token

- **Type**: OAuth
- **Flow**: accessCode
- **Authorization URL**: https://api.openpath.com/auth/login
- **Scopes**: 
  - read:users: Read access to user information
  - read:groups: Read access to group information
  - read:sites: Read access to site information
  - read:zones: Read access to zone information
  - read:user-roles: Read access to user role assignments
  - read:user-sites: Read access to user site associations
  - read:group-users: Read access to group user memberships
  - read:group-entries: Read access to group entries and permissions
  - read:entries: Read access to entry information

