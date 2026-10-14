package com.utsavpost.maker.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.utsavpost.maker.R;
import com.utsavpost.maker.databinding.DialogProfileSetupBinding;
import com.utsavpost.maker.utils.UserProfileManager;

import java.io.File;

/**
 * BottomSheet dialog enabling users to customize their branding profile:
 * profile picture, full name, and designation / social contact info.
 */
public class ProfileBottomSheetDialog extends BottomSheetDialogFragment {

    public interface OnProfileUpdatedListener {
        void onProfileUpdated();
    }

    private DialogProfileSetupBinding binding;
    private UserProfileManager profileManager;
    private OnProfileUpdatedListener updateListener;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private Uri pendingImageUri = null;

    public static ProfileBottomSheetDialog newInstance() {
        return new ProfileBottomSheetDialog();
    }

    public void setOnProfileUpdatedListener(OnProfileUpdatedListener listener) {
        this.updateListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileManager = UserProfileManager.getInstance(requireContext());

        // Register gallery image picker using modern ActivityResultContract
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        pendingImageUri = uri;
                        // Copy to internal app storage immediately to preserve file access
                        String savedPath = profileManager.saveProfileImageToInternalStorage(requireContext(), uri);
                        if (savedPath != null && binding != null) {
                            Glide.with(this)
                                    .load(new File(savedPath))
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_person_placeholder)
                                    .into(binding.ivProfilePhoto);
                            Toast.makeText(requireContext(), "फ़ोटो जोड़ी गई!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogProfileSetupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        populateExistingData();
        setupClickListeners();
    }

    private void populateExistingData() {
        if (profileManager == null || binding == null) return;

        binding.etName.setText(profileManager.getUserName());
        binding.etDesignation.setText(profileManager.getUserDesignation());

        String photoPath = profileManager.getUserPhotoPath();
        if (photoPath != null) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                Glide.with(this)
                        .load(photoFile)
                        .circleCrop()
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(binding.ivProfilePhoto);
            }
        }
    }

    private void setupClickListeners() {
        binding.framePhotoContainer.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        binding.btnSaveProfile.setOnClickListener(v -> saveProfileAndDismiss());
    }

    private void saveProfileAndDismiss() {
        if (binding == null) return;

        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String designation = binding.etDesignation.getText() != null ? binding.etDesignation.getText().toString().trim() : "";

        if (name.isEmpty()) {
            binding.tilName.setError("कृपया अपना नाम दर्ज करें");
            return;
        } else {
            binding.tilName.setError(null);
        }

        profileManager.setUserName(name);
        profileManager.setUserDesignation(designation);
        profileManager.setFirstLaunch(false);

        Toast.makeText(requireContext(), "प्रोफ़ाइल सफलतापूर्वक सहेजी गई!", Toast.LENGTH_SHORT).show();

        if (updateListener != null) {
            updateListener.onProfileUpdated();
        }

        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

