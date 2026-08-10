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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AutoUpdater} and {@link UpdateConfig}.
 *
 * @author unknowIfGuestInDream
 */
public class AutoUpdaterTest {

    // -------------------------------------------------------------------------
    // UpdateConfig tests
    // -------------------------------------------------------------------------

    @Test
    void testUpdateConfigLoadsBundledDefaults() throws IOException {
        UpdateConfig config = UpdateConfig.load(null);
        assertNotNull(config.getApiUrl());
        assertFalse(config.getApiUrl().isBlank());
        assertNotNull(config.getDownloadBaseUrl());
        assertNotNull(config.getAssetName());
        assertNotNull(config.getCurrentVersion());
        assertTrue(config.isEnabled());
    }

    @Test
    void testUpdateConfigExternalOverride(@TempDir Path dir) throws IOException {
        // Write an external override that disables updates
        Files.writeString(dir.resolve("update.properties"),
                "update.enabled=false\nupdate.currentVersion=2.0.0\n");

        UpdateConfig config = UpdateConfig.load(dir);
        assertFalse(config.isEnabled());
        assertEquals("2.0.0", config.getCurrentVersion());
    }

    // -------------------------------------------------------------------------
    // AutoUpdater.isNewer tests
    // -------------------------------------------------------------------------

    @Test
    void testIsNewerReturnsTrueWhenLatestIsNewer() {
        assertTrue(AutoUpdater.isNewer("2.0.0", "1.0.0"));
        assertTrue(AutoUpdater.isNewer("1.1.0", "1.0.0"));
        assertTrue(AutoUpdater.isNewer("1.0.1", "1.0.0"));
        assertTrue(AutoUpdater.isNewer("1.0.0", "0.9.9"));
    }

    @Test
    void testIsNewerReturnsFalseWhenSameVersion() {
        assertFalse(AutoUpdater.isNewer("1.0.0", "1.0.0"));
    }

    @Test
    void testIsNewerReturnsFalseWhenLatestIsOlder() {
        assertFalse(AutoUpdater.isNewer("1.0.0", "2.0.0"));
        assertFalse(AutoUpdater.isNewer("0.9.9", "1.0.0"));
    }

    @Test
    void testIsNewerHandlesNulls() {
        assertFalse(AutoUpdater.isNewer(null, "1.0.0"));
        assertFalse(AutoUpdater.isNewer("1.0.0", null));
        assertFalse(AutoUpdater.isNewer(null, null));
    }

    // -------------------------------------------------------------------------
    // AutoUpdater.normalizeVersion tests
    // -------------------------------------------------------------------------

    @Test
    void testNormalizeVersionStripsLeadingV() {
        assertEquals("1.2.3", AutoUpdater.normalizeVersion("v1.2.3"));
        assertEquals("1.2.3", AutoUpdater.normalizeVersion("1.2.3"));
        assertEquals("", AutoUpdater.normalizeVersion(""));
    }

    // -------------------------------------------------------------------------
    // AutoUpdater.shouldCheck / recordCheckDate tests
    // -------------------------------------------------------------------------

    @Test
    void testShouldCheckReturnsTrueWhenNoSentinelFile(@TempDir Path dir) throws IOException {
        UpdateConfig config = UpdateConfig.load(null);
        AutoUpdater updater = new AutoUpdater(config, dir);
        assertTrue(updater.shouldCheck());
    }

    @Test
    void testShouldCheckReturnsFalseAfterTodayIsRecorded(@TempDir Path dir) throws IOException {
        UpdateConfig config = UpdateConfig.load(null);
        AutoUpdater updater = new AutoUpdater(config, dir);

        updater.recordCheckDate();
        assertFalse(updater.shouldCheck());
    }

    @Test
    void testShouldCheckReturnsTrueWhenSentinelHasYesterdaysDate(@TempDir Path dir) throws IOException {
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        Files.writeString(dir.resolve(AutoUpdater.LAST_CHECK_FILE), yesterday);

        UpdateConfig config = UpdateConfig.load(null);
        AutoUpdater updater = new AutoUpdater(config, dir);
        assertTrue(updater.shouldCheck());
    }

    // -------------------------------------------------------------------------
    // AutoUpdater.checkAndUpdate — disabled scenario
    // -------------------------------------------------------------------------

    @Test
    void testCheckAndUpdateSkipsWhenDisabled(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("update.properties"), "update.enabled=false\n");
        UpdateConfig config = UpdateConfig.load(dir);
        AutoUpdater updater = new AutoUpdater(config, dir);
        assertFalse(updater.checkAndUpdate());
    }
}
