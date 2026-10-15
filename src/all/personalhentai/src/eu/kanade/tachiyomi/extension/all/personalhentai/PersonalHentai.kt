package eu.kanade.tachiyomi.extension.all.personalhentai

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

private const val PREF_CATALOG_URL = "catalog_url"
private const val DEFAULT_CATALOG_URL = ""

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class PersonalHentai :
    HttpSource(),
    ConfigurableSource {

    override val name = "Personal Hentai Catalog"
    override val baseUrl = "https://localhost"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override val client = network.client

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        .add("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")

    // ---------- catálogo ----------

    private fun catalogUrl(): String =
        preferences.getString(PREF_CATALOG_URL, DEFAULT_CATALOG_URL)?.trim().orEmpty()

    private fun loadCatalog(): Catalog {
        val url = catalogUrl()
        if (url.isEmpty()) {
            return Catalog(title = "Configure a URL do catálogo nas opções da extensão")
        }
        val body = client.newCall(GET(url)).execute().use { it.body?.string().orEmpty() }
        return json.decodeFromString(body)
    }

    private fun Catalog.toMangasPage(): MangasPage =
        MangasPage(mangas.map { it.toSManga() }, hasNextPage = false)

    // ---------- listings ----------

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("catálogo pessoal, sem popular")

    override fun popularMangaParse(response: Response): MangasPage =
        loadCatalog().toMangasPage()

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("catálogo pessoal, sem latest")

    override fun latestUpdatesParse(response: Response): MangasPage =
        loadCatalog().toMangasPage()

    // ---------- search local ----------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("busca local")

    override fun searchMangaParse(response: Response): MangasPage =
        loadCatalog().toMangasPage()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val q = query.trim().lowercase()
        val all = loadCatalog().mangas
        if (q.isEmpty()) return MangasPage(all.map { it.toSManga() }, hasNextPage = false)
        val filtered = all.filter {
            it.title.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) }
        }
        return MangasPage(filtered.map { it.toSManga() }, hasNextPage = false)
    }

    // ---------- details ----------

    override fun mangaDetailsRequest(manga: SManga): Request =
        throw UnsupportedOperationException("detalhes do catálogo local")

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("detalhes do catálogo local")

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun fetchMangaDetails(manga: SManga): SManga {
        val entry = loadCatalog().mangas.firstOrNull { it.title == manga.url }
            ?: throw Exception("entrada sumiu do catálogo")
        return entry.toSManga()
    }

    // ---------- chapters ----------

    override fun chapterListRequest(manga: SManga): Request =
        throw UnsupportedOperationException("capítulos do catálogo local")

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("capítulos do catálogo local")

    override fun fetchChapterList(manga: SManga): List<SChapter> {
        val entry = loadCatalog().mangas.firstOrNull { it.title == manga.url }
            ?: throw Exception("entrada sumiu do catálogo")
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return entry.chapters.mapIndexed { idx, ch ->
            SChapter.create().apply {
                url = ch.url
                name = ch.name.ifBlank { "Capítulo ${idx + 1}" }
                chapter_number = (idx + 1).toFloat()
                date_upload = ch.date?.let { runCatching { fmt.parse(it)?.time }.getOrNull() } ?: 0L
            }
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    // ---------- páginas ----------

    override fun pageListRequest(chapter: SChapter): Request =
        GET(chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body?.string().orEmpty()
        val base: HttpUrl = response.request.url
        val doc: Document = Jsoup.parse(html, base.toString())

        val imgs = doc.select("img, source").mapNotNull { el ->
            val src = el.attr("abs:src").ifBlank { el.attr("abs:data-src") }
            src.takeIf { it.isNotBlank() && it.toHttpUrlOrNull() != null }
        }.distinct()

        val picked = imgs.filter { looksLikePage(it, base) }.ifEmpty { imgs }

        return picked.mapIndexed { i, u -> Page(i, url = "", imageUrl = u) }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("resolvido no pageListParse")

    private fun looksLikePage(url: String, base: HttpUrl): Boolean {
        val lower = url.lowercase()
        if (!lower.matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|avif)(\\?.*)?$"))) return false
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        val root = base.host.substringAfterLast('.', base.host)
        if (root.isNotEmpty() && host.contains(root)) return true
        return listOf(
            "/gallery", "/galleries/", "/g/", "/images/", "/img/",
            "/manga/", "/chapter/", "/capitulo/", "/cap/",
        ).any { lower.contains(it) }
    }

    // ---------- filtros ----------

    override fun getFilterList(): FilterList = FilterList()

    // ---------- preferências ----------

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_CATALOG_URL
            title = "URL do catálogo (JSON cru)"
            summary = "Ex.: https://raw.githubusercontent.com/Overthinker-1/meu-cat/main/catalog.json"
            setDefaultValue(DEFAULT_CATALOG_URL)
            dialogTitle = "URL do catalog.json"
            dialogMessage = "Cole a URL crua do JSON. GitHub raw, Gist raw ou Pastebin raw."
        }.also(screen::addPreference)
    }
}

internal fun CatalogManga.toSManga(): SManga = SManga.create().apply {
    url = title
    this.title = this@toSManga.title
    thumbnail_url = cover
    author = this@toSManga.author
    artist = this@toSManga.artist
    description = this@toSManga.description
    genre = tags.joinToString(", ")
    status = when (this@toSManga.status?.lowercase()) {
        "ongoing", "em andamento" -> SManga.ONGOING
        "completed", "completo" -> SManga.COMPLETED
        "hiatus", "pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
    initialized = true
}
