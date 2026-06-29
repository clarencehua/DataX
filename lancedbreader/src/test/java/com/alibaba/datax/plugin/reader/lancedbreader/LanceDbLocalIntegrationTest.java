package com.alibaba.datax.plugin.reader.lancedbreader;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.core.transport.record.DefaultRecord;
import com.alibaba.fastjson2.JSON;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.complex.impl.UnionFixedSizeListWriter;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration test: write Arrow data to a temp file (simulating LanceDB local write),
 * then read it back using ArrowDataParser (simulating LanceDB local read).
 */
public class LanceDbLocalIntegrationTest {

    private static Path tempFile;

    @BeforeClass
    public static void setUp() throws Exception {
        tempFile = Files.createTempFile("lancedb_test_", ".arrow");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Helper to build Arrow IPC file bytes from columns + records.
     */
    private static byte[] buildArrowFile(List<Field> fields, List<Map<String, Object>> rows) {
        try (BufferAllocator allocator = new RootAllocator();
             VectorSchemaRoot root = VectorSchemaRoot.create(new Schema(fields), allocator)) {

            int rowCount = rows.size();
            root.setRowCount(rowCount);

            for (Field field : fields) {
                String name = field.getName();
                ValueVector vector = root.getVector(name);

                if (vector instanceof BigIntVector) {
                    BigIntVector v = (BigIntVector) vector;
                    for (int r = 0; r < rowCount; r++) {
                        Object val = rows.get(r).get(name);
                        if (val == null) v.setNull(r);
                        else v.setSafe(r, ((Number) val).longValue());
                    }
                    v.setValueCount(rowCount);
                } else if (vector instanceof Float8Vector) {
                    Float8Vector v = (Float8Vector) vector;
                    for (int r = 0; r < rowCount; r++) {
                        Object val = rows.get(r).get(name);
                        if (val == null) v.setNull(r);
                        else v.setSafe(r, ((Number) val).doubleValue());
                    }
                    v.setValueCount(rowCount);
                } else if (vector instanceof VarCharVector) {
                    VarCharVector v = (VarCharVector) vector;
                    for (int r = 0; r < rowCount; r++) {
                        Object val = rows.get(r).get(name);
                        if (val == null) v.setNull(r);
                        else v.setSafe(r, val.toString().getBytes());
                    }
                    v.setValueCount(rowCount);
                } else if (vector instanceof BitVector) {
                    BitVector v = (BitVector) vector;
                    for (int r = 0; r < rowCount; r++) {
                        Object val = rows.get(r).get(name);
                        if (val == null) v.setNull(r);
                        else v.setSafe(r, (Boolean) val ? 1 : 0);
                    }
                    v.setValueCount(rowCount);
                } else if (vector instanceof FixedSizeListVector) {
                    FixedSizeListVector v = (FixedSizeListVector) vector;
                    v.allocateNew();
                    UnionFixedSizeListWriter writer = v.getWriter();
                    for (int r = 0; r < rowCount; r++) {
                        Object val = rows.get(r).get(name);
                        if (val == null) {
                            v.setNull(r);
                        } else {
                            writer.setPosition(r);
                            writer.startList();
                            @SuppressWarnings("unchecked")
                            List<Number> list = (List<Number>) val;
                            for (Number n : list) {
                                writer.writeFloat4(n.floatValue());
                            }
                            writer.endList();
                        }
                    }
                    v.setValueCount(rowCount);
                } else if (vector instanceof VarBinaryVector) {
                    VarBinaryVector v = (VarBinaryVector) vector;
                    for (int r = 0; r < rowCount; r++) {
                        Object val = rows.get(r).get(name);
                        if (val == null) v.setNull(r);
                        else v.setSafe(r, (byte[]) val);
                    }
                    v.setValueCount(rowCount);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ArrowFileWriter writer = new ArrowFileWriter(root, null, Channels.newChannel(baos))) {
                writer.start();
                writer.writeBatch();
                writer.end();
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testWriteAndReadBack_BasicTypes() throws Exception {
        // Prepare columns
        List<LanceDbColumn> columns = new ArrayList<>();
        LanceDbColumn c1 = new LanceDbColumn(); c1.setName("id"); c1.setType("Int64"); columns.add(c1);
        LanceDbColumn c2 = new LanceDbColumn(); c2.setName("name"); c2.setType("String"); columns.add(c2);
        LanceDbColumn c3 = new LanceDbColumn(); c3.setName("score"); c3.setType("Double"); columns.add(c3);
        LanceDbColumn c4 = new LanceDbColumn(); c4.setName("active"); c4.setType("Bool"); columns.add(c4);

        // Prepare data
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1L); r1.put("name", "Alice"); r1.put("score", 95.5); r1.put("active", true);
        rows.add(r1);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2L); r2.put("name", "Bob"); r2.put("score", 87.0); r2.put("active", false);
        rows.add(r2);
        Map<String, Object> r3 = new HashMap<>();
        r3.put("id", 3L); r3.put("name", "Charlie"); r3.put("score", 73.2); r3.put("active", true);
        rows.add(r3);

        // Build Arrow fields
        List<Field> fields = Arrays.asList(
                Field.nullable("id", new ArrowType.Int(64, true)),
                Field.nullable("name", ArrowType.Utf8.INSTANCE),
                Field.nullable("score", new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)),
                Field.nullable("active", ArrowType.Bool.INSTANCE)
        );

        // Simulate write: build Arrow file and save to temp file
        byte[] arrowData = buildArrowFile(fields, rows);
        Files.write(tempFile, arrowData);

        // Simulate read: use ArrowDataParser to parse from file
        List<com.alibaba.datax.common.element.Record> parsedRecords = new ArrayList<>();
        RecordSender sender = new RecordSender() {
            @Override public com.alibaba.datax.common.element.Record createRecord() { return new DefaultRecord(); }
            @Override public void sendToWriter(com.alibaba.datax.common.element.Record record) { parsedRecords.add(record); }
            @Override public void flush() {}
            @Override public void terminate() {}
            @Override public void shutdown() {}
        };

        byte[] fileData = Files.readAllBytes(tempFile);
        ArrowDataParser.parseAndSend(fileData, columns, sender);

        // Verify
        assertEquals("Should have 3 records", 3, parsedRecords.size());

        // Record 0: Alice
        assertEquals(1L, parsedRecords.get(0).getColumn(0).asLong().longValue());
        assertEquals("Alice", parsedRecords.get(0).getColumn(1).asString());
        assertEquals(95.5, parsedRecords.get(0).getColumn(2).asDouble(), 0.001);
        assertTrue(parsedRecords.get(0).getColumn(3).asBoolean());

        // Record 1: Bob
        assertEquals(2L, parsedRecords.get(1).getColumn(0).asLong().longValue());
        assertEquals("Bob", parsedRecords.get(1).getColumn(1).asString());
        assertEquals(87.0, parsedRecords.get(1).getColumn(2).asDouble(), 0.001);
        assertFalse(parsedRecords.get(1).getColumn(3).asBoolean());

        // Record 2: Charlie
        assertEquals(3L, parsedRecords.get(2).getColumn(0).asLong().longValue());
        assertEquals("Charlie", parsedRecords.get(2).getColumn(1).asString());
        assertEquals(73.2, parsedRecords.get(2).getColumn(2).asDouble(), 0.001);
        assertTrue(parsedRecords.get(2).getColumn(3).asBoolean());
    }

    @Test
    public void testWriteAndReadBack_WithNullValues() throws Exception {
        List<LanceDbColumn> columns = new ArrayList<>();
        LanceDbColumn c1 = new LanceDbColumn(); c1.setName("id"); c1.setType("Int64"); columns.add(c1);
        LanceDbColumn c2 = new LanceDbColumn(); c2.setName("name"); c2.setType("String"); columns.add(c2);

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1L); r1.put("name", null);
        rows.add(r1);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", null); r2.put("name", "no_id");
        rows.add(r2);

        List<Field> fields = Arrays.asList(
                Field.nullable("id", new ArrowType.Int(64, true)),
                Field.nullable("name", ArrowType.Utf8.INSTANCE)
        );

        byte[] arrowData = buildArrowFile(fields, rows);
        Files.write(tempFile, arrowData);

        List<com.alibaba.datax.common.element.Record> parsedRecords = new ArrayList<>();
        RecordSender sender = new RecordSender() {
            @Override public com.alibaba.datax.common.element.Record createRecord() { return new DefaultRecord(); }
            @Override public void sendToWriter(com.alibaba.datax.common.element.Record record) { parsedRecords.add(record); }
            @Override public void flush() {}
            @Override public void terminate() {}
            @Override public void shutdown() {}
        };

        byte[] fileData = Files.readAllBytes(tempFile);
        ArrowDataParser.parseAndSend(fileData, columns, sender);

        assertEquals(2, parsedRecords.size());
        // Row 0: id=1, name=null
        assertEquals(1L, parsedRecords.get(0).getColumn(0).asLong().longValue());
        assertNull(parsedRecords.get(0).getColumn(1).asString());
        // Row 1: id=null, name="no_id"
        assertNull(parsedRecords.get(1).getColumn(0).asString());
        assertEquals("no_id", parsedRecords.get(1).getColumn(1).asString());
    }

    @Test
    public void testWriteAndReadBack_FloatVector() throws Exception {
        List<LanceDbColumn> columns = new ArrayList<>();
        LanceDbColumn c1 = new LanceDbColumn(); c1.setName("id"); c1.setType("Int64"); columns.add(c1);
        LanceDbColumn c2 = new LanceDbColumn(); c2.setName("embedding"); c2.setType("FloatVector"); c2.setDimension(4); columns.add(c2);

        Field embField = new Field("embedding",
                org.apache.arrow.vector.types.pojo.FieldType.nullable(new ArrowType.FixedSizeList(4)),
                Collections.singletonList(Field.nullable("item", new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE))));

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1L);
        r1.put("embedding", Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f));
        rows.add(r1);

        List<Field> fields = Arrays.asList(
                Field.nullable("id", new ArrowType.Int(64, true)),
                embField
        );

        byte[] arrowData = buildArrowFile(fields, rows);
        Files.write(tempFile, arrowData);

        List<com.alibaba.datax.common.element.Record> parsedRecords = new ArrayList<>();
        RecordSender sender = new RecordSender() {
            @Override public com.alibaba.datax.common.element.Record createRecord() { return new DefaultRecord(); }
            @Override public void sendToWriter(com.alibaba.datax.common.element.Record record) { parsedRecords.add(record); }
            @Override public void flush() {}
            @Override public void terminate() {}
            @Override public void shutdown() {}
        };

        byte[] fileData = Files.readAllBytes(tempFile);
        ArrowDataParser.parseAndSend(fileData, columns, sender);

        assertEquals(1, parsedRecords.size());
        assertEquals(1L, parsedRecords.get(0).getColumn(0).asLong().longValue());
        String embJson = parsedRecords.get(0).getColumn(1).asString();
        List<Float> emb = JSON.parseArray(embJson, Float.class);
        assertEquals(4, emb.size());
        assertEquals(0.1f, emb.get(0), 0.001f);
        assertEquals(0.2f, emb.get(1), 0.001f);
        assertEquals(0.3f, emb.get(2), 0.001f);
        assertEquals(0.4f, emb.get(3), 0.001f);
    }

    @Test
    public void testReadNonexistentFile() {
        try {
            byte[] data = Files.readAllBytes(Path.of("/nonexistent/path/file.arrow"));
            ArrowDataParser.parseAndSend(data, new ArrayList<>(), new RecordSender() {
                @Override public com.alibaba.datax.common.element.Record createRecord() { return new DefaultRecord(); }
                @Override public void sendToWriter(com.alibaba.datax.common.element.Record record) {}
                @Override public void flush() {}
                @Override public void terminate() {}
                @Override public void shutdown() {}
            });
            fail("Expected exception");
        } catch (Exception e) {
            // expected
        }
    }
}
