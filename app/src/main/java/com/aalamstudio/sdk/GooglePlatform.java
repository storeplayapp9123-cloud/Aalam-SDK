package com.aalamstudio.sdk;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Google ke official server se Android platform (android.jar) download karta hai. */
public class GooglePlatform {

    public static final String BASE = "https://dl.google.com/android/repository/";
    public static final String REPO_XML = BASE + "repository2-3.xml";
    /** Kaun sa API level chahiye. Badalna ho to yahan, jaise "platforms;android-35". */
    public static final String PACKAGE_PATH = "platforms;android-34";
    public static final String LABEL = "API 34";

    public interface Progress {
        /** percent -1 = pata nahi */
        void onProgress(String message, int percent);
    }

    static class Info {
        String url;
        long size;
        String sha1;
    }

    public static File installedJar(Context ctx) {
        return new File(ctx.getFilesDir(), SdkProvider.INSTALLED + "/platform/android.jar");
    }

    public static boolean isInstalled(Context ctx) {
        File f = installedJar(ctx);
        return f.isFile() && f.length() > 0;
    }

    /** Pura kaam: list -> download -> checksum -> android.jar nikalo. Background thread me chalao. */
    public static void install(Context ctx, Progress cb) throws Exception {
        cb.onProgress("Google ki list dekh raha hoon...", -1);
        Info info = findPackage(PACKAGE_PATH);
        if (info == null) {
            throw new IOException("Google ki list me " + PACKAGE_PATH + " nahi mila");
        }
        String url = info.url.startsWith("http") ? info.url : BASE + info.url;

        File tmp = new File(ctx.getCacheDir(), "platform.zip");
        String sha = download(url, tmp, info.size, cb);

        if (info.sha1 != null && !info.sha1.isEmpty() && !info.sha1.equalsIgnoreCase(sha)) {
            tmp.delete();
            throw new IOException("Download ka checksum match nahi hua, dobara try karo");
        }

        cb.onProgress("android.jar nikal raha hoon...", -1);
        File dest = installedJar(ctx);
        dest.getParentFile().mkdirs();
        try {
            extractAndroidJar(tmp, dest);
        } finally {
            tmp.delete();
        }
    }

    // ---------- Google ki list (repository2-3.xml) ----------

    private static Info findPackage(String packagePath) throws Exception {
        HttpURLConnection c = open(REPO_XML);
        try (InputStream in = new BufferedInputStream(c.getInputStream(), 65536)) {
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(in, null);

            Info info = new Info();
            boolean inTarget = false;
            boolean inComplete = false;

            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = local(p.getName());
                    if (n.equals("remotePackage")) {
                        inTarget = packagePath.equals(p.getAttributeValue(null, "path"));
                        inComplete = false;
                    } else if (inTarget) {
                        if (n.equals("complete")) {
                            inComplete = true;
                        } else if (inComplete && n.equals("size")) {
                            try {
                                info.size = Long.parseLong(p.nextText().trim());
                            } catch (NumberFormatException ignored) {
                            }
                        } else if (inComplete && n.equals("checksum")) {
                            String type = p.getAttributeValue(null, "type");
                            String v = p.nextText().trim();
                            if (type == null || type.equalsIgnoreCase("sha1")) info.sha1 = v;
                        } else if (inComplete && n.equals("url")) {
                            info.url = p.nextText().trim();
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String n = local(p.getName());
                    if (inTarget && n.equals("complete")) {
                        inComplete = false;
                    } else if (inTarget && n.equals("remotePackage")) {
                        if (info.url != null) return info;
                        inTarget = false;
                    }
                }
                ev = p.next();
            }
        } finally {
            c.disconnect();
        }
        return null;
    }

    private static String local(String name) {
        int i = name.indexOf(':');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    // ---------- download ----------

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "AalamSDK/1.0");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IOException("Server ne HTTP " + code + " diya");
        }
        return c;
    }

    /** File download karta hai. SHA-1 (hex) return karta hai. */
    private static String download(String url, File dest, long expectedSize, Progress cb)
            throws Exception {
        HttpURLConnection c = open(url);
        long total = c.getContentLengthLong();
        if (total <= 0) total = expectedSize;

        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new BufferedInputStream(c.getInputStream(), 65536);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            long done = 0;
            long lastUi = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
                md.update(buf, 0, r);
                done += r;
                long now = System.currentTimeMillis();
                if (now - lastUi > 300) {
                    lastUi = now;
                    int pct = total > 0 ? (int) (done * 100 / total) : -1;
                    cb.onProgress("Download ho raha hai (" + (done / (1024 * 1024)) + " MB)", pct);
                }
            }
        } finally {
            c.disconnect();
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ---------- zip se android.jar nikalo ----------

    private static void extractAndroidJar(File zip, File dest) throws IOException {
        File tmp = new File(dest.getParentFile(), "android.jar.tmp");
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry found = null;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!e.isDirectory() && (n.equals("android.jar") || n.endsWith("/android.jar"))) {
                    found = e;
                    break;
                }
            }
            if (found == null) throw new IOException("Zip me android.jar nahi mili");

            try (InputStream in = zf.getInputStream(found);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
        }
        if (tmp.length() == 0 || !tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("android.jar save nahi hui");
        }
    }
}
