//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 31.10.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Decompiler {

    private String pathToAPK;
    private String pathToAPKsFolder;
    private String pathToJadx;
    private String outputPath;

    public Decompiler(String pathToAPK, String pathToAPKsFolder, String pathToJadx, String outputPath) {
        this.pathToAPK = pathToAPK;
        this.pathToAPKsFolder = pathToAPKsFolder;
        this.pathToJadx = pathToJadx;
        this.outputPath = outputPath;
    }

    public ArrayList<String> startDecompilation() {
        ArrayList<String> pathsToReturn = new ArrayList<>();

        if (pathToAPK != null) {
            String path = decompile(this.pathToAPK);
            if (path != null) {
                pathsToReturn.add(path);
            }
        } else if (pathToAPKsFolder != null) {
            File apkFolder = new File(pathToAPKsFolder);
            File[] apkFiles = apkFolder.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith(".apk");
                }
            });

            for (File file : apkFiles) {
                String path = decompile(file.getPath());
                if (path != null) {
                    pathsToReturn.add(path);
                }
            }
        }

        return pathsToReturn;
    }

    private String decompile(String path) {
        System.out.println("Starting decompilation process...");

        Runtime runtime = Runtime.getRuntime();

        try {
            File file = new File(path);
            String op = Paths.get(outputPath, file.getName()).toString();

            String jadxCommand = pathToJadx + " -d " + op + " -e " + path;
            Process process = runtime.exec(jadxCommand);

            BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String outputLine;

            while ((outputLine = inputStream.readLine()) != null) {
                System.out.println("IStream: " + outputLine);
            }

            inputStream.close();

            boolean encounteredError = false;
            while ((outputLine = errorStream.readLine()) != null) {
                System.out.println("EStream: " + outputLine);
                encounteredError = true;
            }

            errorStream.close();

            process.waitFor(); // Wait for jadx decompilation process to terminate

            if (encounteredError) {
                System.out.println("Decompilation process completed with errors.");
            } else {
                System.out.println("Decompilation process completed.");
                return op;
            }
        } catch (IOException e) {
            System.out.println("IOException while decompiling APK");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("InterruptedException while decompiling APK");
            e.printStackTrace();
        }

        return null;
    }

}
