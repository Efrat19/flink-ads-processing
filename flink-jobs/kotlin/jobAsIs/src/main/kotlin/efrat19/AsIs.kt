package efrat19

import efrat19.ads.Ad
import efrat19.ads.AdDeserializationSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

const val IN_TOPIC = "scraped-ads2"
const val OUT_TOPIC = "scraped-ads-as-is"
const val K_HOST = "broker:19092"
const val GROUP_ID = "flink-asis-job"

fun main() {

    val env = StreamExecutionEnvironment.getExecutionEnvironment()

    val source: KafkaSource<Ad> =
            KafkaSource.builder<Ad>()
                    .setBootstrapServers(K_HOST)
                    .setTopics(IN_TOPIC)
                    .setGroupId(GROUP_ID)
                    .setValueOnlyDeserializer(AdDeserializationSchema())
                    .build()

    val sink =
            KafkaSink.builder<String>()
                    .setBootstrapServers(K_HOST)
                    .setRecordSerializer(
                            KafkaRecordSerializationSchema.builder<String>()
                                    .setTopic(OUT_TOPIC)
                                    .setValueSerializationSchema(SimpleStringSchema())
                                    .build()
                    )
                    .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                    .build()

    val stream: DataStream<Ad> =
            env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
    stream.keyBy(Ad::ad_id).map { Json.encodeToString(it) }.sinkTo(sink)

    env.execute("Flink Kafka Example")
}
