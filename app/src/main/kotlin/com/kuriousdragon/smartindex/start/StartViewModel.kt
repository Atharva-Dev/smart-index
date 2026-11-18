package com.kuriousdragon.smartindex.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.kuriousdragon.smartindex.feature.home.HOME_ROUTE
import com.kuriousdragon.smartindex.feature.onboarding.ONBOARDING_GRAPH_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class StartViewModel @Inject constructor(private val store: OnboardingStore) : ViewModel() {
    private val _startDestination = MutableStateFlow<String?>(null)

    /** Null until the first read of the flag; then [HOME_ROUTE] or [ONBOARDING_GRAPH_ROUTE], never changing again. */
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            _startDestination.value = if (store.completed.first()) HOME_ROUTE else ONBOARDING_GRAPH_ROUTE
        }
    }

    fun onOnboardingFinished(nav: NavController) {
        viewModelScope.launch {
            store.markCompleted()
            nav.navigate(HOME_ROUTE) { popUpTo(ONBOARDING_GRAPH_ROUTE) { inclusive = true } }
        }
    }
}
