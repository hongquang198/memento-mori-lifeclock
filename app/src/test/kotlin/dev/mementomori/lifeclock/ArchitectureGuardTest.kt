package dev.mementomori.lifeclock

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The domain layer is pure Kotlin, and this test is what makes that a fact rather than a promise.
 *
 * `:domain:*` are `java-library` modules with no Android on the classpath, so an `android.*` import
 * would fail to compile anyway. The test exists for the case that is *not* caught: someone adds an
 * Android dependency to a domain module's build file to "just use one helper". The compiler would
 * then be happy and the architecture would be gone, so the rule is checked at the source level too.
 *
 * The README has claimed this guard exists since M0. It did not; now it does.
 */
class ArchitectureGuardTest {

    /** Gradle runs unit tests with the module directory as the working directory. */
    private val root: File = sequenceOf(File(".."), File("../.."), File("."))
        .map { it.canonicalFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun domainSources(): List<File> =
        File(root, "domain").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.invariantSeparatorsPath.contains("/src/main/") }
            .toList()

    @Test
    fun `domain modules import nothing from Android`() {
        val sources = domainSources()
        assertTrue("expected domain sources under ${root.path}/domain", sources.isNotEmpty())

        val offenders = sources.filter { file ->
            file.readLines().any { line ->
                val trimmed = line.trim()
                trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")
            }
        }

        assertTrue(
            "the domain layer must stay Android-free, but these import it:\n" +
                offenders.joinToString("\n") { it.relativeTo(root).path },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every domain module lives under the domain prefix`() {
        // A module outside `:domain:*`, `:data`, `:ui:*` or `:app` would make the layer map a lie.
        val included = File(root, "settings.gradle.kts").readLines()
            .mapNotNull { Regex("""include\("(:[^"]+)"\)""").find(it)?.groupValues?.get(1) }
            .toList()

        assertTrue("settings.gradle.kts listed no modules", included.isNotEmpty())

        val known = Regex(""":(domain:[a-z]+|data|ui:[a-z]+|app)""")
        val strays = included.filterNot { known.matches(it) }
        assertTrue(
            "these modules do not name their layer (expected :domain:*, :data, :ui:*, :app): $strays",
            strays.isEmpty(),
        )
    }
}
