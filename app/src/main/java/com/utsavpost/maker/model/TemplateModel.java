package com.utsavpost.maker.model;

import android.graphics.Color;
import java.io.Serializable;

/**
 * Model representing a festival poster or status template.
 */
public class TemplateModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String title;
    private String category;
    private String language;
    private String bgGradientStart;
    private String bgGradientEnd;
    private String accentColor;
    private String imageUrl;
    private String quoteText;
    private boolean isPremium;

    public TemplateModel() {
    }

    public TemplateModel(int id, String title, String category, String language,
                         String bgGradientStart, String bgGradientEnd, String accentColor,
                         String imageUrl, String quoteText, boolean isPremium) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.language = language;
        this.bgGradientStart = bgGradientStart;
        this.bgGradientEnd = bgGradientEnd;
        this.accentColor = accentColor;
        this.imageUrl = imageUrl;
        this.quoteText = quoteText;
        this.isPremium = isPremium;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category != null ? category : "Festival";
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLanguage() {
        return language != null ? language : "Hindi";
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getBgGradientStart() {
        return bgGradientStart != null && !bgGradientStart.isEmpty() ? bgGradientStart : "#FF6D00";
    }

    public void setBgGradientStart(String bgGradientStart) {
        this.bgGradientStart = bgGradientStart;
    }

    public String getBgGradientEnd() {
        return bgGradientEnd != null && !bgGradientEnd.isEmpty() ? bgGradientEnd : "#D84315";
    }

    public void setBgGradientEnd(String bgGradientEnd) {
        this.bgGradientEnd = bgGradientEnd;
    }

    public String getAccentColor() {
        return accentColor != null && !accentColor.isEmpty() ? accentColor : "#FFD600";
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public String getImageUrl() {
        return imageUrl != null ? imageUrl : "";
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getQuoteText() {
        return quoteText != null ? quoteText : "";
    }

    public void setQuoteText(String quoteText) {
        this.quoteText = quoteText;
    }

    public boolean isPremium() {
        return isPremium;
    }

    public void setPremium(boolean premium) {
        isPremium = premium;
    }

    public int getStartColorInt() {
        try {
            return Color.parseColor(getBgGradientStart());
        } catch (Exception e) {
            return 0xFFFF6D00;
        }
    }

    public int getEndColorInt() {
        try {
            return Color.parseColor(getBgGradientEnd());
        } catch (Exception e) {
            return 0xFFD84315;
        }
    }

    public int getAccentColorInt() {
        try {
            return Color.parseColor(getAccentColor());
        } catch (Exception e) {
            return 0xFFFFD600;
        }
    }

    @Override
    public String toString() {
        return "TemplateModel{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", language='" + language + '\'' +
                ", isPremium=" + isPremium +
                '}';
    }
}

