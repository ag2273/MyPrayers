package app.mawaqeet.about

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** About credits every library the app ships, every font it bundles and every dataset it draws on (#33). */
class CreditsTest {
    /** Runs from the app module's directory. */
    private val root = File("..").canonicalFile

    /** `group:artifact` of every library catalogue entry the shipped app or core depends on. */
    private fun shippedModules(): List<String> {
        val catalogue = File(root, "gradle/libs.versions.toml").readLines()
            .mapNotNull { Regex("""^([\w-]+)\s*=\s*\{\s*module\s*=\s*"([^"]+)"""").find(it)?.destructured }
            .associate { (alias, module) -> alias.replace('-', '.') to module }
        return listOf("app/build.gradle.kts", "core/build.gradle.kts").flatMap { path ->
            Regex("""(?m)^\s*implementation\((?:platform\()?libs\.([\w.]+)""").findAll(File(root, path).readText())
                .map { catalogue.getValue(it.groupValues[1]) }.toList()
        }
    }

    @Test
    fun `every library the app ships is credited`() {
        val modules = shippedModules()
        assertTrue(modules.size >= 8)
        for (module in modules) {
            assertTrue("$module is not credited", Credits.all.any { credit -> credit.modules.any { module.startsWith(it) } })
        }
    }

    @Test
    fun `every bundled font is credited with its licence, and so are the city datasets`() {
        val fonts = File(root, "app/src/main/res/font").list()!!.filter { it.endsWith(".ttf") }
        for (font in fonts) {
            val words = font.removeSuffix(".ttf").split('_')
            assertTrue("$font is not credited", Credits.of(CreditKind.FONT).any { c -> words.all { it in c.name.lowercase() } })
        }
        assertTrue(Credits.of(CreditKind.FONT).all { "Open Font License" in it.licence })
        assertTrue(Credits.of(CreditKind.DATASET).any { it.name == "GeoNames" && it.licence == "CC BY 4.0" })
        assertTrue(Credits.of(CreditKind.DATASET).any { it.name == "Natural Earth" })
    }

    @Test
    fun `the adhkar texts credit Hisn al-Muslim and its author`() {
        val texts = Credits.of(CreditKind.TEXT)
        assertTrue(texts.any { "Hisn al-Muslim" in it.name && "al-Qahtani" in it.name })
    }

    @Test
    fun `every credit links to its source over https`() {
        assertTrue(Credits.all.all { it.url.startsWith("https://") && it.licence.isNotBlank() })
    }
}
