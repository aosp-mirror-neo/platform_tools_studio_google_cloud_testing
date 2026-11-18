load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "test-recorder.gradle",
    srcs = ["src"],
    iml_files = ["test-recorder.gradle.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/studio/google/cloud/testing/test-recorder",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/analytics",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/gradle-dsl-android:intellij.android.gradle.dsl.android",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "test-recorder.gradle.tests",
    iml_files = ["test-recorder.gradle.tests.iml"],
    test_class = "com.google.gct.testrecorder.TestRecorderGradleTestSuite",
    test_data = [
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/studio/google/cloud/testing/test-recorder/gradle:test-recorder.gradle",
        "//tools/studio/google/cloud/testing/test-recorder",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models",
        "//tools/adt/idea/project-system-gradle-sync:intellij.android.projectSystem.gradle.sync",
        "//tools/adt/idea/gradle-dsl-groovy:intellij.android.gradle.dsl.groovy",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
        "//tools/base/testutils:studio.android.sdktools.testutils",
    ],
    test_friends = ["//tools/studio/google/cloud/testing/test-recorder/gradle:test-recorder.gradle"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
)
