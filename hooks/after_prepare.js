const fs = require("fs");
const et = require('elementtree');

// Enable support for AndroidX & Jetifier
function androidXUpgrade (ctx) {
    if (!ctx.opts.platforms.includes('android'))
        return;
    
    const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const gradlePropertiesPath = path.join(androidPlatformRoot, 'platforms/androidgradle.properties');
    
    let gradlePropertiesStr = fs.readFileSync(gradlePropertiesPath, 'utf-8');
    if (gradlePropertiesStr) {
        const enableAndroidX = "android.useAndroidX=true";
        const enableJetifier = "android.enableJetifier=true";
        const isAndroidXEnabled = gradlePropertiesStr.includes(enableAndroidX);
        const isJetifierEnabled = gradlePropertiesStr.includes(enableJetifier);

        if (isAndroidXEnabled && isJetifierEnabled)
            return;

        if (isAndroidXEnabled === false)
            gradlePropertiesStr += "\n" + enableAndroidX;

        if (isJetifierEnabled === false)
            gradlePropertiesStr += "\n" + enableJetifier;

        fs.writeFileSync(gradlePropertiesPath, gradlePropertiesStr, 'utf-8');
    }
}

// Update Manifest to Whitelisting Android Package Visibility Needs
function androidPackagesWhitelisting (ctx) {
    if (!ctx.opts.platforms.includes('android'))
        return;
    
    const packagesToIncludeCSV = context.opts.cli_variables.CORDOVA_ANDROID_PACKAGES;
    if (!packagesToIncludeCSV) {
        console.log('CORDOVA_ANDROID_PACKAGES variable not provided.');
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

module.exports = function (ctx) {
    androidXUpgrade(ctx);
    androidPackagesWhitelisting(ctx);
};
