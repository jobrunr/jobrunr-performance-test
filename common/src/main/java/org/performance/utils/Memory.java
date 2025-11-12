package org.performance.utils;

public class Memory {

    private final int size;
    private final Unit unit;

    public enum Unit {
        bytes(1), kilobytes(1024), megabytes(1024 * 1024), gigabytes(1024 * 1024 * 1024);

        private final long bytesValue;

        Unit(long bytesValue) {
            this.bytesValue = bytesValue;
        }


        public long toBytes() {
            return bytesValue;
        }
    }


    public Memory(int size, Unit unit) {
        this.size = size;
        this.unit = unit;
    }

    public long toBytes() {
        return size * unit.toBytes();
    }

    public static Memory of(int size, Unit unit) {
        return new Memory(size, unit);
    }
}
