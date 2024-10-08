const q = require('q');
const path = require('path');
const fs = require('fs');
const xml2js = require('xml2js');

module.exports = function (context) {
    // Check if any expected client variable is defined
    const packagesToIncludeCSV = context.opts.cli_variables.ANDROID_QUERIES_PACKAGES;
    const actionsToIncludeCSV = context.opts.cli_variables.ANDROID_QUERIES_ACTIONS;
    const permissionsToIncludeCSV = context.opts.cli_variables.ANDROID_USES_PERMISSIONS;
    
    if (packagesToIncludeCSV) {
        console.log('ANDROID_QUERIES_PACKAGES configured: ' + packagesToIncludeCSV);
    } else {
        console.log('ANDROID_QUERIES_PACKAGES not provided.');
    }
    if (actionsToIncludeCSV) {
        console.log('ANDROID_QUERIES_ACTIONS configured: ' + actionsToIncludeCSV);
    } else {
        console.log('ANDROID_QUERIES_ACTIONS not provided.');
    }
    if (permissionsToIncludeCSV) {
        console.log('ANDROID_USES_PERMISSIONS configured: ' + permissionsToIncludeCSV);
    } else {
        console.log('ANDROID_USES_PERMISSIONS not provided.');
    }
    if (!packagesToIncludeCSV && !actionsToIncludeCSV && !permissionsToIncludeCSV) {
        return;
    }
    
    const deferral = new q.defer();

    // Path to the AndroidManifest.xml file
    const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const androidManifestPath = path.join(androidPlatformRoot, 'app/src/main/AndroidManifest.xml');
    // Read AndroidManifest.xml
    fs.readFile(androidManifestPath, 'utf8', (err, data) => {
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
            
            if (packagesToIncludeCSV || actionsToIncludeCSV) {
                // Check if <queries> element already exists
                let queriesNode = result['manifest']['queries'];
                if (!queriesNode) {
                    // Append <queries> element to the root
                    queriesNode = [];
                    result['manifest']['queries'] = queriesNode;
                } else {
                    console.log('<queries> node already exists.');
                }
            
                // Process the required packages
                if (packagesToIncludeCSV) {
                    packagesToIncludeCSV.split(',').forEach(packageName => {
                        let packageExists = false;
                        queriesNode.forEach(query => {
                            if (query.package) {
                                query.package.forEach(package => {
                                    if (package.$ && package.$['android:name'] == packageName) {
                                        packageExists = true;
                                    }
                                });
                            }
                            
                        });
                        if (!packageExists) {
                            queriesNode.push({
                                package: [{ $: { 'android:name': packageName } }]
                            });
                            console.log(`Package '${packageName}' added to <queries> node.`);
                        } else {
                            console.log(`Package '${packageName}' already exists.`);
                        }
                    });
                }
                
                // Process the required actions
                if (actionsToIncludeCSV) {
                    actionsToIncludeCSV.split(',').forEach(actionNameMimeType => {
                        const actionNameMimeTypeSplit = actionNameMimeType.split('|');
                        const actionName = actionNameMimeTypeSplit[0];
                        const mimeType = actionNameMimeTypeSplit.length == 1 ? '' : actionNameMimeTypeSplit[1];
                        
                        let actionExists = false;
                        queriesNode.forEach(query => {
                            if (query.intent) {
                                query.intent.forEach(intent => {
                                    if (intent.action) {
                                        intent.action.forEach(action => {
                                            if (action.$ && action.$['android:name'] === actionName') {
                                                actionExists = true;
                                            }
                                        });
                                    }
                                });
                            }
                        });
                        if (!actionExists) {
                            const intentNode = [{
                                action: [{ $: { 'android:name': actionName } }]
                            }];
                            if (mimeType) {
                                intentNode.push({
                                    data: [{ $: { 'android:scheme': 'content', 'android:mimeType': mimeType } }]
                                });
                            }
                            queriesNode.push({
                                intent: intentNode
                            });
                            console.log(`Intent action '${actionName}' with mime type '${mimeType}' added <queries> to node.`);
                        } else {
                            console.log(`Intent action '${actionName}' with mime type '${mimeType}' already exists.`);
                        }
                    });
                }
            }
            
            // Process the required packages
            if (permissionsToIncludeCSV) {
                permissionsToIncludeCSV.split(',').forEach(permissionName => {
                    let permissionsNode = result['manifest']['uses-permission'];
                    if (!permissionsNode) {
                        // Append <uses-permission> element to the root
                        permissionsNode = [];
                        result['manifest']['uses-permission'] = queriesNode;
                    } else {
                        console.log('<uses-permission> node already exists.');
                    }
                    
                    permissionsNode.forEach(permission => {
                        if (permission.$ && permission.$['android:name'] == permissionName) {
                            permissionExists = true;
                        }
                    });
                    if (!permissionExists) {
                        permissionsNode.push({ $: { 'android:name': permissionName } });
                        console.log(`Uses Permission '${permissionName}' added <queries> to node.`);
                    } else {
                        console.log(`Uses Permission '${permissionName}' already exists.`);
                    }
                });
            }

            // Convert the updated JavaScript object back to XML
            const builder = new xml2js.Builder();
            const updatedManifest = builder.buildObject(result);

            // Write the updated XML back to AndroidManifest.xml
            fs.writeFile(androidManifestPath, updatedManifest, 'utf8', (err) => {
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
