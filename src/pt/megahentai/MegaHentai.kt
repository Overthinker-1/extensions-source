package eu.kanade.tachiyomi.extension.pt.megahentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MegaHentai : ParsedHttpSource() {

    override val name = "MegaHentai"
    override val baseUrl = "https://megahentai.biz"
    override val lang = "pt-BR"
    override val supportsLatest = true

    // --- 1. POPULARES / LISTA PRINCIPAL ---
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/mangas/page/$page/", headers)

    override fun popularMangaSelector() = "div.box-manga, div.manga-item, div.post-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h2, h3, .title, .entry-title").text()
            thumbnail_url = element.select("img").attr("abs:src")
            setUrlWithoutDomain(element.select("a").first()?.attr("href") ?: "")
        }
    }

    override fun popularMangaNextPageSelector() = "a.next, a.next-page, .pagination a:contains(Página seguinte)"

    // --- 2. ÚLTIMAS ATUALIZAÇÕES ---
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/lançamentos/page/$page/", headers)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // --- 3. DETALHES DO MANGÁ / OBRA ---
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1, .entry-title").text()
            author = document.select(".author, .autor, .info-content:contains(Autor)").text()
            description = document.select(".sinopse, .entry-content, .description").text()
            genre = document.select(".generos a, .genres a, .tags a").joinToString { it.text() }
            thumbnail_url = document.select(".capa img, .manga-thumb img, .poster img").attr("abs:src")
            
            val statusText = document.select(".status").text()
            status = when {
                statusText.contains("Ativo", ignoreCase = true) || statusText.contains("Em andamento", ignoreCase = true) -> SManga.ONGOING
                statusText.contains("Completo", ignoreCase = true) || statusText.contains("Finalizado", ignoreCase = true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // --- 4. LISTA DE CAPÍTULOS ---
    override fun chapterListSelector() = "ul.lista-capitulos > li, div.capitulos-list a, .chapters-list a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            val linkElement = if (element.tagName() == "a") element else element.select("a").first()
            name = linkElement?.text() ?: element.text()
            setUrlWithoutDomain(linkElement?.attr("href") ?: "")
        }
    }

    // --- 5. PÁGINAS DO CAPÍTULO (IMAGENS) ---
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        
        // Pega as imagens do leitor
        val imageElements = document.select("div.leitor-area img, div.reading-content img, div.images-content img")
        
        imageElements.forEachIndexed { index, element ->
            // Trata data-src / lazy-loading se existir no site
            var imageUrl = element.attr("abs:src")
            if (imageUrl.isBlank() || imageUrl.startsWith("data:")) {
                imageUrl = element.attr("abs:data-src")
            }
            if (imageUrl.isNotBlank()) {
                pages.add(Page(index, "", imageUrl))
            }
        }
        
        return pages
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Não utilizado")

    // --- 6. PESQUISA ---
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = 
        GET("$baseUrl/?s=$query&page=$page", headers)

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
}
