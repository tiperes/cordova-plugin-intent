package com.tiperes.cordova.plugin.intent;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStorageState;

public class IntentShim extends CordovaPlugin
{
    private final Map<BroadcastReceiver, CallbackContext> receiverCallbacks = new HashMap<>();

    private static final String LOG_TAG = "Cordova Intents Shim";
    private CallbackContext onNewIntentCallbackContext = null;
    private CallbackContext onActivityResultCallbackContext = null;

    private Intent deferredIntent = null;

    public IntentShim() {
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext)
    {
        try
        {
            Log.d(LOG_TAG, "Action: " + action);
            if (action.equals("startActivity") || action.equals("startActivityForResult"))
            {
                //  Credit: https://github.com/chrisekelley/cordova-webintent
                if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
    
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
                int requestCode = obj.has("requestCode") ? obj.getInt("requestCode") : 1;
                
                startActivity(intent, action.equals("startActivityForResult"), requestCode, callbackContext);
            }
            else if (action.equals("sendBroadcast"))
            {
                //  Credit: https://github.com/chrisekelley/cordova-webintent
                if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
    
                // Parse the arguments
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
    
                this.cordova.getActivity().sendBroadcast(intent);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            }
            else if (action.equals("startService"))
            {
                if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
                this.cordova.getActivity().startService(intent);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            }
            else if (action.equals("registerBroadcastReceiver"))
            {
                Log.d(LOG_TAG, "Plugin no longer unregisters receivers on registerBroadcastReceiver invocation");
    
                //  No error callback
                if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
    
                //  Expect an array of filterActions
                JSONObject obj = args.getJSONObject(0);
                IntentFilter filter = new IntentFilter();
                
                JSONArray filterActions = obj.has("filterActions") ? obj.getJSONArray("filterActions") : null;
                if (filterActions == null || filterActions.length() == 0)
                {
                    //  The arguments are not correct
                    Log.w(LOG_TAG, "filterActions argument is not in the expected format");
                    throw new IllegalArgumentException("Filter Actions is mandatory");
                }
    
                for (int i = 0; i < filterActions.length(); i++) {
                    Log.d(LOG_TAG, "Registering broadcast receiver for filter: " + filterActions.getString(i));
                    filter.addAction(filterActions.getString(i));
                }
                //  Allow an array of filterCategories
                JSONArray filterCategories = obj.has("filterCategories") ? obj.getJSONArray("filterCategories") : null;
                if (filterCategories != null) {
                    for (int i = 0; i < filterCategories.length(); i++) {
                        Log.d(LOG_TAG, "Registering broadcast receiver for category filter: " + filterCategories.getString(i));
                        filter.addCategory(filterCategories.getString(i));
                    }
                }
                //  Add any specified Data Schemes
                //  https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent/issues/24
                JSONArray filterDataSchemes = obj.has("filterDataSchemes") ? obj.getJSONArray("filterDataSchemes") : null;
                if (filterDataSchemes != null) {
                    for (int i = 0; i < filterDataSchemes.length(); i++) {
                        Log.d(LOG_TAG, "Associating data scheme to filter: " + filterDataSchemes.getString(i));
                        filter.addDataScheme(filterDataSchemes.getString(i));
                    }
                }

                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(true);
                BroadcastReceiver broadcastReceiver = newBroadcastReceiver();
                this.cordova.getActivity().registerReceiver(broadcastReceiver, filter);
                receiverCallbacks.put(broadcastReceiver, callbackContext);
                callbackContext.sendPluginResult(result);
            }
            else if (action.equals("unregisterBroadcastReceiver"))
            {
                try
                {
                    Log.d(LOG_TAG, "Unregistering all broadcast receivers, size was " + receiverCallbacks.size());
                    for (BroadcastReceiver broadcastReceiver: receiverCallbacks.keySet()){
                        this.cordova.getActivity().unregisterReceiver(broadcastReceiver);
                    }
                }
                catch (Exception e) {
                    // Don't care...   
                }
                finally {
                    receiverCallbacks.clear();
                }
            }
            else if (action.equals("onIntent"))
            {
                //  Credit: https://github.com/napolitano/cordova-plugin-intent
                if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
    
                this.onNewIntentCallbackContext = callbackContext;
    
                if (this.deferredIntent != null) {
                    fireOnNewIntent(this.deferredIntent);
                    this.deferredIntent = null;
                }
    
                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
            else if (action.equals("getIntent"))
            {
                //  Credit: https://github.com/napolitano/cordova-plugin-intent
                if (args.length() != 0) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
    
                Intent intent;
    
                if (this.deferredIntent != null) {
                    intent = this.deferredIntent;
                    this.deferredIntent = null;
                }
                else {
                    intent = cordova.getActivity().getIntent();
                }
    
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getIntentJson(intent)));
            }
            else if (action.equals("sendResult"))
            {
                //  Assuming this application was started with startActivityForResult, send the result back
                //  https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent/issues/3
                
                // tiperes: Normalized parameters processing based in a Intent object, allowing full customization of the result intent to send.
                int resultCode = Activity.RESULT_OK;
                Intent result = new Intent();
                
                if (args.length() == 1) {
                    JSONObject obj = args.getJSONObject(0);
                    resultCode = obj.has("resultCode") ? obj.getInt("resultCode") : Activity.RESULT_OK;
                    result = populateIntent(obj, callbackContext);
                }            
                
                //set result
                cordova.getActivity().setResult(resultCode, result);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    
                //finish the activity
                cordova.getActivity().finish();
            }
            else if (action.equals("realPathFromUri"))
            {
                if (args.length() != 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }
    
                JSONObject obj = args.getJSONObject(0);
                String realPath = getRealPathFromURI_API19(obj, callbackContext);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, realPath));
            }
            else if (action.equals("packageExists"))
            {
                if (args.length() < 1) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                    return false;
                }

                boolean packageFound = checkPackageExists(args.getString(0));
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, packageFound));
            }
            return true;
        }
        catch (Exception e) {
            Throwable innerExp = e.getCause();
            String errorMsg = e.getMessage() + (innerExp == null ? "" : " (" + innerExp.getMessage() + ")");
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, errorMsg));
            return false;
        }
    }

    private boolean checkPackageExists(String packageName)
    {
        try {
            PackageManager packageManager = this.cordova.getActivity().getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0));
            } else {
                packageManager.getApplicationInfo(packageName, 0);
            }
            return true;
        }
        catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    
    private void resolveActivityPackageOrThrow(Intent intent)
    {
        PackageManager packageManager = this.cordova.getActivity().getPackageManager();
        if (intent.resolveActivityInfo(packageManager, 0) == null) {
            throw new RuntimeException("Package not found or not enough permissions to query");
        }
    }

    private Uri remapUriWithFileProvider(String uriAsString, final CallbackContext callbackContext)
    {
        //  Create the URI via FileProvider  Special case for N and above when installing apks
        int permissionCheck = ContextCompat.checkSelfPermission(this.cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
        {
            //  Could do better here - if the app does not already have permission should
            //  only continue when we get the success callback from this.
            ActivityCompat.requestPermissions(this.cordova.getActivity(), new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            throw new RuntimeException("Please grant read external storage permission");
        }

        try
        {
            String externalStorageState = getExternalStorageState();
            if (externalStorageState.equals(Environment.MEDIA_MOUNTED) || externalStorageState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                String fileName = uriAsString.substring(uriAsString.indexOf('/') + 2, uriAsString.length());
                File uriAsFile = new File(fileName);
                boolean fileExists = uriAsFile.exists();
                if (!fileExists)
                {
                    Log.e(LOG_TAG, "File at path " + uriAsFile.getPath() + " with name " + uriAsFile.getName() + "does not exist");
                    throw new RuntimeException("File not found: " + uriAsFile.toString());
                }
                String PACKAGE_NAME = this.cordova.getActivity().getPackageName() + ".cordova.plugin.intent.fileprovider";
                Uri uri = FileProvider.getUriForFile(this.cordova.getActivity().getApplicationContext(), PACKAGE_NAME, uriAsFile);
                return uri;
            }
            else
            {
                Log.e(LOG_TAG, "Storage directory is not mounted. Please ensure the device is not connected via USB for file transfer.");
                throw new RuntimeException("Storage directory is returning not mounted");
            }
        } catch (StringIndexOutOfBoundsException e) {
            Log.e(LOG_TAG, "URL is not well formed");
            throw new RuntimeException("URL is not well formed", e);
        }
    }

    private String getRealPathFromURI_API19(JSONObject obj, CallbackContext callbackContext)
    {
        //  Credit: https://stackoverflow.com/questions/2789276/android-get-real-path-by-uri-getpath/2790688
        try {
            Uri uri = Uri.parse(obj.getString("uri"));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                String filePath = "";
                if (uri.getHost().contains("com.android.providers.media")) {
                    int permissionCheck = ContextCompat.checkSelfPermission(this.cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED)
                    {
                        //  Could do better here - if the app does not already have permission should
                        //  only continue when we get the success callback from this.
                        ActivityCompat.requestPermissions(this.cordova.getActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                        throw new RuntimeException("Please grant read external storage permission");
                    }
                    // Image pick from recent
                    String wholeID = DocumentsContract.getDocumentId(uri);
                    // Split at colon, use second item in the array
                    String id = wholeID.split(":")[1];
                    String[] column = {MediaStore.Images.Media.DATA};
                    // where id is equal to
                    String sel = MediaStore.Images.Media._ID + "=?";
                    //  This line requires read storage permission
                    Cursor cursor = this.cordova.getActivity().getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, column, sel, new String[]{id}, null);
                    int columnIndex = cursor.getColumnIndex(column[0]);
                    if (cursor.moveToFirst()) {
                        filePath = cursor.getString(columnIndex);
                    }
                    cursor.close();
                    return filePath;
                } else {
                    // image pick from gallery
                    String[] proj = {MediaStore.Images.Media.DATA};
                    Cursor cursor = this.cordova.getActivity().getApplicationContext().getContentResolver().query(uri, proj, null, null, null);
                    int column_index
                            = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();
                    return cursor.getString(column_index);
                }
            }   
        } catch (JSONException e) {
            Log.e(LOG_TAG, "URL is not well formed");
            throw new RuntimeException("URL is not well formed", e);
        }
        throw new RuntimeException("Requires KITKAT or higher");
    }

    /**
     * Sends the provided Intent to the onNewIntentCallbackContext.
     *
     * @param intent This is the intent to send to the JS layer.
     */
    private void fireOnNewIntent(Intent intent)
    {
        PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
        result.setKeepCallback(true);
        this.onNewIntentCallbackContext.sendPluginResult(result);
    }

    @Override
    public void onNewIntent(Intent intent)
    {
        if (this.onNewIntentCallbackContext != null) {
            fireOnNewIntent(intent);
        } else {
            // save the intent for use when onIntent action is called in the execute method
            this.deferredIntent = intent;
        }
    }

    private void startActivity(Intent intent, boolean bExpectResult, int requestCode, CallbackContext callbackContext)
    {
        resolveActivityPackageOrThrow(intent);
        if (bExpectResult) {
            this.onActivityResultCallbackContext = callbackContext;
            cordova.setActivityResultCallback(this);
            this.cordova.getActivity().startActivityForResult(intent, requestCode);
        }
        else {
            this.cordova.getActivity().startActivity(intent);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        if (onActivityResultCallbackContext != null && intent != null)
        {
            intent.putExtra("requestCode", requestCode);
            intent.putExtra("resultCode", resultCode);
            PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
            result.setKeepCallback(true);
            onActivityResultCallbackContext.sendPluginResult(result);
        }
        else if (onActivityResultCallbackContext != null)
        {
            Intent canceledIntent = new Intent();
            canceledIntent.putExtra("requestCode", requestCode);
            canceledIntent.putExtra("resultCode", resultCode);
            PluginResult canceledResult = new PluginResult(PluginResult.Status.OK, getIntentJson(canceledIntent));
            canceledResult.setKeepCallback(true);
            onActivityResultCallbackContext.sendPluginResult(canceledResult);
        }
    }

    private BroadcastReceiver newBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                CallbackContext onBroadcastCallbackContext = receiverCallbacks.get(this);
                if (onBroadcastCallbackContext != null)
                {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
                    result.setKeepCallback(true);
                    onBroadcastCallbackContext.sendPluginResult(result);
                }
            }
        };
    }
    
    private Intent populateIntent(JSONObject obj, CallbackContext callbackContext)
    {
        try {
            //  Credit: https://github.com/chrisekelley/cordova-webintent
            String type = obj.has("type") ? obj.getString("type") : null;
            String packageAssociated = obj.has("package") ? obj.getString("package") : null;
    
            //Uri uri = obj.has("url") ? resourceApi.remapUri(Uri.parse(obj.getString("url"))) : null;
            Uri uri = null;
            final CordovaResourceApi resourceApi = webView.getResourceApi();
            if (obj.has("url"))
            {
                String uriAsString = obj.getString("url");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && uriAsString.startsWith("file://"))
                {
                    uri = remapUriWithFileProvider(uriAsString, callbackContext);
                }
                else
                {
                    uri = resourceApi.remapUri(Uri.parse(obj.getString("url")));
                }
            }

            Intent intent = new Intent();
    
            String action = obj.has("action") ? obj.getString("action") : null;
            if (action != null)
                intent.setAction(action);
    
            if (type != null && uri != null) {
                intent.setDataAndType(uri, type); //Fix the crash problem with android 2.3.6
            } else {
                if (type != null) {
                    intent.setType(type);
                }
                if (uri != null) {
                    intent.setData(uri);
                }
            }
    
            JSONObject component = obj.has("component") ? obj.getJSONObject("component") : null;
            if (component != null)
            {
                //  User has specified an explicit intent
                String componentPackage = component.has("package") ? component.getString("package") : null;
                String componentClass = component.has("class") ? component.getString("class") : null;
                if (componentPackage == null || componentClass == null)
                {
                    Log.w(LOG_TAG, "Component specified but missing corresponding package or class");
                    throw new RuntimeException("Component specified but missing corresponding package or class");
                }
                else
                {
                    ComponentName componentName = new ComponentName(componentPackage, componentClass);
                    intent.setComponent(componentName);
                }
            }
    
            if (packageAssociated != null)
                intent.setPackage(packageAssociated);
    
            JSONArray flags = obj.has("flags") ? obj.getJSONArray("flags") : null;
            if (flags != null)
            {
                int length = flags.length();
                for (int k = 0; k < length; k++)
                {
                    intent.addFlags(flags.getInt(k));
                }
            }
    
            JSONObject extras = obj.has("extras") ? obj.getJSONObject("extras") : null;
            if (extras != null) {
                JSONArray extraNames = extras.names();
                for (int s = 0; s < extraNames.length(); s++) {
                    String key = extraNames.getString(s);
                    Object value = extras.get(key);
                    if (value instanceof JSONObject) {
                        //  The extra is a bundle
                        addSerializable(intent, key, (JSONObject) value);
                    } else {
                        String valueStr = String.valueOf(value);
                        // If type is text html, the extra text must sent as HTML
                        if (key.equals(Intent.EXTRA_TEXT) && type.equals("text/html")) {
                            intent.putExtra(key, Html.fromHtml(valueStr));
                        } else if (key.equals(Intent.EXTRA_STREAM)) {
                            // allows sharing of images as attachments.
                            // value in this case should be a URI of a file
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && valueStr.startsWith("file://"))
                            {
                                Uri uriOfStream = remapUriWithFileProvider(valueStr, callbackContext);
                                if (uriOfStream != null)
                                    intent.putExtra(key, uriOfStream);
                            }
                            else
                            {
                                //final CordovaResourceApi resourceApi = webView.getResourceApi();
                                intent.putExtra(key, resourceApi.remapUri(Uri.parse(valueStr)));
                            }
                        } else if (key.equals(Intent.EXTRA_EMAIL)) {
                            // allows to add the email address of the receiver
                            intent.putExtra(Intent.EXTRA_EMAIL, new String[] { valueStr });
                        } else if (key.equals(Intent.EXTRA_KEY_EVENT)) {
                            // allows to add a key event object
                            JSONObject keyEventJson = new JSONObject(valueStr);
                            int keyAction = keyEventJson.getInt("action");
                            int keyCode = keyEventJson.getInt("code");
                            KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
                            intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
                        } else {
                            if (value instanceof Boolean) {
                                intent.putExtra(key, Boolean.valueOf(valueStr));
                            } else if (value instanceof Integer) {
                                intent.putExtra(key, Integer.valueOf(valueStr));
                            } else if (value instanceof Long) {
                                intent.putExtra(key, Long.valueOf(valueStr));
                            } else if (value instanceof Double) {
                                intent.putExtra(key, Double.valueOf(valueStr));
                            } else if (value instanceof Float) {
                                intent.putExtra(key, Float.valueOf(valueStr));
                            } else {
                                intent.putExtra(key, valueStr);
                            }
                        }
                    }
                }
            }
    
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    
            if (obj.has("chooser")) {
                intent = Intent.createChooser(intent, obj.getString("chooser"));
            }
    
            return intent;
        }
        catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON to intent", e);
        }
    }
    
    /**
     * Return JSON representation of intent attributes
     *
     * @param intent
     * Credit: https://github.com/napolitano/cordova-plugin-intent
     */
    private JSONObject getIntentJson(Intent intent)
    {
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if (clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    try {
                        items[i] = new JSONObject();
                        items[i].put("htmlText", item.getHtmlText());
                        items[i].put("intent", item.getIntent());
                        items[i].put("text", item.getText());
                        items[i].put("uri", item.getUri());

                        if (item.getUri() != null) {
                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));

                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }

                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException("Error serializing (KITKAT) intent to JSON", e);
                    }
                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (items != null) {
                    intentJSON.put("clipItems", new JSONArray(items));
                }
            }

            intentJSON.put("type", intent.getType());
            intentJSON.put("extras", toJsonObject(intent.getExtras()));
            intentJSON.put("action", intent.getAction());
            intentJSON.put("categories", intent.getCategories());
            intentJSON.put("flags", intent.getFlags());
            intentJSON.put("component", intent.getComponent());
            intentJSON.put("data", intent.getData());
            intentJSON.put("package", intent.getPackage());

            return intentJSON;
        }
        catch (Exception e) {
            throw new RuntimeException("Error serializing intent to JSON", e);
        }
    }

    private static JSONObject toJsonObject(Bundle bundle)
    {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        try {
            return (JSONObject) toJsonValue(bundle);
        }
        catch (Exception e) {
            throw new RuntimeException("Error serializing bundle to JSON", e);
        }
    }

    private static Object toJsonValue(final Object value)
    {
        try {
            //  Credit: https://github.com/napolitano/cordova-plugin-intent
            if (value == null) {
                return null;
            } else if (value instanceof Bundle) {
                final Bundle bundle = (Bundle) value;
                final JSONObject result = new JSONObject();
                for (final String key : bundle.keySet()) {
                    result.put(key, toJsonValue(bundle.get(key)));
                }
                return result;
            } else if ((value.getClass().isArray())) {
                final JSONArray result = new JSONArray();
                int length = Array.getLength(value);
                for (int i = 0; i < length; ++i) {
                    result.put(i, toJsonValue(Array.get(value, i)));
                }
                return result;
            }else if (value instanceof ArrayList<?>) {
                final ArrayList arrayList = (ArrayList<?>)value;
                final JSONArray result = new JSONArray();
                for (int i = 0; i < arrayList.size(); i++)
                    result.put(toJsonValue(arrayList.get(i)));
                return result;
            } else if (
                    value instanceof String
                            || value instanceof Boolean
                            || value instanceof Integer
                            || value instanceof Long
                            || value instanceof Double) {
                return value;
            } else {
                return String.valueOf(value);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Error serializing value to JSON", e);
        }
    }

    private void addSerializable(Intent intent, String key, final JSONObject obj)
    {
        try {
            if (obj.has("$class")) {
                JSONArray arguments = obj.has("arguments") ? obj.getJSONArray("arguments") : new JSONArray();
    
                Class<?>[] argTypes = new Class[arguments.length()];
                for (int i = 0; i < arguments.length(); i++) {
                    argTypes[i] = getType(arguments.get(i));
                }
    
                Class<?> classForName = Class.forName(obj.getString("$class"));
                Constructor<?> constructor = classForName.getConstructor(argTypes);
    
                intent.putExtra(key, (Serializable) constructor.newInstance(jsonArrayToObjectArray(arguments)));
            } else {
                intent.putExtra(key, toBundle(obj));
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Error serializing extra '" + key + "'", e);
        }
    }

    private Object[] jsonArrayToObjectArray(JSONArray array) throws JSONException
    {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.get(i));
        }
        return list.toArray();
    }

    private Class<?> getType(Object obj)
    {
        if (obj instanceof String) {
            return String.class;
        } else if (obj instanceof Boolean) {
            return Boolean.class;
        } else if (obj instanceof Float) {
            return Float.class;
        } else if (obj instanceof Integer) {
            return Integer.class;
        } else if (obj instanceof Long) {
            return Long.class;
        } else if (obj instanceof Double) {
            return Double.class;
        } else {
            return null;
        }
    }

    private Bundle toBundle(final JSONObject obj)
    {
        if (obj == null) {
            return null;
        }
        try {
            Bundle returnBundle = new Bundle();
            Iterator<?> keys = obj.keys();
            while (keys.hasNext()) {
                String key = (String)keys.next();

                if (obj.get(key) instanceof String)
                    returnBundle.putString(key, obj.getString(key));
                else if (obj.get(key) instanceof Boolean)
                    returnBundle.putBoolean(key, obj.getBoolean(key));
                else if (obj.get(key) instanceof Integer)
                    returnBundle.putInt(key, obj.getInt(key));
                else if (obj.get(key) instanceof Long)
                    returnBundle.putLong(key, obj.getLong(key));
                else if (obj.get(key) instanceof Double)
                    returnBundle.putDouble(key, obj.getDouble(key));
                else if (obj.get(key).getClass().isArray() || obj.get(key) instanceof JSONArray)
                {
                    JSONArray jsonArray = obj.getJSONArray(key);
                    int length = jsonArray.length();
                    if (jsonArray.get(0) instanceof String)
                    {
                        String[] stringArray = new String[length];
                        for (int j = 0; j < length; j++)
                            stringArray[j] = jsonArray.getString(j);
                        returnBundle.putStringArray(key, stringArray);
                        //returnBundle.putParcelableArray(key, obj.get);
                    }
                    else
                    {
                        if (key.equals("PLUGIN_CONFIG")) {
                            ArrayList<Bundle> bundleArray = new ArrayList<Bundle>();
                            for (int k = 0; k < length; k++) {
                                bundleArray.add(toBundle(jsonArray.getJSONObject(k)));
                            }
                            returnBundle.putParcelableArrayList(key, bundleArray);
                        } else {
                            Bundle[] bundleArray = new Bundle[length];
                            for (int k = 0; k < length; k++)
                                bundleArray[k] = toBundle(jsonArray.getJSONObject(k));
                            returnBundle.putParcelableArray(key, bundleArray);
                        }
                    }
                }
                else if (obj.get(key) instanceof JSONObject)
                    returnBundle.putBundle(key, toBundle((JSONObject)obj.get(key)));
            }
            return returnBundle;
        }
        catch (Exception e) {
            throw new RuntimeException("Error deserializing JSON to Bundle", e);
        }
    }
}
