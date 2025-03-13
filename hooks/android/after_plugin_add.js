const path = require('path');
const fs = require('fs');
const axios = require('axios');

module.exports = async function (context) {
	try {
		const apiUrl = context.opts.cli_variables['ANDROID_PROCESS_MANIFEST_API'];
		const apiAuthorization = context.opts.cli_variables['ANDROID_PROCESS_MANIFEST_AUTHORIZATION'];
		if (!apiUrl) {
			console.log('ANDROID_PROCESS_MANIFEST: Not required');
			return;
		}
		
		console.log('ANDROID_PROCESS_MANIFEST: Required using ' + apiUrl);
		
		// Path to the AndroidManifest.xml file
		const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
		const androidManifestPath = path.join(androidPlatformRoot, 'app/src/main/AndroidManifest.xml');
		let manifestXml = null;
		
		// Read AndroidManifest.xml
		try {
			manifestXml = await fs.promises.readFile(androidManifestPath, 'utf8');
		}
		catch (err) {
			throw new Error('Failed to read AndroidManifest.xml', { cause: err });
		}
		
		// Prepare Request Header
		const apiConfig = {
			headers: {
				'Content-Type': 'application/json',
				...(apiAuthorization ? { 'Authorization': apiAuthorization } : {}) // Only add if defined
			}
		};
		
		// Prepare Request Body
		const apiData = {
			timestamp: new Date().toISOString(),
			plugin: context.opts.plugin.id,  // Plugin ID being added
			platform: context.opts.platforms, // Platforms affected
			manifestXml: manifestXml
		};
		
		// Call the API to configure the manifest
		let apiResponse;
		try {
			apiResponse = await axios.post(apiUrl, apiData, apiConfig);
		} catch (err) {
			let serializedError = {
				message: err.message,
				response: err.response ? {
					status: err.response.status,
					statusText: err.response.statusText,
					data: err.response.data
				} : undefined
			};
			throw new Error('Failed to call the API', { cause: serializedError });
		}
		
		// If the API modified the manifest, write it back
        if (apiResponse.data && apiResponse.data !== manifestXml) {
			// Write AndroidManifest.xml
			try {
				await fs.promises.writeFile(androidManifestPath, apiResponse.data, 'utf8');
				console.log('ANDROID_PROCESS_MANIFEST: Success, and the manifest has been modified');
			}
			catch (err) {
				throw new Error('Failed to write AndroidManifest.xml', { cause: err });
			}
		}
		else
			console.log('ANDROID_PROCESS_MANIFEST: Success, and manifest has been validated');
	}
	catch (err) {
		console.error('ANDROID_PROCESS_MANIFEST: Fail', err);
		throw new Error('ANDROID_PROCESS_MANIFEST: Fail', { cause: err });
	}
}
