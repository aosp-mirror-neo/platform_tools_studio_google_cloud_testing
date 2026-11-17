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
package com.google.gct.testrecorder.util

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.getAndroidTestModule
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.testing.AndroidLibraryDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.buildNdkModelStub
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetTypeRegistry
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiManagerEx
import java.awt.EventQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EspressoSetupGradleTokenTest {

  private companion object {
    val INITIAL_SETTINGS_GRADLE =
      """
      include ':app', ':nativeApp'
      """
        .trimIndent()

    val INITIAL_APP_BUILD_GRADLE =
      """
      plugins {
        id 'com.android.application'
      }
      android {
        namespace = 'com.example.app'
        defaultConfig {
          applicationId 'com.example.app'
        }
      }
      dependencies {
        implementation 'androidx.appcompat:appcompat:1.6.1'
      }
      """
        .trimIndent()

    val KOTLIN_AND_NATIVE_BUILD_GRADLE =
      """
      plugins {
        id 'com.android.application'
        id 'org.jetbrains.kotlin.android'
      }
      android {
        namespace = 'com.example.app'
        externalNativeBuild {
          cmake {
            version = '3.22.1'
          }
        }
      }
      """
        .trimIndent()
  }

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
      prepareProjectSources = { root ->
        root.resolve("settings.gradle").writeText(INITIAL_SETTINGS_GRADLE)
        root.resolve("app").apply {
          mkdirs()
          resolve("build.gradle").writeText(INITIAL_APP_BUILD_GRADLE)
        }
      },
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            namespace = { "com.example.app" },
            androidLibraryDependencyList = {
              listOf(AndroidLibraryDependency.fromAddress(GoogleMavenArtifactId.ANDROIDX_APPCOMPAT.getDependency("1.6.1").toIdentifier()!!))
            },
          ),
      ),
      AndroidModuleModelBuilder(
        gradlePath = ":nativeApp",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            namespace = { "com.example.nativeapp" },
            ndkModel = { buildNdkModelStub() },
          ),
      ),
    )

  @Before
  fun setUp() {
    val rootDir = getBaseDirPath(projectRule.project)
    VfsUtil.markDirtyAndRefresh(false, true, true, rootDir)
  }

  @Test
  fun supportsKotlinAndIsNativeProjectDoNotLoadOrParseGradleBuildFiles() {
    val project = projectRule.project
    val rootDir = getBaseDirPath(project)
    val appModule = project.gradleModule(":app")!!
    val appMainModule = appModule.getMainModule()
    val projectSystem = project.getProjectSystem() as GradleProjectSystem
    val token = EspressoSetupGradleToken()
    val appBuildVirtualFile = VfsUtil.findFileByIoFile(rootDir.resolve("app/build.gradle"), true)!!
    val psiManager = PsiManagerEx.getInstanceEx(project)

    assertThat(psiManager.fileManager.findCachedViewProvider(appBuildVirtualFile)).isNull()

    val lockFreeEdtResult = AtomicReference<Pair<Boolean, Boolean>>()
    runOnLockFreeEdt {
      lockFreeEdtResult.set(token.supportsKotlin(projectSystem, appModule) to token.isNativeProject(projectSystem, appMainModule))
    }
    assertThat(lockFreeEdtResult.get()).isEqualTo(false to false)
    assertThat(psiManager.fileManager.findCachedViewProvider(appBuildVirtualFile)).isNull()

    val readLockEdtResult = AtomicReference<Pair<Boolean, Boolean>>()
    SwingUtilities.invokeAndWait {
      ReadAction.run<RuntimeException> {
        assertThat(ApplicationManager.getApplication().isReadAccessAllowed).isTrue()
        readLockEdtResult.set(token.supportsKotlin(projectSystem, appMainModule) to token.isNativeProject(projectSystem, appModule))
      }
    }
    assertThat(readLockEdtResult.get()).isEqualTo(false to false)
    assertThat(psiManager.fileManager.findCachedViewProvider(appBuildVirtualFile)).isNull()

    val bgResult =
      ApplicationManager.getApplication()
        .executeOnPooledThread<Pair<Boolean, Boolean>> {
          assertThat(EventQueue.isDispatchThread()).isFalse()
          token.supportsKotlin(projectSystem, appModule) to token.isNativeProject(projectSystem, appModule)
        }
        .get(30, TimeUnit.SECONDS)
    assertThat(bgResult).isEqualTo(false to false)
    assertThat(psiManager.fileManager.findCachedViewProvider(appBuildVirtualFile)).isNull()
  }

  @Test
  fun isNativeProjectUsesSyncedNdkModelInsteadOfGradleDsl() {
    val project = projectRule.project
    val rootDir = getBaseDirPath(project)
    val appModule = project.gradleModule(":app")!!
    val appMainModule = appModule.getMainModule()
    val nativeAppModule = project.gradleModule(":nativeApp")!!
    val nativeAppMainModule = nativeAppModule.getMainModule()
    val nativeAppAndroidTestModule = nativeAppModule.getAndroidTestModule()!!
    val projectSystem = project.getProjectSystem() as GradleProjectSystem
    val token = EspressoSetupGradleToken()

    // :nativeApp has no build.gradle on disk, yet has a synced NdkModuleModel.
    assertThat(rootDir.resolve("nativeApp/build.gradle").exists()).isFalse()

    // Even if :app's build.gradle declares CMake in DSL (and has an uncommitted edit),
    // :app has no NdkModuleModel so isNativeProject must remain false without committing documents.
    val appBuildVirtualFile = VfsUtil.findFileByIoFile(rootDir.resolve("app/build.gradle"), true)!!
    val documentRef = AtomicReference<Document>()
    WriteCommandAction.runWriteCommandAction(project) {
      val doc = FileDocumentManager.getInstance().getDocument(appBuildVirtualFile)!!
      PsiDocumentManager.getInstance(project).getPsiFile(doc)
      documentRef.set(doc)
      doc.setText(KOTLIN_AND_NATIVE_BUILD_GRADLE)
    }
    assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

    val lockFreeResults = AtomicReference<List<Boolean>>()
    runOnLockFreeEdt {
      lockFreeResults.set(
        listOf(
          token.isNativeProject(projectSystem, appModule),
          token.isNativeProject(projectSystem, appMainModule),
          token.isNativeProject(projectSystem, nativeAppModule),
          token.isNativeProject(projectSystem, nativeAppMainModule),
          token.isNativeProject(projectSystem, nativeAppAndroidTestModule),
        )
      )
    }
    assertThat(lockFreeResults.get()).containsExactly(false, false, true, true, true).inOrder()
    assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

    val readLockEdtResults = AtomicReference<Pair<Boolean, Boolean>>()
    SwingUtilities.invokeAndWait {
      ReadAction.run<RuntimeException> {
        assertThat(ApplicationManager.getApplication().isReadAccessAllowed).isTrue()
        readLockEdtResults.set(token.isNativeProject(projectSystem, appModule) to token.isNativeProject(projectSystem, nativeAppMainModule))
      }
    }
    assertThat(readLockEdtResults.get()).isEqualTo(false to true)
    assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

    val bgResult =
      ApplicationManager.getApplication()
        .executeOnPooledThread<Boolean> {
          assertThat(EventQueue.isDispatchThread()).isFalse()
          token.isNativeProject(projectSystem, nativeAppModule)
        }
        .get(30, TimeUnit.SECONDS)
    assertThat(bgResult).isTrue()
  }

  @Test
  fun isNativeProjectReturnsFalseWhenNdkFacetHasNoSyncedModel() {
    val project = projectRule.project
    val appModule = project.gradleModule(":app")!!
    val appMainModule = appModule.getMainModule()
    val projectSystem = project.getProjectSystem() as GradleProjectSystem
    val token = EspressoSetupGradleToken()

    val ndkFacet = Facets.createAndAddNdkFacet(appModule)
    try {
      val results = AtomicReference<Pair<Boolean, Boolean>>()
      runOnLockFreeEdt {
        results.set(token.isNativeProject(projectSystem, appModule) to token.isNativeProject(projectSystem, appMainModule))
      }
      assertThat(results.get()).isEqualTo(false to false)
    } finally {
      FacetUtil.deleteFacet(ndkFacet)
    }
  }

  @Test
  fun supportsKotlinChecksMainModuleKotlinFacetInsteadOfGradleDsl() {
    val project = projectRule.project
    val rootDir = getBaseDirPath(project)
    val appModule = project.gradleModule(":app")!!
    val appMainModule = appModule.getMainModule()
    val appAndroidTestModule = appModule.getAndroidTestModule()!!
    val projectSystem = project.getProjectSystem() as GradleProjectSystem
    val token = EspressoSetupGradleToken()

    // Even if build.gradle declares the Kotlin Android plugin (and is uncommitted),
    // supportsKotlin must rely on KotlinFacet rather than parsing Gradle DSL.
    val appBuildVirtualFile = VfsUtil.findFileByIoFile(rootDir.resolve("app/build.gradle"), true)!!
    val documentRef = AtomicReference<Document>()
    WriteCommandAction.runWriteCommandAction(project) {
      val doc = FileDocumentManager.getInstance().getDocument(appBuildVirtualFile)!!
      PsiDocumentManager.getInstance(project).getPsiFile(doc)
      documentRef.set(doc)
      doc.setText(KOTLIN_AND_NATIVE_BUILD_GRADLE)
    }
    assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

    val withoutFacet = AtomicReference<List<Boolean>>()
    runOnLockFreeEdt {
      withoutFacet.set(
        listOf(
          token.supportsKotlin(projectSystem, appModule),
          token.supportsKotlin(projectSystem, appMainModule),
          token.supportsKotlin(projectSystem, appAndroidTestModule),
        )
      )
    }
    assertThat(withoutFacet.get()).containsExactly(false, false, false).inOrder()
    assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

    // Attach KotlinFacet to the :app.main source-set module (matching MPSS / AGP 9 built-in Kotlin,
    // where the holder module does not carry a KotlinFacet directly).
    val kotlinFacetType = FacetTypeRegistry.getInstance().findFacetType("kotlin-language")!!
    val kotlinFacet = FacetUtil.addFacet(appMainModule, kotlinFacetType)
    try {
      // Introduce a fresh uncommitted edit after adding the facet (since addFacet may commit
      // documents)
      // to verify supportsKotlin does not commit documents or parse Gradle DSL when returning true.
      WriteCommandAction.runWriteCommandAction(project) {
        val doc = documentRef.get()
        doc.setText(INITIAL_APP_BUILD_GRADLE + "\n// uncommitted edit after adding facet\n")
      }
      assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

      val lockFreeResults = AtomicReference<List<Boolean>>()
      runOnLockFreeEdt {
        lockFreeResults.set(
          listOf(
            token.supportsKotlin(projectSystem, appModule),
            token.supportsKotlin(projectSystem, appMainModule),
            token.supportsKotlin(projectSystem, appAndroidTestModule),
          )
        )
      }
      assertThat(lockFreeResults.get()).containsExactly(true, true, true).inOrder()
      assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

      val readLockEdtResults = AtomicReference<Pair<Boolean, Boolean>>()
      SwingUtilities.invokeAndWait {
        ReadAction.run<RuntimeException> {
          assertThat(ApplicationManager.getApplication().isReadAccessAllowed).isTrue()
          readLockEdtResults.set(
            token.supportsKotlin(projectSystem, appModule) to token.supportsKotlin(projectSystem, appAndroidTestModule)
          )
        }
      }
      assertThat(readLockEdtResults.get()).isEqualTo(true to true)
      assertThat(PsiDocumentManager.getInstance(project).isCommitted(documentRef.get())).isFalse()

      val bgResult =
        ApplicationManager.getApplication()
          .executeOnPooledThread<Boolean> {
            assertThat(EventQueue.isDispatchThread()).isFalse()
            token.supportsKotlin(projectSystem, appModule)
          }
          .get(30, TimeUnit.SECONDS)
      assertThat(bgResult).isTrue()
    } finally {
      FacetUtil.deleteFacet(kotlinFacet)
    }
  }

  private fun runOnLockFreeEdt(block: () -> Unit) {
    SwingUtilities.invokeAndWait {
      assertLockFreeEdt()
      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        assertLockFreeEdt()
        block()
      }
    }
  }

  private fun assertLockFreeEdt() {
    val app = ApplicationManager.getApplication()
    assertThat(EventQueue.isDispatchThread()).isTrue()
    assertThat(app.isReadAccessAllowed).isFalse()
    assertThat(app.isWriteIntentLockAcquired).isFalse()
  }
}
