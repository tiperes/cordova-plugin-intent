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
import android.content.pm.ApplicationInfo;
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
import org.apache.cordova.CordovaInterface;
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
import java.util.UUID;

import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStorageState;

public class IntentShim extends CordovaPlugin
{
	private static final String LOG_TAG = "Cordova Intents Shim";
    /**
     * An extended BroadcastReceiver that supports a contextualized plugin callback on receive event.
     */
    private class UniqueBroadcastReceiver extends BroadcastReceiver
    {
        public String UUID;
        private CallbackContext _callbackContext;
        
        public UniqueBroadcastReceiver(String uuid, CallbackContext callbackContext)
		{
			super();
			this.UUID = (uuid != null && !uuid.isEmpty()) ? uuid : java.util.UUID.randomUUID().toString();
			this._callbackContext = callbackContext;
		}
	
		@Override
		public void onReceive(Context context, Intent intent)
		{
			PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
			result.setKeepCallback(true);
			this._callbackContext.sendPluginResult(result);
		}
	
	    public void Register(CordovaInterface cordova, IntentFilter filter, Map<String, UniqueBroadcastReceiver> broadcastReceivers)
		{
			StringBuilder sb = new StringBuilder();
			// Serialize actions
			sb.append("\nActions: ");
			for (int i = 0; i < filter.countActions(); i++)
				sb.append(filter.getAction(i)).append(", ");
			// Serialize categories
			sb.append("\nCategories: ");
			for (int i = 0; i < filter.countCategories(); i++)
				sb.append(filter.getCategory(i)).append(", ");
			// Serialize data schemes
			sb.append("\nData Schemes: ");
			for (int i = 0; i < filter.countDataSchemes(); i++)
				sb.append(filter.getDataScheme(i)).append(", ");
			Log.d(IntentShim.LOG_TAG, "Registering broadcast receiver #" + this.UUID + sb.toString());
			
			UniqueBroadcastReceiver replacedReceiver = broadcastReceivers.put(this.UUID, this);
			// If a previous Broadcast Receiver existed (same UUID), unregister it.
			if (replacedReceiver != null) {
				try {
					cordova.getActivity().unregisterReceiver(replacedReceiver);
				}
				catch (Exception e) {/* Don't care...*/ }
			}
			// As discussed at Google I/O 2023, registering receivers with intention using the RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED flag was introduced as part of Android 13
			// and is now a requirement for apps running on Android 14 or higher (U+).
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				cordova.getActivity().registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
			else
				cordova.getActivity().registerReceiver(this, filter);		
		}

	    public void Unregister(CordovaInterface cordova, Map<String, UniqueBroadcastReceiver> broadcastReceivers)
		{
            Log.d(IntentShim.LOG_TAG, "Unregistering broadcast receiver #" + this.UUID);
            
            try {
                cordova.getActivity().unregisterReceiver(this);
            }
            catch (Exception e) {/* Don't care...*/ }
            broadcastReceivers.remove(this.UUID);
        }
    }
    
    // Broadcast Receiver UUID >> CallbackContext
    private final Map<String, UniqueBroadcastReceiver> broadcastReceivers = new HashMap<>();
    private CallbackContext onNewIntentCallbackContext = null;
    private CallbackContext onActivityResultCallbackContext = null;
    private Intent deferredIntent = null;

