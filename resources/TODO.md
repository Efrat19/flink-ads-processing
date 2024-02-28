todo:
dockerize submitter
ui
neo4j
use time when the ad was created instead pf scraping time
topk cities https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/windows/#consecutive-windowed-operations

running pyflink:
virtualenv venv
source venv/bin/activate
pip install apache-flink pyflink
start-cluster.sh   
flink run \
      --jobmanager localhost:8081 \
      -pyclientexec /Users/efrat/Documents/flink-ads-processing/venv/bin/python3 \
      -pyexec /Users/efrat/Documents/flink-ads-processing/venv/bin/python3 \
      -py /Users/efrat/Documents/flink-ads-processing/flink-jobs/pyhton/count-by-city-with-session-window.py --output /Users/efrat/Documents/flink-ads-processing/output --input /Users/efrat/Documents/flink-ads-processing/crawler-output/items.jsonl

debug kraft:
docker exec -ti broker /usr/bin/kafka-topics --list  --bootstrap-server broker:19092 
docker exec -ti broker /usr/bin/kafka-console-consumer --bootstrap-server broker:19092 --topic scraped-ads --from-beginning

refs:
https://github.com/katyagorshkova/kafka-kraft
https://github.com/dfdeshom/scrapy-kafka
https://github.com/valeriouberti/flink-with-kotlin
https://stackoverflow.com/questions/41928803/how-to-parse-json-in-kotlin
https://github.com/hoptical/grafana-kafka-datasource


readings:
https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/windows/
https://nightlies.apache.org/flink/flink-docs-master/docs/ops/state/checkpointing_under_backpressure/


useful:
https://stackoverflow.com/questions/68026765/pyflink-table-api-streaming-group-window
https://mkyong.com/maven/how-to-create-a-jar-file-with-maven/


create jar:
mvn package -Dmaven.test.skip
list classes:
jar tvf jarfile.jar
curl -X POST -H "Expect:" -F "jarfile=@/Users/efrat/Documents/flink-ads-processing/flink-jobs/kotlin/jobAsIs/target/job-as-is-1.0-SNAPSHOT.jar" http://localhost:8081/jars/upload
curl -X GET http://localhost:8081/jars 

curl -X POST http://localhost:8081/jars/138c6f4c-0e5b-49ad-a21d-677a8a696935_job-as-is-1.0-SNAPSHOT.jar/run      



ui:
1. table of cities and ads scraped -> sum of gbc tumbling windows
table: sum(sum_over_time({topic="scraped-ads-gbc"} | unwrap num_ads | __error__=`` [1h])) by (city)

2. grph showing ads scraped over the last minute, per city -> last of gbc sliding window
ts: last_over_time({topic="scraped-ads-gbc"} | unwrap num_ads | __error__=`` [1h])) by (city)

2. graph showing ads posted per year per city -> sum of tumbling windows of rolling group city and year of date posted
bar chart: sum(sum_over_time({topic="scraped-ads-gbc"} | unwrap num_ads | __error__=`` [1h])) by (city,year)

final result:
this is a data pipeline where ads are collected, ingested and streamed to grafana for visualization
1. Scrapy crawler scrapes real estate ads from a billboard website
2. The ads are streamed to a kraft "scraped-ads" topic
3. kotlin jobs are 

2. add year agg + tmbl
2. dockerize submitter
3. fix dashboard
