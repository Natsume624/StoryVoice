package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.domain.repository.OpdsRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class ValidateOpdsUrlUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository
) {
    sealed class ValidationResult {
        data class Success(val feed: OpdsFeed) : ValidationResult()
        data class AuthRequired(val url: String) : ValidationResult()
        data class Error(val message: String, val cause: Throwable? = null) : ValidationResult()
    }

    suspend operator fun invoke(
        url: String,
        username: String? = null,
        password: String? = null
    ): ValidationResult {
        return opdsRepository.validateCatalog(url, username, password).fold(
            onSuccess = { feed ->
                ValidationResult.Success(feed)
            },
            onFailure = { error ->
                // 协程取消必须原样放行，不得映射为失败文案
                if (error is CancellationException) throw error
                when (error) {
                    is com.wxn.reader.data.remote.opds.OpdsAuthException ->
                        ValidationResult.AuthRequired(url)
                    else -> ValidationResult.Error(error.message ?: "Unknown error", error)
                }
            }
        )
    }
}
