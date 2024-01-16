/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.gmscompat;

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
public final class AttestationHooks {
    private static final String TAG = "GmsCompat/Attestation";

    private static final String PACKAGE_SVT = "com.hentai.lewdb.svt";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PROCESS_UNSTABLE = "com.google.android.gms.unstable";

    private static volatile boolean sIsSvt = false;
    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    private AttestationHooks() { }

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

    private static void spoofBuildGms(Context context) {
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
                    return;
                }
            } else {
                Log.d(TAG, "Resource ID is not found");
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Error accessing resources for '" + PACKAGE_SVT + "': " + e.getMessage());
            return;
        }
    }

    public static void initApplicationBeforeOnCreate(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || processName == null) {
            return;
        }

        if (packageName.equals(PACKAGE_GMS) &&
                processName.equals(PROCESS_UNSTABLE)) {
            sIsGms = true;
            spoofBuildGms(context);
        }

        if (packageName.equals(PACKAGE_FINSKY)) {
            sIsFinsky = true;
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
