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
 *   <li><b>Start the main monitoring application</b> ({@link WindowMonitorUploader})
 *       in a background thread within the same JVM process.</li>
 *   <li><b>Perform a daily update check</b> using {@link AutoUpdater}.
 *       When an update is successfully applied the service should be restarted
 *       externally (e.g. by the service manager) so the new version takes effect.
 *       If the update fails the current version continues running without
 *       interruption.</li>
 * </ol>
 *
 * <p><b>Important:</b> The monitoring logic ({@link WindowMonitorUploader}) is
 * executed directly in-process rather than by spawning a child process.  Spawning
 * {@code windowMonitor.exe} from within a service that itself started via
 * {@code windowMonitor.exe} would create an infinite chain of processes.
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

        // 1. Run the monitoring application in-process on a dedicated thread.
        //    We deliberately do NOT spawn a child process here: launching
        //    windowMonitor.exe from within a service whose entry point is also
        //    WindowsServiceLauncher would cause an infinite spawn loop (each child
        //    would spawn another child, exhausting memory).
        final String[] monitorArgs = args;
        Thread monitorThread = new Thread(() -> {
            try {
                log.info("Starting WindowMonitorUploader in-process...");
                WindowMonitorUploader.main(monitorArgs);
            } catch (Exception e) {
                log.log(Level.SEVERE, "WindowMonitorUploader terminated with an error.", e);
            }
        }, "monitor-main");
        monitorThread.setDaemon(false);
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
     * Resolves the directory from which the application JAR was loaded.
     *
     * <p>Falls back to the current working directory if the code location cannot
     * be determined (e.g. when running from an IDE or exploded classpath).
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
            return dir != null ? dir : Path.of(".");
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not resolve install directory, using current directory.", e);
            return Path.of(".");
        }
    }
}
