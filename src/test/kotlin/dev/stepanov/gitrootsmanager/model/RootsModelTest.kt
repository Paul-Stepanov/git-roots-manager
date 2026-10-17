package dev.stepanov.gitrootsmanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RootsModelTest {

    @Test
    fun uniteShowsUnregisteredDetectedRootAsUntracked() {
        assertEquals(
            listOf(ManagedRoot("/repo/nested", "Git", tracked = false)),
            RootsModel.unite(
                mappings = emptyList(),
                disabled = emptyList(),
                detected = listOf(Mapping("/repo/nested", "Git")),
            ),
        )
    }

    @Test
    fun uniteDefaultMappingTracksDetectedRootsIncludingPreviouslyDisabled() {
        assertEquals(
            listOf(
                ManagedRoot("", "Git", tracked = true),
                ManagedRoot("/repo/nested", "Git", tracked = true),
                ManagedRoot("/repo/new", "Git", tracked = true),
            ),
            RootsModel.unite(
                mappings = listOf(Mapping("", "Git")),
                disabled = listOf(DisabledRoot("/repo/nested", "Git")),
                detected = listOf(Mapping("/repo/nested", "Git"), Mapping("/repo/new", "Git")),
            ),
        )
    }

    @Test
    fun uniteExplicitBaseMappingShowsNineteenUnregisteredRoots() {
        val detected = listOf(Mapping("/repo", "Git")) +
            (1..19).map { Mapping("/repo/nested$it", "Git") }
        val roots = RootsModel.unite(
            mappings = listOf(Mapping("/repo", "Git")),
            disabled = listOf(DisabledRoot("/repo/", "Git")),
            detected = detected,
            projectBasePath = "/repo",
        )

        assertEquals(20, roots.size)
        assertEquals(ManagedRoot("/repo", "Git", tracked = true, toggleable = false), roots.first())
        assertEquals(19, roots.count { !it.tracked && it.toggleable })
        assertEquals(false, roots.any { it.directory.isEmpty() })
    }

    @Test
    fun uniteDefaultMappingShowsOnlyOneProjectRow() {
        assertEquals(
            listOf(
                ManagedRoot("", "Git", tracked = true),
                ManagedRoot("/repo/nested", "Git", tracked = true),
            ),
            RootsModel.unite(
                mappings = listOf(Mapping("", "Git"), Mapping("/repo", "Git")),
                disabled = listOf(DisabledRoot("/repo", "Git")),
                detected = listOf(Mapping("/repo/", "Git"), Mapping("/repo/nested", "Git")),
                projectBasePath = "/repo",
            ),
        )
    }

    @Test
    fun uniteDoesNotInventDefaultRowFromUnmappedSources() {
        assertEquals(
            emptyList<ManagedRoot>(),
            RootsModel.unite(
                mappings = emptyList(),
                disabled = listOf(DisabledRoot("", "Git")),
                detected = listOf(Mapping("", "Git")),
            ),
        )
    }

    @Test
    fun uniteDeduplicatesSourcesAndKeepsMappingAndDisabledVcsNames() {
        assertEquals(
            listOf(
                ManagedRoot("/repo/mapped", "MappedVcs", tracked = true),
                ManagedRoot("/repo/disabled", "SavedVcs", tracked = false),
                ManagedRoot("/repo/new", "Git", tracked = false),
            ),
            RootsModel.unite(
                mappings = listOf(Mapping("/repo/mapped/", "MappedVcs")),
                disabled = listOf(
                    DisabledRoot("/repo/mapped", "OldVcs"),
                    DisabledRoot("/repo/disabled/", "SavedVcs"),
                ),
                detected = listOf(
                    Mapping("/repo/mapped", "Git"),
                    Mapping("/repo/disabled", "Git"),
                    Mapping("/repo/new", "Git"),
                    Mapping("/repo/new/", "Git"),
                ),
            ),
        )
    }

    @Test
    fun uniteMarksUnregisteredRootTrackedAfterAddingExplicitMapping() {
        val mappings = MappingLogic.addMapping(listOf(Mapping("/repo", "Git")), "/repo/nested", "Git")
        assertEquals(
            listOf(
                ManagedRoot("/repo", "Git", tracked = true, toggleable = false),
                ManagedRoot("/repo/nested", "Git", tracked = true),
            ),
            RootsModel.unite(
                mappings = mappings,
                disabled = emptyList(),
                detected = listOf(Mapping("/repo/nested", "Git")),
                projectBasePath = "/repo",
            ),
        )
    }

    @Test
    fun disablingAllToggleableRootsKeepsDetectedBaseMapping() {
        val mappings = listOf(Mapping("", "Git"))
        val detected = listOf(Mapping("/repo", "Git"), Mapping("/repo/nested", "Git"))
        val roots = RootsModel.unite(mappings, emptyList(), detected, projectBasePath = "/repo")
        val excluded = roots.filter { it.toggleable }.mapTo(HashSet()) { it.directory }

        assertEquals(
            listOf(Mapping("/repo", "Git")),
            RootsModel.expandDefaultMapping(mappings, detected, excluded),
        )
    }

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
