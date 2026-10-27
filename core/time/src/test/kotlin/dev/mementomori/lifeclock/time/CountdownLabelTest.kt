package dev.mementomori.lifeclock.time

import dev.mementomori.lifeclock.model.TimeCategory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

/**
 * A countdown readout has to say its unit. Measured on device: a 65-day countdown rendered
 * "1568:37:23" - the platform Chronometer's total hours - which is unreadable and unit-blind.
 */
class CountdownLabelTest : StringSpec({

    "a day count reads in days" {
        CountdownLabel.of(66, TimeCategory.DAYS, Locale.US) shouldBe "66 days"
    }

    "one unit is singular" {
        CountdownLabel.of(1, TimeCategory.DAYS, Locale.US) shouldBe "1 day"
        CountdownLabel.of(1, TimeCategory.WEEKS, Locale.US) shouldBe "1 week"
        CountdownLabel.of(1, TimeCategory.MONTHS, Locale.US) shouldBe "1 month"
        CountdownLabel.of(1, TimeCategory.HOURS, Locale.US) shouldBe "1 hour"
        CountdownLabel.of(1, TimeCategory.NET_FREE_HOURS, Locale.US) shouldBe "1 hour"
    }

    "each category keeps its own unit" {
        CountdownLabel.of(10, TimeCategory.WEEKS, Locale.US) shouldBe "10 weeks"
        CountdownLabel.of(3, TimeCategory.MONTHS, Locale.US) shouldBe "3 months"
        CountdownLabel.of(1569, TimeCategory.HOURS, Locale.US) shouldBe "1,569 hours"
        CountdownLabel.of(1569, TimeCategory.NET_FREE_HOURS, Locale.US) shouldBe "1,569 hours"
    }

    "large counts are grouped like every other number in the app" {
        CountdownLabel.of(1234, TimeCategory.DAYS, Locale.US) shouldBe "1,234 days"
    }

    "a negative remainder clamps instead of printing a minus sign" {
        CountdownLabel.of(-5, TimeCategory.DAYS, Locale.US) shouldBe "0 days"
    }
})
