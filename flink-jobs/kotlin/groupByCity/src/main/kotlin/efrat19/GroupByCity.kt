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
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.api.Expressions.col
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.streaming.connectors.kafka.table.KafkaDynamicTableFactory;

const val IN_TOPIC = "scraped-ads2"
const val OUT_TOPIC = "scraped-ads-gbc"
const val K_HOST = "broker:19092"
const val GROUP_ID = "flink-gbc-job"

fun main() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.inStreamingMode())

        val source: KafkaSource<Ad> =
                        KafkaSource.builder<Ad>()
                                        .setBootstrapServers(K_HOST)
                                        .setTopics(IN_TOPIC)
                                        .setGroupId(GROUP_ID)
                                        .setValueOnlyDeserializer(AdDeserializationSchema())
                                        .build()                        

        val stream: DataStream<Ad> =
                        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")

        tableEnv.createTable("sink", createKafkaConnectorDescriptor().build())

        val scrapedAdsTable = tableEnv.fromDataStream(stream)
        scrapedAdsTable
                .groupBy(col("city"))
                .select(col("city"),col("ad_id").count())
                .filter(col("city").isNotEqual(""))
                .insertInto("sink").execute()

        env.execute("scraped ads aggregation")
}


public fun createKafkaConnectorDescriptor(): TableDescriptor.Builder {
        return TableDescriptor.forConnector("upsert-kafka")
        .schema(
                Schema.newBuilder()
                        .column("city", DataTypes.STRING().notNull())
                        .column("num_ads", DataTypes.BIGINT())
                        .primaryKey("city")
                        .build())
        .option("key.format", "json")
        .option("value.format", "json")
        .option("topic", OUT_TOPIC)
        .option("properties.bootstrap.servers", K_HOST)
        .option("properties.group.id", GROUP_ID)
    }
