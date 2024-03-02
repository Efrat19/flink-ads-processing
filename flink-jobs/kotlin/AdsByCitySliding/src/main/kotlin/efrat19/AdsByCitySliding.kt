package efrat19

import efrat19.ads.Ad
import efrat19.ads.AdDeserializationSchema
import java.sql.Timestamp
import java.text.SimpleDateFormat
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.Expressions.col
import org.apache.flink.table.api.Schema
import org.apache.flink.table.api.TableDescriptor
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment

const val SLIDING_IN_TOPIC = "scraped-ads"
const val SLIDING_OUT_TOPIC = "ads-by-city-and-year-sld-win"
const val SLIDING_K_HOST = "broker:19092"
const val SLIDING_GROUP_ID = "flink-job1"

fun main() {
        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        val tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.inStreamingMode())

        tableEnv.createTable("sink", createKafkaSinkDescriptor().build())
        val source: KafkaSource<Ad> =
                        KafkaSource.builder<Ad>()
                                        .setBootstrapServers(SLIDING_K_HOST)
                                        .setTopics(SLIDING_IN_TOPIC)
                                        .setGroupId(SLIDING_GROUP_ID)
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
                                        .columnByExpression(
                                                        "ts",
                                                        "CAST(scraped_time AS TIMESTAMP(3))"
                                        )
                                        .watermark("ts", "ts - INTERVAL '5' SECOND")
                                        .build()
        val stream: DataStream<Ad> = env.fromSource(source, watermarks, "Kafka Source")
        val scrapedAdsTable = tableEnv.fromDataStream(stream, streamSchema)

        scrapedAdsTable.groupBy(col("city"))
                        .select(
                                        col("city"),
                                        col("ad_id").count(),
                        )
                        .filter(col("city").isNotEqual(""))
                        .insertInto("sink")
                        .execute()

        env.execute("scraped ads aggregation")
}

public fun createKafkaSinkDescriptor(): TableDescriptor.Builder {
        return TableDescriptor.forConnector("upsert-kafka")
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
                                                        .primaryKey("city")
                                                        .build()
                        )
                        .option("key.format", "json")
                        .option("value.format", "json")
                        .option("topic", SLIDING_OUT_TOPIC)
                        .option("properties.bootstrap.servers", SLIDING_K_HOST)
                        .option("properties.group.id", SLIDING_GROUP_ID)
}

fun sqlTimeToEpochMilli(sqlTime: String): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        val parsedDate = dateFormat.parse(sqlTime)
        return java.sql.Timestamp(parsedDate.getTime()).toInstant().toEpochMilli()
}
