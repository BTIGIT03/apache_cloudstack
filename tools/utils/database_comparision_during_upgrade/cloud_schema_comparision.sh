#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

dbhost=$1
dbuser=$2
dbpwd=$3
path=./base_data
path1=./data_after_upgrade
rm -rf $path1
mkdir $path1

mysql -u $dbuser -p$dbpwd -h $dbhost -e "show tables from cloud" > $path1/tables_upgrade

# to check if number of tables and table name differs

diff $path/tables $path1/tables_upgrade > tables_diff_file
if [ -s tables_diff_file ]
then
        echo "cloud table differs between upgraded and fresh install "
        cat tables_diff_file
        # do something as file has data
else
        echo "cloud tables are identical in upgraded and fresh install"
        rm -rf tables_diff_file
        # do something as file is empty

fi



for tablename in `cat $path1/tables_upgrade`
do
        if [ $tablename != 'Tables_in_cloud' ]
        then
                mysql -u $dbuser -p$dbpwd -h $dbhost -e "describe cloud.$tablename" > $path1/upgradedschema
		cat $path/$tablename >  $tablename.diff
                cat $path1/upgradedschema >> $tablename.diff
                sort $tablename.diff > $tablename.sort
                uniq -u $tablename.sort > $tablename.uniq

                if [ -s $tablename.uniq ]
                then
                        echo $tablename  "table schema is different."
                        cat $path1/upgradedschema > $tablename
                        rm -rf $tablename.diff $tablename.sort

                        # do something as file has data
                else

                        rm -rf $tablename.diff $tablename.sort $tablename.uniq
                fi


        fi
done




rm -rf $path1