    public IntentShim()
    {
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext)
    {
        try {
            Log.d(IntentShim.LOG_TAG, "Action: " + action);
            if (action.equals("startActivity") || action.equals("startActivityForResult")) {    
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
                int requestCode = obj.has("requestCode") ? obj.getInt("requestCode") : 1;
                
                startActivity(intent, action.equals("startActivityForResult"), requestCode, callbackContext);
            }
            else if (action.equals("sendBroadcast")) {    
                // Parse the arguments
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
    
                this.cordova.getActivity().sendBroadcast(intent);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            }
            else if (action.equals("sendOrderedBroadcast")) {    
                // Parse the arguments
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
    
                this.cordova.getActivity().sendOrderedBroadcast(intent, null);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            }
            else if (action.equals("startService")) {
                JSONObject obj = args.getJSONObject(0);
                Intent intent = populateIntent(obj, callbackContext);
                this.cordova.getActivity().startService(intent);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            }
            else if (action.equals("registerBroadcastReceiver")) {
                IntentFilter filter = new IntentFilter();
                
                JSONObject obj = args.getJSONObject(0);
                //  Allow an array of filterActions
                JSONArray filterActions = obj.has("filterActions") ? obj.getJSONArray("filterActions") : null;
                if (filterActions == null || filterActions.length() == 0)
                    throw new IllegalArgumentException("Filter Actions is mandatory");
                for (int i = 0; i < filterActions.length(); i++)
                    filter.addAction(filterActions.getString(i));
                //  Allow an array of filterCategories
                JSONArray filterCategories = obj.has("filterCategories") ? obj.getJSONArray("filterCategories") : null;
                if (filterCategories != null) {
                    for (int i = 0; i < filterCategories.length(); i++)
                        filter.addCategory(filterCategories.getString(i));
                }
                //  Add any specified Data Schemes
                //  https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent/issues/24
                JSONArray filterDataSchemes = obj.has("filterDataSchemes") ? obj.getJSONArray("filterDataSchemes") : null;
                if (filterDataSchemes != null) {
                    for (int i = 0; i < filterDataSchemes.length(); i++)
                        filter.addDataScheme(filterDataSchemes.getString(i));
                }

                String uuid = obj.has("uuid") ? obj.getString("uuid") : null;
                UniqueBroadcastReceiver broadcastReceiver = new UniqueBroadcastReceiver(uuid, callbackContext);
                broadcastReceiver.Register(this.cordova, filter, broadcastReceivers);
                
                PluginResult result = new PluginResult(PluginResult.Status.OK, broadcastReceiver.UUID);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
            else if (action.equals("unregisterBroadcastReceiver")) {
                String uuid = args.getString(0);
                if (uuid.isEmpty()) {
                    // Unregister all registered broadcast receivers
                    for (UniqueBroadcastReceiver broadcastReceiver:  new ArrayList<>(broadcastReceivers.values()))
                        broadcastReceiver.Unregister(this.cordova, broadcastReceivers);
                }
                else {
                    // If registered, Unregister the broadcast receiver with a given UUID
                    UniqueBroadcastReceiver broadcastReceiver = broadcastReceivers.get(uuid);
                    if (broadcastReceiver != null)
                        broadcastReceiver.Unregister(this.cordova, broadcastReceivers);
                }
            }
            else if (action.equals("onIntent")) {    
                this.onNewIntentCallbackContext = callbackContext;
    
                if (this.deferredIntent != null) {
                    fireOnNewIntent(this.deferredIntent);
                    this.deferredIntent = null;
                }
    
                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
            else if (action.equals("getIntent")) {    
                Intent intent;    
                if (this.deferredIntent != null) {
                    intent = this.deferredIntent;
                    this.deferredIntent = null;
                }
                else
                    intent = cordova.getActivity().getIntent();
    
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getIntentJson(intent)));
            }
            else if (action.equals("sendResult")) {
                //  Assuming this application was started with startActivityForResult, send the result back
                //  https://github.com/darryncampbell/darryncampbell-cordova-plugin-intent/issues/3
                
                // tiperes: Normalized parameters processing based in a Intent object, allowing full customization of the result intent to send.
                JSONObject obj = args.getJSONObject(0);
                int resultCode = obj.has("resultCode") ? obj.getInt("resultCode") : Activity.RESULT_OK;
                Intent result = populateIntent(obj, callbackContext);           
                
                //set result
                cordova.getActivity().setResult(resultCode, result);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
    
                //finish the activity
                cordova.getActivity().finish();
            }
            else if (action.equals("packageExists")) {
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
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
			if (!appInfo.enabled) {
			    return false;
			}
            return true;
        }
        catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private Uri remapUriWithFileProvider(String uriAsString, final CallbackContext callbackContext)
    {
        //  Create the URI via FileProvider  Special case for N and above when installing apks
        int permissionCheck = ContextCompat.checkSelfPermission(this.cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            //  Could do better here - if the app does not already have permission should
            //  only continue when we get the success callback from this.
            ActivityCompat.requestPermissions(this.cordova.getActivity(), new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            throw new RuntimeException("Please grant read external storage permission");
        }

        try {
            String externalStorageState = getExternalStorageState();
            if (externalStorageState.equals(Environment.MEDIA_MOUNTED) || externalStorageState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                String fileName = uriAsString.substring(uriAsString.indexOf('/') + 2, uriAsString.length());
                File uriAsFile = new File(fileName);
                boolean fileExists = uriAsFile.exists();
                if (!fileExists) {
                    Log.e(IntentShim.LOG_TAG, "File at path " + uriAsFile.getPath() + " with name " + uriAsFile.getName() + "does not exist");
                    throw new RuntimeException("File not found: " + uriAsFile.toString());
                }
                String PACKAGE_NAME = this.cordova.getActivity().getPackageName() + ".cordova.plugin.intent.fileprovider";
                Uri uri = FileProvider.getUriForFile(this.cordova.getActivity().getApplicationContext(), PACKAGE_NAME, uriAsFile);
                return uri;
            }
            else {
                Log.e(IntentShim.LOG_TAG, "Storage directory is not mounted. Please ensure the device is not connected via USB for file transfer.");
                throw new RuntimeException("Storage directory is returning not mounted");
            }
        } catch (StringIndexOutOfBoundsException e) {
            Log.e(IntentShim.LOG_TAG, "URL is not well formed");
            throw new RuntimeException("URL is not well formed", e);
        }
    }

    /**
     * Sends the provided Intent to the onNewIntentCallbackContext.
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
        if (this.onNewIntentCallbackContext != null)
            fireOnNewIntent(intent);
        // save the intent for use when onIntent action is called in the execute method
		else
            this.deferredIntent = intent;
    }

    private void startActivity(Intent intent, boolean bExpectResult, int requestCode, CallbackContext callbackContext)
    {
        PackageManager packageManager = this.cordova.getActivity().getPackageManager();
        if (intent.resolveActivityInfo(packageManager, 0) == null)
            throw new RuntimeException("Package not found or not enough permissions to query");
		
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
        if (onActivityResultCallbackContext != null && intent != null) {
            intent.putExtra("requestCode", requestCode);
            intent.putExtra("resultCode", resultCode);
            PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
            result.setKeepCallback(true);
            onActivityResultCallbackContext.sendPluginResult(result);
        }
        else if (onActivityResultCallbackContext != null) {
            Intent canceledIntent = new Intent();
            canceledIntent.putExtra("requestCode", requestCode);
            canceledIntent.putExtra("resultCode", resultCode);
            PluginResult canceledResult = new PluginResult(PluginResult.Status.OK, getIntentJson(canceledIntent));
            canceledResult.setKeepCallback(true);
            onActivityResultCallbackContext.sendPluginResult(canceledResult);
        }
    }
    
    private Intent populateIntent(JSONObject obj, CallbackContext callbackContext)
    {
        try {
            Intent intent = new Intent();			
            final CordovaResourceApi resourceApi = webView.getResourceApi();
    
            String action = obj.has("action") ? obj.getString("action") : null;
            if (action != null)
                intent.setAction(action);
	
            String type = obj.has("type") ? obj.getString("type") : null;
            Uri uri = null;
            if (obj.has("url")) {
                String uriAsString = obj.getString("url");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && uriAsString.startsWith("file://"))
                    uri = remapUriWithFileProvider(uriAsString, callbackContext);
                else
                    uri = resourceApi.remapUri(Uri.parse(obj.getString("url")));
            }
			//Fix the crash problem with android 2.3.6
            if (type != null && uri != null)
                intent.setDataAndType(uri, type);
            else {
                if (type != null)
                    intent.setType(type);
                if (uri != null)
                    intent.setData(uri);
            }
    
            JSONObject component = obj.has("component") ? obj.getJSONObject("component") : null;
            if (component != null) {
                //  User has specified an explicit intent
                String componentPackage = component.has("package") ? component.getString("package") : null;
                String componentClass = component.has("class") ? component.getString("class") : null;
                if (componentPackage == null || componentClass == null) {
                    Log.w(IntentShim.LOG_TAG, "Component specified but missing corresponding package or class");
                    throw new RuntimeException("Component specified but missing corresponding package or class");
                }
                else {
                    ComponentName componentName = new ComponentName(componentPackage, componentClass);
                    intent.setComponent(componentName);
                }
            }

	    String targetPackage = obj.has("package") ? obj.getString("package") : null;
            if (targetPackage != null)
                intent.setPackage(targetPackage);
    
            JSONArray flags = obj.has("flags") ? obj.getJSONArray("flags") : null;
            if (flags != null) {
                int length = flags.length();
                for (int k = 0; k < length; k++)
                    intent.addFlags(flags.getInt(k));
            }
    
            JSONObject extras = obj.has("extras") ? obj.getJSONObject("extras") : null;
            if (extras != null) {
                JSONArray extraNames = extras.names();
                for (int s = 0; s < extraNames.length(); s++) {
                    String key = extraNames.getString(s);
                    Object value = extras.get(key);
					//  The extra is a bundle
                    if (value instanceof JSONObject)
                        addSerializable(intent, key, (JSONObject) value);
                    else {
                        String valueStr = String.valueOf(value);
                        // If type is text html, the extra text must sent as HTML
                        if (key.equals(Intent.EXTRA_TEXT) && type.equals("text/html"))
                            intent.putExtra(key, Html.fromHtml(valueStr));
                        else if (key.equals(Intent.EXTRA_STREAM)) {
                            // allows sharing of images as attachments.
                            // value in this case should be a URI of a file
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && valueStr.startsWith("file://")) {
                                Uri uriOfStream = remapUriWithFileProvider(valueStr, callbackContext);
                                if (uriOfStream != null)
                                    intent.putExtra(key, uriOfStream);
                            }
                            else
                                intent.putExtra(key, resourceApi.remapUri(Uri.parse(valueStr)));
                        }	
                        // allows to add the email address of the receiver
						else if (key.equals(Intent.EXTRA_EMAIL))
                            intent.putExtra(Intent.EXTRA_EMAIL, new String[] { valueStr });
						else if (key.equals(Intent.EXTRA_KEY_EVENT)) {
                            // allows to add a key event object
                            JSONObject keyEventJson = new JSONObject(valueStr);
                            int keyAction = keyEventJson.getInt("action");
                            int keyCode = keyEventJson.getInt("code");
                            KeyEvent keyEvent = new KeyEvent(keyAction, keyCode);
                            intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
                        }
						else {
                            if (value instanceof Boolean)
                                intent.putExtra(key, Boolean.valueOf(valueStr));
                            else if (value instanceof Integer)
                                intent.putExtra(key, Integer.valueOf(valueStr));
                            else if (value instanceof Long)
                                intent.putExtra(key, Long.valueOf(valueStr));
                            else if (value instanceof Double)
                                intent.putExtra(key, Double.valueOf(valueStr));
                            else if (value instanceof Float)
                                intent.putExtra(key, Float.valueOf(valueStr));
                            else
                                intent.putExtra(key, valueStr);
                        }
                    }
                }
            }

			
            if (obj.has("chooser"))
                intent = Intent.createChooser(intent, obj.getString("chooser"));    
			
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);    
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
		try {
			JSONObject intentJSON = new JSONObject();
			ClipData clipData = intent.getClipData();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && clipData != null) {
				ContentResolver contentResolver = this.cordova.getActivity().getApplicationContext().getContentResolver();
				MimeTypeMap mime = MimeTypeMap.getSingleton();
				
				JSONArray clipItems = new JSONArray();
	            for (int i = 0; i < clipData.getItemCount(); i++) {
					JSONObject clipItem = new JSONObject();
					ClipData.Item item = clipData.getItemAt(i);
					if (item.getIntent() != null)
						clipItem.put("intent", item.getIntent());
					clipItem.put("htmlText", item.getHtmlText());
					clipItem.put("text", item.getText());
			
					Uri uri = item.getUri();
					if (uri != null) {
						clipItem.put("uri", uri);
						String type = contentResolver.getType(uri);
						if (type != null) {
							clipItem.put("type", type);
							String extension = mime.getExtensionFromMimeType(type);
							clipItem.put("extension", extension);
						}
					}
					clipItems.put(clipItem);
				}
				intentJSON.put("clipItems", clipItems);
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
        } catch (JSONException e) {
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
            if (value == null)
                return null;
			else if (value instanceof Bundle) {
                final Bundle bundle = (Bundle) value;
                final JSONObject result = new JSONObject();
                for (final String key : bundle.keySet())
                    result.put(key, toJsonValue(bundle.get(key)));
                return result;
            }
			else if ((value.getClass().isArray())) {
                final JSONArray result = new JSONArray();
                int length = Array.getLength(value);
                for (int i = 0; i < length; ++i)
                    result.put(i, toJsonValue(Array.get(value, i)));
                return result;
            }
			else if (value instanceof ArrayList<?>) {
                final ArrayList arrayList = (ArrayList<?>)value;
                final JSONArray result = new JSONArray();
                for (int i = 0; i < arrayList.size(); i++)
                    result.put(toJsonValue(arrayList.get(i)));
                return result;
            }
			else if (value instanceof String
					|| value instanceof Boolean
					|| value instanceof Integer
					|| value instanceof Long
					|| value instanceof Double)
                return value;
			else
                return String.valueOf(value);
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
                for (int i = 0; i < arguments.length(); i++)
                    argTypes[i] = getType(arguments.get(i));
    
                Class<?> classForName = Class.forName(obj.getString("$class"));
                Constructor<?> constructor = classForName.getConstructor(argTypes);
    
                intent.putExtra(key, (Serializable) constructor.newInstance(jsonArrayToObjectArray(arguments)));
            }
			else
                intent.putExtra(key, toBundle(obj));
        }
        catch (Exception e) {
            throw new RuntimeException("Error serializing extra '" + key + "'", e);
        }
    }

    private Object[] jsonArrayToObjectArray(JSONArray array) throws JSONException
    {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++)
            list.add(array.get(i));
        return list.toArray();
    }

    private Class<?> getType(Object obj)
    {
        if (obj instanceof String)
            return String.class;
		else if (obj instanceof Boolean)
            return Boolean.class;
		else if (obj instanceof Float)
            return Float.class;
		else if (obj instanceof Integer)
            return Integer.class;
		else if (obj instanceof Long)
            return Long.class;
		else if (obj instanceof Double)
            return Double.class;
		else
            return null;
    }

    private Bundle toBundle(final JSONObject obj)
    {
        if (obj == null)
            return null;
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
                    if (jsonArray.get(0) instanceof String) {
                        String[] stringArray = new String[length];
                        for (int j = 0; j < length; j++)
                            stringArray[j] = jsonArray.getString(j);
                        returnBundle.putStringArray(key, stringArray);
                        //returnBundle.putParcelableArray(key, obj.get);
                    }
                    else {
                        if (key.equals("PLUGIN_CONFIG")) {
                            ArrayList<Bundle> bundleArray = new ArrayList<Bundle>();
                            for (int k = 0; k < length; k++)
                                bundleArray.add(toBundle(jsonArray.getJSONObject(k)));
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
