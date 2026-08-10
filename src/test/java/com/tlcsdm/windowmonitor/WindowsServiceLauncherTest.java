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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WindowsServiceLauncher}.
 *
 * @author unknowIfGuestInDream
 */
public class WindowsServiceLauncherTest {

    /**
     * {@link WindowsServiceLauncher#resolveInstallDir} should return a non-null path
     * and not throw an exception when called from within the test classpath.
     */
    @Test
    void testResolveInstallDirReturnsNonNull() {
        Path dir = WindowsServiceLauncher.resolveInstallDir();
        assertNotNull(dir, "resolveInstallDir() should return a non-null path");
        assertTrue(dir.toString().length() > 0, "resolveInstallDir() should return a non-empty path");
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
