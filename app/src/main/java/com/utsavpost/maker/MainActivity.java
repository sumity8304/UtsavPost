package com.utsavpost.maker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.utsavpost.maker.ads.AdManager;
import com.utsavpost.maker.data.TemplateRepository;
import com.utsavpost.maker.databinding.ActivityMainBinding;
import com.utsavpost.maker.model.TemplateModel;
import com.utsavpost.maker.ui.ProfileBottomSheetDialog;
import com.utsavpost.maker.ui.TemplateAdapter;
import com.utsavpost.maker.utils.UserProfileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main dashboard for browsing festival greetings, birthday wishes, and social status templates.
 * Includes live multilingual & category filtering, AdMob banner, and user branding profile setup.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TemplateAdapter templateAdapter;
    private TemplateRepository templateRepository;
    private UserProfileManager userProfileManager;
    private AdManager adManager;

    private List<TemplateModel> allTemplates = new ArrayList<>();
    private String selectedLanguage = "All";
    private String selectedCategory = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize managers
        userProfileManager = UserProfileManager.getInstance(this);
        templateRepository = TemplateRepository.getInstance();
        adManager = AdManager.getInstance();

        // 1. Initialize AdMob and load banner ad
        adManager.initialize(this);
        adManager.loadBannerAd(binding.bannerAdContainer, this);

        // 2. Setup RecyclerView & Adapter immediately
        setupRecyclerView();

        // 3. Setup Filters (Language & Category Chips)
        setupFilterChips();

        // 4. Setup Header Profile Pill & Actions
        setupHeaderProfile();

        // 5. Load Templates
        loadTemplates();

        // Debug Toast confirming template load count on launch
        Toast.makeText(this, "Loaded " + allTemplates.size() + " templates", Toast.LENGTH_SHORT).show();

        // 6. Onboarding: Auto-prompt profile dialog on first launch
        if (userProfileManager.isFirstLaunch()) {
            binding.getRoot().postDelayed(this::showProfileSetupDialog, 400);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeaderProfile();
        if (templateAdapter != null) {
            templateAdapter.updateUserProfile();
        }
    }

    private void setupRecyclerView() {
        templateAdapter = new TemplateAdapter(this);
        templateAdapter.setOnTemplateClickListener(template -> {
            Intent intent = new Intent(MainActivity.this, EditorActivity.class);
            intent.putExtra("extra_template", template);
            startActivity(intent);
        });

        // 2-column grid layout for festival poster cards
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        binding.recyclerViewTemplates.setLayoutManager(gridLayoutManager);
        binding.recyclerViewTemplates.setHasFixedSize(true);
        binding.recyclerViewTemplates.setAdapter(templateAdapter);
    }

    private void setupFilterChips() {
        // Language Filter Selection with Hindi, Bhojpuri, Marathi mapping
        binding.chipGroupLanguage.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedLanguage = "All";
                binding.chipLangAll.setChecked(true);
            } else {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipLangHindi) {
                    selectedLanguage = "Hindi";
                } else if (checkedId == R.id.chipLangBhojpuri) {
                    selectedLanguage = "Bhojpuri";
                } else if (checkedId == R.id.chipLangMarathi) {
                    selectedLanguage = "Marathi";
                } else {
                    selectedLanguage = "All";
                }
            }
            applyFilters();
        });

        // Category Filter Selection
        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedCategory = "All";
                binding.chipCatAll.setChecked(true);
            } else {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipCatFestival) {
                    selectedCategory = "Festival";
                } else if (checkedId == R.id.chipCatBirthday) {
                    selectedCategory = "Birthday";
                } else if (checkedId == R.id.chipCatShayari) {
                    selectedCategory = "Shayari";
                } else if (checkedId == R.id.chipCatSuvichar) {
                    selectedCategory = "Suvichar";
                } else if (checkedId == R.id.chipCatPolitical) {
                    selectedCategory = "Political/Social";
                } else {
                    selectedCategory = "All";
                }
            }
            applyFilters();
        });
    }

    private void setupHeaderProfile() {
        updateHeaderProfile();
        binding.btnMyProfile.setOnClickListener(v -> showProfileSetupDialog());
    }

    private void updateHeaderProfile() {
        String name = userProfileManager.getUserName();
        if (name != null && !name.equals(UserProfileManager.DEFAULT_NAME)) {
            binding.tvHeaderProfileName.setText(name);
        } else {
            binding.tvHeaderProfileName.setText("My Profile");
        }

        String photoPath = userProfileManager.getUserPhotoPath();
        if (photoPath != null) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                Glide.with(this)
                        .load(photoFile)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(binding.ivHeaderProfilePhoto);
                return;
            }
        }
        binding.ivHeaderProfilePhoto.setImageResource(R.drawable.ic_person_placeholder);
    }

    private void showProfileSetupDialog() {
        ProfileBottomSheetDialog dialog = ProfileBottomSheetDialog.newInstance();
        dialog.setOnProfileUpdatedListener(() -> {
            updateHeaderProfile();
            if (templateAdapter != null) {
                templateAdapter.updateUserProfile();
            }
        });
        dialog.show(getSupportFragmentManager(), "ProfileBottomSheetDialog");
    }

    private void loadTemplates() {
        allTemplates = templateRepository.loadTemplates(this);
        if (allTemplates == null || allTemplates.size() < 10) {
            allTemplates = TemplateRepository.getBuiltInTemplates();
        }
        applyFilters();

        // Remote fetch with safe fallback
        templateRepository.fetchRemoteTemplates(this, "", new TemplateRepository.Callback() {
            @Override
            public void onSuccess(List<TemplateModel> templates) {
                if (templates != null && templates.size() >= 10) {
                    allTemplates = templates;
                    applyFilters();
                }
            }

            @Override
            public void onError(Throwable throwable, List<TemplateModel> fallbackTemplates) {
                if (allTemplates == null || allTemplates.size() < 10) {
                    if (fallbackTemplates != null && fallbackTemplates.size() >= 10) {
                        allTemplates = fallbackTemplates;
                    } else {
                        allTemplates = TemplateRepository.getBuiltInTemplates();
                    }
                    applyFilters();
                }
            }
        });
    }

    private boolean matchesLanguage(String itemLang, String selected) {
        if (selected == null || selected.equalsIgnoreCase("All")) {
            return true;
        }
        if (itemLang == null) {
            return false;
        }
        String item = itemLang.trim().toLowerCase();
        String sel = selected.trim().toLowerCase();

        if (sel.contains("hindi") || sel.contains("हिंदी")) {
            return item.contains("hindi") || item.contains("हिंदी");
        }
        if (sel.contains("bhojpuri") || sel.contains("भोजपुरी")) {
            return item.contains("bhojpuri") || item.contains("भोजपुरी");
        }
        if (sel.contains("marathi") || sel.contains("मराठी")) {
            return item.contains("marathi") || item.contains("मराठी");
        }
        return item.equalsIgnoreCase(sel);
    }

    private boolean matchesCategory(String itemCat, String selected) {
        if (selected == null || selected.equalsIgnoreCase("All")) {
            return true;
        }
        if (itemCat == null) {
            return false;
        }
        String item = itemCat.trim().toLowerCase();
        String sel = selected.trim().toLowerCase();

        return item.equalsIgnoreCase(sel) || item.contains(sel) || sel.contains(item);
    }

    private void applyFilters() {
        runOnUiThread(() -> {
            if (allTemplates == null || allTemplates.size() < 10) {
                allTemplates = TemplateRepository.getBuiltInTemplates();
            }

            List<TemplateModel> filtered = new ArrayList<>();
            for (TemplateModel item : allTemplates) {
                if (item == null) continue;
                if (matchesLanguage(item.getLanguage(), selectedLanguage) &&
                    matchesCategory(item.getCategory(), selectedCategory)) {
                    filtered.add(item);
                }
            }

            // If strict filter yields empty, fallback to showing all templates so screen is never blank!
            if (filtered.isEmpty()) {
                filtered = new ArrayList<>(allTemplates);
                Toast.makeText(this, "No match for filter. Showing all " + allTemplates.size() + " templates", Toast.LENGTH_SHORT).show();
            }

            templateAdapter.setTemplates(filtered);
            templateAdapter.notifyDataSetChanged();

            if (filtered.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.recyclerViewTemplates.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.recyclerViewTemplates.setVisibility(View.VISIBLE);
            }
        });
    }
}
