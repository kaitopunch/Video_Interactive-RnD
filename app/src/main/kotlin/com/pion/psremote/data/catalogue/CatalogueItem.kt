package com.pion.psremote.data.catalogue

import com.pion.psremote.domain.model.DemoSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The catalogue's answer: `{"message": …, "data": [item, …], "status": …}`. Only `data` is read. */
@Serializable
internal data class CatalogueResponse(val data: List<CatalogueItem> = emptyList())

/**
 * One game as the store's CMS holds it. Every field but [id] is optional here: an entry missing its script or
 * video is still listed, and opening it names the missing field (confirm.md Q12), which tells the BA more than
 * a game that silently is not there. `view`, `like` and `old_id` are not read (confirm.md H4).
 */
@Serializable
data class CatalogueItem(
    val id: String,
    val name: String = "",
    /** Home's order, lowest first. An entry without one goes last. */
    val priority: Int = Int.MAX_VALUE,
    /** `false` hides the game from Home (confirm.md H4). Absent means shown: a CMS that drops the field must not empty the list. */
    val status: Boolean = true,
    @SerialName("custom_fields") val customFields: CustomFields? = null,
) {
    /** The title Home shows. A blank name falls back to the id, so the card is never empty. */
    val title: String get() = name.ifBlank { id }
}

@Serializable
data class CustomFields(
    /** The demo script — the JSON array `tools/demo-script-format.md` describes — as a string. */
    val json: String? = null,
    /** The video's URL: the output of `tools/interpolate-slow-segments.py`, uploaded (confirm.md H7). */
    @SerialName("source_vid") val sourceVid: String? = null,
)

/** Home's list: the shown games, by priority. `sortedBy` is stable, so equal priorities keep the server's order. */
internal fun List<CatalogueItem>.toSummaries(): List<DemoSummary> =
    filter { it.status }.sortedBy { it.priority }.map { DemoSummary(id = it.id, title = it.title) }
