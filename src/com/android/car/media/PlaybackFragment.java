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

package com.android.car.media;

import static com.android.car.arch.common.LiveDataFunctions.dataOf;
import static com.android.car.arch.common.LiveDataFunctions.freezable;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.util.ViewHelper;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MetadataController;
import com.android.car.media.common.PlaybackControlsActionBar;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.widgets.AppBarView;
import com.android.car.media.widgets.ViewUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Fragment} that implements both the playback and the content forward browsing experience.
 * It observes a {@link PlaybackViewModel} and updates its information depending on the currently
 * playing media source through the {@link android.media.session.MediaSession} API.
 */
public class PlaybackFragment extends Fragment {
    private static final String TAG = "PlaybackFragment";

    private AppBarView mAppBarView;
    private PlaybackControlsActionBar mPlaybackControls;
    private QueueItemsAdapter mQueueAdapter;
    private RecyclerView mQueue;
    private ConstraintLayout mMetadataContainer;
    private SeekBar mSeekBar;

    private MetadataController mMetadataController;
    private ConstraintLayout mRootView;

    private PlaybackViewModel.PlaybackController mController;
    private Long mActiveQueueItemId;

    private MutableLiveData<Boolean> mUpdatesPaused = dataOf(false);

    private boolean mQueueIsVisible;

    private int mFadeDuration;

    public class QueueViewHolder extends RecyclerView.ViewHolder {

        private final View mView;
        private final TextView mTitle;
        private final View mActiveIcon;

        QueueViewHolder(View itemView) {
            super(itemView);
            mView = itemView;
            mTitle = itemView.findViewById(R.id.title);
            mActiveIcon = itemView.findViewById(R.id.now_playing);
        }

        void bind(MediaItemMetadata item) {
            mView.setOnClickListener(v -> onQueueItemClicked(item));

            mTitle.setText(item.getTitle());
            boolean active = mActiveQueueItemId != null && mActiveQueueItemId == item.getQueueId();
            ViewHelper.setVisible(mActiveIcon, active);
        }
    }


    private class QueueItemsAdapter extends RecyclerView.Adapter<QueueViewHolder> {

        private final List<MediaItemMetadata> mQueueItems;

        QueueItemsAdapter(@Nullable List<MediaItemMetadata> items) {
            mQueueItems = new ArrayList<>(items != null ? items : Collections.emptyList());
            setHasStableIds(true);
        }

        @Override
        public QueueViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new QueueViewHolder(inflater.inflate(R.layout.queue_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(QueueViewHolder holder, int position) {
            int size = mQueueItems.size();
            if (0 <= position && position < size) {
                holder.bind(mQueueItems.get(position));
            } else {
                Log.e(TAG, "onBindViewHolder invalid position " + position + " of " + size);
            }
        }

        @Override
        public int getItemCount() {
            return mQueueItems.size();
        }

        void refresh() {
            // TODO: Perform a diff between current and new content and trigger the proper
            // RecyclerView updates.
            this.notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return mQueueItems.get(position).getQueueId();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playback, container, false);
        mRootView = view.findViewById(R.id.playback_container);
        mQueue = view.findViewById(R.id.queue_list);
        mMetadataContainer = view.findViewById(R.id.metadata_container);
        mSeekBar = view.findViewById(R.id.seek_bar);

        getPlaybackViewModel().getPlaybackController().observe(getViewLifecycleOwner(),
                controller -> mController = controller);
        initPlaybackControls(view.findViewById(R.id.playback_controls));
        initQueue();
        initMetadataController(view);
        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mAppBarView = ((AppBarView.AppBarProvider) context).getAppBar();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void initPlaybackControls(PlaybackControlsActionBar playbackControls) {
        mPlaybackControls = playbackControls;
        mPlaybackControls.setModel(getPlaybackViewModel(), getViewLifecycleOwner());
        mPlaybackControls.setAnimationViewGroup(mRootView);
    }

    private void initQueue() {
        mFadeDuration = getResources().getInteger(
                R.integer.fragment_playback_queue_fade_duration_ms);

        mQueue.setVerticalFadingEdgeEnabled(
                getResources().getBoolean(R.bool.queue_fading_edge_length_enabled));
        mQueueAdapter = new QueueItemsAdapter(null);

        getPlaybackViewModel().getPlaybackStateWrapper().observe(getViewLifecycleOwner(),
                state -> {
                    Long itemId = (state != null) ? state.getActiveQueueItemId() : null;
                    if (!Objects.equals(mActiveQueueItemId, itemId)) {
                        mActiveQueueItemId = itemId;
                        mQueueAdapter.refresh();
                    }
                });
        mQueue.setAdapter(mQueueAdapter);
        mQueue.setLayoutManager(new LinearLayoutManager(getContext()));

        freezable(mUpdatesPaused, getPlaybackViewModel().getQueue()).observe(this, this::setQueue);

        getPlaybackViewModel().hasQueue().observe(getViewLifecycleOwner(), hasQueue -> {
            boolean enableQueue = (hasQueue != null) && hasQueue;
            mAppBarView.setHasQueue(enableQueue);
            if (mQueueIsVisible && !enableQueue) {
                toggleQueueVisibility();
            }
        });
    }

    private void setQueue(List<MediaItemMetadata> queueItems) {
        mQueueAdapter = new QueueItemsAdapter(queueItems);
        mQueue.swapAdapter(mQueueAdapter, false);
        mQueueAdapter.refresh();
    }

    private void initMetadataController(View view) {
        ImageView albumArt = view.findViewById(R.id.album_art);
        TextView title = view.findViewById(R.id.title);
        TextView albumTitle = view.findViewById(R.id.album_title);
        TextView artist = view.findViewById(R.id.artist);
        SeekBar seekbar = view.findViewById(R.id.seek_bar);
        TextView time = view.findViewById(R.id.time);
        mMetadataController = new MetadataController(getViewLifecycleOwner(),
                getPlaybackViewModel(), mUpdatesPaused,
                title, albumTitle, artist, time, seekbar, albumArt, getResources()
                .getDimensionPixelSize(R.dimen.playback_album_art_size));
    }

    /**
     * Hides or shows the playback queue
     */
    public void toggleQueueVisibility() {
        mQueueIsVisible = !mQueueIsVisible;
        mAppBarView.activateQueueButton(mQueueIsVisible);
        if (mQueueIsVisible) {
            ViewUtils.hideViewAnimated(mMetadataContainer, mFadeDuration);
            ViewUtils.hideViewAnimated(mSeekBar, mFadeDuration);
            ViewUtils.showViewAnimated(mQueue, mFadeDuration);
        } else {
            ViewUtils.hideViewAnimated(mQueue, mFadeDuration);
            ViewUtils.showViewAnimated(mMetadataContainer, mFadeDuration);
            ViewUtils.showViewAnimated(mSeekBar, mFadeDuration);
        }
    }

    private void onQueueItemClicked(MediaItemMetadata item) {
        if (mController != null) {
            mController.skipToQueueItem(item.getQueueId());
        }
    }

    /**
     * Collapses the playback controls.
     */
    public void closeOverflowMenu() {
        mPlaybackControls.close();
    }

    private PlaybackViewModel getPlaybackViewModel() {
        return PlaybackViewModel.get(getActivity().getApplication());
    }
}
