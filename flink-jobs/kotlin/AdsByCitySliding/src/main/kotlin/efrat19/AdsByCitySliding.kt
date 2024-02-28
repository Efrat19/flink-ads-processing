package efrat19

import efrat19.ads.Ad
import efrat19.ads.AdDeserializationSchema
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.Expressions.col
import org.apache.flink.table.api.Expressions.lit
import org.apache.flink.table.api.Schema
import org.apache.flink.table.api.Slide
import org.apache.flink.table.api.TableDescriptor
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment

const val IN_TOPIC = "scraped-ads"
const val OUT_TOPIC = "ads-by-city-and-year-tmbl-win"
const val K_HOST = "broker:19092"
const val GROUP_ID = "flink-job1"

const val WIDNOW_SIZE_SEC = 60
const val WIDNOW_SLIDE_SEC = 10
const val WATERMARK_SEC = 5

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

        val watermarks =
                        WatermarkStrategy.forMonotonousTimestamps<Ad>().withTimestampAssigner {
                                        ad,
                                        _ ->
                                isoToEpochMilli(ad.scraped_time)
                        }
        val stream: DataStream<Ad> =
                        env.fromSource(
                                        source,
                                        watermarks,
                                        "Kafka Source"
                        )

        tableEnv.createTable("sink", createKafkaConnectorDescriptor().build())

        val scrapedAdsTable = tableEnv.fromDataStream(stream)
        scrapedAdsTable.window(
                                        Slide.over(lit(60))
                                                        .every(lit(10))
                                                        .on(col("scraped_time"))
                                                        .`as`(lit("w"))
                        )
                        .groupBy(col("city"))
                        .select(col("city"), col("ad_id").count())
                        .filter(col("city").isNotEqual(""))
                        .insertInto("sink")
                        .execute()

        env.execute("scraped ads aggregation")
}


public fun createKafkaConnectorDescriptor(): TableDescriptor.Builder {
        return TableDescriptor.forConnector("upsert-kafka")
                        .schema(
                                        Schema.newBuilder()
                                                        .column(
                                                                        "city",
                                                                        DataTypes.STRING().notNull()
                                                        )
                                                        .column("num_ads", DataTypes.BIGINT())
                                                        .primaryKey("city")
                                                        .build()
                        )
                        .option("key.format", "json")
                        .option("value.format", "json")
                        .option("topic", OUT_TOPIC)
                        .option("properties.bootstrap.servers", K_HOST)
                        .option("properties.group.id", GROUP_ID)
}

fun isoToEpochMilli(iso: String): Long {
        val dateTime = LocalDateTime.parse(iso)
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
}
