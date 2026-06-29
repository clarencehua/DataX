package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.core.transport.record.DefaultRecord;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ArrowDataBuilderTest {

    private List<LanceDbColumn> createTestColumns() {
        List<LanceDbColumn> columns = new ArrayList<>();

        LanceDbColumn idCol = new LanceDbColumn();
        idCol.setName("id");
        idCol.setType("Int64");
        idCol.setPrimaryKey(true);
        columns.add(idCol);

        LanceDbColumn nameCol = new LanceDbColumn();
        nameCol.setName("name");
        nameCol.setType("String");
        columns.add(nameCol);

        LanceDbColumn scoreCol = new LanceDbColumn();
        scoreCol.setName("score");
        scoreCol.setType("Double");
        columns.add(scoreCol);

        LanceDbColumn flagCol = new LanceDbColumn();
        flagCol.setName("flag");
        flagCol.setType("Bool");
        columns.add(flagCol);

        LanceDbColumn embeddingCol = new LanceDbColumn();
        embeddingCol.setName("embedding");
        embeddingCol.setType("FloatVector");
        embeddingCol.setDimension(4);
        columns.add(embeddingCol);

        return columns;
    }

    private List<Record> createTestRecords() {
        List<Record> records = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Record record = new DefaultRecord();
            record.addColumn(new LongColumn(i + 1));
            record.addColumn(new StringColumn("item_" + i));
            record.addColumn(new DoubleColumn((i + 1) * 1.5));
            record.addColumn(new BoolColumn(i % 2 == 0));
            record.addColumn(new StringColumn("[1.0,2.0,3.0,4.0]"));
            records.add(record);
        }
        return records;
    }

    @Test
    public void testBuildEmptyArrow() {
        List<LanceDbColumn> columns = createTestColumns();
        byte[] arrowData = ArrowDataBuilder.buildEmptyArrow(columns);
        assertNotNull(arrowData);
        assertTrue("empty arrow should have content", arrowData.length > 0);

        Schema schema = readArrowSchema(arrowData);
        assertNotNull(schema);
        List<Field> fields = schema.getFields();
        assertEquals(5, fields.size());
        assertEquals("id", fields.get(0).getName());
        assertEquals("name", fields.get(1).getName());
        assertEquals("score", fields.get(2).getName());
        assertEquals("flag", fields.get(3).getName());
        assertEquals("embedding", fields.get(4).getName());
    }

    @Test
    public void testBuildArrowWithData() {
        List<LanceDbColumn> columns = createTestColumns();
        List<Record> records = createTestRecords();
        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);
        assertNotNull(arrowData);
        assertTrue("arrow data should have content", arrowData.length > 0);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowStreamReader reader = new ArrowStreamReader(
                     new ByteArrayInputStream(arrowData), allocator)) {

            reader.loadNextBatch();
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(3, root.getRowCount());

            BigIntVector idVector = (BigIntVector) root.getVector("id");
            assertEquals(1, idVector.get(0));
            assertEquals(2, idVector.get(1));
            assertEquals(3, idVector.get(2));

            VarCharVector nameVector = (VarCharVector) root.getVector("name");
            assertEquals("item_0", new String(nameVector.get(0)));
            assertEquals("item_1", new String(nameVector.get(1)));
            assertEquals("item_2", new String(nameVector.get(2)));

            Float8Vector scoreVector = (Float8Vector) root.getVector("score");
            assertEquals(1.5, scoreVector.get(0), 0.001);
            assertEquals(3.0, scoreVector.get(1), 0.001);
            assertEquals(4.5, scoreVector.get(2), 0.001);

            BitVector flagVector = (BitVector) root.getVector("flag");
            assertEquals(1, flagVector.get(0));
            assertEquals(0, flagVector.get(1));
            assertEquals(1, flagVector.get(2));

        } catch (Exception e) {
            fail("Failed to read arrow data: " + e.getMessage());
        }
    }

    @Test
    public void testBuildArrowWithNullValues() {
        List<LanceDbColumn> columns = createTestColumns();
        List<Record> records = new ArrayList<>();

        Record record = new DefaultRecord();
        record.addColumn(new LongColumn(1));
        record.addColumn(new StringColumn("test"));
        record.addColumn(new DoubleColumn((String) null));
        record.addColumn(new BoolColumn((String) null));
        record.addColumn(new StringColumn("[1.0,2.0,3.0,4.0]"));
        records.add(record);

        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);
        assertNotNull(arrowData);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowStreamReader reader = new ArrowStreamReader(
                     new ByteArrayInputStream(arrowData), allocator)) {

            reader.loadNextBatch();
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(1, root.getRowCount());

            Float8Vector scoreVector = (Float8Vector) root.getVector("score");
            assertTrue("score should be null", scoreVector.isNull(0));

            BitVector flagVector = (BitVector) root.getVector("flag");
            assertTrue("flag should be null", flagVector.isNull(0));

        } catch (Exception e) {
            fail("Failed to read arrow data: " + e.getMessage());
        }
    }

    @Test(expected = RuntimeException.class)
    public void testUnsupportedType() {
        List<LanceDbColumn> columns = new ArrayList<>();
        LanceDbColumn col = new LanceDbColumn();
        col.setName("bad");
        col.setType("UnsupportedType123");
        columns.add(col);

        ArrowDataBuilder.buildEmptyArrow(columns);
    }

    @Test
    public void testIntTypes() {
        List<LanceDbColumn> columns = new ArrayList<>();

        LanceDbColumn c1 = new LanceDbColumn();
        c1.setName("c_int8");
        c1.setType("Int8");
        columns.add(c1);

        LanceDbColumn c2 = new LanceDbColumn();
        c2.setName("c_int16");
        c2.setType("Int16");
        columns.add(c2);

        LanceDbColumn c3 = new LanceDbColumn();
        c3.setName("c_int32");
        c3.setType("Int32");
        columns.add(c3);

        LanceDbColumn c4 = new LanceDbColumn();
        c4.setName("c_int64");
        c4.setType("Int64");
        columns.add(c4);

        List<Record> records = new ArrayList<>();
        Record r = new DefaultRecord();
        r.addColumn(new LongColumn(1));
        r.addColumn(new LongColumn(2));
        r.addColumn(new LongColumn(3));
        r.addColumn(new LongColumn(4));
        records.add(r);

        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowStreamReader reader = new ArrowStreamReader(
                     new ByteArrayInputStream(arrowData), allocator)) {

            reader.loadNextBatch();
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(1, root.getRowCount());

            TinyIntVector v8 = (TinyIntVector) root.getVector("c_int8");
            assertEquals(1, v8.get(0));

            SmallIntVector v16 = (SmallIntVector) root.getVector("c_int16");
            assertEquals(2, v16.get(0));

            IntVector v32 = (IntVector) root.getVector("c_int32");
            assertEquals(3, v32.get(0));

            BigIntVector v64 = (BigIntVector) root.getVector("c_int64");
            assertEquals(4, v64.get(0));
        } catch (Exception e) {
            fail("Failed: " + e.getMessage());
        }
    }

    @Test
    public void testFloatAndStringTypes() {
        List<LanceDbColumn> columns = new ArrayList<>();

        LanceDbColumn c1 = new LanceDbColumn();
        c1.setName("c_float");
        c1.setType("Float");
        columns.add(c1);

        LanceDbColumn c2 = new LanceDbColumn();
        c2.setName("c_double");
        c2.setType("Double");
        columns.add(c2);

        LanceDbColumn c3 = new LanceDbColumn();
        c3.setName("c_str");
        c3.setType("String");
        columns.add(c3);

        LanceDbColumn c4 = new LanceDbColumn();
        c4.setName("c_binary");
        c4.setType("Binary");
        columns.add(c4);

        List<Record> records = new ArrayList<>();
        Record r = new DefaultRecord();
        r.addColumn(new DoubleColumn(3.14f));
        r.addColumn(new DoubleColumn(2.718));
        r.addColumn(new StringColumn("hello"));
        r.addColumn(new BytesColumn("world".getBytes()));
        records.add(r);

        byte[] arrowData = ArrowDataBuilder.buildArrow(columns, records);

        try (BufferAllocator allocator = new RootAllocator();
             ArrowStreamReader reader = new ArrowStreamReader(
                     new ByteArrayInputStream(arrowData), allocator)) {

            reader.loadNextBatch();
            VectorSchemaRoot root = reader.getVectorSchemaRoot();
            assertEquals(1, root.getRowCount());

            Float4Vector fv = (Float4Vector) root.getVector("c_float");
            assertEquals(3.14f, fv.get(0), 0.001f);

            Float8Vector dv = (Float8Vector) root.getVector("c_double");
            assertEquals(2.718, dv.get(0), 0.001);

            VarCharVector sv = (VarCharVector) root.getVector("c_str");
            assertEquals("hello", new String(sv.get(0)));

            VarBinaryVector bv = (VarBinaryVector) root.getVector("c_binary");
            assertEquals("world", new String(bv.get(0)));
        } catch (Exception e) {
            fail("Failed: " + e.getMessage());
        }
    }

    @Test
    public void testSchemaWithAllTypes() {
        List<LanceDbColumn> columns = new ArrayList<>();

        addColumn(columns, "f1", "Int8");
        addColumn(columns, "f2", "Int16");
        addColumn(columns, "f3", "Int32");
        addColumn(columns, "f4", "Int64");
        addColumn(columns, "f5", "Float");
        addColumn(columns, "f6", "Double");
        addColumn(columns, "f7", "String");
        addColumn(columns, "f8", "Bool");
        addColumn(columns, "f9", "Binary");

        byte[] arrowData = ArrowDataBuilder.buildEmptyArrow(columns);
        Schema schema = readArrowSchema(arrowData);
        assertEquals(9, schema.getFields().size());
    }

    @Test
    public void testTypeAliases() {
        List<LanceDbColumn> columns = new ArrayList<>();
        addColumn(columns, "a", "TINYINT");
        addColumn(columns, "b", "SMALLINT");
        addColumn(columns, "c", "INT");
        addColumn(columns, "d", "BIGINT");
        addColumn(columns, "e", "FLOAT32");
        addColumn(columns, "f", "FLOAT64");
        addColumn(columns, "g", "VARCHAR");
        addColumn(columns, "h", "TEXT");
        addColumn(columns, "i", "BOOLEAN");
        addColumn(columns, "j", "BYTES");
        addColumn(columns, "k", "FLOATVECTOR");

        LanceDbColumn col = columns.get(columns.size() - 1);
        col.setDimension(8);

        byte[] arrowData = ArrowDataBuilder.buildEmptyArrow(columns);
        Schema schema = readArrowSchema(arrowData);
        assertEquals(11, schema.getFields().size());
    }

    private void addColumn(List<LanceDbColumn> columns, String name, String type) {
        LanceDbColumn col = new LanceDbColumn();
        col.setName(name);
        col.setType(type);
        columns.add(col);
    }

    private Schema readArrowSchema(byte[] arrowData) {
        try (BufferAllocator allocator = new RootAllocator();
             ArrowStreamReader reader = new ArrowStreamReader(
                     new ByteArrayInputStream(arrowData), allocator)) {
            return reader.getVectorSchemaRoot().getSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Arrow schema", e);
        }
    }
}
