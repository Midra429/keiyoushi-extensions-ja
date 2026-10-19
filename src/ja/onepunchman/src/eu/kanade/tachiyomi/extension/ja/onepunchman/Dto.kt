package eu.kanade.tachiyomi.extension.ja.onepunchman

import kotlinx.serialization.Serializable

@Serializable
class ImageSetDto(
    val imageItemList: List<ImageDto>,
    val updateDate: Long,
)

@Serializable
class ImageDto(
    val fileName: String,
)
