const fs = require("fs");
const path = require('path');
const et = require('elementtree');

function processCordovaVariables (context) {
    const packagesToIncludeCSV = context.opts.cli_variables.ANDROID_QUERIES_PACKAGES;
    if (!packagesToIncludeCSV) {
        console.log('ANDROID_QUERIES_PACKAGES variable not provided.');
        return;
    }
    
    const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const androidManifestPath = path.join(androidPlatformRoot, 'app/src/main/AndroidManifest.xml');
    
    let androidManifestXml = et.parse(fs.readFileSync(androidManifestPath, 'utf-8'));
    let androidManifestRoot = androidManifestXml.getroot();
    let queriesXmlNode = androidManifestRoot.find('./queries');
    if (queriesXmlNode == null) {
        queriesXmlNode = et.fromstring('<queries/>');
        androidManifestRoot.append(queriesXmlNode);
    }
    packagesToIncludeCSV.split(',').forEach(package => {
        if (queriesXmlNode.find(`./package[android:name="${package}"]`) == null) {
            queriesXmlNode.append(et.fromstring(`<package android:name="${package}"/>`));
        }
    });

    fs.writeFileSync(androidManifestPath, androidManifestXml.write({ indent: 4 }), 'utf-8');
}

module.exports = function (context) {
    processCordovaVariables(context);
};
