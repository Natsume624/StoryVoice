package com.wxn.reader.util.download

import com.wxn.reader.domain.model.DownloadMetadata
import com.wxn.reader.data.remote.opds.OpdsRequestCredential
import java.io.File

interface IDownloaderWithResume {

    suspend fun downloadWithResume(
        url: String,
        targetFile: File,
        metadata: DownloadMetadata? = null,
        capabilities: ServerCapabilities,
        headers: Map<String, String>? = null,
        credential: OpdsRequestCredential? = null,
        onProgress: (Float) -> Unit = {}
    ): String

}