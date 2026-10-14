package com.utsavpost.maker;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.utsavpost.maker.ads.AdManager;
import com.utsavpost.maker.databinding.ActivityEditorBinding;
import com.utsavpost.maker.model.TemplateModel;
import com.utsavpost.maker.ui.ProfileBottomSheetDialog;
import com.utsavpost.maker.utils.BitmapUtils;
import com.utsavpost.maker.utils.UserProfileManager;

import java.io.File;

/**
 * Interactive Poster Customizer & Exporter.
 * Supports draggable quote text, dynamic gradients, custom backgrounds,
 * 3 distinct branding footer styles, rewarded watermark removal, and 1-tap WhatsApp sharing.
 */
public class EditorActivity extends AppCompatActivity {

    private ActivityEditorBinding binding;
    private UserProfileManager profileManager;
    private AdManager adManager;
    private TemplateModel template;

    // Customization states
    private int currentFooterStyle = 0; // 0 = Classic Ribbon, 1 = Modern Pill, 2 = VIP Political Banner
    private int currentBgIndex = 0;
    private int currentTextColorIndex = 0;
    private int currentTextSizeIndex = 2; // default 18sp
    private boolean isBorderActive = false;

    // Presets for background gradients
    private static final int[][] GRADIENT_PRESETS = new int[][]{
            {0xFFFF6D00, 0xFFD84315}, // Saffron Sunset
            {0xFF8E0E00, 0xFF1F1C18}, // Royal Festive Red
            {0xFFF12711, 0xFFF5AF19}, // Golden Sunrise
            {0xFF833AB4, 0xFFFD1D1D}, // Vibrant Berry
            {0xFF11998E, 0xFF38EF7D}, // Emerald Green
            {0xFF0F2027, 0xFF2C5364}  // Deep Midnight
    };

    // Presets for text colors
    private static final int[] TEXT_COLORS = new int[]{
            0xFFFFFFFF, // Pure White
            0xFFFFD700, // Royal Gold
            0xFFFFEB3B, // Vibrant Yellow
            0xFF00E5FF, // Cyan Sparkle
            0xFF76FF03, // Festive Green
            0xFFFFE0B2  // Warm Peach
    };

    // Text sizes (in sp)
    private static final float[] TEXT_SIZES = new float[]{14f, 16f, 18f, 20f, 24f, 28f};

    private ActivityResultLauncher<String> bgImagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        profileManager = UserProfileManager.getInstance(this);
        adManager = AdManager.getInstance();

        // 1. Receive Template Intent
        Object extra = getIntent().getSerializableExtra("extra_template");
        if (extra instanceof TemplateModel) {
            template = (TemplateModel) extra;
        } else {
            template = new TemplateModel(
                    0, "Festive Wish", "Festival", "Hindi",
                    "#FF6D00", "#D84315", "#FFFFFF",
                    "", "शुभ दीपावली!\nसुख, शांति और समृद्धि आपके घर आए।", false
            );
        }

        // 2. Setup Toolbar
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnEditProfileHeader.setOnClickListener(v -> showProfileDialog());

        // 3. Initialize AdMob Banner
        adManager.loadBannerAd(binding.bannerAdContainer, this);

        // 4. Register Custom BG Photo Picker
        bgImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        binding.canvasBgImage.setVisibility(View.VISIBLE);
                        Glide.with(this)
                                .load(uri)
                                .transform(new CenterCrop())
                                .into(binding.canvasBgImage);
                        Toast.makeText(this, "पृष्ठभूमि फ़ोटो जोड़ी गई!", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 5. Initialize Poster Canvas Content
        applyTemplateData();
        updateFooters();
        setupDraggableText();

        // 6. Setup Control Panel Actions
        setupControlButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFooters();
    }

    private void applyTemplateData() {
        // Apply initial template gradient
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{template.getStartColorInt(), template.getEndColorInt()}
        );
        binding.canvasBgGradient.setBackground(gradient);

