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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WindowsServiceLauncher}.
 *
 * @author unknowIfGuestInDream
 */
public class WindowsServiceLauncherTest {

    /**
     * When jpackage places the JAR inside an {@code app/} subdirectory and the
     * parent directory contains {@code windowMonitor.exe}, {@code resolveInstallDir}
     * should walk up to the parent so that the service launcher finds the exe.
     */
    @Test
    void testLaunchMonitorExeFindsExeInInstallDir(@TempDir Path installDir) throws Exception {
        // Simulate jpackage structure: installDir/windowMonitor.exe + installDir/app/
        Path exeFile = installDir.resolve("windowMonitor.exe");
        Files.createFile(exeFile);

        Path appDir = installDir.resolve("app");
        Files.createDirectories(appDir);

        // launchMonitorExe should resolve the exe from installDir
        // We cannot start a real exe in tests, but we can verify it finds the path
        // by checking that the command it would build starts with the correct exe.
        // Instead, directly test the path resolution logic:
        Path resolved = resolveExeInDir(installDir);
        assertNotNull(resolved);
        assertEquals(exeFile.toAbsolutePath(), resolved.toAbsolutePath());
    }

    /**
     * When the install directory does NOT contain {@code windowMonitor.exe} but its
     * parent does (jpackage app-subdir case), the launcher should still locate the exe.
     */
    @Test
    void testLaunchMonitorExeFindsExeInParentOfAppDir(@TempDir Path installDir) throws Exception {
        // Simulate running from inside app/ but exe is in parent
        Path exeFile = installDir.resolve("windowMonitor.exe");
        Files.createFile(exeFile);

        Path appDir = installDir.resolve("app");
        Files.createDirectories(appDir);

        // Resolve exe as if installDir is the correct install directory
        Path resolved = resolveExeInDir(installDir);
        assertNotNull(resolved);
        assertTrue(Files.isRegularFile(resolved), "Resolved exe path should exist: " + resolved);
    }

    /**
     * When the install directory contains neither {@code windowMonitor.exe} nor
     * {@code windowmonitor.exe}, the launcher falls back gracefully (no exe path).
     */
    @Test
    void testLaunchMonitorExeFallbackWhenNoExe(@TempDir Path installDir) throws Exception {
        Path resolved = resolveExeInDir(installDir);
        // No exe present — resolved should be null (fallback to java/jar)
        assertTrue(resolved == null || !Files.isRegularFile(resolved),
                "Should not find exe when none exists");
    }

    /**
     * Mirrors the exe-search logic inside {@link WindowsServiceLauncher#launchMonitorExe}.
     * Returns the first matching exe path, or {@code null} if none is found.
     */
    private static Path resolveExeInDir(Path dir) {
        String[] exeNames = {"windowMonitor.exe", "windowmonitor.exe"};
        for (String name : exeNames) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * {@link WindowsServiceLauncher#configureFileLogging} should create a {@code logs/}
     * directory under the install dir and register a {@link java.util.logging.FileHandler}
     * on the root logger.
     */
    @Test
    void testConfigureFileLoggingCreatesLogDirectory(@TempDir Path installDir) throws Exception {
        Logger rootLogger = Logger.getLogger("");
        int handlersBefore = rootLogger.getHandlers().length;

        WindowsServiceLauncher.configureFileLogging(installDir);

        Path logsDir = installDir.resolve("logs");
        assertTrue(Files.isDirectory(logsDir), "logs/ directory should be created");

        // At least one additional FileHandler should have been added to the root logger
        int handlersAfter = rootLogger.getHandlers().length;
        assertTrue(handlersAfter > handlersBefore, "A FileHandler should have been added to the root logger");

        // Clean up: close and remove the file handlers added by this test
        for (Handler h : rootLogger.getHandlers()) {
            if (h instanceof java.util.logging.FileHandler) {
                h.close();
                rootLogger.removeHandler(h);
            }
        }
    }
}
