package com.dsh.dakacheck;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;

/**
 * 极简文件提供者：把 App 私有目录下的 APK 以 content:// 形式交给系统安装器。
 *
 * Android 7+ 禁止用 file:// 分享私有文件，自建一个只读 Provider 最省依赖。
 */
public class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.dsh.dakacheck.apk";
    /** 记录下载好的 APK 路径，安装完后删除。 */
    public static final String PREF = "daka_update";
    public static final String KEY_PENDING = "pending_apk";

    /** 记下待删除的 APK。 */
    public static void markPending(android.content.Context c, java.io.File f) {
        c.getSharedPreferences(PREF, 0).edit()
                .putString(KEY_PENDING, f == null ? "" : f.getAbsolutePath()).apply();
    }

    /** 删除待删除的 APK（安装结束后调用）。 */
    public static void cleanupPending(android.content.Context c) {
        try {
            String p = c.getSharedPreferences(PREF, 0).getString(KEY_PENDING, "");
            if (p != null && !p.isEmpty()) {
                java.io.File f = new java.io.File(p);
                if (f.exists()) f.delete();
            }
            c.getSharedPreferences(PREF, 0).edit().remove(KEY_PENDING).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 清理所有更新包（内部 cache + Download 目录），返回删了多少个。 */
    public static int purgeAll(android.content.Context c) {
        int n = 0;
        try {
            // 内部 cache
            java.io.File cacheDir = c.getCacheDir();
            if (cacheDir != null && cacheDir.listFiles() != null) {
                for (java.io.File f : cacheDir.listFiles()) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) {
                        if (f.delete()) n++;
                    }
                }
            }
            // 外部 cache（旧版本下载位置）
            java.io.File ext = c.getExternalCacheDir();
            if (ext != null && ext.listFiles() != null) {
                for (java.io.File f : ext.listFiles()) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) {
                        if (f.delete()) n++;
                    }
                }
            }
            // 公共 Download
            java.io.File dl = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dl != null && dl.listFiles() != null) {
                for (java.io.File f : dl.listFiles()) {
                    String nm = f.getName();
                    if (f.isFile() && nm.toLowerCase().endsWith(".apk")
                            && nm.contains("打卡提醒")) {
                        if (f.delete()) n++;
                    }
                }
            }
            c.getSharedPreferences(PREF, 0).edit().remove(KEY_PENDING).apply();
        } catch (Throwable ignored) {
        }
        return n;
    }

    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTHORITY + "/" + f.getName());
    }

    private File resolve(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return null;
        // 优先公共 Download 目录（用户可见）
        File pub = new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), name);
        if (pub.exists()) return pub;
        return new File(getContext().getCacheDir(), name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        File f = resolve(uri);
        if (f == null || !f.exists()) throw new java.io.FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] selArgs, String sort) {
        File f = resolve(uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
