package com.example.stationbottle.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationbottle.data.UserDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(private val context: Context) : ViewModel() {
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    init {
        viewModelScope.launch {
            UserDataStore.getDarkMode(context).collect { isDark ->
                _isDarkMode.value = isDark
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val newMode = !_isDarkMode.value
            UserDataStore.saveDarkMode(context, newMode)
            _isDarkMode.value = newMode
        }
    }
}