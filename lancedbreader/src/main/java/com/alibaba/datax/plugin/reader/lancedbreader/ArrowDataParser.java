package com.alibaba.datax.plugin.reader.lancedbreader;

import com.alibaba.datax.common.element.*;
import com.alibaba.datax.common.plugin.RecordSender;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;

public class ArrowDataParser {

    private ArrowDataParser() {}

    static class ByteArraySeekableChannel implements SeekableByteChannel {
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

    public static void parseAndSend(byte[] arrowData, List<LanceDbColumn> columns, RecordSender recordSender) {
        try (BufferAllocator allocator = new RootAllocator();
             ArrowFileReader reader = new ArrowFileReader(new ByteArraySeekableChannel(arrowData), allocator)) {

            for (int block = 0; block < reader.getRecordBlocks().size(); block++) {
                reader.loadRecordBatch(reader.getRecordBlocks().get(block));
                VectorSchemaRoot root = reader.getVectorSchemaRoot();
                int rowCount = root.getRowCount();

                for (int r = 0; r < rowCount; r++) {
                    com.alibaba.datax.common.element.Record record = recordSender.createRecord();
                    for (int i = 0; i < columns.size(); i++) {
                        LanceDbColumn col = columns.get(i);
                        String fieldName = col.getName();
                        String type = col.getType().toUpperCase();
                        ValueVector vector = root.getVector(fieldName);

                        if (vector == null || vector.isNull(r)) {
                            record.addColumn(new StringColumn(null));
                            continue;
                        }

                        switch (type) {
                            case "INT8":
                            case "TINYINT":
                                record.addColumn(new LongColumn((long) ((TinyIntVector) vector).get(r)));
                                break;
                            case "INT16":
                            case "SMALLINT":
                                record.addColumn(new LongColumn((long) ((SmallIntVector) vector).get(r)));
                                break;
                            case "INT32":
                            case "INT":
                                record.addColumn(new LongColumn((long) ((IntVector) vector).get(r)));
                                break;
                            case "INT64":
                            case "BIGINT":
                            case "LONG":
                                record.addColumn(new LongColumn(((BigIntVector) vector).get(r)));
                                break;
                            case "FLOAT":
                            case "FLOAT32":
                                record.addColumn(new DoubleColumn((double) ((Float4Vector) vector).get(r)));
                                break;
                            case "DOUBLE":
                            case "FLOAT64":
                                record.addColumn(new DoubleColumn(((Float8Vector) vector).get(r)));
                                break;
                            case "STRING":
                            case "VARCHAR":
                            case "TEXT":
                                record.addColumn(new StringColumn(new String(((VarCharVector) vector).get(r))));
                                break;
                            case "BOOL":
                            case "BOOLEAN":
                                record.addColumn(new BoolColumn(((BitVector) vector).get(r) != 0));
                                break;
                            case "BINARY":
                            case "BYTES":
                                record.addColumn(new BytesColumn(((VarBinaryVector) vector).get(r)));
                                break;
                            case "FLOAT_VECTOR":
                            case "FLOATVECTOR": {
                                FixedSizeListVector listVector = (FixedSizeListVector) vector;
                                int dim = listVector.getListSize();
                                int offset = listVector.getElementStartIndex(r);
                                FieldVector dataVector = listVector.getDataVector();
                                java.util.List<Float> values = new ArrayList<>();
                                if (dataVector instanceof Float4Vector) {
                                    Float4Vector fv = (Float4Vector) dataVector;
                                    for (int d = 0; d < dim; d++) {
                                        values.add(fv.get(offset + d));
                                    }
                                }
                                record.addColumn(new StringColumn(
                                        com.alibaba.fastjson2.JSON.toJSONString(values)));
                                break;
                            }
                            default:
                                record.addColumn(new StringColumn(vector.getObject(r).toString()));
                        }
                    }
                    recordSender.sendToWriter(record);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Arrow data", e);
        }
    }
}
