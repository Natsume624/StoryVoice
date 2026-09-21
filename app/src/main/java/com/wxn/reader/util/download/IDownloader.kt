package com.wxn.reader.util.download

import com.wxn.reader.data.remote.opds.OpdsRequestCredential
import java.io.File

interface IDownloader {

    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        headers: Map<String, String>? = null,
        credential: OpdsRequestCredential? = null,
        onProgress: (Float) -> Unit = {}
    ): String

    fun cancelDownload(targetFile: File)
}