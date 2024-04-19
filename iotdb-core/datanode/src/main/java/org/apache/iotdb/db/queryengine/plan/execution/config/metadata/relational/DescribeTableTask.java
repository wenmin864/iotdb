/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.plan.execution.config.metadata.relational;

import org.apache.iotdb.commons.schema.table.TsTable;
import org.apache.iotdb.commons.schema.table.column.TsTableColumnSchema;
import org.apache.iotdb.db.queryengine.common.header.ColumnHeader;
import org.apache.iotdb.db.queryengine.common.header.ColumnHeaderConstant;
import org.apache.iotdb.db.queryengine.common.header.DatasetHeader;
import org.apache.iotdb.db.queryengine.common.header.DatasetHeaderFactory;
import org.apache.iotdb.db.queryengine.plan.execution.config.ConfigTaskResult;
import org.apache.iotdb.db.queryengine.plan.execution.config.IConfigTask;
import org.apache.iotdb.db.queryengine.plan.execution.config.executor.IConfigTaskExecutor;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.block.TsBlockBuilder;
import org.apache.iotdb.tsfile.utils.Binary;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.stream.Collectors;

public class DescribeTableTask implements IConfigTask {

  private final String database;

  private final String tableName;

  public DescribeTableTask(String database, String tableName) {
    this.database = database;
    this.tableName = tableName;
  }

  @Override
  public ListenableFuture<ConfigTaskResult> execute(IConfigTaskExecutor configTaskExecutor)
      throws InterruptedException {
    return configTaskExecutor.describeTable(database, tableName);
  }

  public static void buildTsBlock(TsTable table, SettableFuture<ConfigTaskResult> future) {
    List<TSDataType> outputDataTypes =
        ColumnHeaderConstant.describeTableColumnHeaders.stream()
            .map(ColumnHeader::getColumnType)
            .collect(Collectors.toList());

    TsBlockBuilder builder = new TsBlockBuilder(outputDataTypes);
    for (TsTableColumnSchema columnSchema : table.getColumnList()) {
      builder.getTimeColumnBuilder().writeLong(0L);
      builder
          .getColumnBuilder(0)
          .writeBinary(new Binary(columnSchema.getColumnName(), TSFileConfig.STRING_CHARSET));
      builder
          .getColumnBuilder(1)
          .writeBinary(new Binary(columnSchema.getDataType().name(), TSFileConfig.STRING_CHARSET));
      builder
          .getColumnBuilder(2)
          .writeBinary(
              new Binary(columnSchema.getColumnCategory().name(), TSFileConfig.STRING_CHARSET));
      builder.declarePosition();
    }

    DatasetHeader datasetHeader = DatasetHeaderFactory.getDescribeTableHeader();
    future.set(new ConfigTaskResult(TSStatusCode.SUCCESS_STATUS, builder.build(), datasetHeader));
  }
}