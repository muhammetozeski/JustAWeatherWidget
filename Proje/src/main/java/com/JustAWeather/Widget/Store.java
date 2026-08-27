package com.JustAWeather.Widget;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Where the downloaded forecast lives: the cache directory, not the app's data. It can
 * be fetched again at any moment, so Android is free to drop it when space runs short.
 *
 * File layout: [byte version][long fetched at][byte offline][deflate(json)].
 * Deflater at BEST_COMPRESSION is part of the platform, so the compression costs nothing
 * in APK size, and JSON of this shape packs down to roughly a fifth.
 */
class Store {

    private static final byte VERSION = 1;
    private static final int HEADER = 1 + 8;      // version + timestamp, the offline flag follows
    private static final int BUF = 4096;

    private static File file(Context c, int id) {
        return new File(c.getCacheDir(), "f" + id + ".z");
    }

    static void save(Context c, int id, String json, long ts) {
        File dst = file(c, id);
        File tmp = new File(c.getCacheDir(), "f" + id + ".tmp");
        try {
            write(tmp, json, ts);
            if (dst.exists() && !dst.delete()) {
                Log.w(Api.TAG, "old forecast file would not go away, overwriting in place");
            }
            if (!tmp.renameTo(dst)) {
                // Rename can fail on some odd filesystems; writing straight to the
                // destination is worse (a crash mid-write loses the old copy) but is
                // better than dropping the forecast entirely.
                Log.w(Api.TAG, "rename failed, writing the forecast in place");
                write(dst, json, ts);
            }
        } catch (Throwable t) {
            Log.e(Api.TAG, "forecast could not be cached for widget " + id, t);
        } finally {
            try {
                if (tmp.exists() && !tmp.delete()) Log.w(Api.TAG, "leftover temp file stays behind");
            } catch (Throwable t) {
                Log.w(Api.TAG, "temp file could not be cleaned up", t);
            }
        }
    }

    private static void write(File f, String json, long ts) throws Exception {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(f));
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            out.writeByte(VERSION);
            out.writeLong(ts);
            out.writeBoolean(false);
            DeflaterOutputStream zip = new DeflaterOutputStream(out, deflater, BUF);
            zip.write(json.getBytes("UTF-8"));
            zip.finish();
            zip.close();
        } finally {
            deflater.end();
            try {
                out.close();
            } catch (Throwable t) {
                Log.w(Api.TAG, "closing the cache file failed", t);
            }
        }
    }

    /** Fills ts / offline / json of the given Data. False when there is nothing cached. */
    static boolean load(Context c, int id, Data into) {
        File f = file(c, id);
        if (!f.exists()) return false;
        DataInputStream in = null;
        Inflater inflater = new Inflater();
        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), BUF));
            if (in.readByte() != VERSION) throw new Exception("cache written by another version");
            into.ts = in.readLong();
            into.offline = in.readBoolean();
            InflaterInputStream zip = new InflaterInputStream(in, inflater, BUF);
            ByteArrayOutputStream raw = new ByteArrayOutputStream(16384);
            byte[] buf = new byte[BUF];
            for (int n; (n = zip.read(buf)) > 0; ) raw.write(buf, 0, n);
            into.json = new String(raw.toByteArray(), "UTF-8");
            return into.json.length() > 0;
        } catch (Throwable t) {
            Log.w(Api.TAG, "cached forecast unreadable, dropping it", t);
            delete(c, id);
            return false;
        } finally {
            inflater.end();
            try {
                if (in != null) in.close();
            } catch (Throwable t) {
                Log.w(Api.TAG, "closing the cache file failed", t);
            }
        }
    }

    /** Keeps the forecast, only flips the "this is not fresh" byte in the header. */
    static void offline(Context c, int id) {
        RandomAccessFile f = null;
        try {
            File target = file(c, id);
            if (!target.exists()) return;
            f = new RandomAccessFile(target, "rw");
            f.seek(HEADER);
            f.writeBoolean(true);
        } catch (Throwable t) {
            Log.w(Api.TAG, "widget " + id + " could not be flagged offline", t);
        } finally {
            try {
                if (f != null) f.close();
            } catch (Throwable t) {
                Log.w(Api.TAG, "closing the cache file failed", t);
            }
        }
    }

    static void delete(Context c, int id) {
        try {
            File f = file(c, id);
            if (f.exists() && !f.delete()) Log.w(Api.TAG, "cached forecast " + id + " would not delete");
        } catch (Throwable t) {
            Log.w(Api.TAG, "cached forecast " + id + " could not be deleted", t);
        }
    }
}