        // Apply template background photo if available
        if (template.getImageUrl() != null && !template.getImageUrl().trim().isEmpty()) {
            binding.canvasBgImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(template.getImageUrl())
                    .transform(new CenterCrop())
                    .into(binding.canvasBgImage);
        } else {
            binding.canvasBgImage.setVisibility(View.GONE);
        }

        // Apply quote text and initial accent color
        binding.tvQuote.setText(template.getQuoteText());
        binding.tvQuote.setTextColor(template.getAccentColorInt());
    }

    /**
     * Updates all 3 footer styles with the user's latest branding details.
     */
    private void updateFooters() {
        String name = profileManager.getUserName();
        String designation = profileManager.getUserDesignation();
        String photoPath = profileManager.getUserPhotoPath();

        // Style 1 (Classic Ribbon)
        binding.tvUserName1.setText(name);
        binding.tvUserDesignation1.setText(designation);

        // Style 2 (Modern Floating Pill)
        binding.tvUserName2.setText(name);
        binding.tvUserDesignation2.setText(designation);

        // Style 3 (VIP / Political Banner)
        binding.tvUserName3.setText(name);
        binding.tvUserDesignation3.setText(designation);

        // Load photo into all 3 footer image views
        if (photoPath != null) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                Glide.with(this).load(photoFile).circleCrop().into(binding.ivUserPhoto1);
                Glide.with(this).load(photoFile).circleCrop().into(binding.ivUserPhoto2);
                Glide.with(this).load(photoFile).circleCrop().into(binding.ivUserPhoto3);
                return;
            }
        }

        binding.ivUserPhoto1.setImageResource(R.drawable.ic_person_placeholder);
        binding.ivUserPhoto2.setImageResource(R.drawable.ic_person_placeholder);
        binding.ivUserPhoto3.setImageResource(R.drawable.ic_person_placeholder);
    }

    /**
     * Configures the quote TextView with drag-and-drop movement across the canvas,
     * plus click detection to open the text editing dialog.
     */
    private void setupDraggableText() {
        binding.tvQuote.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            private float startX, startY;
            private boolean isClick = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
                        startX = event.getRawX();
                        startY = event.getRawY();
                        isClick = true;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = Math.abs(event.getRawX() - startX);
                        float diffY = Math.abs(event.getRawY() - startY);
                        if (diffX > 10 || diffY > 10) {
                            isClick = false;
                        }

                        // Constrain dragging within the poster canvas
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;

                        float maxX = binding.posterCanvas.getWidth() - v.getWidth();
                        float maxY = binding.posterCanvas.getHeight() - v.getHeight();

                        v.setX(Math.max(0, Math.min(newX, Math.max(0, maxX))));
                        v.setY(Math.max(0, Math.min(newY, Math.max(0, maxY))));
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            openTextEditDialog();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void setupControlButtons() {
        // Edit Text Dialog
        binding.btnEditText.setOnClickListener(v -> openTextEditDialog());

        // Cycle Text Colors
        binding.btnTextColor.setOnClickListener(v -> {
            currentTextColorIndex = (currentTextColorIndex + 1) % TEXT_COLORS.length;
            binding.tvQuote.setTextColor(TEXT_COLORS[currentTextColorIndex]);
        });

        // Cycle Text Sizes
        binding.btnTextSize.setOnClickListener(v -> {
            currentTextSizeIndex = (currentTextSizeIndex + 1) % TEXT_SIZES.length;
            binding.tvQuote.setTextSize(TEXT_SIZES[currentTextSizeIndex]);
            Toast.makeText(this, "Text Size: " + (int) TEXT_SIZES[currentTextSizeIndex] + "sp", Toast.LENGTH_SHORT).show();
        });

        // Cycle Background Gradients
        binding.btnBgColor.setOnClickListener(v -> {
            currentBgIndex = (currentBgIndex + 1) % GRADIENT_PRESETS.length;
            int[] colors = GRADIENT_PRESETS[currentBgIndex];
            GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
            binding.canvasBgGradient.setBackground(gradient);
            binding.canvasBgImage.setVisibility(View.GONE); // Clear custom photo to reveal gradient
        });

        // Pick Custom Background Photo
        binding.btnPickBgPhoto.setOnClickListener(v -> bgImagePickerLauncher.launch("image/*"));

        // Toggle Decorative Border Frame
        binding.btnToggleBorder.setOnClickListener(v -> {
            isBorderActive = !isBorderActive;
            if (isBorderActive) {
                GradientDrawable borderDrawable = new GradientDrawable();
                borderDrawable.setColor(Color.TRANSPARENT);
                borderDrawable.setStroke(6, 0xFFFFD700); // 3dp gold border
                borderDrawable.setCornerRadius(16);
                binding.borderFrame.setBackground(borderDrawable);
                binding.borderFrame.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Decorative Frame: ON", Toast.LENGTH_SHORT).show();
            } else {
                binding.borderFrame.setVisibility(View.GONE);
                Toast.makeText(this, "Decorative Frame: OFF", Toast.LENGTH_SHORT).show();
            }
        });

        // Cycle Footer Styles (Style 1 -> Style 2 -> Style 3)
        binding.btnFooterStyle.setOnClickListener(v -> {
            currentFooterStyle = (currentFooterStyle + 1) % 3;
            binding.footerStyle1.setVisibility(currentFooterStyle == 0 ? View.VISIBLE : View.GONE);
            binding.footerStyle2.setVisibility(currentFooterStyle == 1 ? View.VISIBLE : View.GONE);
            binding.footerStyle3.setVisibility(currentFooterStyle == 2 ? View.VISIBLE : View.GONE);
            Toast.makeText(this, "Footer Style " + (currentFooterStyle + 1) + " Applied", Toast.LENGTH_SHORT).show();
        });

        // Change My Photo / Profile
        binding.btnChangeMyPhoto.setOnClickListener(v -> showProfileDialog());

        // Remove Watermark via Rewarded Ad
        binding.btnRemoveWatermark.setOnClickListener(v -> {
            adManager.showRewardedAd(this, () -> {
                binding.watermarkBadge.setVisibility(View.GONE);
                binding.btnRemoveWatermark.setVisibility(View.GONE);
                Toast.makeText(this, "✨ Watermark Removed!", Toast.LENGTH_LONG).show();
            });
        });

        // Save HD Button
        binding.btnSave.setOnClickListener(v -> savePosterHd());

        // WhatsApp Share Button
        binding.btnShareWhatsApp.setOnClickListener(v -> sharePoster(true));

        // More Share Button
        binding.btnShareMore.setOnClickListener(v -> sharePoster(false));
    }

    private void openTextEditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("संदेश या नाम बदलें (Edit Text)");

        final EditText input = new EditText(this);
        input.setText(binding.tvQuote.getText().toString());
        input.setSelection(input.getText().length());
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);

        builder.setPositiveButton("सहेजें (Save)", (dialog, which) -> {
            String updatedText = input.getText().toString().trim();
            if (!updatedText.isEmpty()) {
                binding.tvQuote.setText(updatedText);
            }
        });

        builder.setNegativeButton("रद्द करें (Cancel)", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showProfileDialog() {
        ProfileBottomSheetDialog dialog = ProfileBottomSheetDialog.newInstance();
        dialog.setOnProfileUpdatedListener(this::updateFooters);
        dialog.show(getSupportFragmentManager(), "ProfileBottomSheetDialog");
    }

    private void savePosterHd() {
        Bitmap posterBitmap = BitmapUtils.captureViewToBitmap(binding.posterCanvas);
        Uri savedUri = BitmapUtils.saveBitmapToGallery(this, posterBitmap, null);

        if (savedUri != null) {
            Toast.makeText(this, "✅ पोस्टर गैलरी (Pictures/UtsavPost) में सहेजा गया!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "पोस्टर सहेजने में विफल", Toast.LENGTH_SHORT).show();
        }

        // Show interstitial ad after save action
        adManager.showInterstitialAd(this, () -> {});
    }

    private void sharePoster(boolean targetWhatsAppOnly) {
        Bitmap posterBitmap = BitmapUtils.captureViewToBitmap(binding.posterCanvas);
        BitmapUtils.shareBitmapToWhatsAppOrSystem(this, posterBitmap, targetWhatsAppOnly);
    }
}
