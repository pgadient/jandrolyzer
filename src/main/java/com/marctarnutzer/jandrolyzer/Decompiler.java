//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 31.10.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

    public void startDecompilation() {
        if (pathToAPK != null) {
            decompile(this.pathToAPK);
        } else if (pathToAPKsFolder != null) {
            // TODO: iterate through folder and call decompile() on each APK file
        }
    }

    private void decompile(String path) {
        System.out.println("Starting decompilation process...");

        Runtime runtime = Runtime.getRuntime();

        try {
            String jadxCommand = pathToJadx + " -d " + outputPath + " -e " + path;
            System.out.println("Command: " + jadxCommand);
            Process process = runtime.exec(jadxCommand);

            BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String outputLine;

            while ((outputLine = inputStream.readLine()) != null) {
                System.out.println(outputLine);
            }

            inputStream.close();

            while ((outputLine = errorStream.readLine()) != null) {
                System.out.println(outputLine);
            }

            errorStream.close();

            process.waitFor(); // Wait for jadx decompilation process to terminate

            System.out.println("Decompilation process completed.");
        } catch (IOException e) {
            System.out.println("IOException while decompiling APK");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("InterruptedException while decompiling APK");
            e.printStackTrace();
        }

    }

}
