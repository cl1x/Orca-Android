package app.orcaandroid

import app.orcaandroid.core.FilamentSlot
import app.orcaandroid.core.OptionDef
import app.orcaandroid.core.PresetType
import app.orcaandroid.core.SettingsGroup
import app.orcaandroid.core.SettingsPage
import app.orcaandroid.core.Translator
import app.orcaandroid.core.gitBlobSha
import app.orcaandroid.ui.PresetController
import app.orcaandroid.ui.components.PickerItem
import app.orcaandroid.ui.components.pickerEntries
import app.orcaandroid.ui.prepare.packRows
import app.orcaandroid.ui.prepare.plain
import app.orcaandroid.ui.settings.buildPages
import app.orcaandroid.ui.settings.favoriteGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic of the app that runs without the engine or a device. Run with `./gradlew testDebugUnitTest`
 * (scripts/build_release.sh runs it before every release build).
 */
class AppLogicTest {

    private val pages = listOf(
        SettingsPage("Quality", listOf(
            SettingsGroup("Layer height", listOf("layer_height", "initial_layer_print_height")),
            SettingsGroup("Seam", listOf("seam_position")),
        )),
        SettingsPage("Strength", listOf(
            SettingsGroup("Walls", listOf("wall_loops")),
            SettingsGroup("Infill", listOf("sparse_infill_density", "sparse_infill_pattern")),
        )),
        SettingsPage("Support", listOf(SettingsGroup("Support", listOf("enable_support", "support_type")))),
    )

    @Test
    fun favouritesAreGroupedBySettingsPageInSettingsOrder() {
        val favorites = listOf("print:sparse_infill_density", "print:layer_height", "print:wall_loops", "print:seam_position",
            "filament:nozzle_temperature", "print:not_in_layout")
        val groups = favoriteGroups(favorites, PresetType.PRINT, pages, Translator.NONE)
        assertEquals(listOf("Quality", "Strength"), groups.map { it.title })
        assertEquals(listOf("layer_height", "seam_position"), groups[0].options)
        // In the pages' order, not in the order they were starred.
        assertEquals(listOf("wall_loops", "sparse_infill_density"), groups[1].options)
    }

    @Test
    fun favouritesOfOtherTypesOrNoneGiveNoGroups() {
        assertTrue(favoriteGroups(listOf("filament:x"), PresetType.PRINT, pages, Translator.NONE).isEmpty())
        assertTrue(favoriteGroups(emptyList(), PresetType.PRINT, pages, Translator.NONE).isEmpty())
    }

    @Test
    fun favouriteListedOnTwoPagesAppearsOnce() {
        val twice = pages + SettingsPage("Others", listOf(SettingsGroup("Misc", listOf("wall_loops"))))
        val groups = favoriteGroups(listOf("print:wall_loops"), PresetType.PRINT, twice, Translator.NONE)
        assertEquals(1, groups.sumOf { it.options.size })
    }

    @Test
    fun optionsTheLayoutDoesNotPlaceGetAnOthersPage() {
        val defs = listOf("layer_height", "extra_a", "extra_b").associateWith { def(it, category = if (it == "extra_b") "Speed" else "") }
        val built = buildPages(pages, defs)
        val others = built.last()
        assertEquals("Others", others.title)
        assertEquals(setOf("Others", "Speed"), others.groups.map { it.title }.toSet())
        assertEquals(listOf("extra_a"), others.groups.first { it.title == "Others" }.options)
    }

    @Test
    fun numbersShareRowsOfThreeAndSwitchesStayAlone() {
        // n = number, s = switch/choice
        val items = listOf("n1", "n2", "n3", "n4", "s1", "n5", "s2", "s3")
        val rows = packRows(items, { it.startsWith("n") })
        assertEquals(listOf(listOf("n1", "n2", "n3"), listOf("n4"), listOf("s1"), listOf("n5"), listOf("s2"), listOf("s3")), rows)
    }

    @Test
    fun pickerGroupsInterleavedItemsAndDropsDuplicates() {
        val items = listOf(PickerItem("a", group = "Qidi"), PickerItem("b", group = "Generic"), PickerItem("c", group = "Qidi"),
            PickerItem("a", group = "Qidi"))
        val entries = pickerEntries(items, "")
        assertEquals(listOf("a", "c", "b"), entries.map { it.id })
        // Unique (group, id) pairs: the picker list uses them as keys and crashed on duplicates.
        assertEquals(entries.size, entries.map { it.group to it.id }.toSet().size)
    }

    @Test
    fun pickerSearchMatchesEveryWordInLabelOrGroup() {
        val items = listOf(PickerItem("1", "Generic PLA @Qidi", "Qidi"), PickerItem("2", "Generic PETG @Qidi", "Qidi"),
            PickerItem("3", "Bambu PLA Basic", "Bambu"))
        assertEquals(listOf("1"), pickerEntries(items, "pla qidi").map { it.id })
        assertEquals(listOf("1", "3"), pickerEntries(items, " PLA ").map { it.id })
        assertEquals(listOf("3"), pickerEntries(items, "bambu").map { it.id })
    }

    @Test
    fun everyFilamentSlotGetsAColour() {
        val slots = PresetController.withColors(listOf(FilamentSlot("PLA"), FilamentSlot("PETG", "#123456"), FilamentSlot("ABS", "")))
        assertTrue(slots.all { !it.color.isNullOrBlank() })
        assertEquals("#123456", slots[1].color)
        assertTrue("slots without colour get different ones", slots[0].color != slots[2].color)
    }

    @Test
    fun gitBlobHashMatchesGit() {
        // `printf 'hello\n' | git hash-object --stdin`
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", gitBlobSha("hello\n".toByteArray()))
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", gitBlobSha(ByteArray(0)))
    }

    @Test
    fun numbersAreWrittenAsTheSettingsStoreThem() {
        assertEquals("0.12", plain(0.12f))
        assertEquals("15", plain(15f))
        assertEquals("0.2", plain(0.2f))
    }

    private fun def(key: String, category: String = "") = OptionDef(key, key, key, category, "", "float", "", 0, null, null,
        emptyList(), emptyList(), readonly = false, multiline = false, isCode = false)
}
