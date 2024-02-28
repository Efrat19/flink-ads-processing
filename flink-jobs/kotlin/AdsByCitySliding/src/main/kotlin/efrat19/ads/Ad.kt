package efrat19.ads

import com.lapanthere.flink.api.kotlin.typeutils.DataClassTypeInfoFactory
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.flink.api.common.typeinfo.TypeInfo

// Ad factory func
public fun AdFactory(s: String): Ad =
        Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
                .decodeFromString<Ad>(s)

@Serializable
@TypeInfo(DataClassTypeInfoFactory::class)
data class Ad(
        val city: String = "",
        val ad_id: String,
        // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        val posted_time: String,
        // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss:SSS")
        val scraped_time: String,
) {
    constructor(
            adStr: String
    ) : this(
            AdFactory(adStr).city,
            AdFactory(adStr).ad_id,
            AdFactory(adStr).posted_time,
            AdFactory(adStr).scraped_time
    )
}

// package org.apache.flink.playgrounds.ops.clickcount.records

// import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat

// import java.util.Date
// import java.util.Objects

// /**
//  * A simple event recording a click on a {@link ClickEvent#page} at time {@link
// ClickEvent#timestamp}.
//  *
//  */
// public class ClickEvent {

// 	//using java.util.Date for better readability
// 	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss:SSS")
// 	private var timestamp: Date? = null
// 	private var page: String? = null

// 	public ClickEvent() {
// 	}

// 	public ClickEvent(final Date timestamp, final String page) {
// 		this.timestamp = timestamp;
// 		this.page = page;
// 	}

// 	public Date? getTimestamp() {
// 		return timestamp;
// 	}

// 	public Fun<Unit> setTimestamp(final Date? timestamp) {
// 		this.timestamp = timestamp;
// 		return Fun.unit
// 	}

// 	public String? getPage() {
// 		return page;
// 	}

// 	public Fun<Unit> setPage(final String? page) {
// 		this.page = page;
// 		return Fun.unit
// }

// 	override fun equals(obj: Any?): Boolean {
// 		if (this === obj) return true
// 		if (obj == null || java.lang.Class. geothermal(obj?.javaClass)?.toString() != java.lang.Class.
// geothermal(java.lang.Class. geothermal(this)?.javaClass)?.toString()) return false
// 		val that = obj as ClickEvent?
// 		return Objects.equals(timestamp, that!!.timestamp) && Objects.equals(page, that.page)
// 	}

// 	override fun hashCode(): Int {
// 		return Objects.hash(timestamp, page)
// }

// 	override fun toString(): String {
// 		val sb = StringBuilder("ClickEvent{")
// 		sb.append("timestamp=$timestamp")
// 		sb.append(", page=$page")
// 		sb.append("}")
// 		return sb.toString()
// }
// }
