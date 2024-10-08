const fs = require("fs");
const path = require('path');
const et = require('elementtree');

function processCordovaVariables (context) {
    // Base paths
    const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const androidManifestPath = path.join(androidPlatformRoot, 'app/src/main/AndroidManifest.xml');
    
    // Init AndroidManifest control
    console.log('processCordovaVariables - 1');
    const androidManifestXml = et.parse(fs.readFileSync(androidManifestPath, 'utf-8'));
    const androidManifestNode = androidManifestXml.getroot();
    let manifestUpdated = false;
    console.log('processCordovaVariables - 2');
    // Queries permissions
    const packagesToIncludeCSV = context.opts.cli_variables.ANDROID_QUERIES_PACKAGES;
    const actionsToIncludeCSV = context.opts.cli_variables.ANDROID_QUERIES_ACTIONS;
    if (!packagesToIncludeCSV || !actionsToIncludeCSV) {
        // If missing, add queries node
        console.log('processCordovaVariables - 3');
        let queriestNode = androidManifestNode.find('./queries');
        if (queriestNode == null) {
            console.log('processCordovaVariables - 4');
            queriestNode = et.Element('queries');
            androidManifestNode.append(queriestNode);
        }
        console.log('processCordovaVariables - 5');
        if (!packagesToIncludeCSV) {
            packagesToIncludeCSV.split(',').forEach(package => {
                console.log('processCordovaVariables - 6');
                if (queriestNode.find(`./package[android:name="${package}"]`) == null) {
                    console.log('processCordovaVariables - 7');
                    queriestNode.append(et.Element('package', {"android:name": package}));
                    manifestUpdated = true;
                }
            });
            console.log('ANDROID_QUERIES_PACKAGES configured: ' + packagesToIncludeCSV);
        } else {
            console.log('ANDROID_QUERIES_PACKAGES not provided.');
        }
        
        if (!actionsToIncludeCSV) {
            actionsToIncludeCSV.split(',').forEach(actionMimeType => {
                const actionMimeTypeSplit = actionMimeType.split('|');
                const action = actionMimeTypeSplit[0];
                const mimeType = actionMimeTypeSplit.length == 1 ? '' : actionMimeTypeSplit[1];
                    
                if (queriestNode.find(`./intent/action[android:name="${action}"]`) == null) {
                    let intentNode = et.Element('intent');
                    intentNode.append(et.Element('action', { "android:name": action }));
                    if (mimeType) {
                        intentNode.append(et.Element('data', { "android:scheme": "content", "android:mimeType": mimeType }));
                    }
                    queriestNode.append(intentNode);
                    manifestUpdated = true;
                }
            });
            console.log('ANDROID_QUERIES_ACTIONS configured: ' + actionsToIncludeCSV);
        } else {
            console.log('ANDROID_QUERIES_ACTIONS not provided.');
        }
    }
    
    const permissionsToIncludeCSV = context.opts.cli_variables.ANDROID_USES_PERMISSIONS;
    if (!permissionsToIncludeCSV) {
        permissionsToIncludeCSV.split(',').forEach(permission => {
            if (androidManifestNode.find(`./uses-permission[android:name="${permission}"]`) == null) {
                androidManifestNode.append(et.Element('uses-permission', { "android:name": permission }));
                manifestUpdated = true;
            }
        });
        console.log('ANDROID_USES_PERMISSIONS configured: ' + permissionsToIncludeCSV);
    } else {
        console.log('ANDROID_USES_PERMISSIONS not provided.');
    }
    
    // Save updated AndroidManifest
    if (manifestUpdated) {
        fs.writeFileSync(androidManifestPath, strings.write({ indent: 4 }), 'utf-8');
        console.log('AndroidManifest.xml updated with requested permissions');
    }
}

module.exports = function (context) {
    processCordovaVariables(context);
};
