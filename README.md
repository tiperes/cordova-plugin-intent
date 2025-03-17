*Forked from [darryncampbell-cordova-plugin-intent](https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent) to provide better support when used with [OutSystems platform](https://www.outsystems.com)*
=========================================================

[![npm version](http://img.shields.io/npm/v/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm downloads](http://img.shields.io/npm/dm/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm downloads](http://img.shields.io/npm/dt/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm licence](http://img.shields.io/npm/l/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")


# Changes
- "onActivityResult" - Purged function since it does not bring any additional value diferent from "onIntent".
- "sendResult" - Normalized parameters processing based in a Intent object, allowing full customization of the result intent to send.
- Broadcast Receiver completly refactored to better deal with various scenarios.
- Reviewed internal serialization action "populateIntent", to support multiple extra entries with Object values that will be serialized as a Bundle.
- Full code review to better control exceptions and improve error message details exposure to cordova side.
- Reviewed plugin.xml to exclude some permissions whitelisting that can be conflituous and added hooks to process cordova client variables processing to deal with this needs.
- Compatibilization with Android 14+ regarding API and permissions requiremnts

# Plugin Configuration with Cordova Client Variables

With Android 14 and higher, the permission requirements are more strict and required more configuration.
The previous version was very limited in terms of delaying the Android Manifest configuration.
This version introduces the capability to process the manifest using an external service for this purpose.
With this, the previous variables introduced in early versions have been removed!

### Client Variables:
- ANDROID_PROCESS_MANIFEST_API: Supply an URL to a service that can validate or even manipulate the resulting android manifext XML to be compliant with the plugin usage requirements.
- ANDROID_PROCESS_MANIFEST_AUTHORIZATION: Optionally use a authorization token to call the service.

### Service Requirements:
- URL: <ANDROID_PROCESS_MANIFEST_API>
- Method: POST
- Header - Content-Type: application/json
- Header - Authorization: **[OPTIONAL]** <ANDROID_PROCESS_MANIFEST_AUTHORIZATION>
- Body:
```
{
    timestamp: <current time as ISO string>
    plugin: <plugin ID being added>
    platform: <platform affected>
    manifestXml: <the current manifest XML>
}
```
- Response: **[OPTIONAL]** <New Manifest XML>


# Use cases that you should evaluate


> Whitelisting Permissions
```
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
```

> Whitelisting Intent Communication
```
<application>
	<activity>
		<intent-filter>
  			<action android:name="com.tiperes.cordova.plugin.intent.ACTION" />
			<category android:name="android.intent.category.DEFAULT" />
		</intent-filter>
	</activity>
</application>
```

> Whitelisting Querying by Package querying
```
<queries>
	<package android:name="com.facebook.android"/>
	<package android:name="com.twitter.android"/>
</queries>
```

> Whitelisting Querying by Intent Actions querying
```
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
