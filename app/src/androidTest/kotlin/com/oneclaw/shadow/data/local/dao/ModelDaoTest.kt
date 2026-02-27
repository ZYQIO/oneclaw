package com.oneclaw.shadow.data.local.dao

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.oneclaw.shadow.data.local.db.AppDatabase
import com.oneclaw.shadow.data.local.entity.ModelEntity
import com.oneclaw.shadow.data.local.entity.ProviderEntity
import com.oneclaw.shadow.testutil.TestDatabaseHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ModelDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var modelDao: ModelDao
    private lateinit var providerDao: ProviderDao

    @Before
    fun setup() {
        database = TestDatabaseHelper.createInMemoryDatabase(
            ApplicationProvider.getApplicationContext()
        )
        modelDao = database.modelDao()
        providerDao = database.providerDao()

        kotlinx.coroutines.runBlocking {
            providerDao.insert(
                ProviderEntity(
                    id = "provider-test",
                    name = "Test Provider",
                    type = "ANTHROPIC",
                    apiBaseUrl = "https://api.anthropic.com",
                    isPreConfigured = false,
                    isActive = true,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryModelsForProvider() = runTest {
        modelDao.insert(ModelEntity("model-1", "Model 1", "provider-test", false, "DYNAMIC"))
        modelDao.insert(ModelEntity("model-2", "Model 2", "provider-test", false, "PRESET"))

        val models = modelDao.getModelsForProvider("provider-test")
        assertEquals(2, models.size)
    }

    @Test
    fun insertAllModels() = runTest {
        val models = listOf(
            ModelEntity("m-1", "M1", "provider-test", false, "DYNAMIC"),
            ModelEntity("m-2", "M2", "provider-test", false, "DYNAMIC"),
            ModelEntity("m-3", "M3", "provider-test", false, "DYNAMIC")
        )
        modelDao.insertAll(models)

        val result = modelDao.getModelsForProvider("provider-test")
        assertEquals(3, result.size)
    }

    @Test
    fun setDefaultModel() = runTest {
        modelDao.insert(ModelEntity("model-1", "Model 1", "provider-test", false, "DYNAMIC"))
        modelDao.insert(ModelEntity("model-2", "Model 2", "provider-test", false, "DYNAMIC"))

        modelDao.setDefault("model-1", "provider-test")

        val default = modelDao.getDefaultModelSnapshot()
        assertNotNull(default)
        assertEquals("model-1", default!!.id)
        assertEquals(true, default.isDefault)
    }

    @Test
    fun clearAllDefaultsRemovesDefaultFlag() = runTest {
        modelDao.insert(ModelEntity("model-1", "Model 1", "provider-test", true, "DYNAMIC"))

        modelDao.clearAllDefaults()

        val default = modelDao.getDefaultModelSnapshot()
        assertNull(default)
    }

    @Test
    fun getDefaultModelFlowEmitsUpdates() = runTest {
        modelDao.getDefaultModel().test {
            assertNull(awaitItem())

            modelDao.insert(ModelEntity("model-1", "Model 1", "provider-test", true, "DYNAMIC"))
            val item = awaitItem()
            assertNotNull(item)
            assertEquals("model-1", item!!.id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteModel() = runTest {
        modelDao.insert(ModelEntity("model-1", "Model 1", "provider-test", false, "MANUAL"))

        modelDao.delete("model-1", "provider-test")

        val result = modelDao.getModel("model-1", "provider-test")
        assertNull(result)
    }

    @Test
    fun deleteByProviderAndSource() = runTest {
        modelDao.insert(ModelEntity("m-dyn-1", "D1", "provider-test", false, "DYNAMIC"))
        modelDao.insert(ModelEntity("m-dyn-2", "D2", "provider-test", false, "DYNAMIC"))
        modelDao.insert(ModelEntity("m-manual", "M1", "provider-test", false, "MANUAL"))

        modelDao.deleteByProviderAndSource("provider-test", "DYNAMIC")

        val remaining = modelDao.getModelsForProvider("provider-test")
        assertEquals(1, remaining.size)
        assertEquals("MANUAL", remaining[0].source)
    }

    @Test
    fun getModelReturnsSpecificModel() = runTest {
        modelDao.insert(ModelEntity("model-1", "Model 1", "provider-test", false, "PRESET"))

        val result = modelDao.getModel("model-1", "provider-test")
        assertNotNull(result)
        assertEquals("Model 1", result!!.displayName)
    }

    @Test
    fun getModelReturnsNullForNonExistent() = runTest {
        val result = modelDao.getModel("nonexistent", "provider-test")
        assertNull(result)
    }
}
