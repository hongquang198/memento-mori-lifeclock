package dev.mementomori.lifeclock.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mementomori.lifeclock.AppDependencies
import dev.mementomori.lifeclock.contracts.WidgetPlacements
import dev.mementomori.lifeclock.contracts.WidgetRefresher
import dev.mementomori.lifeclock.usecases.DeleteConfigUseCase
import dev.mementomori.lifeclock.usecases.DeleteProfileUseCase
import dev.mementomori.lifeclock.usecases.DuplicateConfigUseCase
import dev.mementomori.lifeclock.usecases.ListPlacedWidgetsUseCase
import dev.mementomori.lifeclock.usecases.SaveConfigUseCase
import dev.mementomori.lifeclock.usecases.SaveProfileUseCase
import dev.mementomori.lifeclock.widget.WidgetDependencies
import javax.inject.Singleton

/**
 * The composition root: the one file that knows how the interfaces are implemented.
 *
 * `@Provides` rather than `@Inject constructor` on the use cases on purpose. Putting `@Inject` on a
 * domain class drags a DI annotation into the layer that is supposed to be framework-free; keeping
 * the wiring here means `:domain:usecases` compiles with nothing but Kotlin.
 *
 * [widgetDependencies] is a `@Singleton` because the app and the widget must read the *same* store.
 * Two instances would mean a config saved in the app is invisible to the next widget draw - the
 * exact class of bug this project already spent a milestone on.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun widgetDependencies(@ApplicationContext context: Context): WidgetDependencies =
        AppDependencies.from(context)

    @Provides
    @Singleton
    fun widgetRefresher(@ApplicationContext context: Context): WidgetRefresher =
        AndroidWidgetRefresher(context)

    @Provides
    @Singleton
    fun widgetPlacements(@ApplicationContext context: Context): WidgetPlacements =
        AndroidWidgetPlacements(context)

    @Provides
    fun saveConfigUseCase(deps: WidgetDependencies, refresher: WidgetRefresher): SaveConfigUseCase =
        SaveConfigUseCase(deps.configs, refresher)

    @Provides
    fun deleteConfigUseCase(deps: WidgetDependencies, refresher: WidgetRefresher): DeleteConfigUseCase =
        DeleteConfigUseCase(deps.configs, refresher)

    @Provides
    fun duplicateConfigUseCase(deps: WidgetDependencies): DuplicateConfigUseCase =
        DuplicateConfigUseCase(deps.configs)

    @Provides
    fun saveProfileUseCase(deps: WidgetDependencies, refresher: WidgetRefresher): SaveProfileUseCase =
        SaveProfileUseCase(deps.profiles, refresher)

    @Provides
    fun deleteProfileUseCase(deps: WidgetDependencies): DeleteProfileUseCase =
        DeleteProfileUseCase(deps.profiles)

    @Provides
    fun listPlacedWidgetsUseCase(
        deps: WidgetDependencies,
        placements: WidgetPlacements,
    ): ListPlacedWidgetsUseCase =
        ListPlacedWidgetsUseCase(placements, deps.bindings, deps.configs)
}
