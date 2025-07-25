<p align="center">
  <img src="https://user-images.githubusercontent.com/236501/85893648-1c92e880-b7a8-11ea-926d-95355b8175c7.png" width="128" height="128" alt="CapacitorJS Logo" />
</p>
<h3 align="center">Capacitor Voice Recorder</h3>
<p align="center"><strong><code>@independo/capacitor-voice-recorder</code></strong></p>
<p align="center">Capacitor plugin for audio recording</p>

<p align="center">
  <img src="https://img.shields.io/maintenance/yes/2025" alt="Maintenance Badge: until 2025" />
  <a href="https://www.npmjs.com/package/@independo/capacitor-voice-recorder"><img src="https://img.shields.io/npm/l/@independo/capacitor-voice-recorder" alt="License Badge: MIT" /></a>
<br>
  <a href="https://www.npmjs.com/package/@independo/capacitor-voice-recorder"><img src="https://img.shields.io/npm/dw/@independo/capacitor-voice-recorder" alt="" role="presentation" /></a>
  <a href="https://www.npmjs.com/package/@independo/capacitor-voice-recorder"><img src="https://img.shields.io/npm/v/@independo/capacitor-voice-recorder" alt="" role="presentation" /></a>
</p>

## Installation

```
npm install --save @independo/capacitor-voice-recorder
npx cap sync
```

## Configuration

### Using with Android

Add the following to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### Using with iOS

Add the following to your `Info.plist`:

```xml

<key>NSMicrophoneUsageDescription</key>
<string>This app uses the microphone to record audio.</string>
```

## Overview

The `@independo/capacitor-voice-recorder` plugin allows you to record audio on Android, iOS, and Web platforms.

## API

Below is an index of all available methods. Run `npm run docgen` after updating any JSDoc comments to refresh this section.

<docgen-index>

