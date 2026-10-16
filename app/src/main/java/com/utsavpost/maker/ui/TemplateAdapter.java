package com.utsavpost.maker.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.utsavpost.maker.EditorActivity;
import com.utsavpost.maker.R;
import com.utsavpost.maker.databinding.ItemTemplateBinding;
import com.utsavpost.maker.model.TemplateModel;
import com.utsavpost.maker.utils.UserProfileManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter presenting mini poster cards in a 4:5 aspect ratio with dynamic gradients,
 * authentic multilingual quotes, and live user branding footer strips.
 */
public class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder> {

    public interface OnTemplateClickListener {
        void onTemplateClick(TemplateModel template);
    }

    private final Context context;
    private final List<TemplateModel> templateList = new ArrayList<>();
    private final UserProfileManager profileManager;
    private OnTemplateClickListener clickListener;

    public TemplateAdapter(Context context) {
        this.context = context;
        this.profileManager = UserProfileManager.getInstance(context);
    }

    public void setOnTemplateClickListener(OnTemplateClickListener listener) {
        this.clickListener = listener;
    }

    public void setTemplates(List<TemplateModel> newTemplates) {
        this.templateList.clear();
        if (newTemplates != null) {
            this.templateList.addAll(newTemplates);
        }
        notifyDataSetChanged();
    }

    /**
     * Refreshes user footer branding when profile is updated.
     */
    public void updateUserProfile() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TemplateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTemplateBinding binding = ItemTemplateBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new TemplateViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TemplateViewHolder holder, int position) {
        if (position >= 0 && position < templateList.size()) {
            holder.bind(templateList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return templateList.size();
    }

    class TemplateViewHolder extends RecyclerView.ViewHolder {
        private final ItemTemplateBinding binding;

        TemplateViewHolder(ItemTemplateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TemplateModel template) {
            if (template == null) return;

            // 1. Dynamic Gradient Background
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{template.getStartColorInt(), template.getEndColorInt()}
            );
            gradient.setCornerRadius(0);
            binding.bgGradientView.setBackground(gradient);

            // 2. Background Image (if provided)
            String imageUrl = template.getImageUrl();
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                binding.ivTemplateBg.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(imageUrl)
                        .placeholder(new ColorDrawable(Color.TRANSPARENT))
                        .error(new ColorDrawable(Color.TRANSPARENT))
                        .transform(new CenterCrop())
                        .into(binding.ivTemplateBg);
            } else {
                binding.ivTemplateBg.setVisibility(View.GONE);
                Glide.with(context).clear(binding.ivTemplateBg);
            }

            // 3. Badges and Text Content
            binding.tvBadgeTitle.setText(template.getTitle());
            binding.badgePro.setVisibility(template.isPremium() ? View.VISIBLE : View.GONE);

            binding.tvQuote.setText(template.getQuoteText());
            binding.tvQuote.setTextColor(template.getAccentColorInt());

            // 4. Live User Footer Strip
            binding.tvUserName.setText(profileManager.getUserName());
            binding.tvUserDesignation.setText(profileManager.getUserDesignation());

            String userPhotoPath = profileManager.getUserPhotoPath();
            if (userPhotoPath != null) {
                File file = new File(userPhotoPath);
                if (file.exists()) {
                    Glide.with(context)
                            .load(file)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person_placeholder)
                            .into(binding.ivUserPhoto);
                } else {
                    binding.ivUserPhoto.setImageResource(R.drawable.ic_person_placeholder);
                }
            } else {
                binding.ivUserPhoto.setImageResource(R.drawable.ic_person_placeholder);
            }

            // 5. Click Handler to launch EditorActivity
            binding.cardTemplate.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTemplateClick(template);
                } else {
                    Intent intent = new Intent(context, EditorActivity.class);
                    intent.putExtra("extra_template", template);
                    context.startActivity(intent);
                }
            });
        }
    }
}
