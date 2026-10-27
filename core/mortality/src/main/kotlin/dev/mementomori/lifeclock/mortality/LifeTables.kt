package dev.mementomori.lifeclock.mortality

import dev.mementomori.lifeclock.model.Sex
import java.io.Reader

/**
 * Everything needed to re-derive a number without asking us.
 *
 * [retrieved] is the date we copied the row, [vintage] the revision it came from. Both matter: the
 * same URL returns different numbers next year, and a citation without a vintage cannot tell a
 * refreshed table from a changed formula.
 */
data class Citation(
    val source: String,
    val vintage: String,
    val table: String,
    val url: String,
    val retrieved: String,
    val license: String,
)

/** Remaining life expectancy at a given age, in years, with the table it came from. */
interface LifeTableSource {
    val citation: Citation

    /** Null when this (country, sex, age) is not in the table - never a guessed number. */
    fun lifeExpectancy(countryIso3: String, sex: Sex, age: Int): Double?
}

/**
 * The UN WPP abridged table, filtered to one period by `tools/build_life_tables.py`.
 *
 * Ages are 0, 1, then five-year groups to 100. A person aged 42 falls inside the 40-45 group, so the
 * value is interpolated between the two group starts. The alternative - truncating to 40 - would
 * overstate remaining life by up to five years, which is the entire product.
 */
class CsvLifeTableSource private constructor(
    private val rows: Map<Key, Double>,
    private val ages: List<Int>,
    override val citation: Citation,
) : LifeTableSource {

    private data class Key(val countryIso3: String, val sex: Sex, val age: Int)

    override fun lifeExpectancy(countryIso3: String, sex: Sex, age: Int): Double? {
        rows[Key(countryIso3, sex, age)]?.let { return it }

        val below = ages.lastOrNull { it < age } ?: return null
        val above = ages.firstOrNull { it > age } ?: return null
        val low = rows[Key(countryIso3, sex, below)] ?: return null
        val high = rows[Key(countryIso3, sex, above)] ?: return null
        return low + (high - low) * (age - below).toDouble() / (above - below)
    }

    companion object {
        fun from(reader: Reader, citation: Citation): CsvLifeTableSource {
            val rows = mutableMapOf<Key, Double>()
            reader.useLines { lines ->
                lines.drop(1).forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split(',')
                    if (parts.size < 4) return@forEach
                    val sex = when (parts[1]) {
                        "F" -> Sex.FEMALE
                        "M" -> Sex.MALE
                        else -> return@forEach
                    }
                    rows[Key(parts[0], sex, parts[2].toInt())] = parts[3].toDouble()
                }
            }
            return CsvLifeTableSource(rows, rows.keys.map { it.age }.distinct().sorted(), citation)
        }

        /** The bundled table. JVM resources ship on Android too, so `:core:mortality` stays Android-free. */
        fun bundled(citation: Citation = UN_WPP_2024): CsvLifeTableSource =
            CsvLifeTableSource::class.java.getResourceAsStream("/life_tables.csv")
                ?.bufferedReader()
                ?.let { from(it, citation) }
                ?: error("life_tables.csv missing from :core:mortality resources")
    }
}

/** The one table this app ships. Updating it is a deliberate act: change the vintage, regenerate. */
val UN_WPP_2024 = Citation(
    source = "United Nations, World Population Prospects",
    vintage = "2024 revision, period 2023, medium variant",
    table = "Life Table Abridged (Medium)",
    url = "https://population.un.org/wpp/assets/Excel%20Files/1_Indicator%20(Standard)/CSV_FILES/" +
        "WPP2024_Life_Table_Abridged_Medium_1950-2023.csv.gz",
    retrieved = "2026-10-26",
    license = "CC BY 3.0 IGO",
)
