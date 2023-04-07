/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.engine.compaction;

import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.constant.TestConstant;
import org.apache.iotdb.db.engine.compaction.utils.CompactionConfigRestorer;
import org.apache.iotdb.db.engine.storagegroup.TsFileManager;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.engine.storagegroup.TsFileResourceStatus;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.query.control.FileReaderManager;
import org.apache.iotdb.db.query.reader.series.SeriesRawDataBatchReader;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.fileSystem.FSFactoryProducer;
import org.apache.iotdb.tsfile.read.TimeValuePair;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.reader.IBatchReader;
import org.apache.iotdb.tsfile.utils.FilePathUtils;
import org.apache.iotdb.tsfile.utils.TsFileGeneratorUtils;
import org.apache.iotdb.tsfile.utils.TsPrimitiveType;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.iotdb.tsfile.common.constant.TsFileConstant.PATH_SEPARATOR;
import static org.junit.Assert.fail;

public class AbstractCompactionTest {
  protected int seqFileNum = 5;
  protected int unseqFileNum = 0;
  protected List<TsFileResource> seqResources = new ArrayList<>();
  protected List<TsFileResource> unseqResources = new ArrayList<>();

  protected TsFileManager tsFileManager =
      new TsFileManager(TsFileGeneratorUtils.testStorageGroup, "0", STORAGE_GROUP_DIR.getPath());
  private int chunkGroupSize = 0;
  private int pageSize = 0;
  protected String COMPACTION_TEST_SG = TsFileGeneratorUtils.testStorageGroup;
  private TSDataType dataType;

  private static final long oldTargetChunkSize =
      IoTDBDescriptor.getInstance().getConfig().getTargetChunkSize();
  private static final int oldChunkGroupSize =
      TSFileDescriptor.getInstance().getConfig().getGroupSizeInByte();
  private static final int oldPagePointSize =
      TSFileDescriptor.getInstance().getConfig().getMaxNumberOfPointsInPage();

  private static final int oldMaxCrossCompactionFileNum =
      IoTDBDescriptor.getInstance().getConfig().getMaxCrossCompactionCandidateFileNum();

  protected static File STORAGE_GROUP_DIR =
      new File(
          TestConstant.BASE_OUTPUT_PATH
              + "data"
              + File.separator
              + "sequence"
              + File.separator
              + "root.compactionTest");
  protected static File SEQ_DIRS =
      new File(
          TestConstant.BASE_OUTPUT_PATH
              + "data"
              + File.separator
              + "sequence"
              + File.separator
              + "root.compactionTest"
              + File.separator
              + "0"
              + File.separator
              + "0");
  protected static File UNSEQ_DIRS =
      new File(
          TestConstant.BASE_OUTPUT_PATH
              + "data"
              + File.separator
              + "unsequence"
              + File.separator
              + "root.compactionTest"
              + File.separator
              + "0"
              + File.separator
              + "0");

  private int fileVersion = 0;

  public void setUp() throws IOException, WriteProcessException, MetadataException {
    if (!SEQ_DIRS.exists()) {
      Assert.assertTrue(SEQ_DIRS.mkdirs());
    }
    if (!UNSEQ_DIRS.exists()) {
      Assert.assertTrue(UNSEQ_DIRS.mkdirs());
    }
    dataType = TSDataType.INT64;
    EnvironmentUtils.envSetUp();
    IoTDB.metaManager.init();
  }

