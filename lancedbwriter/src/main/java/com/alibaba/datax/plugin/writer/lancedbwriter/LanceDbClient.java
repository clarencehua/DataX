package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.util.Configuration;
import com.lancedb.LanceDbNamespaceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lance.namespace.LanceNamespace;
import org.lance.namespace.model.*;

import java.util.List;

@Slf4j
public class LanceDbClient {
    private final LanceNamespace namespaceClient;

    public LanceDbClient(Configuration conf) {
        String endpoint = conf.getString(KeyConstant.ENDPOINT);
        String apiKey = conf.getString(KeyConstant.API_KEY);
        String database = conf.getString(KeyConstant.DATABASE);
        String region = conf.getString(KeyConstant.REGION);

        LanceDbNamespaceClientBuilder builder = LanceDbNamespaceClientBuilder.newBuilder()
                .apiKey(apiKey)
                .database(database);

        if (StringUtils.isNotBlank(endpoint)) {
            log.info("using custom endpoint {}", endpoint);
            builder.endpoint(endpoint);
        }
        if (StringUtils.isNotBlank(region)) {
            log.info("using region {}", region);
            builder.region(region);
        }
        this.namespaceClient = builder.build();
    }

    public List<String> buildTableId(String namespace, String table) {
        if (StringUtils.isNotBlank(namespace)) {
            return java.util.Arrays.asList(namespace, table);
        }
        return java.util.Collections.singletonList(table);
    }

    public Boolean hasTable(List<String> tableId) {
        try {
            DescribeTableRequest request = new DescribeTableRequest();
            request.setId(tableId);
            namespaceClient.describeTable(request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void createTable(List<String> tableId, byte[] arrowData) {
        CreateTableRequest request = new CreateTableRequest();
        request.setId(tableId);
        namespaceClient.createTable(request, arrowData);
        log.info("table {} created", tableId);
    }

    public void dropTable(List<String> tableId) {
        DropTableRequest request = new DropTableRequest();
        request.setId(tableId);
        namespaceClient.dropTable(request);
        log.info("table {} dropped", tableId);
    }

    public void createNamespace(List<String> namespaceId) {
        CreateNamespaceRequest request = new CreateNamespaceRequest();
        request.setId(namespaceId);
        namespaceClient.createNamespace(request);
        log.info("namespace {} created", namespaceId);
    }

    public void insert(List<String> tableId, byte[] arrowData) {
        InsertIntoTableRequest request = new InsertIntoTableRequest();
        request.setId(tableId);
        request.setMode("append");
        namespaceClient.insertIntoTable(request, arrowData);
    }

    public void mergeInsert(List<String> tableId, String onColumn, byte[] arrowData) {
        MergeInsertIntoTableRequest request = new MergeInsertIntoTableRequest();
        request.setId(tableId);
        request.setOn(onColumn);
        request.setWhenMatchedUpdateAll(true);
        request.setWhenNotMatchedInsertAll(true);
        namespaceClient.mergeInsertIntoTable(request, arrowData);
    }

    public void close() {
        log.info("Closing LanceDB client");
    }
}
