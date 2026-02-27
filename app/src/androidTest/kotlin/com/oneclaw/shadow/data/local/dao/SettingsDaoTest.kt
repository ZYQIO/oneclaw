package com.oneclaw.shadow.data.local.dao

import androidx.test.core.app.ApplicationProvider
import com.oneclaw.shadow.data.local.db.AppDatabase
import com.oneclaw.shadow.data.local.entity.SettingsEntity
import com.oneclaw.shadow.testutil.TestDatabaseHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SettingsDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var settingsDao: SettingsDao

    @Before
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase(
            ApplicationProvider.getApplicationContext()
        )
        settingsDao = database.settingsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun setAndGetString() = runTest {
        settingsDao.set(SettingsEntity(key = "theme", value = "dark"))

        val result = settingsDao.getString("theme")
        assertEquals("dark", result)
    }

    @Test
    fun getStringReturnsNullForNonExistent() = runTest {
        val result = settingsDao.getString("nonexistent")
        assertNull(result)
    }

    @Test
    fun setOverwritesExistingValue() = runTest {
        settingsDao.set(SettingsEntity(key = "theme", value = "dark"))
        settingsDao.set(SettingsEntity(key = "theme", value = "light"))

        val result = settingsDao.getString("theme")
        assertEquals("light", result)
    }

    @Test
    fun deleteRemovesSetting() = runTest {
        settingsDao.set(SettingsEntity(key = "theme", value = "dark"))

        settingsDao.delete("theme")

        val result = settingsDao.getString("theme")
        assertNull(result)
    }

    @Test
    fun multipleSettingsAreIndependent() = runTest {
        settingsDao.set(SettingsEntity(key = "key1", value = "value1"))
        settingsDao.set(SettingsEntity(key = "key2", value = "value2"))

        assertEquals("value1", settingsDao.getString("key1"))
        assertEquals("value2", settingsDao.getString("key2"))

        settingsDao.delete("key1")

        assertNull(settingsDao.getString("key1"))
        assertEquals("value2", settingsDao.getString("key2"))
    }

    @Test
    fun setBooleanAsString() = runTest {
        settingsDao.set(SettingsEntity(key = "welcome_shown", value = "true"))

        val result = settingsDao.getString("welcome_shown")
        assertEquals("true", result)
        assertEquals(true, result?.toBoolean())
    }
}
