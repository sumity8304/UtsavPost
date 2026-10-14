package com.utsavpost.maker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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

        // 2. Setup RecyclerView & Adapter
        setupRecyclerView();

        // 3. Setup Filters (Language & Category Chips)
        setupFilterChips();

        // 4. Setup Header Profile Pill & Actions
        setupHeaderProfile();

        // 5. Load Templates
        loadTemplates();

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

        // 2-column grid layout for 4:5 festival poster cards
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        binding.recyclerViewTemplates.setLayoutManager(gridLayoutManager);
        binding.recyclerViewTemplates.setAdapter(templateAdapter);
    }

    private void setupFilterChips() {
        // Language Filter Selection
        binding.chipGroupLanguage.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
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
            applyFilters();
        });

        // Category Filter Selection
        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
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
        applyFilters();

        // Optionally check for remote updates in background
        templateRepository.fetchRemoteTemplates(this, "", new TemplateRepository.Callback() {
            @Override
            public void onSuccess(List<TemplateModel> templates) {
                allTemplates = templates;
                applyFilters();
            }

            @Override
            public void onError(Throwable throwable, List<TemplateModel> fallbackTemplates) {
                // Silently keep local templates
            }
        });
    }

    private void applyFilters() {
        List<TemplateModel> filtered = new ArrayList<>();

        for (TemplateModel item : allTemplates) {
            boolean matchesLang = selectedLanguage.equalsIgnoreCase("All") ||
                    selectedLanguage.equalsIgnoreCase(item.getLanguage());

            boolean matchesCategory = selectedCategory.equalsIgnoreCase("All") ||
                    selectedCategory.equalsIgnoreCase(item.getCategory());

            if (matchesLang && matchesCategory) {
                filtered.add(item);
            }
        }

        templateAdapter.setTemplates(filtered);

        if (filtered.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerViewTemplates.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.recyclerViewTemplates.setVisibility(View.VISIBLE);
        }
    }
}
