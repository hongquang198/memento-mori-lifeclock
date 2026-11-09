package dev.mementomori.lifeclock.usecases

import dev.mementomori.lifeclock.contracts.DeleteConfigResult
import dev.mementomori.lifeclock.contracts.DeleteProfileResult
import dev.mementomori.lifeclock.model.Sex
import dev.mementomori.lifeclock.model.PersonProfile
import dev.mementomori.lifeclock.model.WidgetConfigRecord
import dev.mementomori.lifeclock.model.WidgetInstanceBinding
import dev.mementomori.lifeclock.model.WidgetType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private fun config(
    id: String = "cfg",
    name: String = "Countdown",
    profileRef: String? = null,
) = WidgetConfigRecord(
    configId = id,
    name = name,
    type = if (profileRef == null) WidgetType.COUNTDOWN else WidgetType.LIFE_CLOCK,
    startEpochMillis = 0L,
    profileRef = profileRef,
)

private fun profile(id: String = "person") = PersonProfile(
    profileId = id,
    name = "Quang",
    birthEpochDay = 0L,
    sex = Sex.MALE,
    countryIso3 = "VNM",
)

class UseCasesTest : FunSpec({

    test("saving a config stores it and redraws exactly once") {
        val store = FakeStore()
        val refresher = RecordingRefresher()

        SaveConfigUseCase(store.configs, refresher)(config(id = "new", name = "Life clock"))

        store.configs.observeAll().first().single().name shouldBe "Life clock"
        refresher.redraws shouldBe 1
    }

    test("a delete that is refused keeps the config and still redraws") {
        val store = FakeStore(
            configs = listOf(config(id = "bound")),
            bindings = listOf(WidgetInstanceBinding(glanceId = "7", configId = "bound")),
        )
        val refresher = RecordingRefresher()

        val result = DeleteConfigUseCase(store.configs, refresher)("bound")

        result.shouldBeInstanceOf<DeleteConfigResult.BlockedByBindings>()
        (result as DeleteConfigResult.BlockedByBindings).glanceIds shouldBe listOf("7")
        store.configs.observeAll().first() shouldHaveSize 1
        refresher.redraws shouldBe 1
    }

    test("a duplicate gets a new id, a copy suffix, and does not redraw") {
        val store = FakeStore(configs = listOf(config(id = "original", name = "Bold")))
        val refresher = RecordingRefresher()

        val copy = DuplicateConfigUseCase(store.configs)(config(id = "original", name = "Bold"))

        (copy.configId == "original") shouldBe false
        copy.name shouldBe "Bold copy"
        store.configs.observeAll().first() shouldHaveSize 2
        refresher.redraws shouldBe 0
    }

    test("deleting a person is refused while a config still computes from them") {
        val store = FakeStore(
            configs = listOf(config(id = "clock", profileRef = "person")),
            profiles = listOf(profile("person")),
        )

        val result = DeleteProfileUseCase(store.profiles)("person")

        result.shouldBeInstanceOf<DeleteProfileResult.BlockedByConfigs>()
        store.profiles.observeAll().first() shouldHaveSize 1
    }

    test("placed widgets resolve their config name and unbound ones stay null") {
        val store = FakeStore(
            configs = listOf(config(id = "cfg", name = "First year")),
            bindings = listOf(WidgetInstanceBinding(glanceId = "12", configId = "cfg")),
        )

        val placed = ListPlacedWidgetsUseCase(FakePlacements(12, 3), store.bindings, store.configs)()

        placed.map { it.appWidgetId } shouldBe listOf(3, 12)
        placed.first { it.appWidgetId == 12 }.configName shouldBe "First year"
        placed.first { it.appWidgetId == 3 }.configName.shouldBeNull()
        placed.first { it.appWidgetId == 3 }.configId.shouldBeNull()
    }
})
