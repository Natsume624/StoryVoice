package com.wxn.reader.presentation.opds.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.data.model.opds.OpdsEntryCache
import com.wxn.reader.data.remote.opds.OpdsNetworkException
import com.wxn.reader.data.remote.opds.OpdsParseException
import com.wxn.reader.data.source.local.OpdsCredentialStore
import com.wxn.reader.domain.repository.OpdsRepository
import com.wxn.reader.domain.use_case.opds.BrowseOpdsFeedUseCase
import com.wxn.reader.domain.use_case.opds.ManageOpdsCatalogUseCase
import com.wxn.reader.domain.use_case.opds.SearchOpdsUseCase
import com.wxn.reader.presentation.opds.OpdsBrowseError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 方案 docs/plans/2026-09-17-plan-opds-duplicate-entry-id-crash.md §4
 * T5-T7：策略 B 下 fetchFeed 对重复 id 条目全量透传；解析失败映射 ParseError。
 * 方案 docs/plans/2026-09-17-plan-opds-load-more-404-fallback.md §4：T8-T10 loadMore 404 兜底。
 * 方案 docs/plans/2026-09-17-plan-opds-search-url-normalize-and-private-host.md §3：T11 搜索归一。
 * OpdsCredentialStore 依赖 AndroidKeyStore，Robolectric 环境缺失时以 assume 跳过本类用例。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Robolectric 4.14.1 最高支持 SDK 34；compileSdk=36 需显式锁定（同 BackupImporterTest 既有做法）
class OpdsBrowseViewModelTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeOpdsRepository(
        private val feed: OpdsFeed? = null,
        private val fetchError: Exception? = null,
        /** 按 URL 精确分流的 fetch 结果（T8-T10：畸形 nextUrl 404 / 折叠 URL 成功）；未命中回落 feed/fetchError */
        private val resultsByUrl: Map<String, Result<OpdsFeed>> = emptyMap(),
        /** 覆盖 getAllEnabledCatalogs 返回的目录（T11：存量相对模板目录） */
        private val catalogs: List<OpdsCatalogEntity>? = null
    ) : OpdsRepository {
        val requestedUrls = mutableListOf<String>()
        val searchedUrls = mutableListOf<String>()

        override fun getAllCatalogs() = flowOf(emptyList<OpdsCatalogEntity>())
        override fun getAllEnabledCatalogs() = flowOf(
            catalogs ?: listOf(
                OpdsCatalogEntity(id = 1L, name = "Test Catalog", url = "http://test/opds")
            )
        )

        override suspend fun getCatalogById(id: Long): OpdsCatalogEntity? = null
        override suspend fun addCatalog(catalog: OpdsCatalogEntity): Long = 0L
        override suspend fun updateCatalog(catalog: OpdsCatalogEntity) {}
        override suspend fun deleteCatalog(id: Long) {}
        override suspend fun fetchFeed(url: String, catalogId: Long, useCache: Boolean): Result<OpdsFeed> {
            requestedUrls.add(url)
            resultsByUrl[url]?.let { return it }
            fetchError?.let { return Result.failure(it) }
            return Result.success(feed ?: OpdsFeed(title = ""))
        }

        override suspend fun search(
            catalogId: Long,
            searchUrl: String,
            query: String,
            startIndex: Int,
            count: Int
        ): Result<OpdsFeed> {
            searchedUrls.add(searchUrl)
            return Result.success(OpdsFeed(title = ""))
        }

        override suspend fun validateCatalog(
            url: String,
            username: String?,
            password: String?
        ): Result<OpdsFeed> = Result.success(OpdsFeed(title = ""))

        override suspend fun syncPredefinedCatalogs(catalogs: List<com.wxn.reader.data.model.opds.PredefinedCatalogItem>) {}
    }

    private fun buildViewModel(repository: FakeOpdsRepository): OpdsBrowseViewModel {
        val credentialStore = try {
            OpdsCredentialStore(context)
        } catch (e: Exception) {
            assumeNoException("本环境无 AndroidKeyStore，无法构造 OpdsCredentialStore", e)
            error("unreachable")
        }
        return OpdsBrowseViewModel(
            savedStateHandle = SavedStateHandle(mapOf("catalogId" to 1L)),
            context = context,
            browseFeedUseCase = BrowseOpdsFeedUseCase(repository),
            searchUseCase = SearchOpdsUseCase(repository),
            manageCatalogUseCase = ManageOpdsCatalogUseCase(repository, credentialStore),
            entryCache = OpdsEntryCache()
        )
    }

    private fun createViewModel(
        feed: OpdsFeed? = null,
        fetchError: Exception? = null
    ): OpdsBrowseViewModel = buildViewModel(FakeOpdsRepository(feed = feed, fetchError = fetchError))

    /** T5：策略 B——Continue 条目与章节条目同 id="44"，fetchFeed 全量保留、顺序不变 */
    @Test
    fun t5_duplicateIdEntriesAllPreserved() {
        val vm = createViewModel(
            feed = OpdsFeed(
                title = "Series",
                entries = listOf(
                    OpdsEntry(id = "44", title = "Continue Reading from: Book"),
                    OpdsEntry(id = "44", title = "Book")
                )
            )
        )
        val entries = vm.uiState.value.entries
        assertEquals(2, entries.size)
        assertEquals(listOf("44", "44"), entries.map { it.id })
        assertEquals(listOf("Continue Reading from: Book", "Book"), entries.map { it.title })
    }

    /** T6：正常唯一 id 的 feed 原样透传，数量与顺序不变 */
    @Test
    fun t6_normalFeedPassthrough() {
        val vm = createViewModel(
            feed = OpdsFeed(
                title = "Series",
                entries = listOf(
                    OpdsEntry(id = "1", title = "Chapter 1"),
                    OpdsEntry(id = "2", title = "Chapter 2"),
                    OpdsEntry(id = "3", title = "Chapter 3")
                )
            )
        )
        val entries = vm.uiState.value.entries
        assertEquals(listOf("1", "2", "3"), entries.map { it.id })
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), entries.map { it.title })
    }

    /** T7：解析失败走 ParseError，不崩溃 */
    @Test
    fun t7_parseFailureMapsToParseError() {
        val vm = createViewModel(fetchError = OpdsParseException("bad feed", null))
        assertTrue(vm.uiState.value.error is OpdsBrowseError.ParseError)
    }

    /** T8：首次 nextUrl 404 → 折叠重复路径段重试成功 → entries 增长、无 error */
    @Test
    fun t8_loadMore404CollapsesDuplicatedSegmentAndRetries() {
        val nextUrl = "http://test/api/opds/K3aF/K3aF/recently-updated?pageNumber=2"
        val collapsedUrl = "http://test/api/opds/K3aF/recently-updated?pageNumber=2"
        val repository = FakeOpdsRepository(
            feed = OpdsFeed(
                title = "Recently Updated",
                nextUrl = nextUrl,
                entries = listOf(OpdsEntry(id = "1", title = "Book 1"))
            ),
            resultsByUrl = mapOf(
                nextUrl to Result.failure(OpdsNetworkException(404, "Not Found")),
                collapsedUrl to Result.success(
                    OpdsFeed(
                        title = "Recently Updated",
                        entries = listOf(OpdsEntry(id = "2", title = "Book 2"))
                    )
                )
            )
        )
        val vm = buildViewModel(repository)
        assertEquals(listOf("1"), vm.uiState.value.entries.map { it.id })

        vm.loadMore()

        assertEquals(listOf("1", "2"), vm.uiState.value.entries.map { it.id })
        assertFalse(vm.uiState.value.hasLoadMoreError)
        assertFalse(vm.uiState.value.isLoadingMore)
        assertEquals(listOf(nextUrl, collapsedUrl), repository.requestedUrls.drop(1))
    }

    /** T9：折叠后仍 404 → hasLoadMoreError = true，不吞错误 */
    @Test
    fun t9_loadMore404StillFailsAfterCollapseKeepsError() {
        val nextUrl = "http://test/api/opds/K3aF/K3aF/recently-updated?pageNumber=2"
        val collapsedUrl = "http://test/api/opds/K3aF/recently-updated?pageNumber=2"
        val failure = Result.failure<OpdsFeed>(OpdsNetworkException(404, "Not Found"))
        val repository = FakeOpdsRepository(
            feed = OpdsFeed(
                title = "Recently Updated",
                nextUrl = nextUrl,
                entries = listOf(OpdsEntry(id = "1", title = "Book 1"))
            ),
            resultsByUrl = mapOf(nextUrl to failure, collapsedUrl to failure)
        )
        val vm = buildViewModel(repository)

        vm.loadMore()

        assertTrue(vm.uiState.value.hasLoadMoreError)
        assertFalse(vm.uiState.value.isLoadingMore)
        assertEquals(listOf("1"), vm.uiState.value.entries.map { it.id })
    }

    /** T10：非 404（如 500）→ 不触发折叠重试，直接置错误态 */
    @Test
    fun t10_loadMoreNon404DoesNotRetryWithCollapsedUrl() {
        val nextUrl = "http://test/api/opds/K3aF/recently-updated?pageNumber=2"
        val repository = FakeOpdsRepository(
            feed = OpdsFeed(
                title = "Recently Updated",
                nextUrl = nextUrl,
                entries = listOf(OpdsEntry(id = "1", title = "Book 1"))
            ),
            resultsByUrl = mapOf(nextUrl to Result.failure(OpdsNetworkException(500, "Server Error")))
        )
        val vm = buildViewModel(repository)

        vm.loadMore()

        assertTrue(vm.uiState.value.hasLoadMoreError)
        // 仅首屏 URL 与 nextUrl 各请求一次，无折叠重试请求
        assertEquals(listOf("http://test/opds", nextUrl), repository.requestedUrls)
    }

    /** T11：目录存相对模板 searchUrl → search() 兜底归一，仓储收到的是绝对地址 */
    @Test
    fun t11_searchNormalizesStoredRelativeTemplate() {
        val repository = FakeOpdsRepository(
            catalogs = listOf(
                OpdsCatalogEntity(
                    id = 1L,
                    name = "Kavita",
                    url = "http://192.168.1.5:5000",
                    searchUrl = "/api/opds/K3aF/series?query={searchTerms}",
                    supportsSearch = true
                )
            )
        )
        val vm = buildViewModel(repository)

        vm.search("kotlin")

        assertEquals(
            listOf("http://192.168.1.5:5000/api/opds/K3aF/series?query={searchTerms}"),
            repository.searchedUrls
        )
    }
}
