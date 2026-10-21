package com.aalamstudio.sdk;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SdkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.aalamstudio.sdk.provider";
    static final String ROOT = "sdk"; // assets/sdk/...

    @Override
    public boolean onCreate() {
        return true;
    }

    /** content://AUTHORITY/index -> har file ka path (sdk/ ke andar ka). */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[]{"path"});
        try {
            list(getContext(), ROOT, "", c);
        } catch (IOException ignored) {
        }
        return c;
    }

    private static void list(Context ctx, String assetPath, String rel, MatrixCursor c)
            throws IOException {
        String[] kids = ctx.getAssets().list(assetPath);
        if (kids == null) return;
        for (String k : kids) {
            if (k.startsWith(".")) continue;
            String full = assetPath + "/" + k;
            String r = rel.isEmpty() ? k : rel + "/" + k;
            String[] sub = ctx.getAssets().list(full);
            if (sub != null && sub.length > 0) list(ctx, full, r, c);
            else c.addRow(new Object[]{r});
        }
    }

    /** content://AUTHORITY/<path> -> file (sirf padhne ke liye). */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) {
            throw new SecurityException("read only");
        }
        String rel = uri.getPath();
        if (rel == null || rel.length() < 2 || rel.contains("..")) {
            throw new FileNotFoundException("bad path");
        }
        rel = rel.substring(1);

        Context ctx = getContext();
        File cacheRoot = new File(ctx.getFilesDir(), "sdk-cache");
        freshCache(ctx, cacheRoot);

        File f = new File(cacheRoot, rel);
        if (!f.isFile() || f.length() == 0) {
            f.getParentFile().mkdirs();
            try (InputStream in = ctx.getAssets().open(ROOT + "/" + rel);
                 OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            } catch (IOException e) {
                f.delete();
                throw new FileNotFoundException(rel);
            }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** SDK app update hone par purana cache hata do. */
    private static void freshCache(Context ctx, File root) {
        long stamp = 0;
        try {
            stamp = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime;
        } catch (Exception ignored) {
        }
        File marker = new File(root, ".stamp");
        String want = String.valueOf(stamp);
        try {
            if (marker.exists()) {
                try (InputStream in = new java.io.FileInputStream(marker)) {
                    byte[] b = new byte[64];
                    int n = in.read(b);
                    if (n > 0 && want.equals(new String(b, 0, n, "UTF-8"))) return;
                }
            }
            deleteRecursive(root);
            root.mkdirs();
            try (OutputStream out = new FileOutputStream(marker)) {
                out.write(want.getBytes("UTF-8"));
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read only");
    }
}
