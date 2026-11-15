package com.yahya.daftar;

import android.content.Context;
import android.graphics.Bitmap;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** يقرأ النص من صورة بدون إنترنت (Tesseract). بيانات اللغة تُنزَّل مرة واحدة فقط ثم تبقى على الهاتف. */
final class Ocr {
    private static TessBaseAPI api;
    private static String cur = "";

    static synchronized String read(Context c, Bitmap src, String lang) throws Exception {
        if (lang == null || !lang.matches("[a-z_+]+")) lang = "ara";
        File root = c.getFilesDir();
        File td = new File(root, "tessdata");
        td.mkdirs();
        for (String l : lang.split("\\+")) ensure(td, l);

        if (api == null || !cur.equals(lang)) {
            if (api != null) {
                api.recycle();
                api = null;
            }
            TessBaseAPI t = new TessBaseAPI();
            if (!t.init(root.getAbsolutePath(), lang)) {
                t.recycle();
                throw new Exception("تعذّر تجهيز قارئ النص");
            }
            api = t;
            cur = lang;
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
        api.setImage(src);
        String txt = api.getUTF8Text();
        api.clear();
        if (txt == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String ln : txt.split("\n")) {
            String t = ln.replaceAll("\\s+", " ").trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
        }
        return sb.toString();
    }

    private static void ensure(File dir, String l) throws Exception {
        File f = new File(dir, l + ".traineddata");
        if (f.exists() && f.length() > 1000) return;
        File tmp = new File(f.getPath() + ".part");
        try {
            URL u = new URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/" + l + ".traineddata");
            HttpURLConnection h = (HttpURLConnection) u.openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(60000);
            h.setInstanceFollowRedirects(true);
            if (h.getResponseCode() != 200) throw new Exception("http " + h.getResponseCode());
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            if (tmp.length() < 1000 || !tmp.renameTo(f)) throw new Exception("bad file");
        } catch (Exception e) {
            tmp.delete();
            throw new Exception("تعذّر تنزيل بيانات القراءة (مرة واحدة)، تأكد من الإنترنت");
        }
    }

    private Ocr() {}
}
