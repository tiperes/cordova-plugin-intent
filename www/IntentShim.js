var argscheck = require('cordova/argscheck'),
    channel = require('cordova/channel'),
    utils = require('cordova/utils'),
    exec = require('cordova/exec'),
    cordova = require('cordova');

/*!
 * IntenShim Plugin.
 * This represents a thin shim layer over the Android Intent implementation
 */
module.exports = {
    startActivity: function (arg, successCallback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.startActivity', arguments);
        exec(successCallback, errorCallback, "IntentShim", "startActivity", [arg]);
    },
    startActivityForResult: function (arg, successCallback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.startActivityForResult', arguments);
        exec(successCallback, errorCallback, "IntentShim", "startActivityForResult", [arg]);
    },
    sendBroadcast: function (arg, successCallback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.sendBroadcast', arguments);
        exec(successCallback, errorCallback, "IntentShim", "sendBroadcast", [arg]);
    },
    sendOrderedBroadcast: function (arg, successCallback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.sendOrderedBroadcast', arguments);
        exec(successCallback, errorCallback, "IntentShim", "sendOrderedBroadcast", [arg]);
    },
    startService: function (arg, successCallback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.startService', arguments);
        exec(successCallback, errorCallback, "IntentShim", "startService", [arg]);
    },
    registerBroadcastReceiver: function (arg, callback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.registerBroadcastReceiver', arguments);
        exec(callback, errorCallback, "IntentShim", "registerBroadcastReceiver", [arg]);
    },
    unregisterBroadcastReceiver: function (arg) {
        if (arguments.length == 0) {
            arg = '';
        }
        else {
            argscheck.checkArgs('s', 'IntentShim.unregisterBroadcastReceiver', arguments);
        }
        exec(null, null, "IntentShim", "unregisterBroadcastReceiver", [arg]);
    },
    onIntent: function (callback, errorCallback) {
        argscheck.checkArgs('ff', 'IntentShim.onIntent', arguments);
        exec(callback, errorCallback, "IntentShim", "onIntent", [callback]);
    },
    getIntent: function (successCallback, errorCallback) {
        argscheck.checkArgs('ff', 'IntentShim.getIntent', arguments);
        exec(successCallback, errorCallback, "IntentShim", "getIntent", []);
    },
    sendResult: function (arg, successCallback, errorCallback) {
        argscheck.checkArgs('off', 'IntentShim.sendResult', arguments);
        exec(successCallback, errorCallback, "IntentShim", "sendResult", [arg]);
    },
    packageExists: function (packageName, successCallback, errorCallback) {
        argscheck.checkArgs('sff', 'IntentShim.packageExists', arguments);
        exec(successCallback, errorCallback, "IntentShim", "packageExists", [packageName]);
    }
};
