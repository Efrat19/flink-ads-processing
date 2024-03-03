FROM flink:1.17-java11

RUN apt-get update
RUN apt-get install jq maven -y

ARG JOB

WORKDIR /app
COPY ./kotlin/$JOB .
RUN mvn package -Dmaven.test.skip 

ENV JOB $JOB
ENV JAR_PATH /app/target/$JOB-1.0-SNAPSHOT.jar

RUN echo "#!/usr/bin/env bash\n\
echo JAR_PATH: $JAR_PATH\n\
export JAR_ID=\$(curl -X POST -H \"Expect:\" -F \"jarfile=@$JAR_PATH\" http://jobmanager:8081/jars/upload | jq -r '.filename | split(\"/\") | last')\n\
echo JAR_ID: \$JAR_ID\n\
curl -X POST http://jobmanager:8081/jars/\$JAR_ID/run\n" >> ./submit-job.sh

RUN chmod +x ./submit-job.sh
RUN cat ./submit-job.sh
CMD ["./submit-job.sh"]