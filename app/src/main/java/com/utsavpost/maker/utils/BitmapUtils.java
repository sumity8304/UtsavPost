package com.utsavpost.maker.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility helper for:
 * 1. Capturing poster FrameLayout into crisp high-res Bitmaps.
 * 2. Saving Bitmaps directly to Android MediaStore gallery (Pictures/UtsavPost).
 * 3. Sharing posters with viral caption directly to WhatsApp or system chooser.
 */
public final class BitmapUtils {

    private static final String TAG = "BitmapUtils";
    private static final String SUB_DIR = "UtsavPost";

    private BitmapUtils() {
    }

    /**
     * Captures any View (e.g. poster FrameLayout) into an uncompressed high-resolution Bitmap.
     */
    @NonNull
    public static Bitmap captureViewToBitmap(@NonNull View view) {
        int width = view.getWidth();
        int height = view.getHeight();

        if (width <= 0 || height <= 0) {
            // Default 4:5 festival ratio (1080x1350 HD standard)
            width = 1080;
            height = 1350;
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            );
            view.layout(0, 0, width, height);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    /**
     * Saves a poster Bitmap to the system gallery under Pictures/UtsavPost using MediaStore.
     */
    @Nullable
    public static Uri saveBitmapToGallery(@NonNull Context context, @NonNull Bitmap bitmap, @Nullable String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            fileName = "UtsavPost_" + timeStamp + ".png";
        } else if (!fileName.endsWith(".png")) {
            fileName = fileName + ".png";
        }

        ContentResolver resolver = context.getContentResolver();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ Scoped Storage via MediaStore
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + SUB_DIR);
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                if (imageUri != null) {
                    try (OutputStream out = resolver.openOutputStream(imageUri)) {
                        if (out != null) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                            out.flush();
                        }
                    }

                    contentValues.clear();
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(imageUri, contentValues, null, null);
                    Log.d(TAG, "Poster saved to MediaStore: " + imageUri);
                    return imageUri;
                }
            } else {
                // Legacy storage for API < 29
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File appDir = new File(picturesDir, SUB_DIR);
                if (!appDir.exists() && !appDir.mkdirs()) {
                    Log.e(TAG, "Failed creating directory: " + appDir.getAbsolutePath());
                    return null;
                }

                File imageFile = new File(appDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                }

                // Notify media scanner so gallery index is refreshed
                MediaScannerConnection.scanFile(
                        context,
                        new String[]{imageFile.getAbsolutePath()},
                        new String[]{"image/png"},
                        null
                );

                return Uri.fromFile(imageFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed saving bitmap to gallery", e);
        }
        return null;
    }

    /**
     * Writes the bitmap to the app cache and shares it via Intent with viral caption.
     * Can target WhatsApp exclusively or launch the standard Android share sheet.
     */
    public static void shareBitmapToWhatsAppOrSystem(@NonNull Context context,
                                                     @NonNull Bitmap bitmap,
                                                     boolean targetWhatsAppOnly) {
        try {
            File cacheDir = new File(context.getCacheDir(), "shared_posters");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            File shareFile = new File(cacheDir, "poster.png");
            try (FileOutputStream fos = new FileOutputStream(shareFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            }

            String authority = context.getPackageName() + ".fileprovider";
            Uri contentUri = FileProvider.getUriForFile(context, authority, shareFile);

            String viralCaption = "🎉 मैंने अपना त्यौहार और बर्थडे पोस्टर 1 क्लिक में बनाया! " +
                    "आप भी अपनी फोटो और नाम वाला पोस्टर मुफ्त में बनाएं 👇\n" +
                    "https://play.google.com/store/apps/details?id=" + context.getPackageName();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, viralCaption);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (targetWhatsAppOnly) {
                if (isPackageInstalled(context, "com.whatsapp")) {
                    shareIntent.setPackage("com.whatsapp");
                    context.startActivity(shareIntent);
                    return;
                } else if (isPackageInstalled(context, "com.whatsapp.w4b")) {
                    shareIntent.setPackage("com.whatsapp.w4b");
                    context.startActivity(shareIntent);
                    return;
                } else {
                    Toast.makeText(context, "WhatsApp इंस्टॉल नहीं है। अन्य ऐप्स पर शेयर कर रहे हैं...", Toast.LENGTH_SHORT).show();
                }
            }

            Intent chooser = Intent.createChooser(shareIntent, "पोस्टर शेयर करें (Share Poster)");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

        } catch (Exception e) {
            Log.e(TAG, "Error sharing bitmap", e);
            Toast.makeText(context, "पोस्टर शेयर करने में त्रुटि हुई", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}

