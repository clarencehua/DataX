package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.core.transport.record.DefaultRecord;
import com.alibaba.fastjson2.JSON;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration test for LanceDB Writer local mode:
 * 1. Use ArrowDataBuilder to build Arrow data from DataX Records
 * 2. Write to a temp file (simulating local mode)
 * 3. Read back and verify
 */
public class LanceDbLocalIntegrationTest {

    private static Path tempFile;

    @BeforeClass
    public static void setUp() throws Exception {
        tempFile = Files.createTempFile("lancedb_writer_test_", ".arrow");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    private static class ByteArraySeekableChannel implements SeekableByteChannel {
        private final byte[] data;
        private long position;
        private boolean open = true;
        ByteArraySeekableChannel(byte[] data) { this.data = data; }
        @Override public int read(ByteBuffer dst) {
            int remaining = dst.remaining();
            int available = (int) (data.length - position);
            if (available <= 0) return -1;
            int toRead = Math.min(remaining, available);
            dst.put(data, (int) position, toRead);
            position += toRead;
            return toRead;
        }
        @Override public int write(ByteBuffer src) { throw new UnsupportedOperationException(); }
        @Override public long position() { return position; }
        @Override public SeekableByteChannel position(long newPosition) { position = newPosition; return this; }
        @Override public long size() { return data.length; }
        @Override public SeekableByteChannel truncate(long size) { throw new UnsupportedOperationException(); }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }

    private List<LanceDbColumn> buildColumns(String... specs) {
        List<LanceDbColumn> cols = new ArrayList<>();
        for (int i = 0; i < specs.length; i += 2) {
            LanceDbColumn c = new LanceDbColumn();
            c.setName(specs[i]);
            c.setType(specs[i + 1]);
            cols.add(c);
        }
        return cols;
    }

    @Test
    public void testBuildArrowAndVerify() throws Exception {
        List<LanceDbColumn> columns = buildColumns("id", "Int64", "name", "String", "score", "Double", "flag", "Bool");

        List<com.alibaba.datax.common.element.Record> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            com.alibaba.datax.common.element.Record r = new DefaultRecord();
            r.addColumn(new LongColumn(i + 1));
            r.addColumn(new StringColumn("item_" + i));
            r.addColumn(new DoubleColumn((i + 1) * 10.5));
            r.addColumn(new BoolColumn(i % 2 == 0));
            records.add(r);
        }

        // Build Arrow data
        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);
        assertNotNull(arrowData);
        assertTrue(arrowData.length > 0);

        // Write to temp file (simulating local mode)
        Files.write(tempFile, arrowData);

        // Read back using ArrowFileReader
        try (BufferAllocator allocator = new RootAllocator();
             ArrowFileReader reader = new ArrowFileReader(
                     new ByteArraySeekableChannel(Files.readAllBytes(tempFile)), allocator)) {

            reader.loadRecordBatch(reader.getRecordBlocks().get(0));
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(5, root.getRowCount());

            BigIntVector idVec = (BigIntVector) root.getVector("id");
            VarCharVector nameVec = (VarCharVector) root.getVector("name");
            Float8Vector scoreVec = (Float8Vector) root.getVector("score");
            BitVector flagVec = (BitVector) root.getVector("flag");

            for (int i = 0; i < 5; i++) {
                assertEquals(i + 1, idVec.get(i));
                assertEquals("item_" + i, new String(nameVec.get(i)));
                assertEquals((i + 1) * 10.5, scoreVec.get(i), 0.001);
                assertEquals(i % 2 == 0 ? 1 : 0, flagVec.get(i));
            }
        }
    }

    @Test
    public void testBuildArrowWithFloatVector() throws Exception {
        LanceDbColumn embCol = new LanceDbColumn();
        embCol.setName("vec");
        embCol.setType("FloatVector");
        embCol.setDimension(3);

        List<LanceDbColumn> columns = Collections.singletonList(embCol);

        List<com.alibaba.datax.common.element.Record> records = new ArrayList<>();
        com.alibaba.datax.common.element.Record r = new DefaultRecord();
        r.addColumn(new StringColumn("[1.0,2.0,3.0]"));
        records.add(r);

        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);
        Files.write(tempFile, arrowData);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowFileReader reader = new ArrowFileReader(
                     new ByteArraySeekableChannel(Files.readAllBytes(tempFile)), allocator)) {

            reader.loadRecordBatch(reader.getRecordBlocks().get(0));
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(1, root.getRowCount());

            org.apache.arrow.vector.complex.FixedSizeListVector vec =
                    (org.apache.arrow.vector.complex.FixedSizeListVector) root.getVector("vec");
            assertEquals(3, vec.getListSize());

            int offset = vec.getElementStartIndex(0);
            Float4Vector dataVec = (Float4Vector) vec.getDataVector();
            assertEquals(1.0f, dataVec.get(offset), 0.001f);
            assertEquals(2.0f, dataVec.get(offset + 1), 0.001f);
            assertEquals(3.0f, dataVec.get(offset + 2), 0.001f);
        }
    }

    @Test
    public void testBuildEmptyArrow() throws Exception {
        List<LanceDbColumn> columns = buildColumns("id", "Int64", "name", "String");
        byte[] arrowData = ArrowDataBuilder.buildEmptyArrow(columns);
        assertNotNull(arrowData);
        assertTrue(arrowData.length > 0);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowFileReader reader = new ArrowFileReader(
                     new ByteArraySeekableChannel(arrowData), allocator)) {
            // Empty arrow files may have 0 record blocks or a block with 0 rows
            if (reader.getRecordBlocks().isEmpty()) {
                // No data blocks - expected for empty
                assertTrue(true);
            } else {
                reader.loadRecordBatch(reader.getRecordBlocks().get(0));
                assertEquals(0, reader.getVectorSchemaRoot().getRowCount());
            }
        }
    }

    @Test
    public void testAllIntTypes() throws Exception {
        List<LanceDbColumn> columns = buildColumns(
                "a", "Int8", "b", "Int16", "c", "Int32", "d", "Int64");

        List<com.alibaba.datax.common.element.Record> records = new ArrayList<>();
        com.alibaba.datax.common.element.Record r = new DefaultRecord();
        r.addColumn(new LongColumn(127));   // Int8 max
        r.addColumn(new LongColumn(32767)); // Int16 max
        r.addColumn(new LongColumn(2147483647)); // Int32 max
        r.addColumn(new LongColumn(9223372036854775807L)); // Int64 max
        records.add(r);

        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowFileReader reader = new ArrowFileReader(
                     new ByteArraySeekableChannel(arrowData), allocator)) {

            reader.loadRecordBatch(reader.getRecordBlocks().get(0));
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(1, root.getRowCount());

            assertEquals(127, ((TinyIntVector) root.getVector("a")).get(0));
            assertEquals(32767, ((SmallIntVector) root.getVector("b")).get(0));
            assertEquals(2147483647, ((IntVector) root.getVector("c")).get(0));
            assertEquals(9223372036854775807L, ((BigIntVector) root.getVector("d")).get(0));
        }
    }
}
