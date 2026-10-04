package com.cardio.lab;

/** Bluetooth Heart Rate Measurement; -1 means malformed, no contact, or no measurement. */
public final class HeartRatePacket {
    public static int bpm(byte[] data) {
        if (data == null || data.length < 2) return -1;
        int flags = data[0] & 255;
        if ((flags & 4) != 0 && (flags & 2) == 0) return -1;
        boolean wide = (flags & 1) != 0;
        if (wide && data.length < 3) return -1;
        int value = (data[1] & 255) + (wide ? ((data[2] & 255) << 8) : 0);
        return value > 0 && value <= 300 ? value : -1;
    }
}
