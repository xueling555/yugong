package com.taobao.yugong.translator.modules.pass;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.taobao.yugong.common.db.meta.ColumnMeta;
import com.taobao.yugong.common.db.meta.ColumnValue;
import com.taobao.yugong.common.model.record.IncrementOpType;
import com.taobao.yugong.common.model.record.IncrementRecord;
import com.taobao.yugong.common.model.record.Record;
import com.taobao.yugong.translator.RouteMapType;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.BeanUtils;

import java.math.BigInteger;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

public class UserRouterMapUtil {

  @VisibleForTesting
  public static Record buildRouteMapRecord(RouteMapType type, String input, int userId) {
    ColumnMeta idColumn = new ColumnMeta("Id", Types.INTEGER);
    ColumnMeta contentColumn = new ColumnMeta("Content", Types.VARCHAR);
    ColumnMeta typeColumn = new ColumnMeta("Type", Types.INTEGER);
    ColumnMeta userIdColumn = new ColumnMeta("User_Id", Types.INTEGER);
    ColumnMeta createdColumn = new ColumnMeta("Created", Types.DATE);

    List<ColumnValue> primaryKeys = Lists.newArrayList();
    List<ColumnValue> columns = Lists.newArrayList(
        new ColumnValue(contentColumn, input),
        new ColumnValue(typeColumn, type.getValue()),
        new ColumnValue(userIdColumn, userId),
        new ColumnValue(createdColumn, new Date())
    );
    Record record = new Record();
    record.setTableName("LoginRouteMap_" + calculateShardingKey(input));
    record.setPrimaryKeys(primaryKeys);
    record.setColumns(columns);
    return record;
  }
  

  @VisibleForTesting
  public static int calculateShardingKey(String input) {
    String sha1 = DigestUtils.sha1Hex(input);
    BigInteger sharding = new BigInteger(
        sha1.substring(0, sha1.length() < 16 ? sha1.length() : 16), 16);
    return sharding.mod(BigInteger.valueOf(64)).intValue();
  }

  public static List<Record> buildRouteMapRecordCdc(IncrementOpType opType, RouteMapType type,
      String input, int userId) {
    List<Record> records = Lists.newArrayList();
    Record record = buildRouteMapRecord(type, input, userId);
    IncrementRecord incrementRecord = new IncrementRecord();
    BeanUtils.copyProperties(record, incrementRecord);
    
    if (opType == IncrementOpType.I) {
      incrementRecord.setOpType(IncrementOpType.I);
      records.add(incrementRecord);
    } else if (opType == IncrementOpType.U) {
      // delete all previous data
      IntStream.range(0, 64).forEach(x -> {
        IncrementRecord deleteRecord = new IncrementRecord();
        BeanUtils.copyProperties(buildRouteMapRecord(type, input, userId), deleteRecord);
        deleteRecord.setOpType(IncrementOpType.D);
        deleteRecord.setTableName("LoginRouteMap_" + x);
        deleteRecord.getPrimaryKeys().add(deleteRecord.getColumnByName("Content")); // fix no pk
        deleteRecord.getPrimaryKeys().add(deleteRecord.getColumnByName("Type")); // fix no pk
        deleteRecord.getPrimaryKeys().add(deleteRecord.getColumnByName("User_Id")); // fix no pk
        records.add(deleteRecord);
      });
      // add new data
      incrementRecord.setOpType(IncrementOpType.I);
      incrementRecord.getPrimaryKeys().add(incrementRecord.getColumnByName("Content")); // fix no pk
      incrementRecord.getPrimaryKeys().add(incrementRecord.getColumnByName("Type")); // fix no pk
      incrementRecord.getPrimaryKeys().add(incrementRecord.getColumnByName("User_Id")); // fix no pk
      records.add(incrementRecord);
    } else if (opType == IncrementOpType.D) {
      incrementRecord.setOpType(IncrementOpType.D);
      incrementRecord.getPrimaryKeys().add(incrementRecord.getColumnByName("Content")); // fix no pk
      incrementRecord.getPrimaryKeys().add(incrementRecord.getColumnByName("Type")); // fix no pk
      incrementRecord.getPrimaryKeys().add(incrementRecord.getColumnByName("User_Id")); // fix no pk
      records.add(incrementRecord);
    }
    return records;
  }
}