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
                val ad_id: String,
                val posted_date: String,
                @Contextual
                val scraped_time: Instant
) {
        constructor(
                        adStr: String
        ) : this(
                        AdFactory(adStr).city,
                        AdFactory(adStr).ad_id,
                        AdFactory(adStr).posted_date,
                        AdFactory(adStr).scraped_time
        )
}
