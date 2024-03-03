package efrat19

import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.TableEnvironment

const val SLIDING_IN_TOPIC = "scraped-ads"
const val SLIDING_OUT_TOPIC = "ads-by-city-sld-win"
const val SLIDING_K_HOST = "broker:19092"
const val SLIDING_GROUP_ID = "ads-by-city-sld-win"

fun main() {
        val settings = EnvironmentSettings.newInstance().inStreamingMode().build()
        val tableEnv = TableEnvironment.create(settings)
        val sourceDDL =
                        "CREATE TABLE source (" +
                                        "  ad_id STRING," +
                                        "  city STRING," +
                                        "  posted_date STRING," +
                                        "  scraped_time STRING," +
                                        "  spider STRING," +
                                        "  `ts` AS to_timestamp(scraped_time)," +
                                        "  WATERMARK FOR `ts` AS `ts` - INTERVAL '5' SECOND" +
                                        ") WITH (" +
                                        "  'connector' = 'kafka'," +
                                        "  'topic' = '${SLIDING_IN_TOPIC}'," +
                                        "  'properties.bootstrap.servers' = '${SLIDING_K_HOST}'," +
                                        "  'properties.group.id' = '${SLIDING_GROUP_ID}'," +
                                        "  'scan.startup.mode' = 'earliest-offset'," +
                                        "  'format' = 'json'," +
                                        "  'json.encode.decimal-as-plain-number' = 'false'" +
                                        ")"
        val sinkDDL =
                        "CREATE TABLE sink (" +
                                        "  city STRING," +
                                        "  num_ads BIGINT," +
                                        "  window_start TIMESTAMP_LTZ(3)," +
                                        "  window_end TIMESTAMP_LTZ(3)" +
                                        ") WITH (" +
                                        "  'connector' = 'kafka'," +
                                        "  'topic' = '${SLIDING_OUT_TOPIC}'," +
                                        "  'properties.bootstrap.servers' = '${SLIDING_K_HOST}'," +
                                        "  'properties.group.id' = '${SLIDING_GROUP_ID}'," +
                                        "  'format' = 'json'" +
                                        ")"
        val aggDDL =
                        "INSERT INTO sink " +
                                        "SELECT `city`, COUNT(ad_id) AS num_ads, window_start, window_end " +
                                        "FROM TABLE(" +
                                        "HOP(" +
                                        "TABLE source, DESCRIPTOR(ts), INTERVAL '10' SECONDS, INTERVAL '60' SECONDS" +
                                        ")" +
                                        ") " +
                                        "WHERE `city` <> 'null' " +
                                        "GROUP BY `city`, window_start, window_end"
        tableEnv.executeSql(sourceDDL)
        tableEnv.executeSql(sinkDDL)
        tableEnv.executeSql(aggDDL)
}
