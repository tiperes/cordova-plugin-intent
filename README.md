*Forked from [darryncampbell-cordova-plugin-intent](https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent) to provide better support when used with [OutSystems platform](https://www.outsystems.com)*
=========================================================

[![npm version](http://img.shields.io/npm/v/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm downloads](http://img.shields.io/npm/dm/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm downloads](http://img.shields.io/npm/dt/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")
[![npm licence](http://img.shields.io/npm/l/com-darryncampbell-cordova-plugin-intent.svg?style=flat-square)](https://npmjs.org/package/com-darryncampbell-cordova-plugin-intent "View this project on npm")

# Changes
- Support for [MABS10](https://success.outsystems.com/support/release_notes/mobile_apps_build_service_versions/mabs_10_release_notes/)
    - Reviewed plugin.xml, namely File Provider configuration, based on https://developer.android.com/training/secure-file-sharing/setup-sharing
- "onActivityResult" - Purged function since it does not bring any additional value diferent from "onIntent".
- "sendResult" - Normalized parameters processing based in a Intent object, allowing full customization of the result intent to send.
- Reviewed internal serialzation action "populateIntent", to support multiple extra entries with Object values that will be serialized as a Bundle.

# Credits
All credits to [darryncampbell](https://github.com/darryncampbell).
