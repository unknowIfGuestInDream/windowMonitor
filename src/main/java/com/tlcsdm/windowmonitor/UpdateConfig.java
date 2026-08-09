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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Holds configuration for the auto-update feature.
 *
 * <p>Configuration is loaded from {@code update.properties} on the classpath.
 * An optional external override file located next to the running JAR
 * ({@code update.properties}) is also supported and takes precedence.
 *
 * @author unknowIfGuestInDream
 */
public class UpdateConfig {

    private static final String BUNDLED_CONFIG = "/update.properties";

    private final String apiUrl;
    private final String downloadBaseUrl;
    private final String assetName;
    private final String currentVersion;
    private final boolean enabled;

    private UpdateConfig(Properties props) {
        this.apiUrl = props.getProperty("update.apiUrl", "");
        this.downloadBaseUrl = props.getProperty("update.downloadBaseUrl", "");
        this.assetName = props.getProperty("update.assetName", "windowMonitor-{version}.jar");
        this.currentVersion = props.getProperty("update.currentVersion", "0.0.0");
        this.enabled = Boolean.parseBoolean(props.getProperty("update.enabled", "true"));
    }

    /**
     * Loads the update configuration.
     *
     * <p>First reads the bundled {@code update.properties} from the classpath,
     * then overlays any values found in an external {@code update.properties}
     * file located in the directory given by {@code externalConfigDir}.
     *
     * @param externalConfigDir directory to look for an external override file, or {@code null}
     * @return a populated {@link UpdateConfig}
     * @throws IOException if the bundled properties file cannot be read
     */
    public static UpdateConfig load(Path externalConfigDir) throws IOException {
        Properties props = new Properties();

        // Load bundled defaults
        try (InputStream is = UpdateConfig.class.getResourceAsStream(BUNDLED_CONFIG)) {
            if (is != null) {
                props.load(is);
            }
        }

        // Overlay with external file if present
        if (externalConfigDir != null) {
            Path external = externalConfigDir.resolve("update.properties");
            if (Files.isReadable(external)) {
                try (InputStream is = Files.newInputStream(external)) {
                    props.load(is);
                }
            }
        }

        return new UpdateConfig(props);
    }

    /** Returns the GitHub releases API URL used to fetch the latest release. */
    public String getApiUrl() {
        return apiUrl;
    }

    /** Returns the base download URL for release assets. */
    public String getDownloadBaseUrl() {
        return downloadBaseUrl;
    }

    /**
     * Returns the asset filename pattern.
     * The placeholder {@code {version}} is replaced with the release tag at runtime.
     */
    public String getAssetName() {
        return assetName;
    }

    /** Returns the currently installed version string. */
    public String getCurrentVersion() {
        return currentVersion;
    }

    /** Returns {@code true} if the auto-update feature is enabled. */
    public boolean isEnabled() {
        return enabled;
    }
}
