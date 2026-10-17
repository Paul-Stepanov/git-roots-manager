package dev.stepanov.gitrootsmanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MappingLogicTest {

    @Test
    fun normalizeTrimsTrailingSlash() {
        assertEquals("/a/b", MappingLogic.normalizeDirectory("/a/b/"))
    }

    @Test
    fun normalizeKeepsRoot() {
        assertEquals("/", MappingLogic.normalizeDirectory("/"))
    }

    @Test
    fun normalizeTrimsWhitespace() {
        assertEquals("/a/b", MappingLogic.normalizeDirectory(" /a/b "))
    }

    @Test
    fun normalizeEmptyStaysEmpty() {
        assertEquals("", MappingLogic.normalizeDirectory(""))
    }

    @Test
    fun isProjectRootMatchesNormalizedBasePath() {
        assertEquals(true, MappingLogic.isProjectRoot("/repo/", "/repo"))
    }

    @Test
    fun isProjectRootMatchesWindowsSeparators() {
        assertEquals(true, MappingLogic.isProjectRoot("C:/repo", "C:\\repo\\"))
    }

    @Test
    fun isProjectRootDoesNotMatchOtherDirectory() {
        assertEquals(false, MappingLogic.isProjectRoot("/repo/sub", "/repo"))
    }

    @Test
    fun removeDeletesOnlyTarget() {
        val mappings = listOf(
            Mapping("/a", "Git"),
            Mapping("/b", "Git"),
            Mapping("/c", "Git"),
        )
        assertEquals(
            listOf(Mapping("/a", "Git"), Mapping("/c", "Git")),
            MappingLogic.removeMapping(mappings, "/b"),
        )
    }

    @Test
    fun removeMissingIsNoOp() {
        val mappings = listOf(Mapping("/a", "Git"))
        assertEquals(mappings, MappingLogic.removeMapping(mappings, "/x"))
    }

    @Test
    fun addReplacesSameDirectoryKeepingPosition() {
        val mappings = listOf(Mapping("/a", "Git"), Mapping("/b", "Git"))
        assertEquals(
            listOf(Mapping("/a", "Git"), Mapping("/b", "SVN")),
            MappingLogic.addMapping(mappings, "/b", "SVN"),
        )
    }

    @Test
    fun addAppendsNew() {
        val result = MappingLogic.addMapping(listOf(Mapping("/a", "Git")), "/b", "Git")
        assertEquals(listOf(Mapping("/a", "Git"), Mapping("/b", "Git")), result)
    }
}
