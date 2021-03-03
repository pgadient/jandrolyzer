# jandrolyzer
A scriptable lightweight tool for reconstructing web API URLs and JSON data structures from Android closed and open-source apps.
It supports the validation of found data. Use that feature with care as it could cause issues on API servers.

The tool relies on JADX and JavaParser.

> This is the accompanying material for our publication titled “Mining Web APIs in Android Applications”.

## Usage

You can find a compiled version (using OpenJDK 12) in the `/bin` folder.

The tool requires the following parameters:
 
- `lp` The folder path that contains external libraries required for resolving the imports used in the apps. You can get a ready to use copy of that folder from https://drive.google.com/file/d/1tfwFbw_DqrqjaVRbc_tx9P96BT3Kqpbl
- `ap` The file path to a single APK file to be analysed.
- `jp` The file path to the (non-GUI) JADX executable. You can get the latest one from https://github.com/skylot/jadx/releases
- `op` The folder path that will be used for the decompilation of the apps.
- `oj` The folder path that will contain the reconstructed JSON data.
- `os` The folder path that will contain information whether the analyses succeeded or encountered issues.


The tool supports these optional parameters:
- `asp` Used instead of `ap`. The path to a folder containing APK files to be analysed.
- `do` Decompilation only.
- `app` Analyzes web API server responses of an already analyzed project. **ONLY USE FEATURE WHEN YOU KNOW THE POTENTIAL IMPLICATIONS. USE IT WITH EXTREME CARE!**
- `pp` Used instead of `ap` or `asp`. The folder path to a single open-source app project to be analysed. 
- `psp` Used instead of `ap` or `asp`. The folder path that contains multiple open-source app projects to be analysed.  
- `http` Initiates HTTP requests for the collected web APIs. **ONLY USE FEATURE WHEN YOU KNOW THE POTENTIAL IMPLICATIONS. USE IT WITH EXTREME CARE!**
- `rd` Sets the maximal allowed recursion depth for variable resolving. `-1` can be used for unlimited recursion depth.

A typical command for the analysis of a single APK file looks like:

`java -jar jandrolyzer.jar -lp ./libraries -ap sample.apk -jp ./jadx/bin/jadx.bat -op ./decompiled -oj ./json -os ./state`

The tool supports current Debian and Windows operating systems and requires at least Java 8, respectively Java 12 for the compiled version.