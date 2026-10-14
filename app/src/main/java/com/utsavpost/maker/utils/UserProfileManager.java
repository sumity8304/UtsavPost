package com.utsavpost.maker.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages user branding profile (Name, Tagline/Designation, and internal profile photo).
 * Persists data across app sessions and copies gallery images into internal storage
 * so that ContentResolver Uri permissions are never lost upon app restart.
 */
public class UserProfileManager {

    private static final String TAG = "UserProfileManager";
    private static final String PREF_NAME = "utsav_user_profile_prefs";

    private static final String KEY_USER_NAME = "key_user_name";
    private static final String KEY_USER_DESIGNATION = "key_user_designation";
    private static final String KEY_USER_PHOTO_PATH = "key_user_photo_path";
    private static final String KEY_IS_FIRST_LAUNCH = "key_is_first_launch";

    public static final String DEFAULT_NAME = "आपका नाम यहाँ";
    public static final String DEFAULT_DESIGNATION = "समाजसेवी / युवा नेता";
    private static final String PROFILE_IMAGE_FILE_NAME = "profile_pic.jpg";

    private static volatile UserProfileManager instance;
    private final SharedPreferences prefs;
    private final Context appContext;

    private UserProfileManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static UserProfileManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UserProfileManager.class) {
                if (instance == null) {
                    instance = new UserProfileManager(context);
                }
            }
        }
        return instance;
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, DEFAULT_NAME);
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, (name != null && !name.trim().isEmpty()) ? name.trim() : DEFAULT_NAME).apply();
    }

    public String getUserDesignation() {
        return prefs.getString(KEY_USER_DESIGNATION, DEFAULT_DESIGNATION);
    }

    public void setUserDesignation(String designation) {
        prefs.edit().putString(KEY_USER_DESIGNATION, (designation != null && !designation.trim().isEmpty()) ? designation.trim() : DEFAULT_DESIGNATION).apply();
    }

    @Nullable
    public String getUserPhotoPath() {
        String path = prefs.getString(KEY_USER_PHOTO_PATH, null);
        if (path != null) {
            File file = new File(path);
            if (file.exists() && file.length() > 0) {
                return path;
            }
        }
        // Fallback check if default profile file exists in filesDir
        File defaultFile = new File(appContext.getFilesDir(), PROFILE_IMAGE_FILE_NAME);
        if (defaultFile.exists() && defaultFile.length() > 0) {
            return defaultFile.getAbsolutePath();
        }
        return null;
    }

    public void setUserPhotoPath(@Nullable String path) {
        prefs.edit().putString(KEY_USER_PHOTO_PATH, path).apply();
    }

    public boolean hasProfilePhoto() {
        return getUserPhotoPath() != null;
    }

    @Nullable
    public File getProfileFile() {
        String path = getUserPhotoPath();
        if (path != null) {
            File f = new File(path);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true);
    }

    public void setFirstLaunch(boolean isFirstLaunch) {
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, isFirstLaunch).apply();
    }

    /**
     * Copies a selected photo Uri from the system gallery into internal app storage.
     * Prevents Android permission expiry across app restarts.
     *
     * @param context   Application or Activity context
     * @param sourceUri Uri returned by GetContent() or OpenDocument()
     * @return Absolute file path of the copied local image, or null on failure.
     */
    @Nullable
    public String saveProfileImageToInternalStorage(Context context, Uri sourceUri) {
        if (context == null || sourceUri == null) {
            return null;
        }

        try {
            File internalFile = new File(context.getFilesDir(), PROFILE_IMAGE_FILE_NAME);
            if (internalFile.exists()) {
                internalFile.delete();
            }

            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream out = new FileOutputStream(internalFile)) {
                if (in == null) {
                    return null;
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            String savedPath = internalFile.getAbsolutePath();
            setUserPhotoPath(savedPath);
            Log.d(TAG, "Profile image successfully copied to internal storage: " + savedPath);
            return savedPath;
        } catch (Exception e) {
            Log.e(TAG, "Failed copying profile image to internal storage", e);
            return null;
        }
    }

    /**
     * Loads a downsampled Bitmap of the user's saved profile photo for smooth rendering.
     */
    @Nullable
    public Bitmap loadProfileBitmap(int maxDimension) {
        File file = getProfileFile();
        if (file == null || !file.exists()) {
            return null;
        }

        try {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), boundsOptions);

            int sampleSize = 1;
            while (boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
                   boundsOptions.outHeight / sampleSize > maxDimension * 2) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

            return BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding user profile bitmap", e);
            return null;
        }
    }
}

