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

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Windows service launcher for jpackage-packaged builds.
 *
 * <p>This class is used as the main entry point when windowMonitor is run as a
 * Windows service (e.g. via {@code sc.exe} or NSSM). Its responsibilities are:
 * <ol>
 *   <li><b>Start the main monitoring application</b> in a background thread.</li>
 *   <li><b>Perform a daily update check</b> using {@link AutoUpdater}.
 *       When an update is successfully applied the JVM is restarted so the new
 *       version takes effect immediately. If the update fails the current version
 *       continues running without interruption.</li>
 * </ol>
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
        log.info("WindowsServiceLauncher starting...");

        // Resolve the directory where the application JAR lives so that the
        // updater can locate release assets and write its sentinel file.
        Path installDir = resolveInstallDir();

        // Load update configuration (bundled defaults + optional external override)
        UpdateConfig config = UpdateConfig.load(installDir);

        // 1. Launch the monitoring application in a daemon thread so it does not
        //    prevent the JVM from shutting down when the service is stopped.
        Thread monitorThread = new Thread(() -> {
            try {
                WindowMonitorUploader.main(args);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Monitor application exited with an error.", e);
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
                        log.info("Update applied. Restarting JVM to load the new version...");
                        restartJvm(args);
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
            // If running from a JAR, the parent is the install directory.
            // If running from a class directory (IDE), use that directory directly.
            return codeSource.toFile().isFile()
                    ? codeSource.getParent()
                    : codeSource;
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not resolve install directory, using current directory.", e);
            return Path.of(".");
        }
    }

    /**
     * Restarts the JVM process by re-executing the java command with the same
     * arguments that were used to start the current process.
     *
     * <p>This is a best-effort restart; if the JVM cannot be restarted (e.g. the
     * process handle is not available), a warning is logged and the current process
     * continues running with the old version until it is stopped externally.
     *
     * @param originalArgs the {@code main} method arguments to pass to the new JVM
     */
    static void restartJvm(String[] originalArgs) {
        try {
            ProcessHandle current = ProcessHandle.current();
            ProcessHandle.Info info = current.info();

            String javaCmd = info.command().orElse(null);
            if (javaCmd == null) {
                log.warning("Cannot restart JVM: process command not available.");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder();
            pb.command().add(javaCmd);
            info.arguments().ifPresent(jvmArgs -> {
                for (String arg : jvmArgs) {
                    pb.command().add(arg);
                }
            });
            for (String arg : originalArgs) {
                pb.command().add(arg);
            }
            pb.inheritIO();
            pb.start();

            log.info("New process started. Exiting current process.");
            System.exit(0);
        } catch (Exception e) {
            log.log(Level.WARNING, "JVM restart failed; continuing with current version.", e);
        }
    }
}
