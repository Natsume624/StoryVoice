package com.wxn.reader.data.repository

import androidx.room.withTransaction
import com.wxn.base.util.Logger
import com.wxn.reader.data.mapper.sherpamodel.SherpaModelMapper
import com.wxn.reader.data.remote.api.TTSModelsApi
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.SherpaModelDao
import com.wxn.reader.data.source.local.dao.SherpaSpeakerDao
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.domain.model.StoryVoiceOfflineCatalog
import com.wxn.reader.domain.repository.TTSModelsRepository
import com.wxn.reader.util.download.OKHttpStringStreamer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSModelsRepositoryImpl @Inject constructor(
    private val api: TTSModelsApi,
    private val modelDao: SherpaModelDao,
    private val speakerDao: SherpaSpeakerDao,
    private val mapper: SherpaModelMapper,
    private val appDatabase: AppDatabase,
    private val httpStringStreamer: OKHttpStringStreamer
) : TTSModelsRepository {

    override suspend fun getModels(
        page: Int,
        pageSize: Int
    ): Result<Pair<List<TTSModelData>?, Int?>> {
        val storyVoice = StoryVoiceOfflineCatalog.taiwanStoryVoice
        return api.ttsModelsList(page, pageSize).fold(
            onSuccess = { response ->
                val remoteModels = response.data?.list.orEmpty()
                    .filterNot { it.name == storyVoice.name }
                Result.success(
                    Pair(
                        if (page == 1) listOf(storyVoice) + remoteModels else remoteModels,
                        response.pagination?.totalPages ?: 1
                    )
                )
            },
            onFailure = {
                // The StoryVoice model catalog must remain usable even when the
                // original HandyReader service is unavailable.
                Result.success(Pair(if (page == 1) listOf(storyVoice) else emptyList(), 1))
            }
        )
    }

    override suspend fun getModelCard(url: String): Result<String> {
        return httpStringStreamer.getStringFromUrl(url)
    }

    override fun getDownloadedModels(): Flow<List<TTSModelData>> {
        return modelDao.getModelsWithSpeakers().map { modelsWithSpeakers ->
            modelsWithSpeakers.mapNotNull { modelWithSpeakers ->
                try {
                    mapper.toTTSModelData(
                        modelWithSpeakers.model,
                        modelWithSpeakers.speakers
                    )
                } catch (e: Exception) {
                    Logger.e("Failed to map model ${modelWithSpeakers.model.name}: ${e.message}")
                    null
                }
            }
        }
    }

    override suspend fun saveModel(model: TTSModelData) {
        try {
            appDatabase.withTransaction {
                val entity = mapper.toSherpaModelEntity(model)
                modelDao.insertModel(entity)

                val speakers = mapper.toSherpaSpeakerEntities(model.name, model.speakers)
                speakerDao.insertSpeakers(speakers)
            }
        } catch (e: Exception) {
            Logger.e("Failed to save model ${model.name}: ${e.message}")
            throw e
        }
    }

    override suspend fun isDownloaded(modelId: String): Boolean {
        return modelDao.getModelByName(modelId) != null
    }

    override suspend fun getLocalModelByName(modelId: String): TTSModelData? {
        val entry =  modelDao.getModelByName(modelId) ?: return null
        val speakers = speakerDao.getSpeakersByModel(modelId).firstOrNull() ?: return null
        val ttsModelData = mapper.toTTSModelData(entry, speakers)
        return ttsModelData
    }

    override suspend fun deleteModel(modelId: String) {
        appDatabase.withTransaction {
            modelDao.deleteModel(modelId)
            speakerDao.deleteSpeakersByModel(modelId)
        }
    }
}
