package com.alibaba.datax.plugin.writer.lancedbwriter;

import com.alibaba.datax.common.element.Column;
import com.alibaba.datax.common.element.Record;
import com.alibaba.fastjson2.JSONArray;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.complex.impl.UnionFixedSizeListWriter;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

public class ArrowDataBuilder {

    private ArrowDataBuilder() {}

    public static byte[] buildEmptyArrow(List<LanceDbColumn> columns) {
        return buildArrow(columns, new ArrayList<>());
    }

    public static byte[] buildArrow(List<LanceDbColumn> columns, List<Record> records) {
        Schema schema = buildArrowSchema(columns);
        try (BufferAllocator allocator = new RootAllocator();
             VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {

            int rowCount = records.size();
            root.setRowCount(rowCount);

            for (int i = 0; i < columns.size(); i++) {
                LanceDbColumn col = columns.get(i);
                String fieldName = col.getName();
                String type = col.getType().toUpperCase();

                switch (type) {
                    case "INT8":
                    case "TINYINT": {
                        TinyIntVector vector = (TinyIntVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asLong().byteValue());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "INT16":
                    case "SMALLINT": {
                        SmallIntVector vector = (SmallIntVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asLong().shortValue());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "INT32":
                    case "INT": {
                        IntVector vector = (IntVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asLong().intValue());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "INT64":
                    case "BIGINT":
                    case "LONG": {
                        BigIntVector vector = (BigIntVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asLong());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "FLOAT":
                    case "FLOAT32": {
                        Float4Vector vector = (Float4Vector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asDouble().floatValue());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "DOUBLE":
                    case "FLOAT64": {
                        Float8Vector vector = (Float8Vector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asDouble());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "STRING":
                    case "VARCHAR":
                    case "TEXT": {
                        VarCharVector vector = (VarCharVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asString().getBytes());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "BOOL":
                    case "BOOLEAN": {
                        BitVector vector = (BitVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asBoolean() ? 1 : 0);
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "BINARY":
                    case "BYTES": {
                        VarBinaryVector vector = (VarBinaryVector) root.getVector(fieldName);
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                vector.setNull(r);
                            } else {
                                vector.setSafe(r, c.asBytes());
                            }
                        }
                        vector.setValueCount(rowCount);
                        break;
                    }
                    case "FLOAT_VECTOR":
                    case "FLOATVECTOR": {
                        int dim = col.getDimension() != null ? col.getDimension() : 0;
                        FixedSizeListVector listVector = (FixedSizeListVector) root.getVector(fieldName);
                        UnionFixedSizeListWriter writer = listVector.getWriter();
                        for (int r = 0; r < rowCount; r++) {
                            Column c = records.get(r).getColumn(i);
                            if (c.getRawData() == null) {
                                listVector.setNull(r);
                            } else {
                                writer.setPosition(r);
                                writer.startList();
                                JSONArray arr = com.alibaba.fastjson2.JSON.parseArray(c.asString());
                                for (int j = 0; j < arr.size(); j++) {
                                    writer.writeFloat4(arr.getFloatValue(j));
                                }
                                writer.endList();
                            }
                        }
                        listVector.setValueCount(rowCount);
                        break;
                    }
                    default:
                        throw new RuntimeException("Unsupported data type: " + type);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ArrowStreamWriter writer = new ArrowStreamWriter(root, null, Channels.newChannel(out))) {
                writer.start();
                writer.writeBatch();
                writer.end();
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Arrow data", e);
        }
    }

    private static Schema buildArrowSchema(List<LanceDbColumn> columns) {
        List<Field> fields = new ArrayList<>();
        for (LanceDbColumn col : columns) {
            String type = col.getType().toUpperCase();
            String name = col.getName();

            switch (type) {
                case "INT8":
                case "TINYINT":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Int(8, true)), null));
                    break;
                case "INT16":
                case "SMALLINT":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Int(16, true)), null));
                    break;
                case "INT32":
                case "INT":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Int(32, true)), null));
                    break;
                case "INT64":
                case "BIGINT":
                case "LONG":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Int(64, true)), null));
                    break;
                case "FLOAT":
                case "FLOAT32":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), null));
                    break;
                case "DOUBLE":
                case "FLOAT64":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null));
                    break;
                case "STRING":
                case "VARCHAR":
                case "TEXT":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Utf8()), null));
                    break;
                case "BOOL":
                case "BOOLEAN":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Bool()), null));
                    break;
                case "BINARY":
                case "BYTES":
                    fields.add(new Field(name, FieldType.nullable(new ArrowType.Binary()), null));
                    break;
                case "FLOAT_VECTOR":
                case "FLOATVECTOR": {
                    int dim = col.getDimension() != null ? col.getDimension() : 0;
                    Field elementField = new Field("item",
                            FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), null);
                    fields.add(new Field(name,
                            FieldType.nullable(new ArrowType.FixedSizeList(dim)),
                            java.util.Collections.singletonList(elementField)));
                    break;
                }
                default:
                    throw new RuntimeException("Unsupported data type: " + type);
            }
        }
        return new Schema(fields);
    }
}
