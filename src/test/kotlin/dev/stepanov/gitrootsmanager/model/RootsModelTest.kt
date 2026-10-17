package dev.stepanov.gitrootsmanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RootsModelTest {

    @Test
    fun uniteMarksMappingsTracked() {
        val result = RootsModel.unite(
            listOf(Mapping("/a", "Git")),
            emptyList(),
        )
        assertEquals(listOf(ManagedRoot("/a", "Git", tracked = true)), result)
    }

    @Test
    fun uniteAppendsDisabledNotInMappings() {
        val result = RootsModel.unite(
            listOf(Mapping("/a", "Git")),
            listOf(DisabledRoot("/b", "Git")),
        )
        assertEquals(
            listOf(
                ManagedRoot("/a", "Git", tracked = true),
                ManagedRoot("/b", "Git", tracked = false),
            ),
            result,
        )
    }

    @Test
    fun uniteKeepsDisabledVcsName() {
        val result = RootsModel.unite(
            emptyList(),
            listOf(DisabledRoot("/b", "Git")),
        )
        assertEquals(listOf(ManagedRoot("/b", "Git", tracked = false)), result)
    }

    @Test
    fun uniteDropsDisabledPresentInMappings() {
        val result = RootsModel.unite(
            listOf(Mapping("/b", "Git")),
            listOf(DisabledRoot("/b", "Git")),
        )
        assertEquals(listOf(ManagedRoot("/b", "Git", tracked = true)), result)
    }
}
