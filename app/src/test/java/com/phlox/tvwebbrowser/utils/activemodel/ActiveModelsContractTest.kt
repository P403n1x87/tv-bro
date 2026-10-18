package com.phlox.tvwebbrowser.utils.activemodel

import com.phlox.tvwebbrowser.activity.downloads.ActiveDownloadsModel
import com.phlox.tvwebbrowser.activity.downloads.DownloadsHistoryModel
import com.phlox.tvwebbrowser.activity.history.HistoryModel
import com.phlox.tvwebbrowser.activity.main.AdblockModel
import com.phlox.tvwebbrowser.activity.main.AutoUpdateModel
import com.phlox.tvwebbrowser.activity.main.MainActivityViewModel
import com.phlox.tvwebbrowser.activity.main.SettingsModel
import com.phlox.tvwebbrowser.activity.main.TabsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * Pins the [ActiveModelsRepository.get] contract: a model is instantiated through its declared
 * no-argument constructor, which Kotlin only synthesises when every primary-constructor parameter
 * has a default. A missing one would otherwise only show up when the first screen asks for the
 * model at runtime.
 *
 * The list is the set of classes passed to `ActiveModelsRepository.get` across the app; the
 * constructor is looked up by reflection, so the models themselves are never created (some of them
 * build native or Android-only state).
 */
class ActiveModelsContractTest {
    private val activeModels: List<KClass<out ActiveModel>> =
        listOf(
            MainActivityViewModel::class,
            SettingsModel::class,
            AdblockModel::class,
            TabsModel::class,
            AutoUpdateModel::class,
            HistoryModel::class,
            ActiveDownloadsModel::class,
            DownloadsHistoryModel::class,
        )

    @Test
    fun everyActiveModelHasAPublicNoArgConstructor() {
        for (model in activeModels) {
            val constructor = runCatching { model.java.getDeclaredConstructor() }.getOrNull()
            assertNotNull(
                "${model.qualifiedName} needs a declared no-argument constructor for ActiveModelsRepository.get",
                constructor,
            )
            assertEquals("${model.qualifiedName} must be created with no arguments", 0, constructor!!.parameterCount)
            assertTrue(
                "${model.qualifiedName}'s no-argument constructor must be public",
                Modifier.isPublic(constructor.modifiers),
            )
        }
    }
}
