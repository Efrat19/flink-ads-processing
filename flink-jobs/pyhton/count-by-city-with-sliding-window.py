################################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
################################################################################
import logging
import sys
import argparse
import json
from datetime import datetime
# import collections    
from pyflink.common.time import Instant
from pyflink.datastream import StreamExecutionEnvironment

from pyflink.table import ( StreamTableEnvironment, DataTypes, TableDescriptor,
                           Schema, DataTypes, FormatDescriptor)
from pyflink.table.expressions import col
from pyflink.common import Types
from pyflink.table.expressions import lit, col
from pyflink.table.window import Slide

WIDNOW_SIZE_SEC = 5
WIDNOW_SLIDE_SEC = 2
WATERMARK_SEC = 3


def get_input_data(input_file):
    data = []
    with open(input_file) as f:
        for line in f:
            row = json.loads(line)
            data.append((
                int(datetime.fromisoformat(row['scraped_time']).timestamp()*1000),
                row['ad_id'],
                row['city']))
    return data

def process_json_data(input,output):
    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_parallelism(1)
    t_env = StreamTableEnvironment.create(stream_execution_environment=env)
    
    # define the source
    ds = env.from_collection(
        collection=get_input_data(input),
        type_info=Types.ROW([Types.LONG(), Types.STRING(), Types.STRING()]))

    table = t_env.from_data_stream(
        ds,
        Schema.new_builder()
            .column_by_expression("ts", "TO_TIMESTAMP(FROM_UNIXTIME(f0/1000))")
            .column('f1', DataTypes.STRING())
            .column('f2', DataTypes.STRING())
            .watermark("ts", f"ts - INTERVAL '{WATERMARK_SEC}' SECOND")
            .build()
    ).alias("ts", "ad_id", "city")

    t_env.create_temporary_table(
        'sink',
        TableDescriptor.for_connector('filesystem')
            .schema(Schema.new_builder()
                .column('city', DataTypes.STRING())
                .column('num_ads', DataTypes.BIGINT())
                .column('w_start', DataTypes.TIMESTAMP_LTZ())
                .column('w_end', DataTypes.TIMESTAMP_LTZ())
                .build())
            .option('path', output)
            .format(FormatDescriptor.for_format('canal-json')
                    .build())
            .build())

    # execute
    table.window(Slide.over(lit(WIDNOW_SIZE_SEC).seconds).every(lit(WIDNOW_SLIDE_SEC).seconds).on(col("ts")).alias("w"))\
         .group_by(col('city'), col('w') ) \
         .select(col('city'),col("ad_id").count.alias("num_ads"), col("w").start, col("w").end) \
         .filter(col('city') != 'null') \
         .execute_insert('sink') 
         

if __name__ == '__main__':
    logging.basicConfig(stream=sys.stdout, level=logging.INFO, format="%(message)s")
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--output',
        dest='output',
        required=False,
        help='Output file to write results to.')
    parser.add_argument(
        '--input',
        dest='input',
        required=False,
        help='input file to read data from.')

    argv = sys.argv[1:]
    known_args, _ = parser.parse_known_args(argv)

    process_json_data(known_args.input,known_args.output)
