package me.leon.tvlauncher;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WallpaperStore {
    record Picture(Uri uri, String name, long modified) {
        String key() { return uri.toString(); }
        String cacheKey() { return key() + ":" + modified; }
    }

    static List<Picture> list(Context context) {
        List<Picture> images = new ArrayList<>();
        if (Build.VERSION.SDK_INT == 29 && Environment.isExternalStorageLegacy()) {
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "Wallpaper");
            listDirectory(directory, images);
        } else {
            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_MODIFIED};
            try (Cursor cursor = context.getContentResolver().query(collection, projection,
                    MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? AND "
                            + MediaStore.Images.Media.IS_PENDING + " = 0",
                    new String[]{"Pictures/Wallpaper/%"}, null)) {
                if (cursor != null) while (cursor.moveToNext()) {
                    images.add(new Picture(ContentUris.withAppendedId(collection, cursor.getLong(0)),
                            cursor.getString(1), cursor.getLong(2)));
                }
            }
        }
        Collator names = Collator.getInstance(Locale.getDefault());
        images.sort((a, b) -> {
            int byName = names.compare(a.name(), b.name());
            return byName == 0 ? a.key().compareTo(b.key()) : byName;
        });
        return images;
    }

    private static void listDirectory(File directory, List<Picture> images) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                listDirectory(file, images);
            } else if (file.isFile() && file.canRead()) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), bounds);
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    images.add(new Picture(Uri.fromFile(file), file.getName(), file.lastModified()));
                }
            }
        }
    }

    static Bitmap current(Context context) {
        File file = new File(context.getFilesDir(), "wallpaper.jpg");
        try {
            if (file.isFile()) return decode(ImageDecoder.createSource(file), 3840, 8_388_608);
        } catch (IOException ignored) { }
        return null;
    }

    static Bitmap thumbnail(Context context, Picture picture) throws IOException {
        return decode(source(context, picture.uri()), 640, 230_400);
    }

    static Bitmap apply(Context context, Picture picture) throws IOException {
        Bitmap decoded = decode(source(context, picture.uri()), 3840, 8_388_608);
        AtomicFile destination = new AtomicFile(new File(context.getFilesDir(), "wallpaper.jpg"));
        FileOutputStream output = null;
        try (InputStream input = context.getContentResolver().openInputStream(picture.uri())) {
            if (input == null) throw new IOException("Image is unavailable");
            output = destination.startWrite();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            destination.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            if (output != null) destination.failWrite(output);
            throw error;
        }
        return decoded;
    }

    private static ImageDecoder.Source source(Context context, Uri uri) {
        return "file".equals(uri.getScheme()) ? ImageDecoder.createSource(new File(uri.getPath()))
                : ImageDecoder.createSource(context.getContentResolver(), uri);
    }

    private static Bitmap decode(ImageDecoder.Source source, int longestSide, int maxPixels)
            throws IOException {
        return ImageDecoder.decodeBitmap(source, (decoder, info, ignored) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            double scale = Math.min(1, Math.min(longestSide / (double) Math.max(w, h),
                    Math.sqrt(maxPixels / ((double) w * h))));
            decoder.setTargetSize(Math.max(1, (int) Math.round(w * scale)),
                    Math.max(1, (int) Math.round(h * scale)));
        });
    }
}
