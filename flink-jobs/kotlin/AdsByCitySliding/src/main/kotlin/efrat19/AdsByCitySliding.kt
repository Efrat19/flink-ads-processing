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
import org.apache.flink.table.api.Expressions.timestampDiff
import org.apache.flink.table.api.Schema
import org.apache.flink.table.api.Slide
import org.apache.flink.table.api.TableDescriptor
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.expressions.TimePointUnit;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import java.time.Duration

// import java.time.Duration;
// import java.time.LocalTime;


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
                                ad.scraped_time.toEpochMilli()
                        }
        val streamSchema =
                        Schema.newBuilder()
                                        .column("city", DataTypes.STRING())
                                        .column("ad_id", DataTypes.STRING())
                                        .column("scraped_time", DataTypes.TIMESTAMP_LTZ(3))
                                        .watermark("scraped_time", "scraped_time - INTERVAL 5 SECOND")
                                        .build()
        val stream: DataStream<Ad> = env.fromSource(source, watermarks, "Kafka Source")
        val scrapedAdsTable = tableEnv.fromDataStream(stream, streamSchema)

        // val size = lit(60, DataTypes.INTERVAL(DataTypes.SECOND()).notNull()) Data type 'INTERVAL SECOND(6) NOT NULL' with conversion class 'java.time.Duration' does not support a value literal of class 'java.lang.Integer'
        // val size = lit(Time.seconds(60)) Cannot derive a data type for value 'org.apache.flink.streaming.api.windowing.time.Time@2364cc6f'. The data type must be specified explicitly.
        // val size = lit(60,DataTypes.INT().notNull()) A sliding window expects a size literal of a day-time interval or BIGINT type.
        // val size = lit(DataTypes.INTERVAL(DataTypes.SECOND(60))) Fractional precision of day-time intervals must be between 0 and 9 (both inclusive)
        val size = lit(Duration.ofSeconds(60))
        val slide = lit(Duration.ofSeconds(10))
        scrapedAdsTable.window(Slide.over(size).every(slide).on(col("scraped_time")).`as`("w"))
                        .groupBy(col("w"), col("city"))
                        .select(
                                        col("city"),
                                        col("ad_id").count().`as`("num_ads"),
                                        col("w").start(),
                                        col("w").end()
                        )
                        .filter(col("city").isNotEqual(""))
                        .insertInto("sink")
                        .execute()

        env.execute("scraped ads aggregation")
}

public fun createKafkaConnectorDescriptor(): TableDescriptor.Builder {
        return TableDescriptor.forConnector("upsert-kafka")
                        .schema(
                                        Schema.newBuilder()
                                                        .column("city",DataTypes.STRING().notNull())
                                                        .column("num_ads",DataTypes.BIGINT())
                                                        .column("w_start",DataTypes.TIMESTAMP_LTZ())
                                                        .column("w_end", DataTypes.TIMESTAMP_LTZ())
                                                        .primaryKey("city")
                                                        .build()
                        )
                        .option("key.format", "json")
                        .option("value.format", "json")
                        .option("topic", SLIDING_OUT_TOPIC)
                        .option("properties.bootstrap.servers", K_HOST)
                        .option("properties.group.id", GROUP_ID)
}

fun isoToEpochMilli(iso: String): Long {
        val dateTime = LocalDateTime.parse(iso)
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
}