  /**
   * @param fileNum the number of file
   * @param deviceNum device number in each file
   * @param measurementNum measurement number in each device of each file
   * @param pointNum data point number of each timeseries in each file
   * @param startTime start time of each timeseries
   * @param startValue start value of each timeseries
   * @param timeInterval time interval of each timeseries between files
   * @param valueInterval value interval of each timeseries between files
   * @param isAlign when it is true, it will create mix tsfile which contains aligned and nonAligned
   *     timeseries
   * @param isSeq
   * @throws IOException
   * @throws WriteProcessException
   * @throws MetadataException
   */
  protected void createFiles(
      int fileNum,
      int deviceNum,
      int measurementNum,
      int pointNum,
      int startTime,
      int startValue,
      int timeInterval,
      int valueInterval,
      boolean isAlign,
      boolean isSeq)
      throws IOException, WriteProcessException, MetadataException {
    for (int i = 0; i < fileNum; i++) {
      String fileName =
          System.currentTimeMillis()
              + FilePathUtils.FILE_NAME_SEPARATOR
              + fileVersion++
              + "-0-0.tsfile";
      String filePath;
      if (isSeq) {
        filePath = SEQ_DIRS.getPath() + File.separator + fileName;
      } else {
        filePath = UNSEQ_DIRS.getPath() + File.separator + fileName;
      }
      File file;
      if (isAlign) {
        file =
            TsFileGeneratorUtils.generateAlignedTsFile(
                filePath,
                deviceNum,
                measurementNum,
                pointNum,
                startTime + pointNum * i + timeInterval * i,
                startValue + pointNum * i + valueInterval * i,
                chunkGroupSize,
                pageSize);
      } else {
        file =
            TsFileGeneratorUtils.generateNonAlignedTsFile(
                filePath,
                deviceNum,
                measurementNum,
                pointNum,
                startTime + pointNum * i + timeInterval * i,
                startValue + pointNum * i + valueInterval * i,
                chunkGroupSize,
                pageSize);
      }
      addResource(
          file,
          deviceNum,
          startTime + pointNum * i + timeInterval * i,
          startTime + pointNum * i + timeInterval * i + pointNum - 1,
          isAlign,
          isSeq);
      // sleep a moment to avoid generating files with same timestamp
      try {
        Thread.sleep(10);
      } catch (Exception e) {

      }
    }
  }

  /**
   * @param fileNum the number of file
   * @param deviceIndexes device index in each file
   * @param measurementIndexes measurement index in each device of each file
   * @param pointNum data point number of each timeseries in each file
   * @param startTime start time of each timeseries
   * @param timeInterval time interval of each timeseries between files
   * @param isAlign when it is true, it will create mix tsfile which contains aligned and nonAligned
   *     timeseries
   * @param isSeq
   */
  protected void createFilesWithTextValue(
      int fileNum,
      List<Integer> deviceIndexes,
      List<Integer> measurementIndexes,
      int pointNum,
      int startTime,
      int timeInterval,
      boolean isAlign,
      boolean isSeq)
      throws IOException, WriteProcessException {

    for (int i = 0; i < fileNum; i++) {
      String fileName =
          System.currentTimeMillis()
              + FilePathUtils.FILE_NAME_SEPARATOR
              + fileVersion++
              + "-0-0.tsfile";
      String filePath;
      if (isSeq) {
        filePath = SEQ_DIRS.getPath() + File.separator + fileName;
      } else {
        filePath = UNSEQ_DIRS.getPath() + File.separator + fileName;
      }
      File file;
      if (isAlign) {
        file =
            TsFileGeneratorUtils.generateAlignedTsFileWithTextValues(
                filePath,
                deviceIndexes,
                measurementIndexes,
                pointNum,
                startTime + pointNum * i + timeInterval * i,
                chunkGroupSize,
                pageSize);
      } else {
        file =
            TsFileGeneratorUtils.generateNonAlignedTsFileWithTextValues(
                filePath,
                deviceIndexes,
                measurementIndexes,
                pointNum,
                startTime + pointNum * i + timeInterval * i,
                chunkGroupSize,
                pageSize);
      }
      // add resource
      TsFileResource resource = new TsFileResource(file);
      int deviceStartindex = isAlign ? TsFileGeneratorUtils.getAlignDeviceOffset() : 0;
      for (int j = 0; j < deviceIndexes.size(); j++) {
        resource.updateStartTime(
            COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + (deviceIndexes.get(j) + deviceStartindex),
            startTime + pointNum * i + timeInterval * i);
        resource.updateEndTime(
            COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + (deviceIndexes.get(j) + deviceStartindex),
            startTime + pointNum * i + timeInterval * i + pointNum - 1);
      }
      resource.updatePlanIndexes(fileVersion);
      resource.setStatus(TsFileResourceStatus.CLOSED);
      resource.serialize();
      if (isSeq) {
        seqResources.add(resource);
      } else {
        unseqResources.add(resource);
      }
    }
    // sleep a few milliseconds to avoid generating files with same timestamps
    try {
      Thread.sleep(10);
    } catch (Exception e) {

    }
  }

