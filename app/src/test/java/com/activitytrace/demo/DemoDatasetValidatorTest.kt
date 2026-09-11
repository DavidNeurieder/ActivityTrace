package com.activitytrace.demo

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDatasetValidatorTest {

    private fun showcaseDataset(): DemoDataset {
        val generator = DemoDataGenerator(
            captureDao = mockk(relaxed = true),
            database = mockk(relaxed = true),
        )
        return generator.showcaseDataset()
    }

    @Test
    fun `valid showcase dataset passes`() {
        assertEquals("", DemoDatasetValidator.validate(showcaseDataset()).errors.joinToString("; "))
    }

    @Test
    fun `duplicate event ids are rejected`() {
        val dataset = showcaseDataset()
        val broken = dataset.copy(
            events = listOf(
                dataset.events.first().copy(id = "dup"),
                dataset.events.first().copy(id = "dup"),
            ) + dataset.events.drop(2),
        )
        assertTrue(DemoDatasetValidator.validate(broken).errors.any { it.contains("duplicate id") })
    }

    @Test
    fun `an app with no events is rejected`() {
        val ds = showcaseDataset()
        val hunted = ds.events.filter { it.app != DemoAppId.SNACKTRACK }
        val broken = ds.copy(events = hunted)
        assertTrue(
            "expected an error for the missing SnackTrack app, got: ${DemoDatasetValidator.validate(broken).errors}",
            DemoDatasetValidator.validate(broken).errors.any { it.contains("no events") },
        )
    }

    @Test
    fun `every catalog app is represented`() {
        val apps = showcaseDataset().events.map { it.app }.toSet()
        assertEquals(
            DemoAppId.entries.toSet(),
            apps,
        )
    }

    @Test
    fun `required corpus terms and prefixes are present`() {
        val events = showcaseDataset().events
        for (term in listOf("aurora", "project", "invoice", "vienna", "parcel", "keyboard", "croissant", "maya")) {
            assertTrue("term '$term' missing", events.any { it.text.lowercase().contains(term) })
        }
    }

    @Test
    fun `prefix search terms match an app name`() {
        val names = showcaseDataset().events
            .flatMap { listOf(DemoAppCatalog.byId(it.app).name, it.text) }
            .flatMap { it.lowercase().split(Regex("[^a-z0-9]+")) }
            .toList()
        for (prefix in listOf("chat", "chatt", "parc", "parcel", "meet", "libre", "curr", "wand", "snac", "budget")) {
            assertTrue("no token starts with prefix '$prefix'", names.any { it.startsWith(prefix) })
        }
    }

    @Test
    fun `no forbidden cross-app term leaks in`() {
        val text = showcaseDataset().events.joinToString(" ") { it.text }.lowercase()
        assertTrue("whatsapp leaked into showcase", !text.contains("whatsapp"))
    }

    @Test
    fun `demo dataset ids carry a version suffix matching the plan`() {
        assertEquals("activitytrace_showcase_v1", DemoDataScenario.SHOWCASE_DATASET_ID)
        assertEquals(1, DemoDataScenario.datasetVersion(DemoDataScenario.SHOWCASE_DATASET_ID))
    }
}