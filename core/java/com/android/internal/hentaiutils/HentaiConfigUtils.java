/*
 * Copyright (C) 2024 The hentaiOS Project and its Proprietors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.hentaiutils;

import android.content.res.Resources;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import java.util.Arrays;

public final class HentaiConfigUtils {
    private static final String TAG = "HentaiConfigUtils";
    private static final String PACKAGE_SPT = "com.hentai.product.spt";

    private HentaiConfigUtils() { }

    private static String[] getDeviceConfigsOverride(Context context) {
        PackageManager pm = context.getPackageManager();

        try {
            Resources resources = pm.getResourcesForApplication(PACKAGE_SPT);
            int resourceId = resources.getIdentifier("deviceConfig", "array", PACKAGE_SPT);

            if (resourceId != 0) {
                return resources.getStringArray(resourceId);
            } else {
                Log.d(TAG, "Resource ID is not found");
                return new String[0];
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(TAG, "Error accessing resources for '" + PACKAGE_SPT + "': " + e.getMessage());
            return new String[0];
        }
    }

    public static String setOverriddenValue(Context context, String namespace, String name) {
        for (String p : getDeviceConfigsOverride(context)) {
            String[] kv = p.split("=");
            String fullKey = kv[0];
            String[] nsKey = fullKey.split("/");
            if (nsKey.length == 2 && nsKey[0].equals(namespace) && nsKey[1].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }
}
