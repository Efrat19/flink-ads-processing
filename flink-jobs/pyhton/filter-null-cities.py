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

from pyflink.table import (EnvironmentSettings, TableEnvironment, DataTypes, TableDescriptor,
                           Schema, DataTypes, FormatDescriptor)
from pyflink.table.expressions import col

def get_input_data(input_file):
    data = []
    with open(input_file) as f:
        for line in f:
            data.append(json.loads(line))
    return data

def process_json_data(input,output):
    t_env = TableEnvironment.create(EnvironmentSettings.in_streaming_mode())

    # define the source
    table = t_env.from_elements(
        elements=get_input_data(input),
        schema=['ad_id', 'city'])

    t_env.create_temporary_table(
        'sink',
        TableDescriptor.for_connector('filesystem')
                        .schema(Schema.new_builder()
                                .column('ad_id', DataTypes.STRING())
                                .column('city', DataTypes.STRING())
                                .build())
                        .option('path', output)
                        .format(FormatDescriptor.for_format('canal-json')
                                .build())
                        .build())

    # execute
    table.filter(col('city') != 'null').execute_insert('sink') \
         .wait()
    # remove .wait if submitting to a remote cluster, refer to
    # https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/python/faq/#wait-for-jobs-to-finish-when-executing-jobs-in-mini-cluster
    # for more details


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
