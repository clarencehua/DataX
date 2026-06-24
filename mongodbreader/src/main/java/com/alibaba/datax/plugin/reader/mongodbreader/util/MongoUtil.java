package com.alibaba.datax.plugin.reader.mongodbreader.util;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.reader.mongodbreader.KeyConstant;
import com.alibaba.datax.plugin.reader.mongodbreader.MongoDBReaderErrorCode;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public class MongoUtil {

    public static MongoClient initMongoClient(Configuration conf) {

        List<Object> addressList = conf.getList(KeyConstant.MONGO_ADDRESS);
        if(addressList == null || addressList.size() <= 0) {
            throw DataXException.asDataXException(MongoDBReaderErrorCode.ILLEGAL_VALUE,"不合法参数");
        }
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyToClusterSettings(builder -> builder.hosts(parseServerAddress(addressList)))
                    .build();
            return MongoClients.create(settings);
        } catch (NumberFormatException e) {
            throw DataXException.asDataXException(MongoDBReaderErrorCode.ILLEGAL_VALUE,"不合法参数");
        } catch (Exception e) {
            throw DataXException.asDataXException(MongoDBReaderErrorCode.UNEXCEPT_EXCEPTION,"未知异常");
        }
    }

    public static MongoClient initCredentialMongoClient(Configuration conf, String userName, String password, String database) {

        List<Object> addressList = conf.getList(KeyConstant.MONGO_ADDRESS);
        if(!isHostPortPattern(addressList)) {
            throw DataXException.asDataXException(MongoDBReaderErrorCode.ILLEGAL_VALUE,"不合法参数");
        }
        try {
            MongoCredential credential = MongoCredential.createCredential(userName, database, password.toCharArray());
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyToClusterSettings(builder -> builder.hosts(parseServerAddress(addressList)))
                    .credential(credential)
                    .build();
            return MongoClients.create(settings);
        } catch (NumberFormatException e) {
            throw DataXException.asDataXException(MongoDBReaderErrorCode.ILLEGAL_VALUE,"不合法参数");
        } catch (Exception e) {
            throw DataXException.asDataXException(MongoDBReaderErrorCode.UNEXCEPT_EXCEPTION,"未知异常");
        }
    }

    private static boolean isHostPortPattern(List<Object> addressList) {
        for(Object address : addressList) {
            String regex = "(\\S+):([0-9]+)";
            if(!((String)address).matches(regex)) {
                return false;
            }
        }
        return true;
    }

    private static List<ServerAddress> parseServerAddress(List<Object> rawAddressList) {
        List<ServerAddress> addressList = new ArrayList<ServerAddress>();
        for(Object address : rawAddressList) {
            String[] tempAddress = ((String)address).split(":");
            ServerAddress sa = new ServerAddress(tempAddress[0],Integer.valueOf(tempAddress[1]));
            addressList.add(sa);
        }
        return addressList;
    }
}