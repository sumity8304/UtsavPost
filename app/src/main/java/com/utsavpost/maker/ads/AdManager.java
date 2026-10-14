package com.utsavpost.maker.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton ad mediator for Google Mobile Ads (AdMob).
 * Handles Banner loading, preloading & capped display of Interstitials,
 * and Rewarded video ads with graceful fallback so users are never blocked.
 */
public class AdManager {

    private static final String TAG = "AdManager";

    // Official Google Test Ad Unit IDs
    public static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111";
    public static final String INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917";

    private static volatile AdManager instance;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;

    private boolean isInterstitialLoading = false;
    private boolean isRewardedLoading = false;

    // Action counter for frequency capping (show interstitial every 2nd action)
    private int actionCount = 0;

    public interface OnRewardListener {
        void onUserEarnedReward();
    }

    private AdManager() {
    }

    public static AdManager getInstance() {
        if (instance == null) {
            synchronized (AdManager.class) {
                if (instance == null) {
                    instance = new AdManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initializes Google Mobile Ads SDK and pre-fetches initial ad inventory.
     */
    public void initialize(Context context) {
        if (context == null || isInitialized.getAndSet(true)) {
            return;
        }

        MobileAds.initialize(context.getApplicationContext(), initializationStatus -> {
            Log.d(TAG, "AdMob MobileAds initialized successfully");
            preloadInterstitialAd(context.getApplicationContext());
            preloadRewardedAd(context.getApplicationContext());
        });
    }

    /**
     * Loads a responsive banner ad into the provided container.
     */
    public void loadBannerAd(FrameLayout bannerContainer, Context context) {
        if (bannerContainer == null || context == null) {
            return;
        }

        try {
            AdView adView = new AdView(context);
            adView.setAdUnitId(BANNER_TEST_ID);
            adView.setAdSize(AdSize.BANNER);

            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);

            bannerContainer.removeAllViews();
            bannerContainer.addView(adView);
        } catch (Exception e) {
            Log.e(TAG, "Failed loading banner ad", e);
        }
    }

    /**
     * Preloads an Interstitial Ad in the background.
     */
    public void preloadInterstitialAd(Context context) {
        if (context == null || isInterstitialLoading || interstitialAd != null) {
            return;
        }

        isInterstitialLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();

        InterstitialAd.load(
                context.getApplicationContext(),
                INTERSTITIAL_TEST_ID,
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        isInterstitialLoading = false;
                        Log.d(TAG, "Interstitial Ad preloaded successfully");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        isInterstitialLoading = false;
                        Log.w(TAG, "Interstitial Ad failed to load: " + loadAdError.getMessage());
                    }
                }
        );
    }

    /**
     * Displays Interstitial Ad with frequency capping (every 2nd action),
     * ensuring the provided callback is always executed so app navigation never stalls.
     */
    public void showInterstitialAd(Activity activity, Runnable onAdClosedOrFailed) {
        actionCount++;

        // Frequency capping: show every 2nd action
        boolean shouldShow = (actionCount % 2 == 0);

        if (activity != null && shouldShow && interstitialAd != null) {
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    interstitialAd = null;
                    preloadInterstitialAd(activity);
                    if (onAdClosedOrFailed != null) {
                        onAdClosedOrFailed.run();
                    }
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    interstitialAd = null;
                    preloadInterstitialAd(activity);
                    Log.w(TAG, "Interstitial failed to show: " + adError.getMessage());
                    if (onAdClosedOrFailed != null) {
                        onAdClosedOrFailed.run();
                    }
                }
            });

            interstitialAd.show(activity);
        } else {
            // Not ready or frequency check skipped; guarantee immediate flow continuation
            if (activity != null && interstitialAd == null) {
                preloadInterstitialAd(activity);
            }
            if (onAdClosedOrFailed != null) {
                onAdClosedOrFailed.run();
            }
        }
    }

    /**
     * Preloads a Rewarded Ad in the background.
     */
    public void preloadRewardedAd(Context context) {
        if (context == null || isRewardedLoading || rewardedAd != null) {
            return;
        }

        isRewardedLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(
                context.getApplicationContext(),
                REWARDED_TEST_ID,
                adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd ad) {
                        rewardedAd = ad;
                        isRewardedLoading = false;
                        Log.d(TAG, "Rewarded Ad preloaded successfully");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        rewardedAd = null;
                        isRewardedLoading = false;
                        Log.w(TAG, "Rewarded Ad failed to load: " + loadAdError.getMessage());
                    }
                }
        );
    }

    /**
     * Shows a rewarded ad to unlock premium features (e.g., removing watermark).
     * If the ad is not ready or fails, grants the reward with a friendly toast so the user is never stuck.
     */
    public void showRewardedAd(Activity activity, OnRewardListener listener) {
        if (activity == null) {
            if (listener != null) {
                listener.onUserEarnedReward();
            }
            return;
        }

        if (rewardedAd != null) {
            final boolean[] rewardEarned = {false};

            rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    rewardedAd = null;
                    preloadRewardedAd(activity);
                    if (rewardEarned[0] && listener != null) {
                        listener.onUserEarnedReward();
                    }
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    rewardedAd = null;
                    preloadRewardedAd(activity);
                    Log.w(TAG, "Rewarded Ad failed to show: " + adError.getMessage());
                    Toast.makeText(activity, "विज्ञापन लोड नहीं हो सका। वॉटरमार्क हटा दिया गया है!", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onUserEarnedReward();
                    }
                }
            });

            rewardedAd.show(activity, rewardItem -> {
                rewardEarned[0] = true;
                Log.d(TAG, "User earned reward: " + rewardItem.getAmount() + " " + rewardItem.getType());
            });
        } else {
            // Graceful fallback for offline / test environments
            Toast.makeText(activity, "विज्ञापन लोड नहीं हो सका। वॉटरमार्क हटा दिया गया है!", Toast.LENGTH_SHORT).show();
            preloadRewardedAd(activity);
            if (listener != null) {
                listener.onUserEarnedReward();
            }
        }
    }
}

