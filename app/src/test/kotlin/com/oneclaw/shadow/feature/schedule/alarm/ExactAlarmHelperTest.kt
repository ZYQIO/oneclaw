package com.oneclaw.shadow.feature.schedule.alarm

import android.app.Application
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Unit tests for ExactAlarmHelper.
 * Uses Robolectric to provide real Android environment including AlarmManager shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExactAlarmHelperTest {

    private lateinit var helper: ExactAlarmHelper

    @Before
    fun setup() {
        helper = ExactAlarmHelper(RuntimeEnvironment.getApplication())
    }

    @Test
    @Config(sdk = [30])
    fun `canScheduleExactAlarms returns true on API 30 below S`() {
        // On SDK < 31, always returns true without checking AlarmManager
        val result = helper.canScheduleExactAlarms()
        assertTrue(result)
    }

    @Test
    @Config(sdk = [31])
    fun `canScheduleExactAlarms delegates to AlarmManager on API 31 when permission granted`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val result = helper.canScheduleExactAlarms()

        assertTrue(result)
    }

    @Test
    @Config(sdk = [31])
    fun `canScheduleExactAlarms delegates to AlarmManager on API 31 when permission denied`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val result = helper.canScheduleExactAlarms()

        assertFalse(result)
    }

    @Test
    @Config(sdk = [31])
    fun `buildSettingsIntent returns ACTION_REQUEST_SCHEDULE_EXACT_ALARM on API 31+`() {
        val intent = helper.buildSettingsIntent()

        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
    }

    @Test
    @Config(sdk = [30])
    fun `buildSettingsIntent returns APPLICATION_DETAILS_SETTINGS on API below 31`() {
        val intent = helper.buildSettingsIntent()

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    }

    @Test
    @Config(sdk = [31])
    fun `buildSettingsIntent includes package URI on API 31+`() {
        val intent = helper.buildSettingsIntent()

        assertTrue(intent.data?.toString()?.startsWith("package:") == true)
    }

    @Test
    @Config(sdk = [30])
    fun `buildSettingsIntent includes package URI on API below 31`() {
        val intent = helper.buildSettingsIntent()

        assertTrue(intent.data?.toString()?.startsWith("package:") == true)
    }
}
