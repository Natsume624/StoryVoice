package com.wxn.reader.presentation.settings.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.R
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.ReviewPromptManager
import com.wxn.reader.domain.use_case.update.CheckAppUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    application: Application,
    private val checkAppUpdateUseCase: CheckAppUpdateUseCase,
    private val appPreferencesUtil: AppPreferencesUtil,
    private val reviewPromptManager: ReviewPromptManager,
) : AndroidViewModel(application) {

    /** 关于页更新检查 UI 状态 */
    sealed interface UpdateUiState {
        data object Idle : UpdateUiState
        data object Checking : UpdateUiState
        data class Available(val result: CheckAppUpdateUseCase.UpdateResult.Available) : UpdateUiState
        data object UnsupportedAbi : UpdateUiState
        data class Notice(val messageRes: Int) : UpdateUiState   // UpToDate/网络/服务错误，Toast 后回 Idle
    }

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** UI Toast 展示后调用，防止旋转重建重复弹出（Notice 与 UnsupportedAbi 均为一次性 Toast 态） */
    fun consumeNotice() {
        val s = _updateState.value
        if (s is UpdateUiState.Notice || s is UpdateUiState.UnsupportedAbi) {
            _updateState.value = UpdateUiState.Idle
        }
    }

    /** 更新弹窗被取消（或已发起下载）时调用 */
    fun dismissAvailable() {
        if (_updateState.value is UpdateUiState.Available) _updateState.value = UpdateUiState.Idle
    }

    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking) return   // 防重复触发
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            val language = appPreferencesUtil.appPrefsFlow.firstOrNull()?.language.orEmpty()
            _updateState.value = when (val r = checkAppUpdateUseCase(appLanguage = language)) {
                is CheckAppUpdateUseCase.UpdateResult.Available -> UpdateUiState.Available(r)
                CheckAppUpdateUseCase.UpdateResult.UpToDate ->
                    UpdateUiState.Notice(R.string.update_up_to_date)
                CheckAppUpdateUseCase.UpdateResult.UnsupportedAbi -> UpdateUiState.UnsupportedAbi
                is CheckAppUpdateUseCase.UpdateResult.Error ->
                    UpdateUiState.Notice(
                        if (r.isNetwork) R.string.update_error_network else R.string.update_error_server
                    )
            }
        }
    }

    /** 手动点击评价入口 → 永久禁用自动好评弹窗（熔断，与"我的"页评价流程一致） */
    fun onManualReviewClicked() {
        viewModelScope.launch { reviewPromptManager.onManualReviewClicked() }
    }
}
