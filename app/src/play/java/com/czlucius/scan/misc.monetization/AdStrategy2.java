/*
 * Code Scanner. An android app to scan and create codes(barcodes, QR codes, etc)
 * Copyright (C) 2022 czlucius
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.czlucius.scan.misc.monetization;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import com.czlucius.scan.R;
import com.czlucius.scan.callbacks.Callback;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;

/**
 * Singleton for managing ads.
 */
public class AdStrategy2 {


    private final WeakReference<Context> contextWeakReference;
    private boolean shouldShowAds = true; // Used for rewarded ads, will not do IAP due to address concerns


    private static AdStrategy2 INSTANCE;

    private AdStrategy2(Context appCtx) {
        contextWeakReference = new WeakReference<>(appCtx);
    }

    /**
     * Get an instance of {@link AdStrategy2}
     *
     * @param appCtx Application context. Do not pass in other contexts
     */
    public static AdStrategy2 getInstance(Context appCtx) {
        if (INSTANCE == null) {
            INSTANCE = new AdStrategy2(appCtx);
        }
        return INSTANCE;
    }


    /**
     * Initialisation method.
     * ONLY TO BE CALLED ONCE.
     */
    public void initialize() {

        Bundle extras = new Bundle();
        extras.putString("max_ad_content_rating", "G");
        extras.putString("npa", "1"); // Request Non-Personalised Ads

        // PLAY: How to request for child directed ads?
//        AdRequest request = new AdRequest.Builder()
//                .addNetworkExtrasBundle(AdMobAdapter.class, extras)
//                .tagForChildDirectedTreatment(true).build();

        RequestConfiguration requestConfiguration = MobileAds.getRequestConfiguration()
                .toBuilder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .build();
        MobileAds.setRequestConfiguration(requestConfiguration);

        MobileAds.initialize(contextWeakReference.get());
    }


    public void loadAdView(Function<Integer, View> findViewByIdProducer) {

        if (!shouldShowAds) {
            View bannerLayout = findViewByIdProducer.apply(R.id.banner_layout);
            if (bannerLayout != null) {
                bannerLayout.setVisibility(View.GONE);
                if (bannerLayout instanceof ViewGroup) {
                    ((ViewGroup) bannerLayout).removeView(findViewByIdProducer.apply(R.id.banner));
                }
            }
            // If we should not show ads, we do not load them in the first place.
            // Best possible method to disable them (other than removing the views)
            // https://stackoverflow.com/questions/13323097/in-app-purchase-remove-ads
            return;
        }

        // Get AdView
        View view = findViewByIdProducer.apply(R.id.banner);


        if (view instanceof AdView) {
            AdView adView = (AdView) view;
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
        } else {
            throw new RuntimeException("View referenced is not AdView");
        }
    }

    public void addAdViewTo(ViewGroup parent) {
        if (!shouldShowAds) {
            return;
        }
        Context localContext = parent.getContext();
        LayoutInflater layoutInflater = LayoutInflater.from(localContext);
        View adView = layoutInflater.inflate(R.layout.banner, parent);
        loadAdView(parent::findViewById);
    }

    public void loadRewardedAdVideo(Activity activity, View root, Callback resetCallback) {


        AdRequest adRequest = new AdRequest.Builder().build();

        RewardedAd.load(contextWeakReference.get(), AdIdRetriever.retrieveRewardedId(),
                adRequest, new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        super.onAdLoaded(rewardedAd);
                        // Rewarded ad loaded, pass on to RewardedAdsHelper.
                        RewardedAdsHelper helper = new RewardedAdsHelper(rewardedAd, root, activity);
                        helper.startRewardedAds(resetCallback);
                        helper.setRewardObserver(newValue -> {
                            // Reward is obtained.
                            shouldShowAds = false;
                            // Remove current ad in the Info Fragment so user won't be confused.
                            ((ViewGroup) root).removeView(root.findViewById(R.id.banner));
                            activity.recreate();

                        });

                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        super.onAdFailedToLoad(loadAdError);
                        // Ad failed to load.
                        Snackbar.make(root, R.string.ad_failed_to_load, Snackbar.LENGTH_SHORT).show();
                        resetCallback.doAction();
                    }

                });


    }


}
