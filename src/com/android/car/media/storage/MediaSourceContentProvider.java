/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.source.MediaSource;

/**
 * ContentProvider that provides the last browsed media app.
 */
public class MediaSourceContentProvider extends ContentProvider {

    private final static String[] COLUMN_NAMES = {MediaConstants.KEY_PACKAGE_NAME};

    private PrimaryMediaSourceManager mPrimaryMediaSourceManager;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        mPrimaryMediaSourceManager = PrimaryMediaSourceManager.getInstance(context);
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (MediaConstants.URI_MEDIA_SOURCE.equals(uri)) {
            MatrixCursor cursor = new MatrixCursor(COLUMN_NAMES);
            MediaSource source = mPrimaryMediaSourceManager.getPrimaryMediaSource();
            if (source != null) {
                Object[] row = new Object[1];
                row[0] = source.getPackageName();
                cursor.addRow(row);
            }
            return cursor;
        }
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        if (MediaConstants.URI_MEDIA_SOURCE.equals(uri)) {
            String packageName = values.getAsString(MediaConstants.KEY_PACKAGE_NAME);
            mPrimaryMediaSourceManager.setPrimaryMediaSource(
                    new MediaSource(getContext(), packageName));
            return 1;
        }
        return 0;
    }

}
