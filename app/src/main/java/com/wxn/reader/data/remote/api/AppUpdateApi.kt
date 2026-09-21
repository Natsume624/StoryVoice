package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.AppUpdateInfo
import com.wxn.reader.data.remote.dto.BaseResponse
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

interface AppUpdateApi {
    suspend fun checkUpdate(source: String, arch: String, versionCode: Int): Result<BaseResponse<AppUpdateInfo>>
}

@Singleton
class AppUpdateApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : AppUpdateApi {
    override suspend fun checkUpdate(
        source: String,
        arch: String,
        versionCode: Int
    ): Result<BaseResponse<AppUpdateInfo>> {
        return BaseApi.get(
            httpClient,
            ApiPath.API_APP_UPDATE,
            mapOf(
                "source" to source,
                "arch" to arch,
                "versionCode" to versionCode,
            )
        )
    }
}
