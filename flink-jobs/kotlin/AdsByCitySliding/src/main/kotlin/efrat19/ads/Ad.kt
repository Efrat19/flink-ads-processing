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
        val posted_time: String,
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

