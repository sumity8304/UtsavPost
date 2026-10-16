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
 * internal cache, or remote JSON endpoints. If asset loading fails or returns empty,
 * it safely falls back to hardcoded in-memory template models so the UI is never blank.
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
     * Synchronously loads templates:
     * 1. Prefers cached remote JSON if present.
     * 2. Falls back to bundled assets/templates.json.
     * 3. Falls back to hardcoded default template models in Java so the screen is never blank.
     */
    public List<TemplateModel> loadTemplates(Context context) {
        if (context == null) {
            return getDefaultFallbackTemplates();
        }

        // 1. Try reading from internal cached file
        List<TemplateModel> cachedTemplates = loadFromInternalCache(context);
        if (cachedTemplates != null && !cachedTemplates.isEmpty()) {
            return cachedTemplates;
        }

        // 2. Fall back to bundled assets/templates.json
        List<TemplateModel> assetTemplates = loadAssetTemplates(context);
        if (assetTemplates != null && !assetTemplates.isEmpty()) {
            return assetTemplates;
        }

        // 3. Fall back to hardcoded default template models directly in Java
        Log.w(TAG, "Using hardcoded fallback templates to guarantee UI display.");
        return getDefaultFallbackTemplates();
    }

    /**
     * Reads default templates packaged with the APK assets.
     * If assets loading fails or returns empty, falls back to hardcoded Java templates.
     */
    public List<TemplateModel> loadAssetTemplates(Context context) {
        if (context == null) {
            return getDefaultFallbackTemplates();
        }
        try {
            InputStream is = context.getAssets().open(ASSET_FILE);
            String json = readStreamToString(is);
            Type listType = new TypeToken<ArrayList<TemplateModel>>() {}.getType();
            List<TemplateModel> list = gson.fromJson(json, listType);
            if (list != null && !list.isEmpty()) {
                return list;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading " + ASSET_FILE + " from assets", e);
        }
        return getDefaultFallbackTemplates();
    }

    /**
     * Asynchronously fetches updated templates from a remote URL.
     * Caches successful responses to internal storage.
     * Falls back to cached, assets, or hardcoded templates on failure or empty URL.
     */
    public void fetchRemoteTemplates(Context context, String remoteJsonUrl, Callback callback) {
        if (context == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(new IllegalArgumentException("Context cannot be null"), getDefaultFallbackTemplates()));
            }
            return;
        }

        final Context appContext = context.getApplicationContext();

        if (remoteJsonUrl == null || remoteJsonUrl.trim().isEmpty()) {
            List<TemplateModel> localList = loadTemplates(appContext);
            if (localList == null || localList.isEmpty()) {
                localList = getDefaultFallbackTemplates();
            }
            if (callback != null) {
                final List<TemplateModel> finalResult = localList;
                mainHandler.post(() -> callback.onSuccess(finalResult));
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
                Log.w(TAG, "Remote template fetch failed; using local fallback.", e);
                List<TemplateModel> fallback = loadTemplates(appContext);
                if (fallback == null || fallback.isEmpty()) {
                    fallback = getDefaultFallbackTemplates();
                }
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

    // --- Hardcoded Fallback Templates (Ensures Screen is Never Blank) ---

    public static List<TemplateModel> getDefaultFallbackTemplates() {
        List<TemplateModel> list = new ArrayList<>();

        // 1. Hindi Festival
        list.add(new TemplateModel(1, "Diwali Wishes Hindi", "Festival", "Hindi",
                "#8E0E00", "#1F1C18", "#FFD700",
                "https://images.unsplash.com/photo-1574267432553-4b4628081c31?auto=format&fit=crop&w=800&q=80",
                "दीपों का यह पावन त्योहार,\nआपके जीवन में लाए खुशियों की बहार।\nमां लक्ष्मी की कृपा बनी रहे सदा,\nशुभ दीपावली!", false));

        // 2. Hindi Festival
        list.add(new TemplateModel(2, "Holi Celebration Hindi", "Festival", "Hindi",
                "#D31027", "#EA384D", "#FFFF00",
                "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?auto=format&fit=crop&w=800&q=80",
                "रंगों का यह पावन पर्व,\nआपके जीवन में उमंग और प्रेम भर दे।\nसदा मुस्कुराते रहें आप और आपका परिवार,\nहोली की हार्दिक शुभकामनाएं!", false));

        // 3. Bhojpuri Festival
        list.add(new TemplateModel(3, "Chhath Puja Bhojpuri", "Festival", "Bhojpuri",
                "#FF416C", "#FF4B2B", "#FFE600",
                "https://images.unsplash.com/photo-1605379399642-870262d3d051?auto=format&fit=crop&w=800&q=80",
                "कांच ही बांस के बहंगिया, बहंगी लचकत जाए...\nछठी मईया के किरपा रउआ सभे प बनल रहे।\nछठ पूजा के गाढ़ा-गाढ़ा शुभकामना!", false));

        // 4. Bhojpuri Festival
        list.add(new TemplateModel(4, "Chhath Arghya Bhojpuri", "Festival", "Bhojpuri",
                "#E65100", "#BF360C", "#FFD54F",
                "",
                "उग हे सुरुज देव अरघ के बेरा,\nसभे के दुख दूर करीं हे दीनानाथ।\nमहापर्व छठ पूजा के हार्दिक बधाई!", false));

        // 5. Marathi Festival
        list.add(new TemplateModel(5, "Ganeshotsav Marathi", "Festival", "Marathi",
                "#F12711", "#F5AF19", "#FFFFFF",
                "https://images.unsplash.com/photo-1567591414240-e2ff409b52a4?auto=format&fit=crop&w=800&q=80",
                "वक्रतुंड महाकाय सूर्यकोटि समप्रभ।\nनिर्विघ्नं कुरु मे देव सर्वकार्येषु सर्वदा॥\nगणेशोत्सवाच्या सर्वांना हार्दिक शुभेच्छा!", false));

        // 6. Marathi Festival
        list.add(new TemplateModel(6, "Gudhi Padwa Marathi", "Festival", "Marathi",
                "#11998E", "#38EF7D", "#FFD700",
                "",
                "उभारून आनंदाची गुढी,\nनववर्षाची करूया नवी सुरुवात।\nगुढीपाडव्याच्या व हिंदू नववर्षाच्या मनापासून शुभेच्छा!", false));

        // 7. Marathi Festival (PRO)
        list.add(new TemplateModel(7, "Shiv Jayanti Marathi", "Festival", "Marathi",
                "#FF8008", "#FFC837", "#4A0000",
                "",
                "प्रौढ प्रताप पुरंदर, क्षत्रिय कुलावतंस,\nसिंहासनधीश्वर, राजाधिराज छत्रपती शिवाजी महाराज की जय!\nशिवजयंतीच्या मनःपूर्वक शुभेच्छा!", true));

        // 8. Hindi Festival
        list.add(new TemplateModel(8, "Ram Navami Hindi", "Festival", "Hindi",
                "#FF512F", "#F09819", "#FFFFFF",
                "",
                "जिनके मन में श्रीराम हैं, भाग्य में उनके वैकुंठ धाम है।\nउनके चरणों में जिसने जीवन वार दिया, संसार में उसका कल्याण है।\nरामनवमी की बधाई!", false));

        // 9. Hindi Festival
        list.add(new TemplateModel(9, "Makar Sankranti Hindi", "Festival", "Hindi",
                "#00B4DB", "#0083B0", "#FFEB3B",
                "",
                "तिल-गुड़ की मिठास और पतंगों की उड़ान,\nआपके जीवन में आए खुशियों का नया आसमान।\nमकर संक्रांति की हार्दिक बधाई!", false));

        // 10. Hindi Birthday
        list.add(new TemplateModel(10, "Birthday Wishes Hindi", "Birthday", "Hindi",
                "#833AB4", "#FD1D1D", "#FCB045",
                "",
                "खुशियों से महके हर दिन तुम्हारा,\nसफलता चूमे कदम तुम्हारा।\nईश्वर से बस यही दुआ है हमारी,\nजन्मदिन की ढेर सारी शुभकामनाएं एवं बधाई!", false));

        // 11. Bhojpuri Birthday
        list.add(new TemplateModel(11, "Birthday Janamdin Bhojpuri", "Birthday", "Bhojpuri",
                "#1A2980", "#26D0CE", "#FFD700",
                "",
                "जनमदिन के ढेर सारा बधाई आ प्यार!\nमाई-बाबूजी के आशीर्वाद संगे रउआ खूब तरक्की करीं,\nसदा हंसत-मुस्कुरात रहीं।", false));

        // 12. Marathi Birthday
        list.add(new TemplateModel(12, "Vadhdivas Shubhechha Marathi", "Birthday", "Marathi",
                "#B224EF", "#7579FF", "#FFE082",
                "",
                "वाढदिवसाच्या हार्दिक शुभेच्छा!\nआपणास उदंड आयुष्य, उत्तम आरोग्य आणि भरभराट लाभो,\nहीच ईश्वरचरणी प्रार्थना!", false));

        // 13. Hindi Suvichar
        list.add(new TemplateModel(13, "Morning Suvichar Hindi", "Suvichar", "Hindi",
                "#3A1C71", "#D76D77", "#FFAF7B",
                "",
                "मेहनत इतनी खामोशी से करो,\nकि सफलता खुद शोर मचा दे।\nशुभ प्रभात, आपका दिन मंगलमय हो!", false));

        // 14. Bhojpuri Suvichar
        list.add(new TemplateModel(14, "Prernadayak Suvichar Bhojpuri", "Suvichar", "Bhojpuri",
                "#0F2027", "#2C5364", "#00E676",
                "",
                "जे मेहनत करेला ओकरे तकदीर चमकेला,\nभरोसा खुदे प रखीं, मंजिल जरूर भेंटाई।\nसुप्रभात, दिन शुभ होखे!", false));

        // 15. Marathi Suvichar
        list.add(new TemplateModel(15, "Prerna Suvichar Marathi", "Suvichar", "Marathi",
                "#2C3E50", "#4CA1AF", "#F1C40F",
                "",
                "ध्येय साध्य करण्यासाठी परिस्थिती नव्हे,\nतर तुमची मनःस्थिती महत्त्वाची असते.\nशुभ सकाळ!", false));

        // 16. Hindi Shayari (PRO)
        list.add(new TemplateModel(16, "Zindagi Shayari Hindi", "Shayari", "Hindi",
                "#200122", "#6F0000", "#FFD700",
                "",
                "वक्त से लड़कर जो नसीब बदल दे,\nइंसान वही जो अपनी तकदीर बदल दे।\nकल क्या होगा कभी मत सोचो,\nक्या पता कल वक्त खुद अपनी तस्वीर बदल दे!", true));

        // 17. Bhojpuri Shayari
        list.add(new TemplateModel(17, "Prem Shayari Bhojpuri", "Shayari", "Bhojpuri",
                "#4A00E0", "#8E2DE2", "#FF4081",
                "",
                "माटी के खुशबू आ भोजपुरी के बोली,\nएहसे बढ़के का होला प्रीत के डोरी।\nनेह-छोह बनल रहे सदा सबके दिल में।", false));

        // 18. Marathi Shayari
        list.add(new TemplateModel(18, "Kavita Charoli Marathi", "Shayari", "Marathi",
                "#3E5151", "#DECBA4", "#5D001E",
                "",
                "नात्यात असावा गोडवा,\nमनात असावा जिव्हाळा,\nप्रत्येक क्षण असावा आनंदाचा,\nहाच जीवनाचा खरा सोहळा!", false));

        // 19. Hindi Political/Social
        list.add(new TemplateModel(19, "Rashtrahit Sankalp Hindi", "Political/Social", "Hindi",
                "#FF6F00", "#1B5E20", "#FFFFFF",
                "",
                "राष्ट्रहित सर्वोपरि, जनसेवा ही हमारा संकल्प।\nआइए मिलकर एक समृद्ध, सशक्त और आत्मनिर्भर समाज का निर्माण करें।", false));

        // 20. Bhojpuri Political/Social
        list.add(new TemplateModel(20, "Samaj Seva Bhojpuri", "Political/Social", "Bhojpuri",
                "#C2185B", "#512DA8", "#FFD54F",
                "",
                "समाज के सेवा आ हर गरीब के हक,\nयही बा हमनी के जिनगी के असली मकसद।\nजय समाज, जय भारत!", false));

        // 21. Marathi Political/Social
        list.add(new TemplateModel(21, "Maharashtra Din Marathi", "Political/Social", "Marathi",
                "#E53935", "#8E24AA", "#FFEB3B",
                "",
                "मंगल देशा, पवित्र देशा, महाराष्ट्र देशा!\nमहाराष्ट्र दिन व कामगार दिनाच्या सर्वांना मनःपूर्वक शुभेच्छा!", false));

        // 22. Hindi Festival
        list.add(new TemplateModel(22, "Dussehra Vijayadashami Hindi", "Festival", "Hindi",
                "#FF8F00", "#C62828", "#FFF9C4",
                "",
                "अधर्म पर धर्म की विजय,\nअसत्य पर सत्य की जय।\nविजयादशमी के इस पावन पर्व पर आपके जीवन में खुशहाली आए।\nशुभ दशहरा!", false));

        return list;
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
