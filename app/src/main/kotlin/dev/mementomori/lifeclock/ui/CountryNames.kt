package dev.mementomori.lifeclock.ui

import java.util.Locale

/**
 * ISO 3166 alpha-3 -> a name a person recognises.
 *
 * The life table is keyed by alpha-3 because that is what the UN publishes, but "VNM" is not a
 * country name. The platform can convert alpha-2 <-> alpha-3, so the map is derived once instead of
 * shipping a second list that could drift from the table it is meant to describe.
 */
object CountryNames {

    private val byIso3: Map<String, String> by lazy {
        val display = Locale.getDefault()
        buildMap {
            for (iso2 in Locale.getISOCountries()) {
                val locale = Locale("", iso2)
                val iso3 = runCatching { locale.getISO3Country() }.getOrNull() ?: continue
                if (!containsKey(iso3)) put(iso3, locale.getDisplayCountry(display))
            }
        }
    }

    /** Falls back to the code itself: an unknown country must look odd, not look like a name. */
    fun of(iso3: String): String = byIso3[iso3] ?: iso3
}
