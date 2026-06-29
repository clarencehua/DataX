package com.alibaba.datax.plugin.reader.lancedbreader;

import com.alibaba.datax.common.util.Configuration;
import com.lancedb.LanceDbNamespaceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lance.namespace.LanceNamespace;
import org.lance.namespace.model.QueryTableRequest;
import org.lance.namespace.model.DescribeTableRequest;
import org.lance.namespace.model.DescribeTableResponse;

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

    public DescribeTableResponse describeTable(List<String> tableId) {
        DescribeTableRequest request = new DescribeTableRequest();
        request.setId(tableId);
        return namespaceClient.describeTable(request);
    }

    public byte[] queryTable(List<String> tableId, List<String> columns, String filter, int limit, List<Float> queryVector) {
        QueryTableRequest query = new QueryTableRequest();
        query.setId(tableId);
        query.setK(limit);
        if (columns != null && !columns.isEmpty()) {
            org.lance.namespace.model.QueryTableRequestColumns cols =
                    new org.lance.namespace.model.QueryTableRequestColumns();
            cols.setColumnNames(columns);
            query.setColumns(cols);
        }
        if (StringUtils.isNotBlank(filter)) {
            query.setFilter(filter);
        }
        if (queryVector != null && !queryVector.isEmpty()) {
            org.lance.namespace.model.QueryTableRequestVector vector =
                    new org.lance.namespace.model.QueryTableRequestVector();
            vector.setSingleVector(queryVector);
            query.setVector(vector);
        }
        return namespaceClient.queryTable(query);
    }

    public long countRows(List<String> tableId) {
        org.lance.namespace.model.CountTableRowsRequest request =
                new org.lance.namespace.model.CountTableRowsRequest();
        request.setId(tableId);
        return namespaceClient.countTableRows(request);
    }

    public void close() {
        log.info("Closing LanceDB client");
    }
}
