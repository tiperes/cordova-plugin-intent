const fs = require('fs');
const path = require('path');
const et = require('elementtree');

// Function to merge the manifests
function mergeManifests(context) {
    let removeDuplicates = false;
    let xmlNodePath2ContentXml = [];
    Object.keys(context.opts.cli_variables).forEach((name) => {
        if (name == 'ANDROID_REMOVE_DUPLICATES') {
            removeDuplicates = context.opts.cli_variables[name] == 'true';
        } else {
            xmlNodePath2ContentXml.push(name, context.opts.cli_variables[name]);
        }
    });
    
    if (xmlNodePath2ContentXml.length == 0) {
        console.log('No Manifest changes todo.');
        return;
    }
    
    const androidPlatformRoot = path.join(context.opts.projectRoot, 'platforms/android');
    const originalManifestPath = path.join(androidPlatformRoot, 'app/src/main/AndroidManifest.xml');
    if (!fs.existsSync(originalManifestPath)) {
        console.error('AndroidManifest.xml not found at:', originalManifestPath);
        return;
    }
    
    const originalManifestContent = fs.readFileSync(originalManifestPath, 'utf-8');
    const originalManifestTree = et.parse(originalManifestContent);
    
    xmlNodePath2ContentXml.forEach((nodePath) => {
        // Start by removing duplicates
        if (removeDuplicates) {
            const parent = originalManifestTree.find(nodePath + '/*');
            if (parent != null) {
                var children = parent.getchildren();
                for (let i = 0; i < children.length; i++) {
                    for (let j = i; j < children.length; j++) {
                        if (et.equalNodes(children[i], children[j])) {
                            originalManifestTree.remove(children[j]);
                            children.splice(j, 1);
                            break;
                        }
                    }
                }
            }
        }
        
        // Append only if the node does not exist already
        const nodeChildren = et.parse('<root>' + xmlNodePath2ContentXml[nodePath] + '</root>').getRoot().getchildren();
        if (nodeChildren.length > 0) {
            et.graftXML(originalManifestTree, nodeChildren, nodePath);
        }
    });

    // Write the updated manifest back to the original file
    fs.writeFileSync(originalManifestPath, originalManifestTree.write({ indent: 4 }), 'utf-8');
    console.log('AndroidManifest.xml successfully updated.');
}

module.exports = function (context) {
    mergeManifests(context);
}
