package com.wxn.reader.presentation.opds

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.data.remote.opds.OpdsContentTypeException
import com.wxn.reader.domain.use_case.opds.ValidateOpdsUrlUseCase
import com.wxn.reader.domain.util.OpdsUrlAssist
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.launch

@Composable
fun AddOpdsCatalogDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, username: String?, password: String?) -> Unit,
    validateUseCase: ValidateOpdsUrlUseCase = androidx.hilt.navigation.compose.hiltViewModel<ValidateOpdsViewModel>().validateUseCase
) {
    var scheme by rememberSaveable { mutableStateOf(OpdsUrlAssist.SCHEME_HTTPS) }
    var hostInput by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<String?>(null) }
    var validationOk by remember { mutableStateOf(false) }
    var pathAutoCompletedHint by remember { mutableStateOf(false) }
    var showFormatError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val composed = remember(scheme, hostInput) { OpdsUrlAssist.compose(scheme, hostInput) }

    fun resetValidation() {
        validationOk = false
        validationResult = null
        pathAutoCompletedHint = false
    }

    // 格式错误文案仅在触发点（点测试/点保存/失焦）显示：
    // 逐字输入时中间态（h、ht…）不合法属正常过程，逐键闪红会误导用户
    fun showFormatErrorIfNeeded() {
        showFormatError = hostInput.isNotBlank() && composed == null
    }

    fun runValidation() {
        showFormatErrorIfNeeded()
        val target = composed ?: return
        if (isValidating) return
        scope.launch {
            isValidating = true
            resetValidation()
            val result = validateUseCase(
                target.url,
                username.ifBlank { null },
                password.ifBlank { null }
            )
            isValidating = false
            when (result) {
                is ValidateOpdsUrlUseCase.ValidationResult.Success -> {
                    validationOk = true
                    validationResult = context.getString(R.string.opds_validation_success)
                    if (name.isBlank()) {
                        name = result.feed.title
                    }
                }
                is ValidateOpdsUrlUseCase.ValidationResult.AuthRequired -> {
                    validationResult = context.getString(R.string.opds_auth_required)
                }
                is ValidateOpdsUrlUseCase.ValidationResult.Error -> {
                    validationResult = mapValidationMessage(context, result.cause, result.message)
                    // 地址是自动补全 /opds 的且测试失败：提示站点可能用不同路径
                    pathAutoCompletedHint = target.pathAutoCompleted
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.opds_add_catalog)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.opds_scheme_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = scheme == OpdsUrlAssist.SCHEME_HTTPS,
                        onClick = {
                            scheme = OpdsUrlAssist.SCHEME_HTTPS
                            resetValidation()
                            showFormatError = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(OpdsUrlAssist.SCHEME_HTTPS.uppercase())
                    }
                    SegmentedButton(
                        selected = scheme == OpdsUrlAssist.SCHEME_HTTP,
                        onClick = {
                            scheme = OpdsUrlAssist.SCHEME_HTTP
                            resetValidation()
                            showFormatError = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(OpdsUrlAssist.SCHEME_HTTP.uppercase())
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { raw ->
                        // 粘贴完整网址时自动识别协议并联动选择器，只保留域名/路径部分；
                        // 无法识别合法协议时保留原文
                        val parsed = OpdsUrlAssist.parse(raw)
                        if (parsed.scheme != null) {
                            scheme = parsed.scheme
                            hostInput = parsed.hostPath
                        } else {
                            hostInput = raw
                        }
                        resetValidation()
                        showFormatError = false
                    },
                    label = { Text(stringResource(R.string.opds_catalog_url)) },
                    placeholder = { Text(stringResource(R.string.opds_catalog_host_placeholder)) },
                    supportingText = {
                        if (showFormatError) {
                            Text(
                                text = stringResource(R.string.opds_url_invalid_format),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            // supportingText 槽位不是纵向容器，多个 Text 会同位叠绘，必须显式包 Column
                            Column {
                                Text(
                                    text = stringResource(R.string.opds_catalog_host_supporting_text),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                composed?.let { target ->
                                    Text(
                                        text = stringResource(R.string.opds_url_preview, target.url),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    isError = showFormatError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { runValidation() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (!state.isFocused) showFormatErrorIfNeeded()
                        }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.opds_catalog_name)) },
                    supportingText = {
                        Text(
                            text = stringResource(R.string.opds_catalog_name_supporting_text),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.opds_username)) },
                    supportingText = {
                        Text(
                            text = stringResource(R.string.opds_username_supporting_text),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.opds_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { runValidation() },
                    enabled = composed != null && !isValidating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.CenterVertically),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(stringResource(R.string.opds_test_connection))
                }

                validationResult?.let { message ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (validationOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                if (pathAutoCompletedHint) {
                    Text(
                        text = stringResource(R.string.opds_url_path_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showFormatErrorIfNeeded()
                    val target = composed ?: return@TextButton
                    onConfirm(
                        name.ifBlank { target.url },
                        target.url,
                        username.ifBlank { null },
                        password.ifBlank { null }
                    )
                },
                enabled = composed != null && !isValidating
            ) {
                Text(stringResource(R.string.opds_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.opds_cancel))
            }
        }
    )
}

private fun mapValidationMessage(context: Context, cause: Throwable?, fallback: String): String =
    when (cause) {
        is UnknownHostException -> context.getString(R.string.opds_error_host_not_found)
        is SocketTimeoutException, is ConnectException, is SSLException ->
            context.getString(R.string.opds_error_connect_failed)
        is OpdsContentTypeException -> context.getString(R.string.opds_error_content_type)
        else -> context.getString(R.string.opds_validation_error, fallback)
    }
