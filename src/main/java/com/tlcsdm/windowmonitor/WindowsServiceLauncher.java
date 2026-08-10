/*
 * Copyright (c) 2025 unknowIfGuestInDream.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of unknowIfGuestInDream, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UNKNOWIFGUESTINDREAM BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.tlcsdm.windowmonitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Windows service launcher for jpackage-packaged builds.
 *
 * <p>This class is used as the main entry point when windowMonitor is run as a
 * Windows service (e.g. via {@code sc.exe} or NSSM). Its responsibilities are:
 * <ol>
 *   <li><b>Start the main monitoring application</b> by launching the installed
 *       {@code windowMonitor.exe} from the install directory as a child process.
 *       This ensures that the installed version of the application is always used,
 *       and that any update applied to the install directory takes effect on the
 *       next service restart.</li>
 *   <li><b>Perform a daily update check</b> using {@link AutoUpdater}.
 *       When an update is successfully applied the service should be restarted
 *       externally (e.g. by the service manager) so the new version takes effect.
 *       If the update fails the current version continues running without
 *       interruption.</li>
 * </ol>
 *
 * <p><b>Avoiding the recursive-spawn loop:</b> The Windows service is configured
 * to invoke {@code windowMonitor.exe} with this class ({@code WindowsServiceLauncher})
 * as the main entry point.  When this launcher then starts the installed
 * {@code windowMonitor.exe}, it must NOT forward the original command-line
 * arguments: those arguments contain the class-override that would cause the child
 * process to also run {@code WindowsServiceLauncher}, spawning another child, and
 * so on indefinitely.  The child exe is therefore always started with no extra
 * arguments, causing it to use the JAR manifest's default main class
 * ({@link WindowMonitorUploader}).
 *
 * <p>The install directory is derived from the location of the running JAR file.
 * The update configuration is loaded from {@code update.properties} on the
 * classpath, with an optional override file in the same directory.
 *
 * @author unknowIfGuestInDream
 */
public class WindowsServiceLauncher {

    private static final Logger log = Logger.getLogger(WindowsServiceLauncher.class.getName());

