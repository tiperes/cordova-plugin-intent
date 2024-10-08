*Forked from [darryncampbell-cordova-plugin-intent](https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent) to provide better support when used with [OutSystems platform](https://www.outsystems.com)*
=========================================================

[![npm version](http://img.shields.io/npm/v/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm downloads](http://img.shields.io/npm/dm/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm downloads](http://img.shields.io/npm/dt/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm licence](http://img.shields.io/npm/l/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")


# Changes
- "onActivityResult" - Purged function since it does not bring any additional value diferent from "onIntent".
- "sendResult" - Normalized parameters processing based in a Intent object, allowing full customization of the result intent to send.
- Reviewed internal serialization action "populateIntent", to support multiple extra entries with Object values that will be serialized as a Bundle.
- Full code review to better control exceptions and improve error message details exposure to cordova side.
- Reviewed plugin hooks to include cordova client variables processing to improve compability and conflit issues.


## Whitelisting Permissions (Android uses-permission)
Offer the flexibility to control uses-permission in the Android Manifest using comma separated values.

## 
```
For Android:
```
--variable ANDROID_USES_PERMISSIONS = "android.permission.READ_EXTERNAL_STORAGE,android.permission.READ_MEDIA_IMAGES"

Will change the AndroidManifest.xml file to:
```XML
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
```

## Whitelisting Package querying (Android queries/package)
Offer the flexibility to control queries/packages in the Android Manifest using comma separated values.

Refer to:
- [When an app targets Android 11 (API level 30) or higher and queries for information about the other apps that are installed on a device, the system filters this information by default. This filtering behavior means that your app can’t detect all the apps installed on a device, which helps minimize the potentially sensitive information that your app can access but doesn't need to fulfill its use cases.](https://developer.android.com/training/package-visibility)
- [Google Play restricts the use of high-risk or sensitive permissions, including the QUERY_ALL_PACKAGES permission, which gives visibility into the inventory of installed apps on a given device. Play regards the inventory of installed apps queried from a user’s device as personal and sensitive information, and the use of the permission is only permitted when your app's core user-facing functionality or purpose requires broad visibility into installed apps on the user’s device.](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)

## 
```
For Android:
```
--variable ANDROID_QUERIES_PACKAGES = "com.facebook.android,com.twitter.android"

Will change the AndroidManifest.xml file to:
```XML
<queries>
	<package android:name="com.facebook.android"/>
	<package android:name="com.twitter.android"/>
</queries>
```

## Whitelisting Intent Actions querying (Android queries/intent)
Offer the flexibility to control queries/intent in the Android Manifest using comma separated values.

## 
```
For Android:
```
--variable ANDROID_QUERIES_ACTIONS = "android.intent.action.SEND|text/*,android.intent.action.VIEW"

Will change the AndroidManifest.xml file to:
```XML
<queries>
	<intent>
		<action android:name="android.intent.action.SEND" />
		<data android:scheme="content" android:mimeType="text/*"/>
	</intent>
	<intent>
		<action android:name="android.intent.action.VIEW" />
	</intent>
</queries>
```
# Credits
All credits to [darryncampbell](https://github.com/darryncampbell).