* [`canDeviceVoiceRecord()`](#candevicevoicerecord)
* [`requestAudioRecordingPermission()`](#requestaudiorecordingpermission)
* [`hasAudioRecordingPermission()`](#hasaudiorecordingpermission)
* [`startRecording(...)`](#startrecording)
* [`stopRecording()`](#stoprecording)
* [`pauseRecording()`](#pauserecording)
* [`resumeRecording()`](#resumerecording)
* [`getCurrentStatus()`](#getcurrentstatus)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Interface for the VoiceRecorderPlugin which provides methods to record audio.

### canDeviceVoiceRecord()

```typescript
canDeviceVoiceRecord() => Promise<GenericResponse>
```

Checks if the current device can record audio.
On mobile, this function will always resolve to `{ value: true }`.
In a browser, it will resolve to `{ value: true }` or `{ value: false }` based on the browser's ability to record.
This method does not take into account the permission status, only if the browser itself is capable of recording at all.

**Returns:** <code>Promise&lt;<a href="#genericresponse">GenericResponse</a>&gt;</code>

--------------------


### requestAudioRecordingPermission()

```typescript
requestAudioRecordingPermission() => Promise<GenericResponse>
```

Requests audio recording permission from the user.
If the permission has already been provided, the promise will resolve with `{ value: true }`.
Otherwise, the promise will resolve to `{ value: true }` or `{ value: false }` based on the user's response.

**Returns:** <code>Promise&lt;<a href="#genericresponse">GenericResponse</a>&gt;</code>

--------------------


### hasAudioRecordingPermission()

```typescript
hasAudioRecordingPermission() => Promise<GenericResponse>
```

Checks if audio recording permission has been granted.
Will resolve to `{ value: true }` or `{ value: false }` based on the status of the permission.
The web implementation of this plugin uses the Permissions API, which is not widespread.
If the status of the permission cannot be checked, the promise will reject with `COULD_NOT_QUERY_PERMISSION_STATUS`.
In that case, use `requestAudioRecordingPermission` or `startRecording` and capture any exception that is thrown.

**Returns:** <code>Promise&lt;<a href="#genericresponse">GenericResponse</a>&gt;</code>

--------------------


### startRecording(...)

```typescript
startRecording(options?: RecordingOptions | undefined) => Promise<GenericResponse>
```

Starts audio recording.
On success, the promise will resolve to { value: true }.
On error, the promise will reject with one of the following error codes:
"MISSING_PERMISSION", "ALREADY_RECORDING", "MICROPHONE_BEING_USED", "DEVICE_CANNOT_VOICE_RECORD", or "FAILED_TO_RECORD".

| Param         | Type                                                          | Description                    |
| ------------- | ------------------------------------------------------------- | ------------------------------ |
| **`options`** | <code><a href="#recordingoptions">RecordingOptions</a></code> | The options for the recording. |

**Returns:** <code>Promise&lt;<a href="#genericresponse">GenericResponse</a>&gt;</code>

--------------------


### stopRecording()

```typescript
stopRecording() => Promise<RecordingData>
```

Stops audio recording.
Will stop the recording that has been previously started.
If the function `startRecording` has not been called beforehand, the promise will reject with `RECORDING_HAS_NOT_STARTED`.
If the recording has been stopped immediately after it has been started, the promise will reject with `EMPTY_RECORDING`.
In a case of unknown error, the promise will reject with `FAILED_TO_FETCH_RECORDING`.
In case of success, the promise resolves to <a href="#recordingdata">RecordingData</a> containing the recording in base-64, the duration of the recording in milliseconds, and the MIME type.

**Returns:** <code>Promise&lt;<a href="#recordingdata">RecordingData</a>&gt;</code>

--------------------


### pauseRecording()

```typescript
pauseRecording() => Promise<GenericResponse>
```

Pauses the ongoing audio recording.
If the recording has not started yet, the promise will reject with an error code `RECORDING_HAS_NOT_STARTED`.
On success, the promise will resolve to { value: true } if the pause was successful or { value: false } if the recording is already paused.
On certain mobile OS versions, this function is not supported and will reject with `NOT_SUPPORTED_OS_VERSION`.

**Returns:** <code>Promise&lt;<a href="#genericresponse">GenericResponse</a>&gt;</code>

--------------------


### resumeRecording()

```typescript
resumeRecording() => Promise<GenericResponse>
```

Resumes a paused audio recording.
If the recording has not started yet, the promise will reject with an error code `RECORDING_HAS_NOT_STARTED`.
On success, the promise will resolve to { value: true } if the resume was successful or { value: false } if the recording is already running.
On certain mobile OS versions, this function is not supported and will reject with `NOT_SUPPORTED_OS_VERSION`.

**Returns:** <code>Promise&lt;<a href="#genericresponse">GenericResponse</a>&gt;</code>

--------------------


### getCurrentStatus()

```typescript
getCurrentStatus() => Promise<CurrentRecordingStatus>
```

Gets the current status of the voice recorder.
Will resolve with one of the following values:
`{ status: "NONE" }` if the plugin is idle and waiting to start a new recording.
`{ status: "RECORDING" }` if the plugin is in the middle of recording.
`{ status: "PAUSED" }` if the recording is paused.

**Returns:** <code>Promise&lt;<a href="#currentrecordingstatus">CurrentRecordingStatus</a>&gt;</code>

--------------------


### Interfaces


#### GenericResponse

Interface representing a generic response with a boolean value.

| Prop        | Type                 | Description                                     |
| ----------- | -------------------- | ----------------------------------------------- |
| **`value`** | <code>boolean</code> | The result of the operation as a boolean value. |


#### RecordingOptions

Can be used to specify options for the recording.

| Prop               | Type                                            | Description                                                                                                                                                                                                        |
| ------------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`directory`**    | <code><a href="#directory">Directory</a></code> | The capacitor filesystem directory where the recording should be saved. If not specified, the recording will be stored in a base64 string and returned in the <a href="#recordingdata">`RecordingData`</a> object. |
| **`subDirectory`** | <code>string</code>                             | An optional subdirectory in the specified directory where the recording should be saved.                                                                                                                           |


#### RecordingData

Interface representing the data of a recording.

| Prop        | Type                                                                                           | Description                                 |
| ----------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------- |
| **`value`** | <code>{ recordDataBase64: string; msDuration: number; mimeType: string; uri?: string; }</code> | The value containing the recording details. |


#### CurrentRecordingStatus

Interface representing the current status of the voice recorder.

| Prop         | Type                                           | Description                                                                                                  |
| ------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| **`status`** | <code>'RECORDING' \| 'PAUSED' \| 'NONE'</code> | The current status of the recorder, which can be one of the following values: 'RECORDING', 'PAUSED', 'NONE'. |


### Type Aliases


#### Base64String

Represents a Base64 encoded string.

<code>string</code>


### Enums


#### Directory

| Members               | Value                           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Since |
| --------------------- | ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| **`Documents`**       | <code>"DOCUMENTS"</code>        | The Documents directory. On iOS it's the app's documents directory. Use this directory to store user-generated content. On Android it's the Public Documents folder, so it's accessible from other apps. It's not accesible on Android 10 unless the app enables legacy External Storage by adding `android:requestLegacyExternalStorage="true"` in the `application` tag in the `AndroidManifest.xml`. On Android 11 or newer the app can only access the files/folders the app created. | 1.0.0 |
| **`Data`**            | <code>"DATA"</code>             | The Data directory. On iOS it will use the Documents directory. On Android it's the directory holding application files. Files will be deleted when the application is uninstalled.                                                                                                                                                                                                                                                                                                       | 1.0.0 |
| **`Library`**         | <code>"LIBRARY"</code>          | The Library directory. On iOS it will use the Library directory. On Android it's the directory holding application files. Files will be deleted when the application is uninstalled.                                                                                                                                                                                                                                                                                                      | 1.1.0 |
| **`Cache`**           | <code>"CACHE"</code>            | The Cache directory. Can be deleted in cases of low memory, so use this directory to write app-specific files. that your app can re-create easily.                                                                                                                                                                                                                                                                                                                                        | 1.0.0 |
| **`External`**        | <code>"EXTERNAL"</code>         | The external directory. On iOS it will use the Documents directory. On Android it's the directory on the primary shared/external storage device where the application can place persistent files it owns. These files are internal to the applications, and not typically visible to the user as media. Files will be deleted when the application is uninstalled.                                                                                                                        | 1.0.0 |
| **`ExternalStorage`** | <code>"EXTERNAL_STORAGE"</code> | The external storage directory. On iOS it will use the Documents directory. On Android it's the primary shared/external storage directory. It's not accesible on Android 10 unless the app enables legacy External Storage by adding `android:requestLegacyExternalStorage="true"` in the `application` tag in the `AndroidManifest.xml`. It's not accesible on Android 11 or newer.                                                                                                      | 1.0.0 |

</docgen-api>


## Format and Mime type

The plugin will return the recording in one of several possible formats.
the format is dependent on the os / web browser that the user uses.
on android and ios the mime type will be `audio/aac`, while on chrome and firefox it
will be `audio/webm;codecs=opus` and on safari it will be `audio/mp4`.
note that these 3 browsers has been tested on. the plugin should still work on
other browsers, as there is a list of mime types that the plugin checks against the
user's browser.

Note that this fact might cause unexpected behavior in case you'll try to play recordings
between several devices or browsers - as they not all support the same set of audio formats.
it is recommended to convert the recordings to a format that all your target devices supports.
as this plugin focuses on the recording aspect, it does not provide any conversion between formats.

## Playback

To play the recorded file you can use plain javascript:

```typescript
const base64Sound = '...' // from plugin
const mimeType = '...'  // from plugin
const audioRef = new Audio(`data:${mimeType};base64,${base64Sound}`)
audioRef.oncanplaythrough = () => audioRef.play()
audioRef.load()
```

## Compatibility

Versioning follows Capacitor versioning. Major versions of the plugin are compatible with major versions of Capacitor.

| Plugin Version | Capacitor Version |
|----------------|-------------------|
| 5.*            | 5                 |
| 6.*            | 6                 |
| 7.*            | 7                 |

## Collaborators

| Collaborators      |                                                             | GitHub                                    | Donation                                                                                                                          |
|--------------------|-------------------------------------------------------------|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Avihu Harush       | Original Author                                             | [tchvu3](https://github.com/tchvu3)       | [!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/tchvu3) |
| Konstantin Strümpf | Contributor for [Independo GmbH](https://www.independo.app) | [kstruempf](https://github.com/kstruempf) |                                                                                                                                   |

