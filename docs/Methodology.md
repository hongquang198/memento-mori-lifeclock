# Methodology

Every number the app shows is either **derived from a cited source** or **an assumption we own**.
Nothing is a default we cannot defend. This file is the contract; the code is the implementation of
it, and the tests in `:core:mortality` pin the numbers.

## 1. Sources and vintages

| What | Source | Vintage | Licence |
|---|---|---|---|
| Life expectancy `e_x` by country, sex, age | United Nations, World Population Prospects 2024 | 2024 revision, period **2023**, medium variant | CC BY 3.0 IGO |
| Resting heart rate range | American Heart Association, Target Heart Rates Chart | accessed 2026-10-26 | AHA site terms |

### The life table

- File: `WPP2024_Life_Table_Abridged_Medium_1950-2023.csv.gz` (137.8 MB) from
  <https://population.un.org/wpp/assets/Excel%20Files/1_Indicator%20(Standard)/CSV_FILES/WPP2024_Life_Table_Abridged_Medium_1950-2023.csv.gz>
- Shipped subset: `core/mortality/src/main/resources/life_tables.csv` — 10,428 rows, 237 countries,
  183 KB. Columns `iso3,sex,age,ex`.
- Regenerate with `python tools/build_life_tables.py <downloaded.csv.gz> <output.csv>`. The script is
  the only transform: period `2023`, `LocTypeID = 4` (countries only, no regions or world aggregates),
  sexes `Male`/`Female`. Everything else is copied unchanged.

**Why WPP and not SSA.** The plan named the SSA period life table as the US source. `ssa.gov` returns
403 to every programmatic client available here, and a hand-copied table would not be reproducible.
WPP is the upstream of WHO's life-expectancy estimates, covers every country the app offers, and is
re-derivable from a public URL. One source for all countries also removes a class of bug: two
tables with different vintages producing a discontinuity at the US border.

**Vintage policy.** A table refresh is a deliberate act: new `Citation`, regenerate the asset, golden
tests fail, numbers change in a visible commit. The app never fetches tables at runtime, so a user's
number cannot change because a server updated overnight.

## 2. Age handling

- Age is counted in the **user's zone** (`LocalDate` arithmetic, not instant subtraction): at 20:00Z
  on 31 December a person in Ho Chi Minh City is already a year older than the same person in UTC.
- The table is abridged: ages 0, 1, then five-year groups to 100. A person aged 42 falls inside
  40–45, so `e_42` is linearly interpolated between `e_40` and `e_45`. Truncating to 40 would
  overstate remaining life by up to five years.
- Above 100 the table has no row and the engine returns "cannot answer". It never repeats the last
  row: a 101-year-old is not immortal.

## 3. Formulas

| Quantity | Formula | Notes |
|---|---|---|
| Days left | `e_x × 365.2425` | mean Gregorian year; 365 drifts a day every four years |
| Weeks left | `days / 7` | |
| Months left | `days / 30.436875` | `365.2425 / 12` |
| Arc fraction | `daysLeft / (daysLeft + daysLived)` | one ratio for every ring, so arcs cannot disagree |
| Heartbeats | `daysLeft × 1440 × 72` | **model**: 72 bpm inside the AHA's 60–100 range |
| Seasons | `daysLeft / 91.310625` | arithmetic on the table, no new assumption |

Rounding: displayed counts round **up**. `1.2 days` shows as `2`, never `1` — and never `0` while a
day remains. Fractions are not rounded; only presentation is.

`e_x` is a *period* life expectancy: it answers "if today's age-specific death rates held forever".
It is not a prediction for an individual, and the UI must not imply one.

## 4. What is deliberately not computed

- **Net free hours** needs the ATUS time-use split. It lands with that source and its reconciliation
  test, not before; `TimeCategory.NET_FREE_HOURS` exists but the engine does not emit it yet.
- **Books read** has no source that survives scrutiny (average book length and reading speed are
  both contested), so it is not shown rather than shown with false precision.

## 5. How the result is checked

| Check | Where | Tolerance |
|---|---|---|
| Table cells match the source | `LifeTablesTest` golden values | exact (4 dp) |
| Interpolation is linear | `LifeTablesTest` | 1e-12 of the expected ratio |
| Conversions agree with the table | `LifeStatsEngineTest` | exact integers, pinned |
| Arc fractions agree across categories | `LifeStatsEngineTest` | identical doubles |
| Age respects the zone | `LifeStatsEngineTest` | exact |
| Regeneration reproduces the asset | `tools/build_life_tables.py` + `git diff` | byte-identical |

Cross-source reconciliation (UN `e_0` vs WHO `e_0` for the same country) is the next check to add:
two agencies, independent models, one number. It needs the WHO GHO series pinned to a vintage first.

## 6. Known limitations

- The table is a population average. Sex and country are the only stratifiers; education, income and
  health status all move `e_x` substantially and are not in the model.
- The 2023 period includes COVID-era mortality for some countries; WPP smooths it, but a user
  comparing against a national table may see a difference of a year or more.
- Heartbeats use a single rate for every age and activity level. It is a rhetorical number, and it
  is labelled as a model wherever it appears.
