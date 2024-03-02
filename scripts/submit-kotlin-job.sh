JOB=$1
cd flink-jobs/kotlin/$JOB
mvn package -Dmaven.test.skip 
cd ../../..
JAR_PATH=$(pwd)/flink-jobs/kotlin/$JOB/target/$JOB-1.0-SNAPSHOT.jar
echo "JAR_PATH: $JAR_PATH"

JAR_ID=$(curl -X POST -H "Expect:" -F "jarfile=@$JAR_PATH" http://localhost:8081/jars/upload | jq -r '.filename | split("/") | .[4]')
echo "JAR_ID: $JAR_ID"

curl -X POST http://localhost:8081/jars/$JAR_ID/run    

open "http://localhost:8081/#/job/completed"