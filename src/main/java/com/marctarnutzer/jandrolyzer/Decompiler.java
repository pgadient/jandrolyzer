//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 31.10.2018.
//  Copyright © 2018 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Decompiler {

    private String pathToAPK;
    private String pathToAPKsFolder;
    private String pathToJadx;
    private String outputPath;
    private String successPath;
    //private Timer timer = new Timer();
    private boolean timerRanOut = false;

    public Decompiler(String pathToAPK, String pathToAPKsFolder, String pathToJadx, String outputPath, String successPath) {
        this.pathToAPK = pathToAPK;
        this.pathToAPKsFolder = pathToAPKsFolder;
        this.pathToJadx = pathToJadx;
        this.outputPath = outputPath;
        this.successPath = successPath;
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

        try {
            File file = new File(path);
            String op = Paths.get(outputPath, file.getName()).toString();

            List<String> jadxCommand = new ArrayList<>(Arrays.asList(pathToJadx, "-d", op, "-e", path));

            ProcessBuilder processBuilder = new ProcessBuilder(jadxCommand);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            //rescheduleTimer(process);

            BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String outputLine;
            while ((outputLine = inputStream.readLine()) != null) {
                System.out.println("Output stream: " + outputLine);
                //rescheduleTimer(process);
            }
            inputStream.close();

            process.waitFor();

            //timer.cancel();
            //timer.purge();

            if (process.exitValue() == 0) {
                System.out.println("Decompilation process completed.");
                markProject(true, op);
            } else {
                System.out.println("Decompilation process completed with errors. ExitValue: " + process.exitValue());
                markProject(false, op);
            }

            return op;
        } catch (IOException e) {
            System.out.println("IOException while decompiling APK");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.out.println("InterruptedException while decompiling APK");
            e.printStackTrace();
        }

        return null;
    }

    /*
    private void rescheduleTimer(Process process) {
        timer.cancel();
        timer.purge();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                System.out.println("Timer ran out. Stopping decompilation");
                timerRanOut = true;
                process.destroyForcibly();
            }
        }, 600000); // Waits for 10 Minutes before stopping the decompilation
    }
    */

    private void markProject(boolean jadxSuccess, String decompiledProjectPath) {
        System.out.println("Decompiled project path: " + decompiledProjectPath + ", jadxSuccess: " + jadxSuccess
                + ", timer ran out: " + timerRanOut);

        if (decompiledProjectPath == null) {
            return;
        }

        String fileName = new File(decompiledProjectPath).getName() + ".success";
        //String fileName;
        //if (jadxSuccess) {
        //    fileName = "noJadxErrors";
        //} else {
        //    fileName = "hasJadxErrors";
        //}
        //if (timerRanOut) {
        //    fileName = "abortedDecompilation";
        //}

        Path filePath = Paths.get(successPath, fileName);
        File file = new File(filePath.toString());
        try {
            file.createNewFile();
            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);

            if (jadxSuccess) {
                bw.write("noJadxErrors");
            } else {
                if (timerRanOut) {
                    bw.write(fileName = "abortedDecompilation");
                } else {
                    bw.write("hasJadxErrors");
                }
            }

            bw.close();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
