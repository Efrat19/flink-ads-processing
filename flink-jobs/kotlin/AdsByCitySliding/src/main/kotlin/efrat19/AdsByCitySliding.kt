package efrat19

import efrat19.ads.Ad
import efrat19.ads.AdDeserializationSchema
import java.text.DateFormat;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
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
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import java.sql.Timestamp;

const val IN_TOPIC = "scraped-ads"
const val SLIDING_OUT_TOPIC = "ads-by-city-and-year-sld-win"
const val K_HOST = "broker:19092"
const val GROUP_ID = "flink-job1"

const val WIDNOW_SIZE_SEC = 60
const val WIDNOW_SLIDE_SEC = 10
const val WATERMARK_SEC = 5

fun main() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.inStreamingMode())

        tableEnv.createTable("sink", createKafkaSinkDescriptor().build())
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
                                        sqlTimeToEpochMilli(ad.scraped_time)
                        }
        val streamSchema =
                        Schema.newBuilder()
                                        .column("city", DataTypes.STRING())
                                        .column("ad_id", DataTypes.STRING())
                                        .column("posted_date", DataTypes.STRING())
                                        .columnByExpression("ts", "CAST(scraped_time AS TIMESTAMP(3))")
                                        .watermark(
                                                        "ts",
                                                        "ts - INTERVAL '5' SECOND"
                                        )
                                        .build()
        val stream: DataStream<Ad> = env.fromSource(source, watermarks, "Kafka Source")
        val scrapedAdsTable = tableEnv.fromDataStream(stream, streamSchema)

        val size = lit(Duration.ofSeconds(60))
        val slide = lit(Duration.ofSeconds(10))
        scrapedAdsTable.window(Slide.over(size).every(slide).on(col("ts")).`as`("w"))
                        .groupBy(col("city"),col("w"))
                        .select(
                                        col("city"),
                                        col("ad_id").count(),
                                        col("w").start(),
                                        col("w").end()
                                        )
                        .filter(col("city").isNotEqual(""))
                        .insertInto("sink")
                        .execute()

        env.execute("scraped ads aggregation")
}

// public fun createKafkaSourceDescriptor(): TableDescriptor.Builder {
//         return TableDescriptor.forConnector(KafkaDynamicTableFactory.IDENTIFIER)
//                         .schema(
//                                         Schema.newBuilder()
//                                         .column("ad_id", DataTypes.STRING())
//                                                         .column("city", DataTypes.STRING())
//                                                         .column("posted_date", DataTypes.STRING())
//                                                         .columnByExpression(
//                                                                 "ts",
//                                                                 "CAST(scraped_time AS TIMESTAMP(3))"
//                                                                 ) // '2024-02-29 19:37:12.491506'
//                                                         .watermark("ts", "ts - INTERVAL '5' SECOND")
//                                                         .build()
//                         )
//                         .format("json")
//                         .option("value.format", "json")
//                         .option("topic", IN_TOPIC)
//                         .option("properties.bootstrap.servers", K_HOST)
//                         .option("properties.group.id", GROUP_ID)
//                         .option("json.timestamp-format.standard", "ISO-8601")
// }

public fun createKafkaSinkDescriptor(): TableDescriptor.Builder {
        return TableDescriptor.forConnector("kafka")
                        .schema(
                                        Schema.newBuilder()
                                                        .column(
                                                                        "city",
                                                                        DataTypes.STRING().notNull()
                                                        )
                                                        .column(
                                                                        "num_ads",
                                                                        DataTypes.BIGINT().notNull()
                                                        )
                                                        .column("w_start",DataTypes.TIMESTAMP_LTZ(3).notNull())
                                                        .column("w_end",
                                                        DataTypes.TIMESTAMP_LTZ(3).notNull())
                                                        // .primaryKey("city")
                                                        .build()
                        )
                        .format("json")
                        // .option("value.format", "json")
                        .option("topic", SLIDING_OUT_TOPIC)
                        .option("properties.bootstrap.servers", K_HOST)
                        .option("properties.group.id", GROUP_ID)
}

fun sqlTimeToEpochMilli(sqlTime: String): Long {
        // val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:SS")
        // val dateTime = LocalDateTime.parse(sqlTime, formatter)
        // return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val parsedDate = dateFormat.parse(sqlTime)
        return java.sql.Timestamp(parsedDate.getTime()).toInstant().toEpochMilli()
}

// fun sqlTimeToEpochMilli(sqlTime: String): Long {
//         val formatter = DateTimeFormatter.ofPattern("YYYY-mm-dd HH:MM:SS")
//         val dateTime = LocalDate.parse(sqlTime, formatter)
//         return  dateTime.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
//         // return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
// }