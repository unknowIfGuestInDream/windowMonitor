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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles automatic update detection, download, and rollback for jpackage-packaged builds.
 *
 * <p>The updater performs the following steps once per day:
 * <ol>
 *   <li>Query the configured GitHub releases API for the latest release tag.</li>
 *   <li>Compare the latest tag with the currently installed version.</li>
 *   <li>If a newer version is available, download the release asset.</li>
 *   <li>Replace the running JAR with the downloaded file (backup the old JAR first).</li>
 *   <li>On any failure, restore the backup so the current version keeps running.</li>
 * </ol>
 *
 * <p>The check interval is controlled by a date-stamp file ({@code .last-update-check})
 * written next to the running JAR. Only one check is performed per calendar day.
 *
 * @author unknowIfGuestInDream
 */
public class AutoUpdater {

    private static final Logger log = Logger.getLogger(AutoUpdater.class.getName());

    /** Name of the sentinel file that records the date of the last update check. */
    static final String LAST_CHECK_FILE = ".last-update-check";

    /** Regex to extract the {@code tag_name} field from a GitHub releases JSON response. */
    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private final UpdateConfig config;
    private final Path installDir;
    private final HttpClient httpClient;

    /**
     * Creates an {@link AutoUpdater} with the given configuration.
     *
     * @param config     update configuration
     * @param installDir directory that contains the running JAR
     */
    public AutoUpdater(UpdateConfig config, Path installDir) {
        this.config = config;
        this.installDir = installDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Runs a daily update check.
     *
     * <p>The check is skipped if it has already been performed today. When a
     * newer version is detected the update is downloaded and applied. If the
     * update fails, the previous version is restored and the method returns
     * {@code false}.
     *
     * @return {@code true} if an update was successfully applied, {@code false} otherwise
     */
    public boolean checkAndUpdate() {
        if (!config.isEnabled()) {
            log.info("Auto-update is disabled.");
            return false;
        }

        if (!shouldCheck()) {
            log.info("Update check already performed today, skipping.");
            return false;
        }

        recordCheckDate();

        try {
            String latestTag = fetchLatestTag();
            if (latestTag == null || latestTag.isBlank()) {
                log.warning("Could not determine the latest release tag.");
                return false;
            }

            String normalizedLatest = normalizeVersion(latestTag);
            String normalizedCurrent = normalizeVersion(config.getCurrentVersion());

            if (!isNewer(normalizedLatest, normalizedCurrent)) {
                log.info("Application is up to date (current=" + config.getCurrentVersion()
                        + ", latest=" + latestTag + ").");
                return false;
            }

            log.info("New version detected: " + latestTag + ". Starting update...");
            return applyUpdate(latestTag);
        } catch (Exception e) {
            log.log(Level.WARNING, "Update check failed: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Returns {@code true} if today's date differs from the date recorded in the
     * {@value #LAST_CHECK_FILE} sentinel file.
     */
    boolean shouldCheck() {
        Path sentinel = installDir.resolve(LAST_CHECK_FILE);
        if (!Files.exists(sentinel)) {
            return true;
        }
        try {
            String recorded = Files.readString(sentinel).strip();
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            return !today.equals(recorded);
        } catch (IOException e) {
            log.log(Level.FINE, "Cannot read last-check file, will check now.", e);
            return true;
        }
    }

    /** Writes today's date to the {@value #LAST_CHECK_FILE} sentinel file. */
    void recordCheckDate() {
        Path sentinel = installDir.resolve(LAST_CHECK_FILE);
        try {
            Files.writeString(sentinel, LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not write last-check file: " + e.getMessage(), e);
        }
    }

    /**
     * Queries the configured GitHub releases API and extracts the {@code tag_name} field.
     *
     * @return the tag name string, or {@code null} if it could not be parsed
     * @throws IOException          on network or I/O errors
     * @throws InterruptedException if the HTTP request is interrupted
     */
    String fetchLatestTag() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "windowMonitor-updater")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warning("GitHub API returned HTTP " + response.statusCode());
            return null;
        }

        Matcher m = TAG_PATTERN.matcher(response.body());
        return m.find() ? m.group(1) : null;
    }

    /**
     * Downloads the release asset for {@code tag}, replaces the running JAR,
     * and rolls back if anything goes wrong.
     *
     * @param tag the release tag to download
     * @return {@code true} if the update was applied successfully
     */
    boolean applyUpdate(String tag) {
        String assetFilename = config.getAssetName().replace("{version}", tag.replaceFirst("^v", ""));
        String downloadUrl = config.getDownloadBaseUrl() + "/" + tag + "/" + assetFilename;

        Path targetJar = installDir.resolve(assetFilename);
        Path backupJar = installDir.resolve(assetFilename + ".bak");
        Path tempJar = installDir.resolve(assetFilename + ".tmp");

        // Remove stale temp file if present
        try {
            Files.deleteIfExists(tempJar);
        } catch (IOException ignored) {
        }

        try {
            // 1. Download new JAR to a temp file
            log.info("Downloading update from: " + downloadUrl);
            downloadToFile(downloadUrl, tempJar);

            // 2. Back up the current JAR (if it exists)
            if (Files.exists(targetJar)) {
                Files.copy(targetJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
            }

            // 3. Replace the current JAR atomically
            Files.move(tempJar, targetJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Update applied successfully: " + targetJar);

            // 4. Remove the backup on success
            Files.deleteIfExists(backupJar);
            return true;
        } catch (Exception e) {
            log.log(Level.WARNING, "Update failed, rolling back: " + e.getMessage(), e);
            rollback(targetJar, backupJar, tempJar);
            return false;
        }
    }

    /**
     * Downloads the resource at {@code url} and writes it to {@code dest}.
     *
     * @param url  the URL to download from
     * @param dest the path to write the downloaded content to
     * @throws IOException          on I/O errors
     * @throws InterruptedException if the download is interrupted
     */
    private void downloadToFile(String url, Path dest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "windowMonitor-updater")
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download failed with HTTP " + response.statusCode() + " for " + url);
        }

        try (InputStream in = response.body()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Attempts to restore the backup JAR after a failed update.
     * Cleans up temporary files regardless of rollback success.
     */
    private void rollback(Path targetJar, Path backupJar, Path tempJar) {
        try {
            Files.deleteIfExists(tempJar);
        } catch (IOException e) {
            log.log(Level.FINE, "Could not delete temp file during rollback.", e);
        }
        try {
            if (Files.exists(backupJar)) {
                Files.move(backupJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                log.info("Rollback successful, restored previous version.");
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Rollback failed! Manual intervention may be needed.", e);
        }
    }

    /**
     * Strips a leading {@code v} from a version tag for comparison purposes.
     *
     * @param version a version string such as {@code v1.2.3} or {@code 1.2.3}
     * @return the version without a leading {@code v}
     */
    static String normalizeVersion(String version) {
        if (version != null && version.startsWith("v")) {
            return version.substring(1);
        }
        return version;
    }

    /**
     * Compares two dot-separated version strings.
     *
     * @param latest  the latest available version (already normalized)
     * @param current the currently installed version (already normalized)
     * @return {@code true} if {@code latest} is strictly newer than {@code current}
     */
    static boolean isNewer(String latest, String current) {
        if (latest == null || current == null) {
            return false;
        }
        String[] latestParts = latest.split("\\.", -1);
        String[] currentParts = current.split("\\.", -1);
        int len = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < len; i++) {
            int l = i < latestParts.length ? parseIntSafe(latestParts[i]) : 0;
            int c = i < currentParts.length ? parseIntSafe(currentParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
