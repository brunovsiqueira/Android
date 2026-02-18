/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.onboarding.ui

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.browser.newaddressbaroption.RealNewAddressBarOptionManager
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.FullOnboardingSkipper.ViewState
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@Suppress("EXPERIMENTAL_API_USAGE")
class OnboardingViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var userStageStore: UserStageStore = mock()

    private val pageLayout: OnboardingPageManager = mock()

    private val onboardingSkipper: OnboardingSkipper = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private val newAddressBarOptionManager: RealNewAddressBarOptionManager = mock()

    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles =
        FakeFeatureToggleFactory.create(OnboardingBrandDesignUpdateToggles::class.java)

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(
            userStageStore = userStageStore,
            pageLayoutManager = pageLayout,
            dispatchers = coroutineRule.testDispatcherProvider,
            onboardingSkipper = onboardingSkipper,
            appBuildConfig = appBuildConfig,
            newAddressBarOptionManager = newAddressBarOptionManager,
            onboardingBrandDesignUpdateToggles = onboardingBrandDesignUpdateToggles,
        )
    }

    @Test
    fun whenOnboardingDoneThenCompleteStage() = runTest {
        testee.onOnboardingDone()
        verify(userStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenAppBuildConfigPreventsSkippingOnboardingThenOnboardingSkipperNotInteractedWith() = runTest {
        configureAppBuildConfigPreventsSkipping()
        testee.initializeOnboardingSkipper()
        verifyNoInteractions(onboardingSkipper)
    }

    @Test
    fun whenAppBuildConfigAllowsSkippingOnboardingAndPrivacyConfigDownloadedSkippingIsPossible() = runTest {
        onboardingBrandDesignUpdateToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = false))
        configureAppBuildConfigAllowsSkipping()
        configureSkipperFlow()
        testee.initializeOnboardingSkipper()
        verify(onboardingSkipper).privacyConfigDownloaded
    }

    @Test
    fun whenOnOnboardingSkippedCalledThenMarkOnboardingAsCompleted() = runTest {
        testee.onOnboardingSkipped()
        verify(onboardingSkipper).markOnboardingAsCompleted()
    }

    @Test
    fun whenDevOnlyFullyCompleteAllOnboardingCalledThenMarkOnboardingAsCompletedAndSetAsShown() = runTest {
        testee.devOnlyFullyCompleteAllOnboarding()

        verify(onboardingSkipper).markOnboardingAsCompleted()
        verify(newAddressBarOptionManager).setAsShown()
    }

    @Test
    fun whenBrandDesignUpdateDisabledThenInitializePagesUsesOriginalBlueprints() {
        onboardingBrandDesignUpdateToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = false))
        testee.initializePages()
        verify(pageLayout).buildPageBlueprints()
        verify(pageLayout, never()).buildBrandDesignUpdatePageBlueprints()
    }

    @Test
    fun whenBrandDesignUpdateDisabledAndSkippingAllowedThenOnboardingSkipperInitializes() = runTest {
        onboardingBrandDesignUpdateToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = false))
        configureAppBuildConfigAllowsSkipping()
        configureSkipperFlow()
        testee.initializeOnboardingSkipper()
        verify(onboardingSkipper).privacyConfigDownloaded
    }

    @Test
    fun whenBrandDesignUpdateEnabledThenInitializePagesUsesBrandDesignUpdateBlueprints() {
        onboardingBrandDesignUpdateToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = true))
        testee.initializePages()
        verify(pageLayout).buildBrandDesignUpdatePageBlueprints()
        verify(pageLayout, never()).buildPageBlueprints()
    }

    @Test
    fun whenBrandDesignUpdateEnabledThenOnboardingSkipperDoesNotInitialize() = runTest {
        onboardingBrandDesignUpdateToggles.brandDesignUpdate().setRawStoredState(Toggle.State(enable = true))
        configureAppBuildConfigAllowsSkipping()
        testee.initializeOnboardingSkipper()
        verifyNoInteractions(onboardingSkipper)
    }

    private fun configureSkipperFlow() = runTest {
        val flow = MutableSharedFlow<ViewState>()
        flow.emit(ViewState(skipOnboardingPossible = true))
        whenever(onboardingSkipper.privacyConfigDownloaded).thenReturn(flow)
    }

    private fun configureAppBuildConfigAllowsSkipping() {
        whenever(appBuildConfig.canSkipOnboarding).thenReturn(true)
    }

    private fun configureAppBuildConfigPreventsSkipping() {
        whenever(appBuildConfig.canSkipOnboarding).thenReturn(false)
    }
}
