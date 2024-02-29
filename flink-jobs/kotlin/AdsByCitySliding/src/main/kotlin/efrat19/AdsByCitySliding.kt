package efrat19

import efrat19.ads.Ad
import efrat19.ads.AdDeserializationSchema
import java.time.Duration
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
import java.time.LocalDateTime
import java.time.ZoneOffset

const val IN_TOPIC = "scraped-ads"
const val SLIDING_OUT_TOPIC = "ads-by-city-and-year-tmbl-win"
const val K_HOST = "broker:19092"
const val GROUP_ID = "flink-job1"

const val WIDNOW_SIZE_SEC = 60
const val WIDNOW_SLIDE_SEC = 10
const val WATERMARK_SEC = 5

fun main() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.inStreamingMode())

        tableEnv.createTable("sink", createKafkaConnectorDescriptor().build())
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
                                        recordTimestamp ->
                                        isoToEpochMilli(ad.scraped_time)
                        }
        val streamSchema =
                        Schema.newBuilder()
                                        .column("city", DataTypes.STRING())
                                        .column("ad_id", DataTypes.STRING())
                                        .column("posted_date", DataTypes.STRING())
                                        .columnByExpression("ts", "TO_TIMESTAMP(scraped_time)")
                                        .watermark(
                                                        "ts",
                                                        "ts - INTERVAL 5 SECOND"
                                        )
                                        .build()
        val stream: DataStream<Ad> = env.fromSource(source, watermarks, "Kafka Source")
        val scrapedAdsTable = tableEnv.fromDataStream(stream, streamSchema)

        val size = lit(Duration.ofSeconds(60))
        val slide = lit(Duration.ofSeconds(10))
        scrapedAdsTable.window(Slide.over(size).every(slide).on(col("ts")).`as`("w"))
                        .groupBy(col("w"), col("city"))
                        .select(
                                        col("city"),
                                        col("ad_id").count().`as`("num_ads"),
                                        col("w").start().`as`("w_start"),
                                        col("w").end().`as`("w_end")
                        )
                        .filter(col("city").isNotEqual(""))
                        .insertInto("sink")
                        .execute()

        env.execute("scraped ads aggregation")
}

public fun createKafkaConnectorDescriptor(): TableDescriptor.Builder {
        return TableDescriptor.forConnector("kafka")
                        .schema(
                                        Schema.newBuilder()
                                                        .column("city", DataTypes.STRING().notNull())
                                                        .column("num_ads", DataTypes.BIGINT().notNull())
                                                        .column("w_start",DataTypes.TIMESTAMP(3).notNull())
                                                        .column("w_end", DataTypes.TIMESTAMP(3).notNull())
                                                        .build()
                        )
                        .format("json")
                        .option("topic", SLIDING_OUT_TOPIC)
                        .option("properties.bootstrap.servers", K_HOST)
                        .option("properties.group.id", GROUP_ID)
}
fun isoToEpochMilli(iso: String): Long {
        val dateTime = LocalDateTime.parse(iso)
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
}