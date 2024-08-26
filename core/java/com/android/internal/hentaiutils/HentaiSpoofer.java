/*
 * Copyright (C) 2021 The Android Open Source Project
 * Copyright (C) 2024 The hentaiOS Project and its Proprietors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.hentaiutils;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;

/** @hide */
public final class HentaiSpoofer {
    private static final String TAG = "HentaiSpoofer";

    // Play Integrity
    private static final String PACKAGE_SVT = "com.hentai.lewdb.svt";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PROCESS_UNSTABLE = "com.google.android.gms.unstable";

    private static volatile boolean sIsSvt = false;
    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    // Product Spoofing
    private static final String PACKAGE_SPT = "com.hentai.product.spt";
    private static final String[] PROCESSES_SPT = {
        // Google
        "com.google.android.googlequicksearchbox",
        // Photos
        "com.google.android.apps.photos",
        // Pixel Launcher
        "com.google.android.apps.nexuslauncher",
        // Wallpaper & style
        "com.google.android.apps.wallpaper",
        // Pixel Live Wallpaper
        "com.google.pixel.livewallpaper",
    };

    private HentaiSpoofer() { }

    private static void setBuildField(String key, String value) {
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static boolean spoofProcesses(String processName, String[] spoofProcesses) {
        for (String spoofProcess : spoofProcesses) {
            if (processName.startsWith(spoofProcess)) {
                return true;
            }
        }
        return false;
    }

    // Play Integrity
    private static void spoofGmsAttest(Context context) {
        PackageManager pm = context.getPackageManager();

        try {
            Resources resources = pm.getResourcesForApplication(PACKAGE_SVT);
            int resourceId = resources.getIdentifier("certifiedBuildProperties", "array", PACKAGE_SVT);

            if (resourceId != 0) {
                String[] sCertifiedProps = resources.getStringArray(resourceId);
                String[] buildProperties = {"MODEL", "DEVICE", "PRODUCT", "BRAND", "MANUFACTURER", "FINGERPRINT", "TYPE", "TAGS"};

                if (sCertifiedProps != null) {
                    sIsSvt = true;

                    for (String prop : buildProperties) {
                        int index = Arrays.asList(buildProperties).indexOf(prop);
                        if (index < sCertifiedProps.length && sCertifiedProps[index] != null && !sCertifiedProps[index].isEmpty()) {
                            setBuildField(prop, sCertifiedProps[index]);
                        }
                    }
                } else {
                    Log.d(TAG, "sCertifiedProps is null");
                }
            } else {
                Log.d(TAG, "Resource ID is not found");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Error accessing resources for '" + PACKAGE_SVT + "': " + e.getMessage());
        }

        return;
    }

    // Product Spoofing
    private static void spoofGms(Context context) {
        PackageManager pm = context.getPackageManager();

        try {
            Resources resources = pm.getResourcesForApplication(PACKAGE_SPT);
            int resourceId = resources.getIdentifier("buildProperties", "array", PACKAGE_SPT);

            if (resourceId != 0) {
                String[] sSpoofProps = resources.getStringArray(resourceId);
                String[] buildProperties = {"MODEL", "DEVICE", "PRODUCT", "BRAND", "MANUFACTURER", "FINGERPRINT", "TYPE", "TAGS"};

                if (sSpoofProps != null) {
                    for (String prop : buildProperties) {
                        int index = Arrays.asList(buildProperties).indexOf(prop);
                        if (index < sSpoofProps.length && sSpoofProps[index] != null && !sSpoofProps[index].isEmpty()) {
                            setBuildField(prop, sSpoofProps[index]);
                        }
                    }
                } else {
                    Log.d(TAG, "sSpoofProps is null");
                }
            } else {
                Log.d(TAG, "Resource ID is not found");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Error accessing resources for '" + PACKAGE_SPT + "': " + e.getMessage());
        }

        return;
    }

    public static void initApplicationBeforeOnCreate(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || processName == null) {
            return;
        }

        // Play Integrity
        if (packageName.equals(PACKAGE_GMS) &&
                processName.equals(PROCESS_UNSTABLE)) {
            sIsGms = true;
            spoofGmsAttest(context);
        }

        if (packageName.equals(PACKAGE_FINSKY)) {
            sIsFinsky = true;
        }

        // Product Spoofing
        if (spoofProcesses(processName, PROCESSES_SPT)) {
            spoofGms(context);
        }
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (sIsSvt && (isCallerSafetyNet() || sIsFinsky)) {
            Log.i(TAG, "Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }
}
