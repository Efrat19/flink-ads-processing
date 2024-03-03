package efrat19

import java.time.Duration
import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.Expressions.col
import org.apache.flink.table.api.Expressions.lit
import org.apache.flink.table.api.Slide
import org.apache.flink.table.api.TableEnvironment

const val SLIDING_IN_TOPIC = "scraped-ads"
const val SLIDING_OUT_TOPIC = "ads-by-city-sld-win-test3"
const val SLIDING_K_HOST = "broker:19092"
const val SLIDING_GROUP_ID = "flink-job1"

fun main() {
        val settings = EnvironmentSettings.newInstance().inStreamingMode().build()
        val tableEnv = TableEnvironment.create(settings)
        val sourceDDL =
                        "CREATE TABLE source (" +
                                        "  ad_id STRING," + // AS JSON_VALUE('$', '$.ad_id') ," +
                                        "  city STRING," + // AS JSON_VALUE('$', '$.city') ," +
                                        "  posted_date STRING," + // AS JSON_VALUE('$',
                                        // '$.posted_date') ," +
                                        "  scraped_time STRING," + // AS JSON_VALUE('$',
                                        // '$.scraped_time') " +
                                        "  spider STRING," + // AS JSON_VALUE('$', '$.scraped_time')
                                        // " +
                                        "  `ts` AS to_timestamp(scraped_time)," +
                                        "  WATERMARK FOR `ts` AS `ts` - INTERVAL '5' SECOND" +
                                        ") WITH (" +
                                        "  'connector' = 'kafka'," +
                                        "  'topic' = 'scraped-ads'," +
                                        "  'properties.bootstrap.servers' = 'broker:19092'," +
                                        "  'scan.startup.mode' = 'earliest-offset'," +
                                        "  'format' = 'json'," +
                                        // "  'json.fail-on-missing-field' = 'false'," +
                                        "  'json.encode.decimal-as-plain-number' = 'false'," +
                                        "  'json.ignore-parse-errors' = 'true'" +
                                        // "  'value.fields-include' = 'ALL'" +
                                        ")"
        val sinkDDL =
                        "CREATE TABLE sink (" +
                                        "  city STRING NOT NULL," +
                                        "  num_ads BIGINT," +
                                        "  window_start TIMESTAMP_LTZ(3)," +
                                        "  window_end TIMESTAMP_LTZ(3)," +
                                        "  PRIMARY KEY (city) NOT ENFORCED" +
                                        ") WITH (" +
                                        "  'connector' = 'upsert-kafka'," +
                                        "  'topic' = 'ads-by-city-sld-win-test3'," +
                                        "  'properties.bootstrap.servers' = 'broker:19092'," +
                                        "  'value.format' = 'json'," +
                                        "  'key.format' = 'json'" +
                                        ")"
        val aggDDL = "INSERT INTO sink " +
                                        "SELECT `city`, COUNT(ad_id) AS num_ads, window_start, window_end " +
                                        "FROM TABLE(HOP(TABLE source, DESCRIPTOR(ts), INTERVAL '10' SECONDS, INTERVAL '60' SECONDS)) " +
                                        "WHERE `city` <> 'null' " +
                                        "GROUP BY `city`, window_start, window_end"
        tableEnv.executeSql(sourceDDL)
        tableEnv.executeSql(sinkDDL)

        // var scrapedAdsTable = tableEnv.sqlQuery("SELECT * FROM source")
        // val size = lit(Duration.ofSeconds(60))
        // val slide = lit(Duration.ofSeconds(10))
        // scrapedAdsTable.window(Slide.over(size).every(slide).on(col("ts")).`as`("w"))
        //                 .groupBy(col("city"), col("w"))
        //                 .select(
        //                                 col("city"),
        //                                 col("ad_id").count(),
        //                                 col("w").end(),
        //                                 col("w").start(),
        //                 )
        //                 .filter(col("city").isNotEqual("null"))
        //                 .insertInto("sink")
        //                 .execute()
        //                 .print()

        tableEnv.executeSql(aggDDL)
}
