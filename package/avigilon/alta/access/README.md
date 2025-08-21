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
import { Email } from '@auditmation/types-core-js';

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
import { Email } from '@auditmation/types-core-js';

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

📋 **Important**: This documentation is auto-generated. Please verify code examples work with the current version.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Apis](#apis)
  - [AcuApi](#acuapi)
  - [**getAcu**](#getacu)
  - [**listAcuPorts**](#listacuports)
  - [**listAcus**](#listacus)
  - [GroupApi](#groupapi)
  - [**getGroup**](#getgroup)
  - [**listGroupEntries**](#listgroupentries)
  - [**listGroupUsers**](#listgroupusers)
  - [**listGroups**](#listgroups)
  - [UserApi](#userapi)
  - [**getUser**](#getuser)
  - [**listUserRoles**](#listuserroles)
  - [**listUserSites**](#listusersites)
  - [**listUsers**](#listusers)
- [Models](#models)
  - [AccessRule](#accessrule)
    - [Properties](#properties)
  - [Acu](#acu)
    - [Properties](#properties-1)
  - [AcuConfiguration](#acuconfiguration)
    - [Properties](#properties-2)
  - [AcuConfiguration_networkSettings](#acuconfiguration_networksettings)
    - [Properties](#properties-3)
  - [AcuInfo](#acuinfo)
    - [Properties](#properties-4)
  - [AcuInfo_allOf](#acuinfo_allof)
    - [Properties](#properties-5)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-6)
  - [ConnectionState](#connectionstate)
    - [Properties](#properties-7)
  - [ConnectionState_allOf](#connectionstate_allof)
    - [Properties](#properties-8)
  - [ConnectionState_allOf_1](#connectionstate_allof_1)
    - [Properties](#properties-9)
  - [Entry](#entry)
    - [Properties](#properties-10)
  - [Group](#group)
    - [Properties](#properties-11)
  - [GroupInfo](#groupinfo)
    - [Properties](#properties-12)
  - [GroupInfo_allOf](#groupinfo_allof)
    - [Properties](#properties-13)
  - [Port](#port)
    - [Properties](#properties-14)
  - [PortConfiguration](#portconfiguration)
    - [Properties](#properties-15)
  - [Role](#role)
    - [Properties](#properties-16)
  - [Site](#site)
    - [Properties](#properties-17)
  - [TimeRestriction](#timerestriction)
    - [Properties](#properties-18)
  - [User](#user)
    - [Properties](#properties-19)
  - [UserInfo](#userinfo)
    - [Properties](#properties-20)
  - [UserInfo_allOf](#userinfo_allof)
    - [Properties](#properties-21)
- [Documentation for @zerobias-org/module-avigilon-alta-access](#documentation-for-zerobias-orgmodule-avigilon-alta-access)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)
    - [access-token](#access-token)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisacuapimd"></a>

## AcuApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getAcu**](#getAcu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
[**listAcuPorts**](#listAcuPorts) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
[**listAcus**](#listAcus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization


<a name="getAcu"></a>
## **getAcu**
> AcuInfo getAcu(organizationId, acuId)

Retrieves single ACU details by ACU ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]

#### Return type

[**AcuInfo**](#modelsacuinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcuPorts"></a>
## **listAcuPorts**
> List listAcuPorts(organizationId, acuId, pageNumber, pageSize)

Retrieves all ports for a specific ACU

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsportmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcus"></a>
## **listAcus**
> List listAcus(organizationId, pageNumber, pageSize)

Retrieves all Access Control Units (ACUs) in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsacumd)

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
[**listGroups**](#listGroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization


<a name="getGroup"></a>
## **getGroup**
> GroupInfo getGroup(organizationId, groupId)

Retrieves single access group details by group ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **Long**| Unique identifier for a group | [default to null]

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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusermd)

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



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getUser**](#getUser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
[**listUserRoles**](#listUserRoles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
[**listUserSites**](#listUserSites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
[**listUsers**](#listUsers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="getUser"></a>
## **getUser**
> UserInfo getUser(organizationId, userId)

Retrieves single user details by user ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **Long**| Unique identifier for a user | [default to null]

#### Return type

[**UserInfo**](#modelsuserinfomd)

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
 **userId** | **Long**| Unique identifier for a user | [default to null]
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
 **userId** | **Long**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssitemd)

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


# Models


<a name="modelsaccessrulemd"></a>

## AccessRule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the access rule | [default to null]
**name** | **String** | Name of the access rule | [default to null]
**description** | **String** | Description of the access rule | [optional] [default to null]
**conditions** | **List** | Conditions that must be met for access | [optional] [default to null]
**actions** | **List** | Actions to take when rule is triggered | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacumd"></a>

## Acu
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfigurationmd"></a>

## AcuConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pollingInterval** | **Integer** | Heartbeat polling interval in seconds | [optional] [default to null]
**timeoutSettings** | **Map** | Various timeout settings | [optional] [default to null]
**securitySettings** | **Map** | Security-related configuration | [optional] [default to null]
**networkSettings** | [**AcuConfiguration_networkSettings**](#modelsacuconfiguration_networksettingsmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfiguration_networksettingsmd"></a>

## AcuConfiguration_networkSettings
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**dhcpEnabled** | **Boolean** | Whether DHCP is enabled | [optional] [default to null]
**staticIp** | **String** | Static IP address if DHCP is disabled | [optional] [default to null]
**subnetMask** | **String** | Subnet mask | [optional] [default to null]
**gateway** | **String** | Gateway IP address | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfomd"></a>

## AcuInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfo_allofmd"></a>

## AcuInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**email** | **String** | The email address to use for authentication | [default to null]
**password** | **String** | The password to provide for authentication | [default to null]
**totpCode** | **String** | Time-based One-Time Password for MFA | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstatemd"></a>

## ConnectionState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allofmd"></a>

## ConnectionState_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allof_1md"></a>

## ConnectionState_allOf_1
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrymd"></a>

## Entry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the entry | [default to null]
**type** | **String** | Type of entry/permission | [default to null]
**name** | **String** | Name of the entry | [optional] [default to null]
**description** | **String** | Description of the entry | [optional] [default to null]
**doorId** | **Long** | Door ID associated with the entry | [optional] [default to null]
**zoneId** | **Long** | Zone ID associated with the entry | [optional] [default to null]
**timeRestrictions** | [**TimeRestriction**](#modelstimerestrictionmd) |  | [optional] [default to null]
**status** | **String** | Current status of the entry | [optional] [default to null]
**createdAt** | **Date** | Timestamp when entry was created | [optional] [default to null]
**expiresAt** | **Date** | Timestamp when entry expires | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupmd"></a>

## Group
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfomd"></a>

## GroupInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfo_allofmd"></a>

## GroupInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportmd"></a>

## Port
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the port | [default to null]
**number** | **Integer** | Port number on the ACU | [default to null]
**type** | **String** | Type of device connected to the port | [default to null]
**name** | **String** | Name assigned to the port | [optional] [default to null]
**description** | **String** | Description of the port | [optional] [default to null]
**status** | **String** | Current status of the port | [optional] [default to null]
**configuration** | [**PortConfiguration**](#modelsportconfigurationmd) |  | [optional] [default to null]
**lastActivity** | **Date** | Timestamp of last activity on the port | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportconfigurationmd"></a>

## PortConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**sensitivity** | **Integer** | Sensitivity level for the port | [optional] [default to null]
**debounceTime** | **Integer** | Debounce time in milliseconds | [optional] [default to null]
**pollingRate** | **Integer** | Polling rate in milliseconds | [optional] [default to null]
**thresholds** | **Map** | Various threshold settings | [optional] [default to null]
**customSettings** | **Map** | Custom configuration settings | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrolemd"></a>

## Role
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the role | [default to null]
**name** | **String** | Name of the role | [default to null]
**description** | **String** | Description of the role | [optional] [default to null]
**permissions** | **List** | List of permissions granted by this role | [optional] [default to null]
**assignedAt** | **Date** | Timestamp when the role was assigned | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssitemd"></a>

## Site
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the site | [default to null]
**name** | **String** | Name of the site | [default to null]
**address** | **String** | Physical address of the site | [optional] [default to null]
**city** | **String** | City where the site is located | [optional] [default to null]
**state** | **String** | State/province where the site is located | [optional] [default to null]
**country** | **String** | Country where the site is located | [optional] [default to null]
**postalCode** | **String** | Postal/zip code of the site | [optional] [default to null]
**timezone** | **String** | Timezone of the site | [optional] [default to null]
**status** | **String** | Current status of the site | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstimerestrictionmd"></a>

## TimeRestriction
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**startTime** | **String** | Start time for access (HH:MM format) | [optional] [default to null]
**endTime** | **String** | End time for access (HH:MM format) | [optional] [default to null]
**daysOfWeek** | **List** | Days of the week when access is allowed | [optional] [default to null]
**validFrom** | **date** | Start date for the time restriction | [optional] [default to null]
**validUntil** | **date** | End date for the time restriction | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusermd"></a>

## User
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfomd"></a>

## UserInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfo_allofmd"></a>

## UserInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-avigilon-alta-access

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AcuApi* | [**getAcu**](#getacu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
*AcuApi* | [**listAcuPorts**](#listacuports) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
*AcuApi* | [**listAcus**](#listacus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization
*GroupApi* | [**getGroup**](#getgroup) | **GET** /organizations/{organizationId}/groups/{groupId} | Retrieves single access group details by group ID
*GroupApi* | [**listGroupEntries**](#listgroupentries) | **GET** /organizations/{organizationId}/groups/{groupId}/entries | Retrieves all entries/permissions for a specific group
*GroupApi* | [**listGroupUsers**](#listgroupusers) | **GET** /organizations/{organizationId}/groups/{groupId}/users | Retrieves all users belonging to a specific group
*GroupApi* | [**listGroups**](#listgroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization
*UserApi* | [**getUser**](#getuser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
*UserApi* | [**listUserRoles**](#listuserroles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
*UserApi* | [**listUserSites**](#listusersites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
*UserApi* | [**listUsers**](#listusers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AccessRule](#modelsaccessrulemd)
 - [Acu](#modelsacumd)
 - [AcuConfiguration](#modelsacuconfigurationmd)
 - [AcuConfiguration_networkSettings](#modelsacuconfiguration_networksettingsmd)
 - [AcuInfo](#modelsacuinfomd)
 - [AcuInfo_allOf](#modelsacuinfo_allofmd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [ConnectionState](#modelsconnectionstatemd)
 - [ConnectionState_allOf](#modelsconnectionstate_allofmd)
 - [ConnectionState_allOf_1](#modelsconnectionstate_allof_1md)
 - [Entry](#modelsentrymd)
 - [Group](#modelsgroupmd)
 - [GroupInfo](#modelsgroupinfomd)
 - [GroupInfo_allOf](#modelsgroupinfo_allofmd)
 - [Port](#modelsportmd)
 - [PortConfiguration](#modelsportconfigurationmd)
 - [Role](#modelsrolemd)
 - [Site](#modelssitemd)
 - [TimeRestriction](#modelstimerestrictionmd)
 - [User](#modelsusermd)
 - [UserInfo](#modelsuserinfomd)
 - [UserInfo_allOf](#modelsuserinfo_allofmd)


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
  - read:acus: Read access to access control units
  - read:user-roles: Read access to user role assignments
  - read:user-sites: Read access to user site associations
  - read:group-users: Read access to group user memberships
  - read:group-entries: Read access to group entries and permissions
  - read:acu-ports: Read access to ACU port information

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Apis](#apis)
  - [AcuApi](#acuapi)
  - [**getAcu**](#getacu)
  - [**listAcuPorts**](#listacuports)
  - [**listAcus**](#listacus)
  - [GroupApi](#groupapi)
  - [**getGroup**](#getgroup)
  - [**listGroupEntries**](#listgroupentries)
  - [**listGroupUsers**](#listgroupusers)
  - [**listGroups**](#listgroups)
  - [UserApi](#userapi)
  - [**getUser**](#getuser)
  - [**listUserRoles**](#listuserroles)
  - [**listUserSites**](#listusersites)
  - [**listUsers**](#listusers)
- [Models](#models)
  - [AccessRule](#accessrule)
    - [Properties](#properties)
  - [Acu](#acu)
    - [Properties](#properties-1)
  - [AcuConfiguration](#acuconfiguration)
    - [Properties](#properties-2)
  - [AcuConfiguration_networkSettings](#acuconfiguration_networksettings)
    - [Properties](#properties-3)
  - [AcuInfo](#acuinfo)
    - [Properties](#properties-4)
  - [AcuInfo_allOf](#acuinfo_allof)
    - [Properties](#properties-5)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-6)
  - [ConnectionState](#connectionstate)
    - [Properties](#properties-7)
  - [ConnectionState_allOf](#connectionstate_allof)
    - [Properties](#properties-8)
  - [ConnectionState_allOf_1](#connectionstate_allof_1)
    - [Properties](#properties-9)
  - [Entry](#entry)
    - [Properties](#properties-10)
  - [Group](#group)
    - [Properties](#properties-11)
  - [GroupInfo](#groupinfo)
    - [Properties](#properties-12)
  - [GroupInfo_allOf](#groupinfo_allof)
    - [Properties](#properties-13)
  - [Port](#port)
    - [Properties](#properties-14)
  - [PortConfiguration](#portconfiguration)
    - [Properties](#properties-15)
  - [Role](#role)
    - [Properties](#properties-16)
  - [Site](#site)
    - [Properties](#properties-17)
  - [TimeRestriction](#timerestriction)
    - [Properties](#properties-18)
  - [User](#user)
    - [Properties](#properties-19)
  - [UserInfo](#userinfo)
    - [Properties](#properties-20)
  - [UserInfo_allOf](#userinfo_allof)
    - [Properties](#properties-21)
- [Documentation for @zerobias-org/module-avigilon-alta-access](#documentation-for-zerobias-orgmodule-avigilon-alta-access)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)
    - [access-token](#access-token)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisacuapimd"></a>

## AcuApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getAcu**](#getAcu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
[**listAcuPorts**](#listAcuPorts) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
[**listAcus**](#listAcus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization


<a name="getAcu"></a>
## **getAcu**
> AcuInfo getAcu(organizationId, acuId)

Retrieves single ACU details by ACU ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]

#### Return type

[**AcuInfo**](#modelsacuinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcuPorts"></a>
## **listAcuPorts**
> List listAcuPorts(organizationId, acuId, pageNumber, pageSize)

Retrieves all ports for a specific ACU

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsportmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcus"></a>
## **listAcus**
> List listAcus(organizationId, pageNumber, pageSize)

Retrieves all Access Control Units (ACUs) in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsacumd)

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
[**listGroups**](#listGroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization


<a name="getGroup"></a>
## **getGroup**
> GroupInfo getGroup(organizationId, groupId)

Retrieves single access group details by group ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **Long**| Unique identifier for a group | [default to null]

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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusermd)

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



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getUser**](#getUser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
[**listUserRoles**](#listUserRoles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
[**listUserSites**](#listUserSites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
[**listUsers**](#listUsers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="getUser"></a>
## **getUser**
> UserInfo getUser(organizationId, userId)

Retrieves single user details by user ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **Long**| Unique identifier for a user | [default to null]

#### Return type

[**UserInfo**](#modelsuserinfomd)

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
 **userId** | **Long**| Unique identifier for a user | [default to null]
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
 **userId** | **Long**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssitemd)

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


# Models


<a name="modelsaccessrulemd"></a>

## AccessRule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the access rule | [default to null]
**name** | **String** | Name of the access rule | [default to null]
**description** | **String** | Description of the access rule | [optional] [default to null]
**conditions** | **List** | Conditions that must be met for access | [optional] [default to null]
**actions** | **List** | Actions to take when rule is triggered | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacumd"></a>

## Acu
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfigurationmd"></a>

## AcuConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pollingInterval** | **Integer** | Heartbeat polling interval in seconds | [optional] [default to null]
**timeoutSettings** | **Map** | Various timeout settings | [optional] [default to null]
**securitySettings** | **Map** | Security-related configuration | [optional] [default to null]
**networkSettings** | [**AcuConfiguration_networkSettings**](#modelsacuconfiguration_networksettingsmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfiguration_networksettingsmd"></a>

## AcuConfiguration_networkSettings
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**dhcpEnabled** | **Boolean** | Whether DHCP is enabled | [optional] [default to null]
**staticIp** | **String** | Static IP address if DHCP is disabled | [optional] [default to null]
**subnetMask** | **String** | Subnet mask | [optional] [default to null]
**gateway** | **String** | Gateway IP address | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfomd"></a>

## AcuInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfo_allofmd"></a>

## AcuInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**email** | **String** | The email address to use for authentication | [default to null]
**password** | **String** | The password to provide for authentication | [default to null]
**totpCode** | **String** | Time-based One-Time Password for MFA | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstatemd"></a>

## ConnectionState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allofmd"></a>

## ConnectionState_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allof_1md"></a>

## ConnectionState_allOf_1
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrymd"></a>

## Entry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the entry | [default to null]
**type** | **String** | Type of entry/permission | [default to null]
**name** | **String** | Name of the entry | [optional] [default to null]
**description** | **String** | Description of the entry | [optional] [default to null]
**doorId** | **Long** | Door ID associated with the entry | [optional] [default to null]
**zoneId** | **Long** | Zone ID associated with the entry | [optional] [default to null]
**timeRestrictions** | [**TimeRestriction**](#modelstimerestrictionmd) |  | [optional] [default to null]
**status** | **String** | Current status of the entry | [optional] [default to null]
**createdAt** | **Date** | Timestamp when entry was created | [optional] [default to null]
**expiresAt** | **Date** | Timestamp when entry expires | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupmd"></a>

## Group
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfomd"></a>

## GroupInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfo_allofmd"></a>

## GroupInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportmd"></a>

## Port
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the port | [default to null]
**number** | **Integer** | Port number on the ACU | [default to null]
**type** | **String** | Type of device connected to the port | [default to null]
**name** | **String** | Name assigned to the port | [optional] [default to null]
**description** | **String** | Description of the port | [optional] [default to null]
**status** | **String** | Current status of the port | [optional] [default to null]
**configuration** | [**PortConfiguration**](#modelsportconfigurationmd) |  | [optional] [default to null]
**lastActivity** | **Date** | Timestamp of last activity on the port | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportconfigurationmd"></a>

## PortConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**sensitivity** | **Integer** | Sensitivity level for the port | [optional] [default to null]
**debounceTime** | **Integer** | Debounce time in milliseconds | [optional] [default to null]
**pollingRate** | **Integer** | Polling rate in milliseconds | [optional] [default to null]
**thresholds** | **Map** | Various threshold settings | [optional] [default to null]
**customSettings** | **Map** | Custom configuration settings | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrolemd"></a>

## Role
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the role | [default to null]
**name** | **String** | Name of the role | [default to null]
**description** | **String** | Description of the role | [optional] [default to null]
**permissions** | **List** | List of permissions granted by this role | [optional] [default to null]
**assignedAt** | **Date** | Timestamp when the role was assigned | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssitemd"></a>

## Site
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the site | [default to null]
**name** | **String** | Name of the site | [default to null]
**address** | **String** | Physical address of the site | [optional] [default to null]
**city** | **String** | City where the site is located | [optional] [default to null]
**state** | **String** | State/province where the site is located | [optional] [default to null]
**country** | **String** | Country where the site is located | [optional] [default to null]
**postalCode** | **String** | Postal/zip code of the site | [optional] [default to null]
**timezone** | **String** | Timezone of the site | [optional] [default to null]
**status** | **String** | Current status of the site | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstimerestrictionmd"></a>

## TimeRestriction
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**startTime** | **String** | Start time for access (HH:MM format) | [optional] [default to null]
**endTime** | **String** | End time for access (HH:MM format) | [optional] [default to null]
**daysOfWeek** | **List** | Days of the week when access is allowed | [optional] [default to null]
**validFrom** | **date** | Start date for the time restriction | [optional] [default to null]
**validUntil** | **date** | End date for the time restriction | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusermd"></a>

## User
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfomd"></a>

## UserInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfo_allofmd"></a>

## UserInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-avigilon-alta-access

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AcuApi* | [**getAcu**](#getacu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
*AcuApi* | [**listAcuPorts**](#listacuports) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
*AcuApi* | [**listAcus**](#listacus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization
*GroupApi* | [**getGroup**](#getgroup) | **GET** /organizations/{organizationId}/groups/{groupId} | Retrieves single access group details by group ID
*GroupApi* | [**listGroupEntries**](#listgroupentries) | **GET** /organizations/{organizationId}/groups/{groupId}/entries | Retrieves all entries/permissions for a specific group
*GroupApi* | [**listGroupUsers**](#listgroupusers) | **GET** /organizations/{organizationId}/groups/{groupId}/users | Retrieves all users belonging to a specific group
*GroupApi* | [**listGroups**](#listgroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization
*UserApi* | [**getUser**](#getuser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
*UserApi* | [**listUserRoles**](#listuserroles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
*UserApi* | [**listUserSites**](#listusersites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
*UserApi* | [**listUsers**](#listusers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AccessRule](#modelsaccessrulemd)
 - [Acu](#modelsacumd)
 - [AcuConfiguration](#modelsacuconfigurationmd)
 - [AcuConfiguration_networkSettings](#modelsacuconfiguration_networksettingsmd)
 - [AcuInfo](#modelsacuinfomd)
 - [AcuInfo_allOf](#modelsacuinfo_allofmd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [ConnectionState](#modelsconnectionstatemd)
 - [ConnectionState_allOf](#modelsconnectionstate_allofmd)
 - [ConnectionState_allOf_1](#modelsconnectionstate_allof_1md)
 - [Entry](#modelsentrymd)
 - [Group](#modelsgroupmd)
 - [GroupInfo](#modelsgroupinfomd)
 - [GroupInfo_allOf](#modelsgroupinfo_allofmd)
 - [Port](#modelsportmd)
 - [PortConfiguration](#modelsportconfigurationmd)
 - [Role](#modelsrolemd)
 - [Site](#modelssitemd)
 - [TimeRestriction](#modelstimerestrictionmd)
 - [User](#modelsusermd)
 - [UserInfo](#modelsuserinfomd)
 - [UserInfo_allOf](#modelsuserinfo_allofmd)


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
  - read:acus: Read access to access control units
  - read:user-roles: Read access to user role assignments
  - read:user-sites: Read access to user site associations
  - read:group-users: Read access to group user memberships
  - read:group-entries: Read access to group entries and permissions
  - read:acu-ports: Read access to ACU port information

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Apis](#apis)
  - [AcuApi](#acuapi)
  - [**getAcu**](#getacu)
  - [**listAcuPorts**](#listacuports)
  - [**listAcus**](#listacus)
  - [GroupApi](#groupapi)
  - [**getGroup**](#getgroup)
  - [**listGroupEntries**](#listgroupentries)
  - [**listGroupUsers**](#listgroupusers)
  - [**listGroups**](#listgroups)
  - [UserApi](#userapi)
  - [**getUser**](#getuser)
  - [**listUserRoles**](#listuserroles)
  - [**listUserSites**](#listusersites)
  - [**listUsers**](#listusers)
- [Models](#models)
  - [AccessRule](#accessrule)
    - [Properties](#properties)
  - [Acu](#acu)
    - [Properties](#properties-1)
  - [AcuConfiguration](#acuconfiguration)
    - [Properties](#properties-2)
  - [AcuConfiguration_networkSettings](#acuconfiguration_networksettings)
    - [Properties](#properties-3)
  - [AcuInfo](#acuinfo)
    - [Properties](#properties-4)
  - [AcuInfo_allOf](#acuinfo_allof)
    - [Properties](#properties-5)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-6)
  - [ConnectionState](#connectionstate)
    - [Properties](#properties-7)
  - [ConnectionState_allOf](#connectionstate_allof)
    - [Properties](#properties-8)
  - [ConnectionState_allOf_1](#connectionstate_allof_1)
    - [Properties](#properties-9)
  - [Entry](#entry)
    - [Properties](#properties-10)
  - [Group](#group)
    - [Properties](#properties-11)
  - [GroupInfo](#groupinfo)
    - [Properties](#properties-12)
  - [GroupInfo_allOf](#groupinfo_allof)
    - [Properties](#properties-13)
  - [Port](#port)
    - [Properties](#properties-14)
  - [PortConfiguration](#portconfiguration)
    - [Properties](#properties-15)
  - [Role](#role)
    - [Properties](#properties-16)
  - [Site](#site)
    - [Properties](#properties-17)
  - [TimeRestriction](#timerestriction)
    - [Properties](#properties-18)
  - [User](#user)
    - [Properties](#properties-19)
  - [UserInfo](#userinfo)
    - [Properties](#properties-20)
  - [UserInfo_allOf](#userinfo_allof)
    - [Properties](#properties-21)
- [Documentation for @zerobias-org/module-avigilon-alta-access](#documentation-for-zerobias-orgmodule-avigilon-alta-access)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)
    - [access-token](#access-token)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisacuapimd"></a>

## AcuApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getAcu**](#getAcu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
[**listAcuPorts**](#listAcuPorts) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
[**listAcus**](#listAcus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization


<a name="getAcu"></a>
## **getAcu**
> AcuInfo getAcu(organizationId, acuId)

Retrieves single ACU details by ACU ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]

#### Return type

[**AcuInfo**](#modelsacuinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcuPorts"></a>
## **listAcuPorts**
> List listAcuPorts(organizationId, acuId, pageNumber, pageSize)

Retrieves all ports for a specific ACU

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsportmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcus"></a>
## **listAcus**
> List listAcus(organizationId, pageNumber, pageSize)

Retrieves all Access Control Units (ACUs) in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsacumd)

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
[**listGroups**](#listGroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization


<a name="getGroup"></a>
## **getGroup**
> GroupInfo getGroup(organizationId, groupId)

Retrieves single access group details by group ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **Long**| Unique identifier for a group | [default to null]

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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusermd)

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



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getUser**](#getUser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
[**listUserRoles**](#listUserRoles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
[**listUserSites**](#listUserSites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
[**listUsers**](#listUsers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="getUser"></a>
## **getUser**
> UserInfo getUser(organizationId, userId)

Retrieves single user details by user ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **Long**| Unique identifier for a user | [default to null]

#### Return type

[**UserInfo**](#modelsuserinfomd)

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
 **userId** | **Long**| Unique identifier for a user | [default to null]
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
 **userId** | **Long**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssitemd)

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


# Models


<a name="modelsaccessrulemd"></a>

## AccessRule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the access rule | [default to null]
**name** | **String** | Name of the access rule | [default to null]
**description** | **String** | Description of the access rule | [optional] [default to null]
**conditions** | **List** | Conditions that must be met for access | [optional] [default to null]
**actions** | **List** | Actions to take when rule is triggered | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacumd"></a>

## Acu
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfigurationmd"></a>

## AcuConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pollingInterval** | **Integer** | Heartbeat polling interval in seconds | [optional] [default to null]
**timeoutSettings** | **Map** | Various timeout settings | [optional] [default to null]
**securitySettings** | **Map** | Security-related configuration | [optional] [default to null]
**networkSettings** | [**AcuConfiguration_networkSettings**](#modelsacuconfiguration_networksettingsmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfiguration_networksettingsmd"></a>

## AcuConfiguration_networkSettings
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**dhcpEnabled** | **Boolean** | Whether DHCP is enabled | [optional] [default to null]
**staticIp** | **String** | Static IP address if DHCP is disabled | [optional] [default to null]
**subnetMask** | **String** | Subnet mask | [optional] [default to null]
**gateway** | **String** | Gateway IP address | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfomd"></a>

## AcuInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfo_allofmd"></a>

## AcuInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**email** | **String** | The email address to use for authentication | [default to null]
**password** | **String** | The password to provide for authentication | [default to null]
**totpCode** | **String** | Time-based One-Time Password for MFA | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstatemd"></a>

## ConnectionState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allofmd"></a>

## ConnectionState_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allof_1md"></a>

## ConnectionState_allOf_1
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrymd"></a>

## Entry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the entry | [default to null]
**type** | **String** | Type of entry/permission | [default to null]
**name** | **String** | Name of the entry | [optional] [default to null]
**description** | **String** | Description of the entry | [optional] [default to null]
**doorId** | **Long** | Door ID associated with the entry | [optional] [default to null]
**zoneId** | **Long** | Zone ID associated with the entry | [optional] [default to null]
**timeRestrictions** | [**TimeRestriction**](#modelstimerestrictionmd) |  | [optional] [default to null]
**status** | **String** | Current status of the entry | [optional] [default to null]
**createdAt** | **Date** | Timestamp when entry was created | [optional] [default to null]
**expiresAt** | **Date** | Timestamp when entry expires | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupmd"></a>

## Group
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfomd"></a>

## GroupInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfo_allofmd"></a>

## GroupInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportmd"></a>

## Port
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the port | [default to null]
**number** | **Integer** | Port number on the ACU | [default to null]
**type** | **String** | Type of device connected to the port | [default to null]
**name** | **String** | Name assigned to the port | [optional] [default to null]
**description** | **String** | Description of the port | [optional] [default to null]
**status** | **String** | Current status of the port | [optional] [default to null]
**configuration** | [**PortConfiguration**](#modelsportconfigurationmd) |  | [optional] [default to null]
**lastActivity** | **Date** | Timestamp of last activity on the port | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportconfigurationmd"></a>

## PortConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**sensitivity** | **Integer** | Sensitivity level for the port | [optional] [default to null]
**debounceTime** | **Integer** | Debounce time in milliseconds | [optional] [default to null]
**pollingRate** | **Integer** | Polling rate in milliseconds | [optional] [default to null]
**thresholds** | **Map** | Various threshold settings | [optional] [default to null]
**customSettings** | **Map** | Custom configuration settings | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrolemd"></a>

## Role
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the role | [default to null]
**name** | **String** | Name of the role | [default to null]
**description** | **String** | Description of the role | [optional] [default to null]
**permissions** | **List** | List of permissions granted by this role | [optional] [default to null]
**assignedAt** | **Date** | Timestamp when the role was assigned | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssitemd"></a>

## Site
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the site | [default to null]
**name** | **String** | Name of the site | [default to null]
**address** | **String** | Physical address of the site | [optional] [default to null]
**city** | **String** | City where the site is located | [optional] [default to null]
**state** | **String** | State/province where the site is located | [optional] [default to null]
**country** | **String** | Country where the site is located | [optional] [default to null]
**postalCode** | **String** | Postal/zip code of the site | [optional] [default to null]
**timezone** | **String** | Timezone of the site | [optional] [default to null]
**status** | **String** | Current status of the site | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstimerestrictionmd"></a>

## TimeRestriction
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**startTime** | **String** | Start time for access (HH:MM format) | [optional] [default to null]
**endTime** | **String** | End time for access (HH:MM format) | [optional] [default to null]
**daysOfWeek** | **List** | Days of the week when access is allowed | [optional] [default to null]
**validFrom** | **date** | Start date for the time restriction | [optional] [default to null]
**validUntil** | **date** | End date for the time restriction | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusermd"></a>

## User
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfomd"></a>

## UserInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfo_allofmd"></a>

## UserInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-avigilon-alta-access

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AcuApi* | [**getAcu**](#getacu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
*AcuApi* | [**listAcuPorts**](#listacuports) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
*AcuApi* | [**listAcus**](#listacus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization
*GroupApi* | [**getGroup**](#getgroup) | **GET** /organizations/{organizationId}/groups/{groupId} | Retrieves single access group details by group ID
*GroupApi* | [**listGroupEntries**](#listgroupentries) | **GET** /organizations/{organizationId}/groups/{groupId}/entries | Retrieves all entries/permissions for a specific group
*GroupApi* | [**listGroupUsers**](#listgroupusers) | **GET** /organizations/{organizationId}/groups/{groupId}/users | Retrieves all users belonging to a specific group
*GroupApi* | [**listGroups**](#listgroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization
*UserApi* | [**getUser**](#getuser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
*UserApi* | [**listUserRoles**](#listuserroles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
*UserApi* | [**listUserSites**](#listusersites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
*UserApi* | [**listUsers**](#listusers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AccessRule](#modelsaccessrulemd)
 - [Acu](#modelsacumd)
 - [AcuConfiguration](#modelsacuconfigurationmd)
 - [AcuConfiguration_networkSettings](#modelsacuconfiguration_networksettingsmd)
 - [AcuInfo](#modelsacuinfomd)
 - [AcuInfo_allOf](#modelsacuinfo_allofmd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [ConnectionState](#modelsconnectionstatemd)
 - [ConnectionState_allOf](#modelsconnectionstate_allofmd)
 - [ConnectionState_allOf_1](#modelsconnectionstate_allof_1md)
 - [Entry](#modelsentrymd)
 - [Group](#modelsgroupmd)
 - [GroupInfo](#modelsgroupinfomd)
 - [GroupInfo_allOf](#modelsgroupinfo_allofmd)
 - [Port](#modelsportmd)
 - [PortConfiguration](#modelsportconfigurationmd)
 - [Role](#modelsrolemd)
 - [Site](#modelssitemd)
 - [TimeRestriction](#modelstimerestrictionmd)
 - [User](#modelsusermd)
 - [UserInfo](#modelsuserinfomd)
 - [UserInfo_allOf](#modelsuserinfo_allofmd)


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
  - read:acus: Read access to access control units
  - read:user-roles: Read access to user role assignments
  - read:user-sites: Read access to user site associations
  - read:group-users: Read access to group user memberships
  - read:group-entries: Read access to group entries and permissions
  - read:acu-ports: Read access to ACU port information

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Apis](#apis)
  - [AcuApi](#acuapi)
  - [**getAcu**](#getacu)
  - [**listAcuPorts**](#listacuports)
  - [**listAcus**](#listacus)
  - [GroupApi](#groupapi)
  - [**getGroup**](#getgroup)
  - [**listGroupEntries**](#listgroupentries)
  - [**listGroupUsers**](#listgroupusers)
  - [**listGroups**](#listgroups)
  - [UserApi](#userapi)
  - [**getUser**](#getuser)
  - [**listUserRoles**](#listuserroles)
  - [**listUserSites**](#listusersites)
  - [**listUsers**](#listusers)
- [Models](#models)
  - [AccessRule](#accessrule)
    - [Properties](#properties)
  - [Acu](#acu)
    - [Properties](#properties-1)
  - [AcuConfiguration](#acuconfiguration)
    - [Properties](#properties-2)
  - [AcuConfiguration_networkSettings](#acuconfiguration_networksettings)
    - [Properties](#properties-3)
  - [AcuInfo](#acuinfo)
    - [Properties](#properties-4)
  - [AcuInfo_allOf](#acuinfo_allof)
    - [Properties](#properties-5)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-6)
  - [ConnectionState](#connectionstate)
    - [Properties](#properties-7)
  - [ConnectionState_allOf](#connectionstate_allof)
    - [Properties](#properties-8)
  - [ConnectionState_allOf_1](#connectionstate_allof_1)
    - [Properties](#properties-9)
  - [Entry](#entry)
    - [Properties](#properties-10)
  - [Group](#group)
    - [Properties](#properties-11)
  - [GroupInfo](#groupinfo)
    - [Properties](#properties-12)
  - [GroupInfo_allOf](#groupinfo_allof)
    - [Properties](#properties-13)
  - [Port](#port)
    - [Properties](#properties-14)
  - [PortConfiguration](#portconfiguration)
    - [Properties](#properties-15)
  - [Role](#role)
    - [Properties](#properties-16)
  - [Site](#site)
    - [Properties](#properties-17)
  - [TimeRestriction](#timerestriction)
    - [Properties](#properties-18)
  - [User](#user)
    - [Properties](#properties-19)
  - [UserInfo](#userinfo)
    - [Properties](#properties-20)
  - [UserInfo_allOf](#userinfo_allof)
    - [Properties](#properties-21)
- [Documentation for @zerobias-org/module-avigilon-alta-access](#documentation-for-zerobias-orgmodule-avigilon-alta-access)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)
    - [access-token](#access-token)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisacuapimd"></a>

## AcuApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getAcu**](#getAcu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
[**listAcuPorts**](#listAcuPorts) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
[**listAcus**](#listAcus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization


<a name="getAcu"></a>
## **getAcu**
> AcuInfo getAcu(organizationId, acuId)

Retrieves single ACU details by ACU ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]

#### Return type

[**AcuInfo**](#modelsacuinfomd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcuPorts"></a>
## **listAcuPorts**
> List listAcuPorts(organizationId, acuId, pageNumber, pageSize)

Retrieves all ports for a specific ACU

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **acuId** | **Long**| Unique identifier for an access control unit (ACU) | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsportmd)

#### Authorization

[access-token](#access-token)

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="listAcus"></a>
## **listAcus**
> List listAcus(organizationId, pageNumber, pageSize)

Retrieves all Access Control Units (ACUs) in the organization

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsacumd)

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
[**listGroups**](#listGroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization


<a name="getGroup"></a>
## **getGroup**
> GroupInfo getGroup(organizationId, groupId)

Retrieves single access group details by group ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **groupId** | **Long**| Unique identifier for a group | [default to null]

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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
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
 **groupId** | **Long**| Unique identifier for a group | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelsusermd)

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



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**getUser**](#getUser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
[**listUserRoles**](#listUserRoles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
[**listUserSites**](#listUserSites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
[**listUsers**](#listUsers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="getUser"></a>
## **getUser**
> UserInfo getUser(organizationId, userId)

Retrieves single user details by user ID

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **organizationId** | **String**| Unique identifier for the organization | [default to null]
 **userId** | **Long**| Unique identifier for a user | [default to null]

#### Return type

[**UserInfo**](#modelsuserinfomd)

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
 **userId** | **Long**| Unique identifier for a user | [default to null]
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
 **userId** | **Long**| Unique identifier for a user | [default to null]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]

#### Return type

[**List**](#modelssitemd)

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


# Models


<a name="modelsaccessrulemd"></a>

## AccessRule
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the access rule | [default to null]
**name** | **String** | Name of the access rule | [default to null]
**description** | **String** | Description of the access rule | [optional] [default to null]
**conditions** | **List** | Conditions that must be met for access | [optional] [default to null]
**actions** | **List** | Actions to take when rule is triggered | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacumd"></a>

## Acu
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfigurationmd"></a>

## AcuConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pollingInterval** | **Integer** | Heartbeat polling interval in seconds | [optional] [default to null]
**timeoutSettings** | **Map** | Various timeout settings | [optional] [default to null]
**securitySettings** | **Map** | Security-related configuration | [optional] [default to null]
**networkSettings** | [**AcuConfiguration_networkSettings**](#modelsacuconfiguration_networksettingsmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuconfiguration_networksettingsmd"></a>

## AcuConfiguration_networkSettings
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**dhcpEnabled** | **Boolean** | Whether DHCP is enabled | [optional] [default to null]
**staticIp** | **String** | Static IP address if DHCP is disabled | [optional] [default to null]
**subnetMask** | **String** | Subnet mask | [optional] [default to null]
**gateway** | **String** | Gateway IP address | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfomd"></a>

## AcuInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the ACU | [default to null]
**name** | **String** | Name of the ACU | [default to null]
**status** | **String** | Current status of the ACU | [default to null]
**modelNumber** | **String** | ACU model number | [optional] [default to null]
**serialNumber** | **String** | ACU serial number | [optional] [default to null]
**firmwareVersion** | **String** | Current firmware version | [optional] [default to null]
**ipAddress** | **String** | IP address of the ACU | [optional] [default to null]
**macAddress** | **String** | MAC address of the ACU | [optional] [default to null]
**location** | **String** | Physical location of the ACU | [optional] [default to null]
**createdAt** | **Date** | Timestamp when ACU was registered | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when ACU was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsacuinfo_allofmd"></a>

## AcuInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the ACU belongs to | [optional] [default to null]
**siteId** | **BigDecimal** | Site ID where the ACU is located | [optional] [default to null]
**portCount** | **Integer** | Number of ports on the ACU | [optional] [default to null]
**lastHeartbeat** | **Date** | Timestamp of last heartbeat from the ACU | [optional] [default to null]
**configuration** | [**AcuConfiguration**](#modelsacuconfigurationmd) |  | [optional] [default to null]
**customFields** | **Map** | Custom field values for the ACU | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**email** | **String** | The email address to use for authentication | [default to null]
**password** | **String** | The password to provide for authentication | [default to null]
**totpCode** | **String** | Time-based One-Time Password for MFA | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstatemd"></a>

## ConnectionState
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allofmd"></a>

## ConnectionState_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**expiresIn** | **BigDecimal** | Number of seconds after which the access token becomes invalid | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionstate_allof_1md"></a>

## ConnectionState_allOf_1
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**accessToken** | **String** | The access token returned by the Auth client | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsentrymd"></a>

## Entry
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the entry | [default to null]
**type** | **String** | Type of entry/permission | [default to null]
**name** | **String** | Name of the entry | [optional] [default to null]
**description** | **String** | Description of the entry | [optional] [default to null]
**doorId** | **Long** | Door ID associated with the entry | [optional] [default to null]
**zoneId** | **Long** | Zone ID associated with the entry | [optional] [default to null]
**timeRestrictions** | [**TimeRestriction**](#modelstimerestrictionmd) |  | [optional] [default to null]
**status** | **String** | Current status of the entry | [optional] [default to null]
**createdAt** | **Date** | Timestamp when entry was created | [optional] [default to null]
**expiresAt** | **Date** | Timestamp when entry expires | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupmd"></a>

## Group
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfomd"></a>

## GroupInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the group | [default to null]
**name** | **String** | Name of the group | [default to null]
**type** | **String** | Type of group | [default to null]
**description** | **String** | Description of the group | [optional] [default to null]
**userCount** | **Integer** | Number of users in the group | [optional] [default to null]
**createdAt** | **Date** | Timestamp when group was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when group was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsgroupinfo_allofmd"></a>

## GroupInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the group belongs to | [optional] [default to null]
**parentGroupId** | **Long** | Parent group ID if this is a child group | [optional] [default to null]
**permissions** | **List** | List of permissions assigned to the group | [optional] [default to null]
**accessRules** | [**List**](#modelsaccessrulemd) | Access rules for the group | [optional] [default to null]
**customFields** | **Map** | Custom field values for the group | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportmd"></a>

## Port
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the port | [default to null]
**number** | **Integer** | Port number on the ACU | [default to null]
**type** | **String** | Type of device connected to the port | [default to null]
**name** | **String** | Name assigned to the port | [optional] [default to null]
**description** | **String** | Description of the port | [optional] [default to null]
**status** | **String** | Current status of the port | [optional] [default to null]
**configuration** | [**PortConfiguration**](#modelsportconfigurationmd) |  | [optional] [default to null]
**lastActivity** | **Date** | Timestamp of last activity on the port | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsportconfigurationmd"></a>

## PortConfiguration
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**sensitivity** | **Integer** | Sensitivity level for the port | [optional] [default to null]
**debounceTime** | **Integer** | Debounce time in milliseconds | [optional] [default to null]
**pollingRate** | **Integer** | Polling rate in milliseconds | [optional] [default to null]
**thresholds** | **Map** | Various threshold settings | [optional] [default to null]
**customSettings** | **Map** | Custom configuration settings | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsrolemd"></a>

## Role
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the role | [default to null]
**name** | **String** | Name of the role | [default to null]
**description** | **String** | Description of the role | [optional] [default to null]
**permissions** | **List** | List of permissions granted by this role | [optional] [default to null]
**assignedAt** | **Date** | Timestamp when the role was assigned | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelssitemd"></a>

## Site
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the site | [default to null]
**name** | **String** | Name of the site | [default to null]
**address** | **String** | Physical address of the site | [optional] [default to null]
**city** | **String** | City where the site is located | [optional] [default to null]
**state** | **String** | State/province where the site is located | [optional] [default to null]
**country** | **String** | Country where the site is located | [optional] [default to null]
**postalCode** | **String** | Postal/zip code of the site | [optional] [default to null]
**timezone** | **String** | Timezone of the site | [optional] [default to null]
**status** | **String** | Current status of the site | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstimerestrictionmd"></a>

## TimeRestriction
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**startTime** | **String** | Start time for access (HH:MM format) | [optional] [default to null]
**endTime** | **String** | End time for access (HH:MM format) | [optional] [default to null]
**daysOfWeek** | **List** | Days of the week when access is allowed | [optional] [default to null]
**validFrom** | **date** | Start date for the time restriction | [optional] [default to null]
**validUntil** | **date** | End date for the time restriction | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsusermd"></a>

## User
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfomd"></a>

## UserInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **Long** | Unique identifier for the user | [default to null]
**email** | **String** | User&#39;s email address | [default to null]
**firstName** | **String** | User&#39;s first name | [default to null]
**lastName** | **String** | User&#39;s last name | [default to null]
**phoneNumber** | **String** | User&#39;s phone number | [optional] [default to null]
**status** | **String** | User&#39;s current status | [optional] [default to null]
**createdAt** | **Date** | Timestamp when user was created | [optional] [default to null]
**updatedAt** | **Date** | Timestamp when user was last updated | [optional] [default to null]
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsuserinfo_allofmd"></a>

## UserInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**organizationId** | **BigDecimal** | Organization ID the user belongs to | [optional] [default to null]
**avatarUrl** | **String** | URL to user&#39;s avatar image | [optional] [default to null]
**lastLoginAt** | **Date** | Timestamp of user&#39;s last login | [optional] [default to null]
**permissions** | **List** | List of user permissions | [optional] [default to null]
**customFields** | **Map** | Custom field values for the user | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-avigilon-alta-access

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AcuApi* | [**getAcu**](#getacu) | **GET** /organizations/{organizationId}/acus/{acuId} | Retrieves single ACU details by ACU ID
*AcuApi* | [**listAcuPorts**](#listacuports) | **GET** /organizations/{organizationId}/acus/{acuId}/ports | Retrieves all ports for a specific ACU
*AcuApi* | [**listAcus**](#listacus) | **GET** /organizations/{organizationId}/acus | Retrieves all Access Control Units (ACUs) in the organization
*GroupApi* | [**getGroup**](#getgroup) | **GET** /organizations/{organizationId}/groups/{groupId} | Retrieves single access group details by group ID
*GroupApi* | [**listGroupEntries**](#listgroupentries) | **GET** /organizations/{organizationId}/groups/{groupId}/entries | Retrieves all entries/permissions for a specific group
*GroupApi* | [**listGroupUsers**](#listgroupusers) | **GET** /organizations/{organizationId}/groups/{groupId}/users | Retrieves all users belonging to a specific group
*GroupApi* | [**listGroups**](#listgroups) | **GET** /organizations/{organizationId}/groups | Retrieves all access groups in the organization
*UserApi* | [**getUser**](#getuser) | **GET** /organizations/{organizationId}/users/{userId} | Retrieves single user details by user ID
*UserApi* | [**listUserRoles**](#listuserroles) | **GET** /organizations/{organizationId}/users/{userId}/roles | Retrieves all roles assigned to a specific user
*UserApi* | [**listUserSites**](#listusersites) | **GET** /organizations/{organizationId}/users/{userId}/sites | Retrieves all sites associated with a specific user
*UserApi* | [**listUsers**](#listusers) | **GET** /organizations/{organizationId}/users | Retrieves all users in the organization


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AccessRule](#modelsaccessrulemd)
 - [Acu](#modelsacumd)
 - [AcuConfiguration](#modelsacuconfigurationmd)
 - [AcuConfiguration_networkSettings](#modelsacuconfiguration_networksettingsmd)
 - [AcuInfo](#modelsacuinfomd)
 - [AcuInfo_allOf](#modelsacuinfo_allofmd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [ConnectionState](#modelsconnectionstatemd)
 - [ConnectionState_allOf](#modelsconnectionstate_allofmd)
 - [ConnectionState_allOf_1](#modelsconnectionstate_allof_1md)
 - [Entry](#modelsentrymd)
 - [Group](#modelsgroupmd)
 - [GroupInfo](#modelsgroupinfomd)
 - [GroupInfo_allOf](#modelsgroupinfo_allofmd)
 - [Port](#modelsportmd)
 - [PortConfiguration](#modelsportconfigurationmd)
 - [Role](#modelsrolemd)
 - [Site](#modelssitemd)
 - [TimeRestriction](#modelstimerestrictionmd)
 - [User](#modelsusermd)
 - [UserInfo](#modelsuserinfomd)
 - [UserInfo_allOf](#modelsuserinfo_allofmd)


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
  - read:acus: Read access to access control units
  - read:user-roles: Read access to user role assignments
  - read:user-sites: Read access to user site associations
  - read:group-users: Read access to group user memberships
  - read:group-entries: Read access to group entries and permissions
  - read:acu-ports: Read access to ACU port information

