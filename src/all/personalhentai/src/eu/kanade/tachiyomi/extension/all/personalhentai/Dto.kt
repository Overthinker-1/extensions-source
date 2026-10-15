package eu.kanade.tachiyomi.extension.all.personalhentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Catalog(
    val title: String = "Meu catálogo",
    val mangas: List<CatalogManga> = emptyList(),
)

@Serializable
data class CatalogManga(
    val title: String = "",
    val cover: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val tags: List<String> = emptyList(),
    val status: String? = null,
    val chapters: List<CatalogChapter> = emptyList(),
)

@Serializable
data class CatalogChapter(
    val name: String = "",
    val url: String = "",
    @SerialName("date") val date: String? = null,
)
