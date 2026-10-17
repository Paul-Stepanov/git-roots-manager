package dev.stepanov.gitrootsmanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RootsModelTest {

    @Test
    fun withoutProjectRootDropsBasePathAndKeepsNestedRoots() {
        assertEquals(
            listOf(DisabledRoot("/repo/nested", "Git")),
            RootsModel.withoutProjectRoot(
                listOf(DisabledRoot("/repo/", "Git"), DisabledRoot("/repo/nested", "Git")),
                "/repo",
            ),
        )
    }

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

    @Test
    fun expandDropsDefaultMappingAndUsesDetected() {
        val result = RootsModel.expandDefaultMapping(
            mappings = listOf(Mapping("", "Git")),
            detected = listOf(Mapping("/a", "Git"), Mapping("/b", "Git")),
            exclude = emptySet(),
        )
        assertEquals(listOf(Mapping("/a", "Git"), Mapping("/b", "Git")), result)
    }

    @Test
    fun expandExcludesDisabledRoots() {
        val result = RootsModel.expandDefaultMapping(
            mappings = listOf(Mapping("", "Git")),
            detected = listOf(Mapping("/a", "Git"), Mapping("/b", "Git")),
            exclude = setOf("/b"),
        )
        assertEquals(listOf(Mapping("/a", "Git")), result)
    }

    @Test
    fun expandKeepsExplicitMappingsNotInDetected() {
        val result = RootsModel.expandDefaultMapping(
            mappings = listOf(Mapping("", "Git"), Mapping("/x", "SVN")),
            detected = listOf(Mapping("/a", "Git")),
            exclude = emptySet(),
        )
        assertEquals(listOf(Mapping("/x", "SVN"), Mapping("/a", "Git")), result)
    }

    @Test
    fun expandDedupesDetectedAgainstExplicit() {
        val result = RootsModel.expandDefaultMapping(
            mappings = listOf(Mapping("", "Git"), Mapping("/a", "SVN")),
            detected = listOf(Mapping("/a/", "Git"), Mapping("/b", "Git")),
            exclude = emptySet(),
        )
        assertEquals(listOf(Mapping("/a", "SVN"), Mapping("/b", "Git")), result)
    }

    @Test
    fun expandNormalizesExclude() {
        val result = RootsModel.expandDefaultMapping(
            mappings = listOf(Mapping("", "Git")),
            detected = listOf(Mapping("/a", "Git"), Mapping("/b", "Git")),
            exclude = setOf("/b/"),
        )
        assertEquals(listOf(Mapping("/a", "Git")), result)
    }

    @Test
    fun expandRemovesExplicitMappingInExclude() {
        val result = RootsModel.expandDefaultMapping(
            mappings = listOf(Mapping("", "Git"), Mapping("/x", "SVN")),
            detected = listOf(Mapping("/x", "Git")),
            exclude = setOf("/x"),
        )
        assertEquals(emptyList<Mapping>(), result)
    }
}
