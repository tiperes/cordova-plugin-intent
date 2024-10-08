const q = require('q');
const fs = require('fs');
const path = require('path');
const xml2js = require('xml2js');

module.exports = function (context) {
    // Check if any expected client variable is defined
    const packagesToIncludeCSV = context.opts.cli_variables.ANDROID_QUERIES_PACKAGES;
    if (!packagesToIncludeCSV) {
        console.log('ANDROID_QUERIES_PACKAGES variable not provided.');
        return;
    }
    
    const deferral = new q.defer();

    // Path to the AndroidManifest.xml file
    const manifestPath = path.join(context.opts.projectRoot, 'platforms', 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
    // Read AndroidManifest.xml
    fs.readFile(manifestPath, 'utf8', (err, data) => {
        if (err) {
            console.error('Failed to read AndroidManifest.xml:', err);
            deferral.reject();
            return;
        }

        // Parse the XML to JavaScript object
        xml2js.parseString(data, (err, result) => {
            if (err) {
                console.error('Failed to parse AndroidManifest.xml:', err);
                deferral.reject();
                return;
            }

            // Check if <queries> element already exists
            let queriesNode = result.manifest.queries;
            if (!queriesNode) {
                // Append <queries> element to the root
                queriesNode = [];
                result.manifest.queries = queriesNode;
            } else {
                console.log('<queries> node already exists.');
            }
            
            // Process the required packages
            packagesToIncludeCSV.split(',').forEach(packageName => {
                let packageExists = false;
                queriesNode.forEach(query => {
                    if (query.package && query.package.$ && query.package.$['android:name'] == packageName) {
                        packageExists = true;
                    }
                });
                if (!packageExists) {
                    queriesNode.push({ package: { $: { 'android:name': packageName } } });
                    console.log('Added <package android:name="android.intent.action.VIEW" /> to <queries> node.');
                } else {
                    console.log('<package android:name="android.intent.action.VIEW" /> already exists.');
                }
            });

            // Convert the updated JavaScript object back to XML
            const builder = new xml2js.Builder();
            const updatedManifest = builder.buildObject(result);

            // Write the updated XML back to AndroidManifest.xml
            fs.writeFile(manifestPath, updatedManifest, 'utf8', (err) => {
                if (err) {
                    console.error('Failed to write AndroidManifest.xml:', err);
                    deferral.reject();
                } else {
                    console.log('Successfully updated AndroidManifest.xml with <queries> node.');
                    deferral.resolve();
                }
            });
        });
    });

    return deferral.promise;
};
