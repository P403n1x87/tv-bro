package com.phlox.tvwebbrowser.utils.activemodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ActiveModelsRepository] itself: the locator has to be able to build the models, which
 * now take their data-layer gateways as constructor parameters (with production defaults) so they
 * can be unit-tested with fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ActiveModelsRepositoryTest {
    /** Stand-in for a model with an injectable (but defaulted) dependency. */
    class ModelWithInjectedDependency(
        val label: String = "production-default",
    ) : ActiveModel()

    @Test
    fun modelsWithDefaultedDependenciesAreBuiltFromTheirNoArgumentConstructor() {
        val model = ActiveModelsRepository.get(ModelWithInjectedDependency::class, this)

        assertEquals("production-default", model.label)
    }

    @Test
    fun theSameModelIsSharedByEveryUser() {
        val first = ActiveModelsRepository.get(ModelWithInjectedDependency::class, "first")
        val second = ActiveModelsRepository.get(ModelWithInjectedDependency::class, "second")

        assertSame(first, second)
    }
}
