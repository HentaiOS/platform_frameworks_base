/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.os.Message;
import android.platform.test.annotations.Presubmit;
import android.view.DisplayInfo;

import androidx.test.filters.SmallTest;

import com.android.server.wm.TransitionController.OnStartCollect;
import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Tests for the {@link DisplayContent} class when FLAG_DEFER_DISPLAY_UPDATES is enabled.
 *
 * Build/Install/Run:
 * atest WmTests:DisplayContentDeferredUpdateTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class DisplayContentDeferredUpdateTests extends WindowTestsBase {

    // The fields to override the current DisplayInfo.
    private String mUniqueId;
    private int mColorMode;
    private int mLogicalDensityDpi;

    private final Message mScreenUnblocker = mock(Message.class);

    @Override
    protected void onBeforeSystemServicesCreated() {
        // Set other flags to their default values
        mSetFlagsRule.initAllFlagsToReleaseConfigDefault();

        mSetFlagsRule.enableFlags(Flags.FLAG_DEFER_DISPLAY_UPDATES);
    }

    @Before
    public void before() {
        mockTransitionsController(/* enabled= */ true);
        mockRemoteDisplayChangeController();
        performInitialDisplayUpdate();
    }

    @Test
    public void testUpdate_deferrableFieldChangedTransitionStarted_deferrableFieldUpdated() {
        mUniqueId = "old";
        Runnable onUpdated = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated);

        // Emulate that collection has started
        captureStartTransitionCollection().getValue().onCollectStarted(/* deferred= */ true);
        verify(onUpdated).run();
        clearInvocations(mDisplayContent.mTransitionController, onUpdated);

        mUniqueId = "new";
        mDisplayContent.requestDisplayUpdate(onUpdated);
        captureStartTransitionCollection().getValue().onCollectStarted(/* deferred= */ true);
        verify(onUpdated).run();
        verify(mDisplayContent.mTransitionController).requestStartTransition(
                any(), any(), any(), any());
        assertThat(mDisplayContent.getDisplayInfo().uniqueId).isEqualTo("new");
        clearInvocations(mDisplayContent.mTransitionController, onUpdated);

        mLogicalDensityDpi += 100;
        mDisplayContent.requestDisplayUpdate(onUpdated);
        captureStartTransitionCollection().getValue().onCollectStarted(/* deferred= */ true);
        verify(onUpdated).run();
        verify(mDisplayContent.mTransitionController).requestStartTransition(
                any(), any(), any(), any());
    }

    @Test
    public void testUpdate_nonDeferrableUpdateAndTransitionDeferred_nonDeferrableFieldUpdated() {
        // Update only color mode (non-deferrable field) and keep the same unique id
        mUniqueId = "initial_unique_id";
        mColorMode = 123;
        Runnable onUpdated = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated);

        verify(onUpdated).run();
        assertThat(mDisplayContent.getDisplayInfo().colorMode).isEqualTo(123);
    }

    @Test
    public void testUpdate_nonDeferrableUpdateTwiceAndTransitionDeferred_fieldHasLatestValue() {
        // Update only color mode (non-deferrable field) and keep the same unique id
        mUniqueId = "initial_unique_id";
        mColorMode = 123;
        mDisplayContent.requestDisplayUpdate(mock(Runnable.class));

        assertThat(mDisplayContent.getDisplayInfo().colorMode).isEqualTo(123);
        assertThat(mDisplayContent.getDisplayInfo().uniqueId)
                .isEqualTo("initial_unique_id");

        // Update unique id (deferrable field), keep the same color mode,
        // this update should be deferred
        mUniqueId = "new_unique_id";
        mDisplayContent.requestDisplayUpdate(mock(Runnable.class));

        assertThat(mDisplayContent.getDisplayInfo().colorMode).isEqualTo(123);
        assertThat(mDisplayContent.getDisplayInfo().uniqueId)
                .isEqualTo("initial_unique_id");

        // Update color mode again and keep the same unique id, color mode update
        // should not be deferred, unique id update is still deferred as transition
        // has not started collecting yet
        mColorMode = 456;
        Runnable onUpdated = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated);

        assertThat(mDisplayContent.getDisplayInfo().colorMode).isEqualTo(456);
        assertThat(mDisplayContent.getDisplayInfo().uniqueId)
                .isEqualTo("initial_unique_id");

        // Mark transition as started collected, so pending changes are applied
        captureStartTransitionCollection().getValue().onCollectStarted(/* deferred= */ true);

        // Verify that all fields have the latest values
        verify(onUpdated).run();
        assertThat(mDisplayContent.getDisplayInfo().colorMode).isEqualTo(456);
        assertThat(mDisplayContent.getDisplayInfo().uniqueId).isEqualTo("new_unique_id");
    }

    @Test
    public void testUpdate_deferrableFieldUpdatedTransitionPending_fieldNotUpdated() {
        mUniqueId = "old";
        Runnable onUpdated = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated);
        captureStartTransitionCollection().getValue().onCollectStarted(/* deferred= */ true);
        verify(onUpdated).run();
        clearInvocations(mDisplayContent.mTransitionController, onUpdated);

        mUniqueId = "new";
        mDisplayContent.requestDisplayUpdate(onUpdated);

        captureStartTransitionCollection(); // do not continue by not starting the collection
        verify(onUpdated, never()).run();
        assertThat(mDisplayContent.getDisplayInfo().uniqueId).isEqualTo("old");
    }

    @Test
    public void testTwoDisplayUpdates_transitionStarted_displayUpdated() {
        mUniqueId = "old";
        Runnable onUpdated = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated);
        captureStartTransitionCollection().getValue()
                .onCollectStarted(/* deferred= */ true);
        verify(onUpdated).run();
        clearInvocations(mDisplayContent.mTransitionController, onUpdated);

        // Perform two display updates while WM is 'busy'
        mUniqueId = "new1";
        Runnable onUpdated1 = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated1);
        mUniqueId = "new2";
        Runnable onUpdated2 = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated2);

        // Continue with the first update
        captureStartTransitionCollection().getAllValues().get(0)
                .onCollectStarted(/* deferred= */ true);
        verify(onUpdated1).run();
        verify(onUpdated2, never()).run();
        assertThat(mDisplayContent.getDisplayInfo().uniqueId).isEqualTo("new1");

        // Continue with the second update
        captureStartTransitionCollection().getAllValues().get(1)
                .onCollectStarted(/* deferred= */ true);
        verify(onUpdated2).run();
        assertThat(mDisplayContent.getDisplayInfo().uniqueId).isEqualTo("new2");
    }

    @Test
    public void testWaitForTransition_displaySwitching_waitsForTransitionToBeStarted() {
        mSetFlagsRule.enableFlags(Flags.FLAG_WAIT_FOR_TRANSITION_ON_DISPLAY_SWITCH);
        mDisplayContent.mDisplayUpdater.onDisplaySwitching(/* switching= */ true);
        boolean willWait = mDisplayContent.mDisplayUpdater.waitForTransition(mScreenUnblocker);
        assertThat(willWait).isTrue();
        mUniqueId = "new";
        mDisplayContent.requestDisplayUpdate(mock(Runnable.class));
        when(mDisplayContent.mTransitionController.inTransition()).thenReturn(true);
        captureStartTransitionCollection().getValue().onCollectStarted(/* deferred= */ true);

        // Verify that screen is not unblocked yet as the start transaction hasn't been presented
        verify(mScreenUnblocker, never()).sendToTarget();

        when(mDisplayContent.mTransitionController.inTransition()).thenReturn(false);
        final Transition transition = captureRequestedTransition().getValue();
        makeTransitionTransactionCompleted(transition);

        // Verify that screen is unblocked as start transaction of the transition
        // has been completed
        verify(mScreenUnblocker).sendToTarget();
    }

    @Test
    public void testWaitForTransition_displayNotSwitching_doesNotWait() {
        mSetFlagsRule.enableFlags(Flags.FLAG_WAIT_FOR_TRANSITION_ON_DISPLAY_SWITCH);
        mDisplayContent.mDisplayUpdater.onDisplaySwitching(/* switching= */ false);

        boolean willWait = mDisplayContent.mDisplayUpdater.waitForTransition(mScreenUnblocker);

        assertThat(willWait).isFalse();
        verify(mScreenUnblocker, never()).sendToTarget();
    }

    @Test
    public void testWaitForTransition_displayIsSwitchingButFlagDisabled_doesNotWait() {
        mSetFlagsRule.disableFlags(Flags.FLAG_WAIT_FOR_TRANSITION_ON_DISPLAY_SWITCH);
        mDisplayContent.mDisplayUpdater.onDisplaySwitching(/* switching= */ true);

        boolean willWait = mDisplayContent.mDisplayUpdater.waitForTransition(mScreenUnblocker);

        assertThat(willWait).isFalse();
        verify(mScreenUnblocker, never()).sendToTarget();
    }

    private void mockTransitionsController(boolean enabled) {
        spyOn(mDisplayContent.mTransitionController);
        when(mDisplayContent.mTransitionController.isShellTransitionsEnabled()).thenReturn(enabled);
        doReturn(true).when(mDisplayContent.mTransitionController).startCollectOrQueue(any(),
                any());
    }

    private void mockRemoteDisplayChangeController() {
        spyOn(mDisplayContent.mRemoteDisplayChangeController);
        doReturn(true).when(mDisplayContent.mRemoteDisplayChangeController)
                .performRemoteDisplayChange(anyInt(), anyInt(), any(), any());
    }

    private ArgumentCaptor<OnStartCollect> captureStartTransitionCollection() {
        ArgumentCaptor<OnStartCollect> callbackCaptor =
                ArgumentCaptor.forClass(OnStartCollect.class);
        verify(mDisplayContent.mTransitionController, atLeast(1)).startCollectOrQueue(any(),
                callbackCaptor.capture());
        return callbackCaptor;
    }

    private ArgumentCaptor<Transition> captureRequestedTransition() {
        ArgumentCaptor<Transition> callbackCaptor =
                ArgumentCaptor.forClass(Transition.class);
        verify(mDisplayContent.mTransitionController, atLeast(1))
                .requestStartTransition(callbackCaptor.capture(), any(), any(), any());
        return callbackCaptor;
    }

    private void makeTransitionTransactionCompleted(Transition transition) {
        if (transition.mTransactionCompletedListeners != null) {
            for (int i = 0; i < transition.mTransactionCompletedListeners.size(); i++) {
                final Runnable listener = transition.mTransactionCompletedListeners.get(i);
                listener.run();
            }
        }
    }

    private void performInitialDisplayUpdate() {
        mUniqueId = "initial_unique_id";
        mColorMode = 0;
        mLogicalDensityDpi = 400;

        spyOn(mDisplayContent.mDisplay);
        doAnswer(invocation -> {
            DisplayInfo info = invocation.getArgument(0);
            info.uniqueId = mUniqueId;
            info.colorMode = mColorMode;
            info.logicalDensityDpi = mLogicalDensityDpi;
            return null;
        }).when(mDisplayContent.mDisplay).getDisplayInfo(any());
        Runnable onUpdated = mock(Runnable.class);
        mDisplayContent.requestDisplayUpdate(onUpdated);
    }
}