    public static void main(String[] args) throws Exception {
        // Resolve install dir early so we can direct logs there from the start.
        Path installDir = resolveInstallDir();
        configureFileLogging(installDir);

        log.info("WindowsServiceLauncher starting...");

        // Load update configuration (bundled defaults + optional external override)
        UpdateConfig config = UpdateConfig.load(installDir);

        // 1. Launch the installed windowMonitor.exe from the install directory.
        //    The child process is started WITHOUT forwarding the original args.
        //    Forwarding args would propagate the WindowsServiceLauncher class-override
        //    into the child, causing the child to spawn another child — an infinite
        //    recursive loop that exhausts memory.  With no extra args the child exe
        //    runs the JAR manifest's default main class (WindowMonitorUploader).
        Process monitorProcess = launchMonitorExe(installDir);

        // Wrap the process in a thread so we can join() on it below.
        Thread monitorThread = new Thread(() -> {
            try {
                int exitCode = monitorProcess.waitFor();
                log.info("Monitor process exited with code: " + exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitorProcess.destroyForcibly();
            }
        }, "monitor-main");
        monitorThread.setDaemon(true);
        monitorThread.start();

        // 2. Perform a daily update check on a dedicated background thread.
        //    The check runs once at startup (the AutoUpdater itself decides whether
        //    to skip based on the sentinel file) and then once per day thereafter.
        Thread updateThread = new Thread(() -> {
            AutoUpdater updater = new AutoUpdater(config, installDir);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    boolean updated = updater.checkAndUpdate();
                    if (updated) {
                        log.info("Update applied. Please restart the service to load the new version.");
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Unexpected error in update thread.", e);
                }

                // Sleep for 24 hours before the next check
                try {
                    Thread.sleep(24L * 60 * 60 * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "auto-updater");
        updateThread.setDaemon(true);
        updateThread.start();

        // Keep the main thread alive while the monitor is running
        monitorThread.join();
    }

    /**
     * Configures a rolling file handler that writes log records to
     * {@code logs/windowMonitor.log} inside the install directory.
     *
     * <p>Up to 10 log files of 10 MB each are kept (rotating). A
     * {@link SimpleFormatter} is used so the output is human-readable.
     * If the log directory cannot be created or the handler cannot be
     * attached, a warning is printed to stderr and the application
     * continues using only the console handler.
     *
     * @param installDir the directory that contains the installed application
     */
    static void configureFileLogging(Path installDir) {
        try {
            Path logDir = installDir.resolve("logs");
            Files.createDirectories(logDir);
            String pattern = logDir.resolve("windowMonitor%g.log").toString();
            // 10 MB per file, 10 rotating files, append mode
            FileHandler fileHandler = new FileHandler(pattern, 10 * 1024 * 1024, 10, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("[WindowsServiceLauncher] Failed to configure file logging: " + e.getMessage());
        }
    }

    /**
     * Launches the packaged {@code windowMonitor.exe} from the install directory.
     *
     * <p>Candidate executable names are tried in order: {@code windowMonitor.exe},
     * then {@code windowmonitor.exe} (case-insensitive fallback).  If neither is
     * found, the method falls back to launching the JAR with the bundled JRE so
     * that the service continues to function even in non-packaged deployments.
     *
     * <p><b>No args are forwarded to the child process.</b>  The original
     * command-line arguments that launched this {@code WindowsServiceLauncher}
     * contain a class-override entry that would make the child exe re-run
     * {@code WindowsServiceLauncher} instead of {@code WindowMonitorUploader},
     * restarting the spawn loop.  By passing no extra arguments the child exe
     * uses the JAR manifest's default main class ({@link WindowMonitorUploader}).
     *
     * @param installDir the directory that contains the installed application
     * @return the started {@link Process}
     * @throws Exception if the process cannot be started
     */
    static Process launchMonitorExe(Path installDir) throws Exception {
        // Preferred executable names (jpackage output on Windows)
        String[] exeNames = {"windowMonitor.exe", "windowmonitor.exe"};
        Path exePath = null;
        for (String name : exeNames) {
            Path candidate = installDir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                exePath = candidate;
                break;
            }
        }

        List<String> command = new ArrayList<>();
        if (exePath != null) {
            command.add(exePath.toString());
        } else {
            // Fallback: run via the bundled JRE if available, otherwise plain java
            Path javaExe = installDir.resolve(Path.of("runtime", "bin", "java.exe"));
            if (!Files.isRegularFile(javaExe)) {
                javaExe = installDir.resolve(Path.of("jre", "bin", "java.exe"));
            }
            command.add(Files.isRegularFile(javaExe) ? javaExe.toString() : "java");
            // Locate the application JAR in the install directory
            Path appJar = installDir.resolve("windowMonitor.jar");
            if (!Files.isRegularFile(appJar)) {
                // Try app/ sub-directory used by jpackage
                appJar = installDir.resolve(Path.of("app", "windowMonitor.jar"));
            }
            command.add("-jar");
            command.add(appJar.toString());
        }

        // NOTE: original args are intentionally NOT forwarded — see Javadoc above.

        log.info("Launching monitor process: " + command);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        return pb.start();
    }

    /**
     * Resolves the directory from which the application JAR was loaded.
     *
     * <p>Falls back to the current working directory if the code location cannot
     * be determined (e.g. when running from an IDE or exploded classpath).
     *
     * <p>When jpackage packages the application, the JAR is placed inside an
     * {@code app/} subdirectory while the launcher executable ({@code windowMonitor.exe})
     * resides in the parent directory. This method automatically walks up to the
     * parent when the resolved directory is named {@code app} and the parent
     * directory contains the launcher executable, so that {@link #launchMonitorExe}
     * can locate {@code windowMonitor.exe} regardless of which executable (including
     * an NSSM install helper) was used to start the service.
     *
     * @return the install directory {@link Path}
     */
    static Path resolveInstallDir() {
        try {
            Path codeSource = Path.of(
                    WindowsServiceLauncher.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            // If running from a JAR, the parent is the directory containing that JAR.
            // If running from a class directory (IDE), use that directory directly.
            Path dir = codeSource.toFile().isFile()
                    ? codeSource.getParent()
                    : codeSource;

            // jpackage places the JAR inside an "app/" subdirectory while the
            // launcher exe lives in the parent. Walk up so that launchMonitorExe
            // can find windowMonitor.exe even when the service was registered via
            // an NSSM helper exe located in the parent directory.
            if (dir != null && "app".equalsIgnoreCase(dir.getFileName().toString())) {
                Path parent = dir.getParent();
                if (parent != null && (Files.isRegularFile(parent.resolve("windowMonitor.exe"))
                        || Files.isRegularFile(parent.resolve("windowmonitor.exe")))) {
                    log.info("Detected jpackage app/ subdirectory; using parent as install dir: " + parent);
                    return parent;
                }
            }

            return dir != null ? dir : Path.of(".");
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not resolve install directory, using current directory.", e);
            return Path.of(".");
        }
    }
}
