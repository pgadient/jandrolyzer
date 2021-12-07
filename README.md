# jandrolyzer
A scriptable lightweight tool for reconstructing web API URLs and JSON data structures from Android closed and open-source apps.
It supports the validation of found data. Use that feature with care as it could cause issues on API servers.

The tool relies on JADX and JavaParser.

> This is the accompanying material for the publication titled “Web APIs in Android through the Lens of Security” that has been presented at the 27th IEEE International Conference on Software Analysis, Evolution and Reengineering (SANER 2020). The preprint can be downloaded [here](https://arxiv.org/abs/2001.00195) and a presentation that builds on the results of this tool is available [here](https://www.youtube.com/watch?v=Ylgu-0CFUXA).


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
- `pp` Used instead of `ap` or `asp`. The folder path to a single open-source app project to be analysed. 
- `psp` Used instead of `ap` or `asp`. The folder path that contains multiple open-source app projects to be analysed.
- `rd` Sets the maximal allowed recursion depth for variable resolving. `-1` can be used for unlimited recursion depth.  
- **ONLY USE THE FEATURE BELOW WHEN YOU ARE AWARE OF THE POSSIBLE IMPLICATIONS.**
    
  `app` Analyzes web API server responses of an already analyzed project. Requires the parameter `http` set to be `true`.

- **ONLY USE THE FEATURE BELOW WHEN YOU ARE AWARE OF THE POSSIBLE IMPLICATIONS.**
  
  `http` Can be set to `true` or `false`. Enables HTTP requests for the collected web APIs. 

### Example
A typical command for the analysis of a single APK file looks like:

```
java -jar jandrolyzer.jar -lp ./libraries -ap sample.apk -jp ./jadx/bin/jadx.bat -op ./decompiled -oj ./json -os ./state
```

The tool supports current Debian and Windows operating systems and requires at least Java 8, respectively Java 12 for the compiled jar file in the `/bin` folder.

## Generated output

### Console
The console output reveals errors during the analysis and presents the found web API data with the corresponding source code snippets.
The extracted web API data is also stored in the provided JSON folder (`oj`).
An example output for a successful analysis is shown below.

```Starting decompilation process...
Output stream: INFO  - loading ...
Output stream: INFO  - processing ...
Output stream: INFO  - done
Decompilation process completed.
Decompiled project path: .\decompiled\sample.apk, jadxSuccess: true, timer ran out: false
Analyzing project: .\decompiled\sample.apk
WARNING: An illegal reflective access operation has occurred
<...> (more errors and warnings during the decompilation process)
Found libraries: {android.core=23}
Added library: .\libraries\android.core-23
1 detected JSON models:
2 detected base API URLs:
Processed: 1 of 1
Name:
sample.apk
<...> (extracted web API data with corresponding code snippets)
Analyzing API endpoints of project: sample.apk
Preparing data...
Saved data
All done!
```

### Decompilation folder (`op`)
This folder contains the source code of the decompiled APK file.

### JSON folder (`oj`)
This folder contains the reconstructed JSON data structures. The detected data type is provided for each variable.
If a value could be traced back to a certain JSON variable, the resolved value is provided in place. An example is shown below.

```
<...> (found web API URLs)
<...> (found JSON key-value pairs)
<...> (found String variables with assigned values)
JSON DETAILS:
Path:
/home/decompiled/sample.apk/src/main/java/com/crashlytics/android/answers/SessionEventTransform.java
Library:
org.json
JSON Object: 
{"advertisingId":"<STRING>","buildId":"<STRING>","appVersionName":"<STRING>","type":"<STRING>","appVersionCode":"<STRING>","limitAdTrackingEnabled":"<BOOLEAN>","betaDeviceToken":"<STRING>","executionId":"<STRING>","customType":"<STRING>","osVersion":"<STRING>","predefinedType":"<STRING>","appBundleId":"<STRING>","deviceModel":"<STRING>","installationId":"<STRING>","androidId":"<STRING>"}
<...> (web API URLs with corresponding query parameters)
<...> (relevant code snippets)
```


### State folder (`os`)
This folder contains a file for each analysis that indicates whether the decompilation process and the subsequent analysis task succeeded. For example, `hasJadxErrors` indicates that the decompilation process skipped some class files, crashed, or had been terminated. `noJadxErrors` indicates that the decompilation process finished successfully.
