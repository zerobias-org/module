# readyplayerme-readyplayerme Hub Module

## Authentication and authorization

## Usage

# Test
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Apis](#apis)
  - [ApplicationApi](#applicationapi)
  - [**listApplicationAssets**](#listapplicationassets)
  - [AssetApi](#assetapi)
  - [**addAssetToApplication**](#addassettoapplication)
  - [**createAsset**](#createasset)
  - [**lockAssetForUser**](#lockassetforuser)
  - [**removeAssetFromApplication**](#removeassetfromapplication)
  - [**unlockAssetForUser**](#unlockassetforuser)
  - [**updateAsset**](#updateasset)
  - [Avatar2dApi](#avatar2dapi)
  - [**get2DAvatarRender**](#get2davatarrender)
  - [Avatar3dApi](#avatar3dapi)
  - [**get3DAvatarModel**](#get3davatarmodel)
  - [AvatarApi](#avatarapi)
  - [**equipAssetToAvatar**](#equipassettoavatar)
  - [**getAvatarMetadata**](#getavatarmetadata)
  - [**unequipAssetFromAvatar**](#unequipassetfromavatar)
  - [UserApi](#userapi)
  - [**createUser**](#createuser)
  - [**getUserToken**](#getusertoken)
- [Models](#models)
  - [AddAssetToApplicationRequest](#addassettoapplicationrequest)
    - [Properties](#properties)
  - [AddAssetToApplicationRequest_data](#addassettoapplicationrequest_data)
    - [Properties](#properties-1)
  - [ApplicationAssetRelation](#applicationassetrelation)
    - [Properties](#properties-2)
  - [Asset](#asset)
    - [Properties](#properties-3)
  - [AssetBase](#assetbase)
    - [Properties](#properties-4)
  - [AssetInfo](#assetinfo)
    - [Properties](#properties-5)
  - [AssetInfoResponse](#assetinforesponse)
    - [Properties](#properties-6)
  - [AssetInfo_allOf](#assetinfo_allof)
    - [Properties](#properties-7)
  - [AssetResponse](#assetresponse)
    - [Properties](#properties-8)
  - [Asset_allOf](#asset_allof)
    - [Properties](#properties-9)
  - [AvatarMetadata](#avatarmetadata)
    - [Properties](#properties-10)
  - [ConnectionProfile](#connectionprofile)
    - [Properties](#properties-11)
  - [CreateAssetRequest](#createassetrequest)
    - [Properties](#properties-12)
  - [CreateAssetRequest_data](#createassetrequest_data)
    - [Properties](#properties-13)
  - [CreateAssetRequest_data_applications](#createassetrequest_data_applications)
    - [Properties](#properties-14)
  - [CreateUserRequest](#createuserrequest)
    - [Properties](#properties-15)
  - [CreateUserRequest_data](#createuserrequest_data)
    - [Properties](#properties-16)
  - [CreateUserResponse](#createuserresponse)
    - [Properties](#properties-17)
  - [CreateUserResponse_data](#createuserresponse_data)
    - [Properties](#properties-18)
  - [EquipAssetRequest](#equipassetrequest)
    - [Properties](#properties-19)
  - [EquipAssetRequest_data](#equipassetrequest_data)
    - [Properties](#properties-20)
  - [LockAssetRequest](#lockassetrequest)
    - [Properties](#properties-21)
  - [LockAssetRequest_data](#lockassetrequest_data)
    - [Properties](#properties-22)
  - [RemoveAssetFromApplicationRequest](#removeassetfromapplicationrequest)
    - [Properties](#properties-23)
  - [RemoveAssetFromApplicationRequest_data](#removeassetfromapplicationrequest_data)
    - [Properties](#properties-24)
  - [TokenResponse](#tokenresponse)
    - [Properties](#properties-25)
  - [TokenResponse_data](#tokenresponse_data)
    - [Properties](#properties-26)
  - [UnequipAssetRequest](#unequipassetrequest)
    - [Properties](#properties-27)
  - [UnequipAssetRequest_data](#unequipassetrequest_data)
    - [Properties](#properties-28)
  - [UnlockAssetRequest](#unlockassetrequest)
    - [Properties](#properties-29)
  - [UnlockAssetRequest_data](#unlockassetrequest_data)
    - [Properties](#properties-30)
  - [UpdateAssetRequest](#updateassetrequest)
    - [Properties](#properties-31)
  - [UpdateAssetRequest_data](#updateassetrequest_data)
    - [Properties](#properties-32)
- [Documentation for @zerobias-org/module-readyplayerme-readyplayerme](#documentation-for-zerobias-orgmodule-readyplayerme-readyplayerme)
  - [Documentation for API Endpoints](#documentation-for-api-endpoints)
  - [Documentation for Models](#documentation-for-models)
  - [Documentation for Authorization](#documentation-for-authorization)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Apis


<a name="apisapplicationapimd"></a>

## ApplicationApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**listApplicationAssets**](#listApplicationAssets) | **GET** /applications/{appId}/assets | List Application Assets


<a name="listApplicationAssets"></a>
## **listApplicationAssets**
> List listApplicationAssets(appId, sortBy, sortDir, pageNumber, pageSize, name, organizationId, type, gender, ids, applicationIds)

List Application Assets

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **appId** | **String**| The id of the application | [default to null]
 **sortBy** | [**List**](../Models/String.md)| Array of columns to sort by, in order | [optional] [default to null]
 **sortDir** | [**List**](../Models/String.md)| Array of directions to sort by. Must correspond with &#x60;sortBy&#x60; | [optional] [default to null] [enum: asc, desc]
 **pageNumber** | **Integer**| The requested page. This value is 1-indexed. | [optional] [default to 1]
 **pageSize** | **Integer**| The number of items in each page of data. | [optional] [default to 50]
 **name** | **String**| Filter to find assets by their name. Looks for partial matches | [optional] [default to null]
 **organizationId** | **String**| Filter to find assets by organizationId | [optional] [default to null]
 **type** | [**List**](../Models/String.md)| Filter to find assets by their type. Supports multiple values | [optional] [default to null] [enum: outfit, top, shirt, bottom, beard, eye, eyebrows, eyeshape, facemask, faceshape, facewear, footwear, glasses, hair, headwear, lipshape, noseshape, costume]
 **gender** | [**List**](../Models/String.md)| Filter to find assets by their gender. Supports multiple values | [optional] [default to null] [enum: male, female, neutral]
 **ids** | [**List**](../Models/String.md)| Filter to find assets by Ids | [optional] [default to null]
 **applicationIds** | [**List**](../Models/String.md)| Filter to find assets that are available in specific applications | [optional] [default to null]

#### Return type

[**List**](#modelsassetinfomd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json



<a name="apisassetapimd"></a>

## AssetApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**addAssetToApplication**](#addAssetToApplication) | **POST** /assets/{assetId}/application | Add Asset to Application
[**createAsset**](#createAsset) | **POST** /assets | Create Asset
[**lockAssetForUser**](#lockAssetForUser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
[**removeAssetFromApplication**](#removeAssetFromApplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
[**unlockAssetForUser**](#unlockAssetForUser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
[**updateAsset**](#updateAsset) | **PATCH** /assets/{assetId} | Update Asset


<a name="addAssetToApplication"></a>
## **addAssetToApplication**
> AssetResponse addAssetToApplication(assetId, AddAssetToApplicationRequest)

Add Asset to Application

    Use this endpoint to add an asset to an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **AddAssetToApplicationRequest** | [**AddAssetToApplicationRequest**](#modelsaddassettoapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="createAsset"></a>
## **createAsset**
> AssetInfoResponse createAsset(CreateAssetRequest)

Create Asset

    Use this endpoint to create a new asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateAssetRequest** | [**CreateAssetRequest**](#modelscreateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="lockAssetForUser"></a>
## **lockAssetForUser**
> lockAssetForUser(assetId, LockAssetRequest)

Lock asset for a user

    Use this endpoint for locking an unlocked asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **LockAssetRequest** | [**LockAssetRequest**](#modelslockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="removeAssetFromApplication"></a>
## **removeAssetFromApplication**
> AssetResponse removeAssetFromApplication(assetId, RemoveAssetFromApplicationRequest)

Remove Asset from Application

    Use this endpoint to remove an asset from an application

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **RemoveAssetFromApplicationRequest** | [**RemoveAssetFromApplicationRequest**](#modelsremoveassetfromapplicationrequestmd)|  |

#### Return type

[**AssetResponse**](#modelsassetresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="unlockAssetForUser"></a>
## **unlockAssetForUser**
> unlockAssetForUser(assetId, UnlockAssetRequest)

Unlock asset for a user

    Use this endpoint for unlocking an asset for a user

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UnlockAssetRequest** | [**UnlockAssetRequest**](#modelsunlockassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="updateAsset"></a>
## **updateAsset**
> AssetInfoResponse updateAsset(assetId, UpdateAssetRequest)

Update Asset

    Use this endpoint to update an asset

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **assetId** | **String**| The id of the asset | [default to null]
 **UpdateAssetRequest** | [**UpdateAssetRequest**](#modelsupdateassetrequestmd)|  |

#### Return type

[**AssetInfoResponse**](#modelsassetinforesponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json



<a name="apisavatar2dapimd"></a>

## Avatar2dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get2DAvatarRender**](#get2DAvatarRender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar


<a name="get2DAvatarRender"></a>
## **get2DAvatarRender**
> File get2DAvatarRender(avatarId, expression, pose, blendShapes, camera, background, quality, size, uat, cacheControl)

Get 2D Render of an Avatar

    Get a 2D render of an avatar for stickers or profile pictures

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **expression** | **String**| Avatar facial expression | [optional] [default to null] [enum: happy, lol, sad, scared, rage]
 **pose** | **String**| Avatar pose | [optional] [default to null] [enum: power-stance, relaxed, standing, thumbs-up]
 **blendShapes** | **String**| Map of 3D meshes to their blend shapes | [optional] [default to null]
 **camera** | **String**| Camera preset | [optional] [default to portrait] [enum: portrait, fullbody, fit]
 **background** | **String**| Background color value in RGB format | [optional] [default to null]
 **quality** | **Integer**| Image compression quality for lossy formats like jpg | [optional] [default to null]
 **size** | **Integer**| Image width and height in pixels | [optional] [default to null]
 **uat** | **Date**| User Avatar Timestamp | [optional] [default to null]
 **cacheControl** | **Boolean**| Uses custom Cache-Control header | [optional] [default to null]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: image/png, image/jpeg



<a name="apisavatar3dapimd"></a>

## Avatar3dApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**get3DAvatarModel**](#get3DAvatarModel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file


<a name="get3DAvatarModel"></a>
## **get3DAvatarModel**
> File get3DAvatarModel(avatarId, quality, textureSizeLimit, textureQuality, textureAtlas, textureChannels, morphTargets, useDracoMeshCompression, useQuantizeMeshOptCompression, pose, useHands, textureFormat, lod)

Get a 3D avatar GLB file

    Get a 3D avatar GLB model with desired performance and configuration settings

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar | [default to null]
 **quality** | **String**| Use quality presets to combine performance related parameters | [optional] [default to null] [enum: low, medium, high]
 **textureSizeLimit** | **Integer**| Sets the upper limit for texture resolution in pixels of any texture in the avatar | [optional] [default to 1024]
 **textureQuality** | **String**| Set the quality for textures on the Avatar by choosing from one of the presets | [optional] [default to medium] [enum: low, medium, high]
 **textureAtlas** | **String**| Generates a texture atlas of the desired resolution. The operation merges all meshes and splits opaque objects from transparent ones resulting in a maximum of 2 draw calls | [optional] [default to none] [enum: none, 256, 512, 1024]
 **textureChannels** | **String**| Define which textureChannels should be included in the .glb. It can be a comma-separated combination of values | [optional] [default to baseColor,normal,metallicRoughness,emissive,occlusion]
 **morphTargets** | **String**| Comma-separated list of individual morph targets or morph target standard groups to include on the avatar | [optional] [default to Default]
 **useDracoMeshCompression** | **Boolean**| Reduces file size by compressing output avatars with Draco mesh compression. More effective on complex meshes | [optional] [default to false]
 **useQuantizeMeshOptCompression** | **Boolean**| Reduces file size by quantizing vertex attributes and compressing output avatars with Mesh Optimization compression. More effective on meshes with morph targets | [optional] [default to false]
 **pose** | **String**| Defines the pose for a full-body avatar | [optional] [default to A] [enum: A, T]
 **useHands** | **Boolean**| Toggles hands for half-body VR avatars - &#39;Include hands with half-body VR avatars (default)&#39; - &#39;Do not include hands with half-body VR avatars&#39; | [optional] [default to true]
 **textureFormat** | **String**| Reduce avatar file size by formatting all textures into the specified format | [optional] [default to null] [enum: webp, jpeg, png]
 **lod** | **Integer**| Control the triangle count of the entire avatar - &#39;No triangle count reduction (default)&#39; - &#39;Retain 50% of the original triangle count&#39; - &#39;Retain 25% of the original triangle count&#39; | [optional] [default to 0]

#### Return type

**File**

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: model/gltf-binary



<a name="apisavatarapimd"></a>

## AvatarApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**equipAssetToAvatar**](#equipAssetToAvatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
[**getAvatarMetadata**](#getAvatarMetadata) | **GET** /avatars/{avatarId} | Get Metadata
[**unequipAssetFromAvatar**](#unequipAssetFromAvatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset


<a name="equipAssetToAvatar"></a>
## **equipAssetToAvatar**
> equipAssetToAvatar(avatarId, EquipAssetRequest)

Equip an asset

    Use this endpoint to equip an asset to a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **EquipAssetRequest** | [**EquipAssetRequest**](#modelsequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

<a name="getAvatarMetadata"></a>
## **getAvatarMetadata**
> AvatarMetadata getAvatarMetadata(avatarId)

Get Metadata

    Get the metadata of an avatar in JSON format

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| ID of an avatar with .json ending | [default to null]

#### Return type

[**AvatarMetadata**](#modelsavatarmetadatamd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

<a name="unequipAssetFromAvatar"></a>
## **unequipAssetFromAvatar**
> unequipAssetFromAvatar(avatarId, UnequipAssetRequest)

Unequip an asset

    Use this endpoint to unequip an asset from a user&#39;s avatar

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **avatarId** | **String**| The id of the avatar | [default to null]
 **UnequipAssetRequest** | [**UnequipAssetRequest**](#modelsunequipassetrequestmd)|  |

#### Return type

null (empty response body)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined



<a name="apisuserapimd"></a>

## UserApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**createUser**](#createUser) | **POST** /users | Create a Ready Player Me Guest User
[**getUserToken**](#getUserToken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="createUser"></a>
## **createUser**
> CreateUserResponse createUser(CreateUserRequest)

Create a Ready Player Me Guest User

    Create a guest user account for your user.

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **CreateUserRequest** | [**CreateUserRequest**](#modelscreateuserrequestmd)|  |

#### Return type

[**CreateUserResponse**](#modelscreateuserresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

<a name="getUserToken"></a>
## **getUserToken**
> TokenResponse getUserToken(userId, partner)

Request a token for iFrame session restoration

#### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **userId** | **String**| User ID you want to get the access token for. Note, that this user needs to authorize your app first. | [default to null]
 **partner** | **String**| Your partner name / subdomain. | [default to null]

#### Return type

[**TokenResponse**](#modelstokenresponsemd)

#### Authorization

No authorization required

#### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json


# Models


<a name="modelsaddassettoapplicationrequestmd"></a>

## AddAssetToApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AddAssetToApplicationRequest_data**](#modelsaddassettoapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsaddassettoapplicationrequest_datamd"></a>

## AddAssetToApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to add the asset to | [default to null]
**isVisibleInEditor** | **Boolean** | Defines if asset is visible in this application&#39;s avatar editor | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsapplicationassetrelationmd"></a>

## ApplicationAssetRelation
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Id of the application | [optional] [default to null]
**organizationId** | **String** | Application owner organization ID | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Boolean which defines if the asset is visible in provided applications avatar editor | [optional] [default to null]
**masculineOrder** | **Integer** | For male/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]
**feminineOrder** | **Integer** | For female/neutral assets, defines the asset order in avatar editor for specified application | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetmd"></a>

## Asset
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetbasemd"></a>

## AssetBase
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfomd"></a>

## AssetInfo
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | Automatically generated numeric Identifier of an asset | [optional] [default to null]
**name** | **String** | The name of an asset | [optional] [default to null]
**organizationId** | **String** | The Id of the asset owner organization | [optional] [default to null]
**locked** | **Boolean** | A boolean value which when set to true requires the asset to be unlocked for specific user before it is usable by them | [optional] [default to null]
**type** | **String** | Defines which body part of the avatar this asset belongs to | [optional] [default to null]
**gender** | **String** | Defines which avatar genders are supported for this asset | [optional] [default to null]
**modelUrl** | **String** | The URL where you can find the GLB model for the asset | [optional] [default to null]
**iconUrl** | **String** | The URL where you can find the icon for the asset | [optional] [default to null]
**hasApps** | **Boolean** | A boolean value indicating if this asset is available in any applications or not | [optional] [default to null]
**createdAt** | **Date** | Datetime when this asset was created | [optional] [default to null]
**updatedAt** | **Date** | Datetime when this asset was last updated | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinforesponsemd"></a>

## AssetInfoResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**AssetInfo**](#modelsassetinfomd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetinfo_allofmd"></a>

## AssetInfo_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applications** | [**List**](#modelsapplicationassetrelationmd) | Array of application-asset relations | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsassetresponsemd"></a>

## AssetResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**Asset**](#modelsassetmd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsasset_allofmd"></a>

## Asset_allOf
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationIds** | **List** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsavatarmetadatamd"></a>

## AvatarMetadata
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**bodyType** | **String** | The type of the avatar | [optional] [default to null]
**outfitGender** | **String** |  | [optional] [default to null]
**outfitVersion** | **Integer** |  | [optional] [default to null]
**skinTone** | **String** |  | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsconnectionprofilemd"></a>

## ConnectionProfile
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**apiToken** | **String** | Personal access token, an OAuth token, an installation access token or a JSON Web Token | [default to null]
**url** | **String** | Organization domain | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequestmd"></a>

## CreateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateAssetRequest_data**](#modelscreateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_datamd"></a>

## CreateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | Minimum length of 1 character | [default to null]
**type** | **String** | Type of asset | [default to null]
**gender** | **String** | Gender compatibility of the asset | [default to null]
**modelUrl** | **URI** | Must be a valid url pointing to a GLB file | [default to null]
**iconUrl** | **URI** | Must be a valid url pointing to a PNG or JPG file | [default to null]
**organizationId** | **String** | The id of the organization you wish to create the asset under | [default to null]
**applications** | [**List**](#modelscreateassetrequest_data_applicationsmd) | List of applications-asset relations that defines to which applications this asset should be linked to. If empty, this asset is not added to any applications | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to false]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateassetrequest_data_applicationsmd"></a>

## CreateAssetRequest_data_applications
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **String** | ID of the application | [optional] [default to null]
**organizationId** | **String** | Organization ID that owns the application | [optional] [default to null]
**isVisibleInEditor** | **Boolean** | Whether the asset is visible in the editor | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequestmd"></a>

## CreateUserRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserRequest_data**](#modelscreateuserrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserrequest_datamd"></a>

## CreateUserRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | User will be authorized for this application | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponsemd"></a>

## CreateUserResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**CreateUserResponse_data**](#modelscreateuserresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelscreateuserresponse_datamd"></a>

## CreateUserResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**partners** | **List** | Your subdomain | [optional] [default to null]
**applicationIds** | **List** | Your app-id | [optional] [default to null]
**createdAt** | **Date** |  | [optional] [default to null]
**updatedAt** | **Date** |  | [optional] [default to null]
**id** | **String** | User-id you want to store | [optional] [default to null]
**name** | **String** | User name | [optional] [default to null]
**email** | **String** | User email | [optional] [default to null]
**unverifiedEmail** | **String** | Unverified email | [optional] [default to null]
**externalId** | **String** | External id | [optional] [default to null]
**isAnonymous** | **Boolean** | Whether the user is anonymous | [optional] [default to null]
**isGuest** | **Boolean** | Whether the user is a guest | [optional] [default to null]
**wallets** | [**List**](AnyType.md) | Wallets | [optional] [default to null]
**settings** | [**oas_any_type_not_mapped**](.md) |  | [optional] [default to null]
**visitedAt** | **Date** | Last visited date time | [optional] [default to null]
**verifiedAt** | **Date** | Verified date time | [optional] [default to null]
**deleteAt** | **Date** | Deleted date time | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequestmd"></a>

## EquipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**EquipAssetRequest_data**](#modelsequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsequipassetrequest_datamd"></a>

## EquipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to equip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequestmd"></a>

## LockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**LockAssetRequest_data**](#modelslockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelslockassetrequest_datamd"></a>

## LockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to lock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequestmd"></a>

## RemoveAssetFromApplicationRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**RemoveAssetFromApplicationRequest_data**](#modelsremoveassetfromapplicationrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsremoveassetfromapplicationrequest_datamd"></a>

## RemoveAssetFromApplicationRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**applicationId** | **String** | The id of the application you wish to remove from the asset | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponsemd"></a>

## TokenResponse
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**TokenResponse_data**](#modelstokenresponse_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelstokenresponse_datamd"></a>

## TokenResponse_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**token** | **String** | The token that can be used to restore the session. The token only lives for 15 seconds. Needs to be created right after the web-view/iFrame call. | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequestmd"></a>

## UnequipAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnequipAssetRequest_data**](#modelsunequipassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunequipassetrequest_datamd"></a>

## UnequipAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**assetId** | **String** | The id of the asset you want to unequip | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequestmd"></a>

## UnlockAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UnlockAssetRequest_data**](#modelsunlockassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsunlockassetrequest_datamd"></a>

## UnlockAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userId** | **String** | The id of the user you want to unlock the asset for | [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequestmd"></a>

## UpdateAssetRequest
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**data** | [**UpdateAssetRequest_data**](#modelsupdateassetrequest_datamd) |  | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="modelsupdateassetrequest_datamd"></a>

## UpdateAssetRequest_data
### Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **String** | The name of the asset | [optional] [default to null]
**type** | **String** | The type of the asset | [optional] [default to null]
**gender** | **String** | The gender supported by the asset | [optional] [default to null]
**modelUrl** | **String** | Must be a valid url pointing to a GLB file | [optional] [default to null]
**iconUrl** | **String** | Must be a valid url pointing to a PNG or JPG file | [optional] [default to null]
**locked** | **Boolean** | Whether the asset is locked | [optional] [default to null]
**applications** | [**List**](#modelsapplicationassetrelationmd) | List of application-asset relations this asset should be available in | [optional] [default to null]

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)



<a name="readmemd"></a>

# Documentation for @zerobias-org/module-readyplayerme-readyplayerme

<a name="documentation-for-api-endpoints"></a>
## Documentation for API Endpoints

All URIs are relative to *http://localhost*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*ApplicationApi* | [**listApplicationAssets**](#listapplicationassets) | **GET** /applications/{appId}/assets | List Application Assets
*AssetApi* | [**addAssetToApplication**](#addassettoapplication) | **POST** /assets/{assetId}/application | Add Asset to Application
*AssetApi* | [**createAsset**](#createasset) | **POST** /assets | Create Asset
*AssetApi* | [**lockAssetForUser**](#lockassetforuser) | **PUT** /assets/{assetId}/lock | Lock asset for a user
*AssetApi* | [**removeAssetFromApplication**](#removeassetfromapplication) | **DELETE** /assets/{assetId}/application | Remove Asset from Application
*AssetApi* | [**unlockAssetForUser**](#unlockassetforuser) | **PUT** /assets/{assetId}/unlock | Unlock asset for a user
*AssetApi* | [**updateAsset**](#updateasset) | **PATCH** /assets/{assetId} | Update Asset
*AvatarApi* | [**equipAssetToAvatar**](#equipassettoavatar) | **PUT** /avatars/{avatarId}/equip | Equip an asset
*AvatarApi* | [**getAvatarMetadata**](#getavatarmetadata) | **GET** /avatars/{avatarId} | Get Metadata
*AvatarApi* | [**unequipAssetFromAvatar**](#unequipassetfromavatar) | **PUT** /avatars/{avatarId}/unequip | Unequip an asset
*Avatar2dApi* | [**get2DAvatarRender**](#get2davatarrender) | **GET** /avatars/2d/{avatarId} | Get 2D Render of an Avatar
*Avatar3dApi* | [**get3DAvatarModel**](#get3davatarmodel) | **GET** /avatars/3d/{avatarId} | Get a 3D avatar GLB file
*UserApi* | [**createUser**](#createuser) | **POST** /users | Create a Ready Player Me Guest User
*UserApi* | [**getUserToken**](#getusertoken) | **GET** /users/{userId}/token | Request a token for iFrame session restoration


<a name="documentation-for-models"></a>
## Documentation for Models

 - [AddAssetToApplicationRequest](#modelsaddassettoapplicationrequestmd)
 - [AddAssetToApplicationRequest_data](#modelsaddassettoapplicationrequest_datamd)
 - [ApplicationAssetRelation](#modelsapplicationassetrelationmd)
 - [Asset](#modelsassetmd)
 - [AssetBase](#modelsassetbasemd)
 - [AssetInfo](#modelsassetinfomd)
 - [AssetInfoResponse](#modelsassetinforesponsemd)
 - [AssetInfo_allOf](#modelsassetinfo_allofmd)
 - [AssetResponse](#modelsassetresponsemd)
 - [Asset_allOf](#modelsasset_allofmd)
 - [AvatarMetadata](#modelsavatarmetadatamd)
 - [ConnectionProfile](#modelsconnectionprofilemd)
 - [CreateAssetRequest](#modelscreateassetrequestmd)
 - [CreateAssetRequest_data](#modelscreateassetrequest_datamd)
 - [CreateAssetRequest_data_applications](#modelscreateassetrequest_data_applicationsmd)
 - [CreateUserRequest](#modelscreateuserrequestmd)
 - [CreateUserRequest_data](#modelscreateuserrequest_datamd)
 - [CreateUserResponse](#modelscreateuserresponsemd)
 - [CreateUserResponse_data](#modelscreateuserresponse_datamd)
 - [EquipAssetRequest](#modelsequipassetrequestmd)
 - [EquipAssetRequest_data](#modelsequipassetrequest_datamd)
 - [LockAssetRequest](#modelslockassetrequestmd)
 - [LockAssetRequest_data](#modelslockassetrequest_datamd)
 - [RemoveAssetFromApplicationRequest](#modelsremoveassetfromapplicationrequestmd)
 - [RemoveAssetFromApplicationRequest_data](#modelsremoveassetfromapplicationrequest_datamd)
 - [TokenResponse](#modelstokenresponsemd)
 - [TokenResponse_data](#modelstokenresponse_datamd)
 - [UnequipAssetRequest](#modelsunequipassetrequestmd)
 - [UnequipAssetRequest_data](#modelsunequipassetrequest_datamd)
 - [UnlockAssetRequest](#modelsunlockassetrequestmd)
 - [UnlockAssetRequest_data](#modelsunlockassetrequest_datamd)
 - [UpdateAssetRequest](#modelsupdateassetrequestmd)
 - [UpdateAssetRequest_data](#modelsupdateassetrequest_datamd)


<a name="documentation-for-authorization"></a>
## Documentation for Authorization

All endpoints do not require authorization.
