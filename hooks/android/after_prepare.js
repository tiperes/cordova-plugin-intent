const fs = require("fs");
const path = require('path');

// Enable support for AndroidX & Jetifier
function androidXUpgrade (context) {
    const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const gradlePropertiesPath = path.join(androidPlatformRoot, 'gradle.properties');
    
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

module.exports = function (context) {
    androidXUpgrade(context);
};
