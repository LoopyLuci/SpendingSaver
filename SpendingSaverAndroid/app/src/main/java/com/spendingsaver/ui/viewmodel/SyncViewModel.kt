package com.spendingsaver.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingsaver.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val repo: SyncRepository,
) : ViewModel() {
    val state: StateFlow<SyncRepository.UiState> = repo.ui

    fun pair(hubUrl: String, key: String) { viewModelScope.launch { repo.pair(hubUrl, key) } }
    fun syncNow() { viewModelScope.launch { repo.syncNow() } }
    fun setAutoSync(enabled: Boolean) = repo.setAutoSync(enabled)
    fun unpair() = repo.unpair()
}
