package com.alibaba.datax.plugin.reader.lancedbreader;

import com.alibaba.datax.common.element.BoolColumn;
import com.alibaba.datax.common.element.BytesColumn;
import com.alibaba.datax.common.element.DoubleColumn;
import com.alibaba.datax.common.element.LongColumn;
import com.alibaba.datax.common.element.StringColumn;
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
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ArrowDataParserTest {

    private static List<LanceDbColumn> columns(String... specs) {
        List<LanceDbColumn> cols = new ArrayList<>();
        for (int i = 0; i < specs.length; i += 2) {
            LanceDbColumn c = new LanceDbColumn();
            c.setName(specs[i]);
            c.setType(specs[i + 1]);
            cols.add(c);
        }
        return cols;
    }

    @FunctionalInterface
    interface VectorLoader {
        void load(VectorSchemaRoot root);
    }

    private static byte[] createArrowFile(BufferAllocator allocator, List<Field> fields, VectorLoader loader) {
        Schema schema = new Schema(fields);
        VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
        loader.load(root);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ArrowFileWriter writer = new ArrowFileWriter(root, null, Channels.newChannel(baos))) {
            writer.start();
            writer.writeBatch();
            writer.end();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        root.close();
        return baos.toByteArray();
    }

    private static List<com.alibaba.datax.common.element.Record> parseAndCollect(byte[] arrowData, List<LanceDbColumn> cols) {
        List<com.alibaba.datax.common.element.Record> result = new ArrayList<>();
        RecordSender sender = new RecordSender() {
            @Override
            public com.alibaba.datax.common.element.Record createRecord() {
                return new DefaultRecord();
            }
            @Override
            public void sendToWriter(com.alibaba.datax.common.element.Record record) {
                result.add(record);
            }
            @Override public void flush() {}
            @Override public void terminate() {}
            @Override public void shutdown() {}
        };
        ArrowDataParser.parseAndSend(arrowData, cols, sender);
        return result;
    }

    @Test
    public void testParseEmptyArrow() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Arrays.asList(
                    Field.nullable("id", new ArrowType.Int(64, true)),
                    Field.nullable("name", ArrowType.Utf8.INSTANCE)
            );
            byte[] data = createArrowFile(allocator, fields, root -> root.setRowCount(0));
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data, columns("id", "Int64", "name", "String"));
            assertTrue("expected 0 records", records.isEmpty());
        }
    }

    @Test
    public void testParseBasicTypes() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Arrays.asList(
                    Field.nullable("id", new ArrowType.Int(64, true)),
                    Field.nullable("name", ArrowType.Utf8.INSTANCE),
                    Field.nullable("score", new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)),
                    Field.nullable("flag", ArrowType.Bool.INSTANCE)
            );
            byte[] data = createArrowFile(allocator, fields, root -> {
                BigIntVector idVec = (BigIntVector) root.getVector("id");
                VarCharVector nameVec = (VarCharVector) root.getVector("name");
                Float8Vector scoreVec = (Float8Vector) root.getVector("score");
                BitVector flagVec = (BitVector) root.getVector("flag");
                root.allocateNew();
                idVec.set(0, 101L);
                nameVec.set(0, "hello".getBytes());
                scoreVec.set(0, 3.14);
                flagVec.set(0, 1);
                idVec.set(1, 102L);
                nameVec.set(1, "world".getBytes());
                scoreVec.set(1, 2.71);
                flagVec.set(1, 0);
                root.setRowCount(2);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("id", "Int64", "name", "String", "score", "Double", "flag", "Bool"));
            assertEquals(2, records.size());
            assertTrue(((LongColumn) records.get(0).getColumn(0)).asLong() == 101L);
            assertEquals("hello", ((StringColumn) records.get(0).getColumn(1)).asString());
            assertEquals(3.14, ((DoubleColumn) records.get(0).getColumn(2)).asDouble(), 1e-9);
            assertTrue(((BoolColumn) records.get(0).getColumn(3)).asBoolean());
            assertTrue(((LongColumn) records.get(1).getColumn(0)).asLong() == 102L);
            assertEquals("world", ((StringColumn) records.get(1).getColumn(1)).asString());
            assertEquals(2.71, ((DoubleColumn) records.get(1).getColumn(2)).asDouble(), 1e-9);
            assertFalse(((BoolColumn) records.get(1).getColumn(3)).asBoolean());
        }
    }

    @Test
    public void testParseAllIntTypes() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Arrays.asList(
                    Field.nullable("a", new ArrowType.Int(8, true)),
                    Field.nullable("b", new ArrowType.Int(16, true)),
                    Field.nullable("c", new ArrowType.Int(32, true)),
                    Field.nullable("d", new ArrowType.Int(64, true))
            );
            byte[] data = createArrowFile(allocator, fields, root -> {
                TinyIntVector v1 = (TinyIntVector) root.getVector("a");
                SmallIntVector v2 = (SmallIntVector) root.getVector("b");
                IntVector v3 = (IntVector) root.getVector("c");
                BigIntVector v4 = (BigIntVector) root.getVector("d");
                root.allocateNew();
                v1.set(0, 1);
                v2.set(0, 2);
                v3.set(0, 3);
                v4.set(0, 4L);
                v1.set(1, -1);
                v2.set(1, -2);
                v3.set(1, -3);
                v4.set(1, -4L);
                root.setRowCount(2);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("a", "Int8", "b", "Int16", "c", "Int32", "d", "Int64"));
            assertEquals(2, records.size());
            assertTrue(records.get(0).getColumn(0).asLong() == 1L);
            assertTrue(records.get(0).getColumn(1).asLong() == 2L);
            assertTrue(records.get(0).getColumn(2).asLong() == 3L);
            assertTrue(records.get(0).getColumn(3).asLong() == 4L);
            assertTrue(records.get(1).getColumn(0).asLong() == -1L);
            assertTrue(records.get(1).getColumn(1).asLong() == -2L);
            assertTrue(records.get(1).getColumn(2).asLong() == -3L);
            assertTrue(records.get(1).getColumn(3).asLong() == -4L);
        }
    }

    @Test
    public void testParseFloatAndDouble() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Arrays.asList(
                    Field.nullable("f32", new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)),
                    Field.nullable("f64", new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE))
            );
            byte[] data = createArrowFile(allocator, fields, root -> {
                Float4Vector v1 = (Float4Vector) root.getVector("f32");
                Float8Vector v2 = (Float8Vector) root.getVector("f64");
                root.allocateNew();
                v1.set(0, 1.5f);
                v2.set(0, 2.5);
                root.setRowCount(1);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("f32", "Float", "f64", "Double"));
            assertEquals(1, records.size());
            assertEquals(1.5, records.get(0).getColumn(0).asDouble(), 1e-6);
            assertEquals(2.5, records.get(0).getColumn(1).asDouble(), 1e-9);
        }
    }

    @Test
    public void testParseStringAndBinary() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Arrays.asList(
                    Field.nullable("txt", ArrowType.Utf8.INSTANCE),
                    Field.nullable("bin", ArrowType.Binary.INSTANCE)
            );
            byte[] data = createArrowFile(allocator, fields, root -> {
                VarCharVector v1 = (VarCharVector) root.getVector("txt");
                VarBinaryVector v2 = (VarBinaryVector) root.getVector("bin");
                root.allocateNew();
                v1.set(0, "text".getBytes());
                v2.set(0, new byte[]{1, 2, 3});
                root.setRowCount(1);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("txt", "String", "bin", "Binary"));
            assertEquals(1, records.size());
            assertEquals("text", records.get(0).getColumn(0).asString());
            assertArrayEquals(new byte[]{1, 2, 3}, records.get(0).getColumn(1).asBytes());
        }
    }

    @Test
    public void testParseFloatVector() {
        try (BufferAllocator allocator = new RootAllocator()) {
            Field embedding = new Field("embedding",
                    org.apache.arrow.vector.types.pojo.FieldType.nullable(
                            new ArrowType.FixedSizeList(4)),
                    Collections.singletonList(
                            Field.nullable("item", new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE))));
            List<Field> fields = Collections.singletonList(embedding);
            byte[] data = createArrowFile(allocator, fields, root -> {
                FixedSizeListVector vec = (FixedSizeListVector) root.getVector("embedding");
                vec.allocateNew();
                UnionFixedSizeListWriter writer = vec.getWriter();
                writer.setPosition(0);
                writer.startList();
                writer.writeFloat4(0.1f);
                writer.writeFloat4(0.2f);
                writer.writeFloat4(0.3f);
                writer.writeFloat4(0.4f);
                writer.endList();
                writer.setPosition(1);
                writer.startList();
                writer.writeFloat4(0.5f);
                writer.writeFloat4(0.6f);
                writer.writeFloat4(0.7f);
                writer.writeFloat4(0.8f);
                writer.endList();
                root.setRowCount(2);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("embedding", "FloatVector"));
            assertEquals(2, records.size());
            List<Float> expected0 = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f);
            List<Float> expected1 = Arrays.asList(0.5f, 0.6f, 0.7f, 0.8f);
            assertEquals(JSON.toJSONString(expected0), records.get(0).getColumn(0).asString());
            assertEquals(JSON.toJSONString(expected1), records.get(1).getColumn(0).asString());
        }
    }

    @Test
    public void testParseNullValues() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Arrays.asList(
                    Field.nullable("id", new ArrowType.Int(64, true)),
                    Field.nullable("name", ArrowType.Utf8.INSTANCE),
                    Field.nullable("score", new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE))
            );
            byte[] data = createArrowFile(allocator, fields, root -> {
                BigIntVector idVec = (BigIntVector) root.getVector("id");
                VarCharVector nameVec = (VarCharVector) root.getVector("name");
                Float8Vector scoreVec = (Float8Vector) root.getVector("score");
                root.allocateNew();
                idVec.setNull(0);
                nameVec.setNull(0);
                scoreVec.setNull(0);
                idVec.set(1, 10L);
                nameVec.set(1, "a".getBytes());
                scoreVec.set(1, 1.0);
                root.setRowCount(2);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("id", "Int64", "name", "String", "score", "Double"));
            assertEquals(2, records.size());
            assertNull(records.get(0).getColumn(0).asString());
            assertNull(records.get(0).getColumn(1).asString());
            assertNull(records.get(0).getColumn(2).asString());
            assertEquals("10", records.get(1).getColumn(0).asString());
            assertEquals("a", records.get(1).getColumn(1).asString());
            assertEquals("1.0", records.get(1).getColumn(2).asString());
        }
    }

    @Test
    public void testTypeAliases() {
        try (BufferAllocator allocator = new RootAllocator()) {
            List<Field> fields = Collections.singletonList(
                    Field.nullable("val", new ArrowType.Int(32, true))
            );
            byte[] data = createArrowFile(allocator, fields, root -> {
                IntVector vec = (IntVector) root.getVector("val");
                root.allocateNew();
                vec.set(0, 42);
                root.setRowCount(1);
            });
            List<com.alibaba.datax.common.element.Record> records = parseAndCollect(data,
                    columns("val", "INT"));
            assertEquals(1, records.size());
            assertTrue(records.get(0).getColumn(0).asLong() == 42L);
        }
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidArrowData() {
        ArrowDataParser.parseAndSend("not valid arrow".getBytes(), new ArrayList<>(), new RecordSender() {
            @Override public com.alibaba.datax.common.element.Record createRecord() { return new DefaultRecord(); }
            @Override public void sendToWriter(com.alibaba.datax.common.element.Record record) {}
            @Override public void flush() {}
            @Override public void terminate() {}
            @Override public void shutdown() {}
        });
    }
}
