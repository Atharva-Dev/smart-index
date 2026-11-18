package com.kuriousdragon.smartindex.feature.home

import androidx.lifecycle.SavedStateHandle
import com.kuriousdragon.smartindex.feature.home.model.DateFilter
import com.kuriousdragon.smartindex.feature.home.model.HomeConfig
import com.kuriousdragon.smartindex.feature.home.model.Tab
import com.kuriousdragon.smartindex.feature.home.state.HomeMode
import java.time.LocalDate

/** The five persisted user inputs (LLD §4.8). [text] is the committed, trimmed query text. */
internal data class UserInputs(
    val mode: HomeMode,
    val day: LocalDate,
    val text: String,
    val filter: DateFilter,
    val tab: Tab,
)

/** `SavedStateHandle` keys of LLD §4.8. */
internal object HomeSavedKeys {
    const val MODE = "mode"
    const val DAY = "day"
    const val QUERY = "query"
    const val FILTER = "filter"
    const val TAB = "tab"
}

internal fun DateFilter.encode(): String = when (this) {
    DateFilter.AnyDate -> "ANY"
    DateFilter.Today -> "TODAY"
    DateFilter.Yesterday -> "YESTERDAY"
    DateFilter.Last7Days -> "L7"
    DateFilter.Last30Days -> "L30"
    is DateFilter.Day -> "D:${date.toEpochDay()}"
    is DateFilter.Range -> "R:${start.toEpochDay()}:${end.toEpochDay()}"
}

/** Anything unparsable is [DateFilter.AnyDate]; dates after [today] are clamped to [today]. */
internal fun decodeFilter(raw: String?, today: LocalDate): DateFilter {
    fun date(text: String?): LocalDate? {
        val epochDay = text?.toLongOrNull() ?: return null
        return runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull()?.let { minOf(it, today) }
    }
    val parts = raw?.split(':').orEmpty()
    return when (parts.firstOrNull()) {
        "TODAY" -> DateFilter.Today

        "YESTERDAY" -> DateFilter.Yesterday

        "L7" -> DateFilter.Last7Days

        "L30" -> DateFilter.Last30Days

        "D" -> date(parts.getOrNull(1))?.let { DateFilter.Day(it) } ?: DateFilter.AnyDate

        "R" -> {
            val start = date(parts.getOrNull(1))
            val end = date(parts.getOrNull(2))
            if (parts.size == RANGE_PARTS && start != null && end != null && !end.isBefore(start)) {
                DateFilter.Range(start, end)
            } else {
                DateFilter.AnyDate
            }
        }

        else -> DateFilter.AnyDate
    }
}

private const val RANGE_PARTS = 3

/** Reads the persisted inputs; parse errors fall back to the defaults, a future day becomes [today]. */
internal fun SavedStateHandle.restoreInputs(today: LocalDate): Pair<UserInputs, String> {
    val queryRaw = (get<Any?>(HomeSavedKeys.QUERY) as? String).orEmpty().take(HomeConfig.MAX_QUERY_CHARS)
    val modeName = get<Any?>(HomeSavedKeys.MODE) as? String
    val mode = HomeMode.entries.firstOrNull { it.name == modeName } ?: HomeMode.HOME
    val day = (get<Any?>(HomeSavedKeys.DAY) as? Long)
        ?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
        ?.let { minOf(it, today) }
        ?: today
    val tabName = get<Any?>(HomeSavedKeys.TAB) as? String
    val tab = Tab.entries.firstOrNull { it.name == tabName } ?: Tab.ALL
    val filter = decodeFilter(get<Any?>(HomeSavedKeys.FILTER) as? String, today)
    return UserInputs(mode, day, queryRaw.trim(), filter, tab) to queryRaw
}

internal fun SavedStateHandle.persist(user: UserInputs) {
    set(HomeSavedKeys.MODE, user.mode.name)
    set(HomeSavedKeys.DAY, user.day.toEpochDay())
    set(HomeSavedKeys.FILTER, user.filter.encode())
    set(HomeSavedKeys.TAB, user.tab.name)
}
