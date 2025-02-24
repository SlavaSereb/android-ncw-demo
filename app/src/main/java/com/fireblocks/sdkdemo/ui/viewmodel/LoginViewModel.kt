package com.fireblocks.sdkdemo.ui.viewmodel

import android.content.Context
import com.fireblocks.sdk.Fireblocks
import com.fireblocks.sdkdemo.FireblocksManager
import com.fireblocks.sdkdemo.bl.core.MultiDeviceManager
import com.fireblocks.sdkdemo.bl.core.environment.EnvironmentProvider
import com.fireblocks.sdkdemo.bl.core.server.Api
import com.fireblocks.sdkdemo.bl.core.storage.StorageManager
import com.fireblocks.sdkdemo.ui.main.BaseViewModel
import com.fireblocks.sdkdemo.ui.signin.SignInResult
import com.fireblocks.sdkdemo.ui.signin.SignInState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Created by Fireblocks Ltd. on 03/07/2023.
 */
class LoginViewModel : BaseViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    data class LoginUiState(
        val signInSelected: Boolean = true,
        val showSnackbar: Boolean = false,
        val snackbarText: String = "",
        val signInState: SignInState = SignInState(),
    )

    fun onSignInResult(result: SignInResult) {
        _uiState.update { it.copy(
                signInState = SignInState(
                    isSignInSuccessful = result.data != null,
                    signInError = result.errorMessage
                ),
            )
        }
    }

    fun resetSignInState() {
        _uiState.update {
            it.copy(
                signInState = SignInState(),
            )
        }
    }

    fun setSignInSelected(selected: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                signInSelected = selected,
            )
        }
    }

    fun onSnackbarChanged(show: Boolean, text: String) {
        _uiState.update { currentState ->
            currentState.copy(
                showSnackbar = show,
                snackbarText = text
            )
        }
    }

    fun handleSuccessSignIn(signInFlow: Boolean, context: Context, viewModel: LoginViewModel) {
        var deviceId : String? = null
        if (signInFlow) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    val availableEnvironments = EnvironmentProvider.availableEnvironments()
                    val items = hashSetOf<String>()
                    availableEnvironments.forEach { environment ->
                        items.add(environment.env())
                    }
                    val defaultEnv = availableEnvironments.first {
                        it.isDefault()
                    }
                    FireblocksManager.getInstance().initEnvironments(context, "default", defaultEnv.env())
                    runCatching {
                        val response = Api.with(StorageManager.get(context, "default")).getDevices().execute()
                        Timber.d("got response from getDevices rest API code:${response.code()}, isSuccessful:${response.isSuccessful} response.body(): ${response.body()}", response)
                        deviceId = response.body()?.let {
                            if (it.devices?.isNotEmpty() == true){
                                it.devices.last().deviceId
                            } else {
                                null
                            }
                        }
                    }.onFailure {
                        Timber.w(it, "Failed to call getDevices API")
                    }
                }
            }
            if (deviceId.isNullOrEmpty()) {
                deviceId = Fireblocks.generateDeviceId()
            }
        } else {
            deviceId = Fireblocks.generateDeviceId()
        }
        initializeFireblocksSdk(deviceId!!, context, viewModel)
    }

    private fun initializeFireblocksSdk(deviceId: String, context: Context, viewModel: LoginViewModel) {
        if (deviceId.isNotEmpty()) {
            Timber.d("before My All deviceIds: ${MultiDeviceManager.instance.allDeviceIds()}")
            StorageManager.get(context, deviceId).apply {
                MultiDeviceManager.instance.addDeviceId(deviceId)
            }
            Timber.d("after My All deviceIds: ${MultiDeviceManager.instance.allDeviceIds()}")
        }
        val fireblocksManager = FireblocksManager.getInstance()
        fireblocksManager.clearTransactions()
        fireblocksManager.setupEnvironmentsAndDevice(context)

        val availableEnvironments = EnvironmentProvider.availableEnvironments()
        val items = hashSetOf<String>()
        availableEnvironments.forEach { environment ->
            items.add(environment.env())
        }
        val defaultEnv = availableEnvironments.first {
            it.isDefault()
        }
        FireblocksManager.getInstance().initEnvironments(context, deviceId, defaultEnv.env())

        fireblocksManager.init(context, viewModel, true)
    }
}