  private void addResource(
      File file, int deviceNum, long startTime, long endTime, boolean isAlign, boolean isSeq)
      throws IOException {
    TsFileResource resource = new TsFileResource(file);
    int deviceStartindex = isAlign ? TsFileGeneratorUtils.getAlignDeviceOffset() : 0;

    for (int i = deviceStartindex; i < deviceStartindex + deviceNum; i++) {
      resource.updateStartTime(COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + i, startTime);
      resource.updateEndTime(COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + i, endTime);
    }

    resource.updatePlanIndexes(fileVersion);
    resource.setStatus(TsFileResourceStatus.CLOSED);
    // resource.setTimeIndexType((byte) 0);
    resource.serialize();
    if (isSeq) {
      seqResources.add(resource);
    } else {
      unseqResources.add(resource);
    }
  }

  protected void registerTimeseriesInMManger(int deviceNum, int measurementNum, boolean isAligned)
      throws MetadataException {
    for (int i = 0; i < deviceNum; i++) {
      if (isAligned) {
        List<String> measurements = new ArrayList<>();
        List<TSDataType> dataTypes = new ArrayList<>();
        List<TSEncoding> encodings = new ArrayList<>();
        List<CompressionType> compressionTypes = new ArrayList<>();
        for (int j = 0; j < measurementNum; j++) {
          measurements.add("s" + j);
          dataTypes.add(dataType);
          encodings.add(TSEncoding.PLAIN);
          compressionTypes.add(CompressionType.UNCOMPRESSED);
          IoTDB.metaManager.createTimeseries(
              new PartialPath(COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + i, "s" + j),
              dataType,
              TSEncoding.PLAIN,
              CompressionType.UNCOMPRESSED,
              Collections.emptyMap());
        }
        IoTDB.metaManager.createAlignedTimeSeries(
            new PartialPath(COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + (i + 10000)),
            measurements,
            dataTypes,
            encodings,
            compressionTypes);
      } else {
        for (int j = 0; j < measurementNum; j++) {
          IoTDB.metaManager.createTimeseries(
              new PartialPath(COMPACTION_TEST_SG + PATH_SEPARATOR + "d" + i, "s" + j),
              dataType,
              TSEncoding.PLAIN,
              CompressionType.UNCOMPRESSED,
              Collections.emptyMap());
        }
      }
    }
  }

  protected Map<PartialPath, List<TimeValuePair>> readSourceFiles(
      List<PartialPath> timeseriesPaths, List<TSDataType> dataTypes) throws IOException {
    Map<PartialPath, List<TimeValuePair>> sourceData = new LinkedHashMap<>();
    for (PartialPath path : timeseriesPaths) {
      List<TimeValuePair> dataList = new ArrayList<>();
      sourceData.put(path, dataList);
      IBatchReader tsFilesReader =
          new SeriesRawDataBatchReader(
              path,
              path.getSeriesType(),
              EnvironmentUtils.TEST_QUERY_CONTEXT,
              tsFileManager.getTsFileList(true),
              tsFileManager.getTsFileList(false),
              null,
              null,
              true);
      while (tsFilesReader.hasNextBatch()) {
        BatchData batchData = tsFilesReader.nextBatch();
        while (batchData.hasCurrent()) {
          dataList.add(
              new TimeValuePair(
                  batchData.currentTime(),
                  TsPrimitiveType.getByType(path.getSeriesType(), batchData.currentValue())));
          batchData.next();
        }
      }
    }
    return sourceData;
  }

