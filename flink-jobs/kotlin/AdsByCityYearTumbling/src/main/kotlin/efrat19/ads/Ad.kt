package efrat19.ads

import com.fasterxml.jackson.annotation.JsonFormat
import com.lapanthere.flink.api.kotlin.typeutils.DataClassTypeInfoFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.apache.flink.table.api.DataTypes

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
                val ad_id: String = "",
                val posted_date: String = "",
                val scraped_time: String = "",
                val spider: String = "",
                var posted_year: String = "",
) {
        constructor() : this("")

        public fun withPostedYear() : Ad {
                this.posted_year = extractYearFrom(this.posted_date)
                return this
        } 
}

fun extractYearFrom(date : String): String {
        return date.subSequence(0, 4).toString()
}
