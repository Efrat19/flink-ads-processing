package efrat19

import efrat19.ads.Ad
import efrat19.ads.AdDeserializationSchema
import java.sql.Timestamp
import java.text.SimpleDateFormat
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
import org.apache.flink.table.api.TableDescriptor
import org.apache.flink.table.api.Tumble
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment

const val TUMBLING_IN_TOPIC = "scraped-ads"
const val TUMBLING_OUT_TOPIC = "ads-by-city-and-year-tmbl-win"
const val TUMBLING_K_HOST = "broker:19092"
const val TUMBLING_GROUP_ID = "ads-by-city-and-year-tmbl-win"

fun main() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.inStreamingMode())

        tableEnv.createTable("sink", createKafkaSinkDescriptor().build())
        val source: KafkaSource<Ad> =
                        KafkaSource.builder<Ad>()
                                        .setBootstrapServers(TUMBLING_K_HOST)
                                        .setTopics(TUMBLING_IN_TOPIC)
                                        .setGroupId(TUMBLING_GROUP_ID)
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
                                        .column("posted_year", DataTypes.STRING())
                                        .columnByExpression(
                                                        "ts",
                                                        "CAST(scraped_time AS TIMESTAMP(3))"
                                        )
                                        .watermark("ts", "ts - INTERVAL '5' SECOND")
                                        .build()
        val stream: DataStream<Ad> = env.fromSource(source, watermarks, "Kafka Source")
        val scrapedAdsTable =
                        tableEnv.fromDataStream(
                                        stream.map { ad -> ad.withPostedYear() },
                                        streamSchema
                        )

        val size = lit(Duration.ofSeconds(300))
        scrapedAdsTable.window(Tumble.over(size).on(col("ts")).`as`("w"))
                        .groupBy(col("city"), col("posted_year"), col("w"))
                        .select(
                                        col("city"),
                                        col("ad_id").count(),
                                        col("posted_year"),
                                        col("w").start(),
                                        col("w").end()
                        )
                        .filter(col("city").isNotEqual(""))
                        .insertInto("sink")
                        .execute()

        env.execute("scraped ads aggregation")
}

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
                                                        .column(
                                                                        "posted_year",
                                                                        DataTypes.STRING().notNull()
                                                        )
                                                        .column(
                                                                        "w_start",
                                                                        DataTypes.TIMESTAMP(3)
                                                                                        .notNull()
                                                        )
                                                        .column(
                                                                        "w_end",
                                                                        DataTypes.TIMESTAMP(3)
                                                                                        .notNull()
                                                        )
                                                        .build()
                        )
                        .format("json")
                        .option("topic", TUMBLING_OUT_TOPIC)
                        .option("properties.bootstrap.servers", TUMBLING_K_HOST)
                        .option("properties.group.id", TUMBLING_GROUP_ID)
}

fun sqlTimeToEpochMilli(sqlTime: String): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val parsedDate = dateFormat.parse(sqlTime)
        return java.sql.Timestamp(parsedDate.getTime()).toInstant().toEpochMilli()
}