  protected void validateTargetDatas(
      Map<PartialPath, List<TimeValuePair>> sourceDatas, List<TSDataType> dataTypes)
      throws IOException {
    for (Map.Entry<PartialPath, List<TimeValuePair>> entry : sourceDatas.entrySet()) {
      IBatchReader tsFilesReader =
          new SeriesRawDataBatchReader(
              entry.getKey(),
              entry.getKey().getSeriesType(),
              EnvironmentUtils.TEST_QUERY_CONTEXT,
              tsFileManager.getTsFileList(true),
              tsFileManager.getTsFileList(false),
              null,
              null,
              true);
      List<TimeValuePair> timeseriesData = entry.getValue();
      while (tsFilesReader.hasNextBatch()) {
        BatchData batchData = tsFilesReader.nextBatch();
        while (batchData.hasCurrent()) {
          TimeValuePair data = timeseriesData.remove(0);
          Assert.assertEquals(data.getTimestamp(), batchData.currentTime());
          Assert.assertEquals(
              data.getValue(),
              TsPrimitiveType.getByType(entry.getKey().getSeriesType(), batchData.currentValue()));
          batchData.next();
        }
      }
      if (timeseriesData.size() > 0) {
        // there are still data points left, which are not in the target file. Lost the data after
        // compaction.
        fail();
      }
    }
  }

  protected void deleteTimeseriesInMManager(List<String> timeseries) throws MetadataException {
    for (String path : timeseries) {
      IoTDB.metaManager.deleteTimeseries(new PartialPath(path));
    }
  }

  public void tearDown() throws IOException, StorageEngineException {
    new CompactionConfigRestorer().restoreCompactionConfig();
    removeFiles();
    seqResources.clear();
    unseqResources.clear();
    IoTDB.metaManager.clear();
    IoTDBDescriptor.getInstance().getConfig().setTargetChunkSize(oldTargetChunkSize);
    IoTDBDescriptor.getInstance()
        .getConfig()
        .setMaxCrossCompactionCandidateFileNum(oldMaxCrossCompactionFileNum);
    TSFileDescriptor.getInstance().getConfig().setGroupSizeInByte(oldChunkGroupSize);
    TSFileDescriptor.getInstance().getConfig().setMaxNumberOfPointsInPage(oldPagePointSize);
    EnvironmentUtils.cleanEnv();
    if (SEQ_DIRS.exists()) {
      FileUtils.deleteDirectory(SEQ_DIRS);
    }
    if (UNSEQ_DIRS.exists()) {
      FileUtils.deleteDirectory(UNSEQ_DIRS);
    }
  }

  private void removeFiles() throws IOException {
    FileReaderManager.getInstance().closeAndRemoveAllOpenedReaders();
    for (TsFileResource tsFileResource : seqResources) {
      if (tsFileResource.getTsFile().exists()) {
        tsFileResource.remove();
      }
    }
    for (TsFileResource tsFileResource : unseqResources) {
      if (tsFileResource.getTsFile().exists()) {
        tsFileResource.remove();
      }
    }
    File[] files = FSFactoryProducer.getFSFactory().listFilesBySuffix("target", ".tsfile");
    for (File file : files) {
      file.delete();
    }
    File[] resourceFiles =
        FSFactoryProducer.getFSFactory().listFilesBySuffix("target", ".resource");
    for (File resourceFile : resourceFiles) {
      resourceFile.delete();
    }
  }

  protected void setDataType(TSDataType dataType) {
    this.dataType = dataType;
  }
}
