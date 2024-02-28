package efrat19.ads

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper

public class AdDeserializationSchema : DeserializationSchema<Ad> {

    private val serializerVersion = 1L

    private var objectMapper: ObjectMapper

    init {
        objectMapper = ObjectMapper()
    }

    override fun deserialize(message: ByteArray): Ad {
        return objectMapper.readValue(message, Ad::class.java)
    }
    override fun isEndOfStream(nextElement: Ad): Boolean {
        return false
    }
    override fun getProducedType(): TypeInformation<Ad> {
        return TypeInformation.of(Ad::class.java)
    }
}
