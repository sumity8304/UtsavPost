package com.utsavpost.maker.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.utsavpost.maker.model.TemplateModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository responsible for loading festival and greeting templates from local assets,
 * internal cache, or remote JSON endpoints.
 */
public class TemplateRepository {

    private static final String TAG = "TemplateRepository";
    private static final String ASSET_FILE = "templates.json";
    private static final String CACHE_FILE_NAME = "cached_templates.json";
    private static final String PREFS_NAME = "utsav_template_repo_prefs";
    private static final String KEY_LAST_SYNC = "key_last_sync_time";

    private static volatile TemplateRepository instance;

    private final Gson gson;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface Callback {
        void onSuccess(List<TemplateModel> templates);
        void onError(Throwable throwable, List<TemplateModel> fallbackTemplates);
    }

    private TemplateRepository() {
        this.gson = new Gson();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static TemplateRepository getInstance() {
        if (instance == null) {
            synchronized (TemplateRepository.class) {
                if (instance == null) {
                    instance = new TemplateRepository();
                }
            }
        }
        return instance;
    }

    /**
     * Synchronously loads templates: prefers cached remote JSON if present,
     * otherwise falls back to the embedded assets/templates.json file.
     */
    public List<TemplateModel> loadTemplates(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }

        // 1. Try reading from internal cached file
        List<TemplateModel> cachedTemplates = loadFromInternalCache(context);
        if (cachedTemplates != null && !cachedTemplates.isEmpty()) {
            return cachedTemplates;
        }

        // 2. Fall back to bundled assets/templates.json
        return loadAssetTemplates(context);
    }

    /**
     * Reads the default templates packaged with the APK assets.
     */
    public List<TemplateModel> loadAssetTemplates(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        try {
            InputStream is = context.getAssets().open(ASSET_FILE);
            String json = readStreamToString(is);
            Type listType = new TypeToken<ArrayList<TemplateModel>>() {}.getType();
            List<TemplateModel> list = gson.fromJson(json, listType);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "Error reading " + ASSET_FILE + " from assets", e);
            return Collections.emptyList();
        }
    }

    /**
     * Asynchronously fetches updated templates from a remote URL (e.g. GitHub raw URL).
     * Caches successful responses to internal storage.
     * Falls back to cached or assets templates on network failure or if URL is empty.
     */
    public void fetchRemoteTemplates(Context context, String remoteJsonUrl, Callback callback) {
        if (context == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(new IllegalArgumentException("Context cannot be null"), Collections.emptyList()));
            }
            return;
        }

        final Context appContext = context.getApplicationContext();

        if (remoteJsonUrl == null || remoteJsonUrl.trim().isEmpty()) {
            List<TemplateModel> localList = loadTemplates(appContext);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess(localList));
            }
            return;
        }

        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(remoteJsonUrl.trim());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "UtsavPost-Android");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    String jsonString = readStreamToString(inputStream);

                    Type listType = new TypeToken<ArrayList<TemplateModel>>() {}.getType();
                    List<TemplateModel> remoteList = gson.fromJson(jsonString, listType);

                    if (remoteList != null && !remoteList.isEmpty()) {
                        saveToInternalCache(appContext, jsonString);
                        postSuccess(callback, remoteList);
                        return;
                    }
                }
                throw new IllegalStateException("Failed to fetch templates: HTTP response code " + responseCode);
            } catch (Exception e) {
                Log.w(TAG, "Remote template fetch failed; using fallback.", e);
                List<TemplateModel> fallback = loadTemplates(appContext);
                postError(callback, e, fallback);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Reads cached JSON from internal app storage.
     */
    private List<TemplateModel> loadFromInternalCache(Context context) {
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
            if (!cacheFile.exists()) {
                return null;
            }
            FileInputStream fis = new FileInputStream(cacheFile);
            String json = readStreamToString(fis);
            Type listType = new TypeToken<ArrayList<TemplateModel>>() {}.getType();
            return gson.fromJson(json, listType);
        } catch (Exception e) {
            Log.w(TAG, "Failed reading cached templates", e);
            return null;
        }
    }

    /**
     * Persists remote JSON string into internal storage.
     */
    private void saveToInternalCache(Context context, String json) {
        try {
            File cacheFile = new File(context.getFilesDir(), CACHE_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
            Log.d(TAG, "Templates successfully cached to internal storage");
        } catch (Exception e) {
            Log.e(TAG, "Failed saving templates to internal storage", e);
        }
    }

    /**
     * Converts an InputStream to a UTF-8 String.
     */
    private String readStreamToString(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    private void postSuccess(Callback callback, List<TemplateModel> list) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(list));
        }
    }

    private void postError(Callback callback, Throwable throwable, List<TemplateModel> fallback) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(throwable, fallback));
        }
    }

    // --- Helper Filter Methods for UI Consumption ---

    public List<TemplateModel> filterByCategory(List<TemplateModel> templates, String category) {
        if (templates == null || category == null || category.equalsIgnoreCase("All")) {
            return templates != null ? new ArrayList<>(templates) : new ArrayList<>();
        }
        List<TemplateModel> result = new ArrayList<>();
        for (TemplateModel item : templates) {
            if (category.equalsIgnoreCase(item.getCategory())) {
                result.add(item);
            }
        }
        return result;
    }

    public List<TemplateModel> filterByLanguage(List<TemplateModel> templates, String language) {
        if (templates == null || language == null || language.equalsIgnoreCase("All")) {
            return templates != null ? new ArrayList<>(templates) : new ArrayList<>();
        }
        List<TemplateModel> result = new ArrayList<>();
        for (TemplateModel item : templates) {
            if (language.equalsIgnoreCase(item.getLanguage())) {
                result.add(item);
            }
        }
        return result;
    }

    public List<String> extractCategories(List<TemplateModel> templates) {
        Set<String> set = new LinkedHashSet<>();
        set.add("All");
        if (templates != null) {
            for (TemplateModel item : templates) {
                if (item.getCategory() != null && !item.getCategory().isEmpty()) {
                    set.add(item.getCategory());
                }
            }
        }
        return new ArrayList<>(set);
    }

    public List<String> extractLanguages(List<TemplateModel> templates) {
        Set<String> set = new LinkedHashSet<>();
        set.add("All");
        if (templates != null) {
            for (TemplateModel item : templates) {
                if (item.getLanguage() != null && !item.getLanguage().isEmpty()) {
                    set.add(item.getLanguage());
                }
            }
        }
        return new ArrayList<>(set);
    }
}

