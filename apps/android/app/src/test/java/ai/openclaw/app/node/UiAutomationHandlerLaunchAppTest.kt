package ai.openclaw.app.node

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class UiAutomationHandlerLaunchAppTest : NodeHandlerRobolectricTest() {
  @Test
  @Suppress("DEPRECATION")
  fun resolveLaunchIntentForPackage_buildsExplicitLauncherIntentFromQueryMatch() {
    val packageName = "com.coloros.calculator"
    val activityName = "com.android.calculator2.Calculator"
    val launchIntent =
      Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(packageName)
      }
    val resolveInfo =
      ResolveInfo().apply {
        activityInfo =
          ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
          }
      }

    shadowOf(appContext().packageManager).addResolveInfoForIntent(launchIntent, resolveInfo)

    val resolvedIntent =
      UiAutomationHandler.resolveLaunchIntentForPackage(appContext().packageManager, packageName)

    assertNotNull(resolvedIntent)
    assertEquals(packageName, resolvedIntent?.`package`)
    assertEquals(packageName, resolvedIntent?.component?.packageName)
    assertEquals(activityName, resolvedIntent?.component?.className)
  }

  @Test
  @Suppress("DEPRECATION")
  fun launchAppFromContext_launchesResolvedLauncherIntentEvenWhenPackageInfoLookupMisses() {
    val packageName = "com.coloros.calculator"
    val activityName = "com.android.calculator2.Calculator"
    val launchIntent =
      Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setPackage(packageName)
      }
    val resolveInfo =
      ResolveInfo().apply {
        activityInfo =
          ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
          }
      }

    shadowOf(appContext().packageManager).addResolveInfoForIntent(launchIntent, resolveInfo)

    val result = UiAutomationHandler.launchAppFromContext(appContext(), packageName)

    assertTrue(result.launched)
    assertEquals(packageName, result.packageName)
    assertEquals(activityName, result.activityClassName)
  }

  @Test
  fun launchAppFromContext_reportsMissingPackagesClearly() {
    val result = UiAutomationHandler.launchAppFromContext(appContext(), "com.example.missing")

    assertFalse(result.launched)
    assertEquals("APP_NOT_INSTALLED", result.errorCode)
  }
}
