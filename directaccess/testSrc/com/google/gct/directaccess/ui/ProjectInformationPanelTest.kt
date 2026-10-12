/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.google.gct.directaccess.ui

import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.google.gct.directaccess.DirectAccessCloudProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.mock

private const val INSTRUCTION_TEXT = "dropdown in device manager to add new devices."

@RunsInEdt
class ProjectInformationPanelTest {

  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val edtRule = EdtRule()

  private val scope = CoroutineScope(Dispatchers.Unconfined)

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun testPlanInformationShownWhenBrandingNotUpdated() {
    StudioFlags.DIRECT_ACCESS_CLOUD_BRANDING.overrideForTest(false, disposableRule.disposable)

    val panel = ProjectInformationPanel(scope, Dispatchers.Unconfined, MutableStateFlow<DirectAccessCloudProjectManager?>(null))

    assertThat(panel.findAllDescendants<UsageProgressBar>().toList()).hasSize(1)
    assertThat(panel.findAllDescendants<HyperlinkLabel> { it.text == "View Pricing Details" }.toList()).hasSize(1)
    assertThat(panel.labelTexts()).contains("Plan: -")
    assertThat(panel.labelTexts()).contains("-- mins used")
    assertThat(panel.labelTexts()).contains("-- mins remaining")
    assertThat(panel.labelTexts()).contains("Estimated minutes based on usage across all Firebase project members.")
    assertThat(panel.labelTexts()).contains(INSTRUCTION_TEXT)
  }

  @Test
  fun testPlanInformationHiddenWhenBrandingIsUpdated() {
    StudioFlags.DIRECT_ACCESS_CLOUD_BRANDING.overrideForTest(true, disposableRule.disposable)
    val cloudProjectManager = mock<DirectAccessCloudProjectManager>()

    val panel = ProjectInformationPanel(scope, Dispatchers.Unconfined, MutableStateFlow(cloudProjectManager))

    assertThat(panel.findAllDescendants<UsageProgressBar>().toList()).isEmpty()
    assertThat(panel.findAllDescendants<HyperlinkLabel>().toList()).isEmpty()
    assertThat(panel.labelTexts()).containsExactly("Click ", INSTRUCTION_TEXT)
    // Billing and quota information is not fetched when it is not displayed.
    verifyNoInteractions(cloudProjectManager)
    // No background coroutines are launched when Google Cloud branding is enabled.
    assertThat(scope.coroutineContext.job.children.toList()).isEmpty()
  }

  private fun ProjectInformationPanel.labelTexts() = findAllDescendants<JBLabel>().map { it.text }.toList()
}
