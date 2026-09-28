package com.cardio.lab;

import java.util.ArrayDeque;

/** Experimental vibration peak counter. A peak is a candidate foot strike, not ground truth. */
public final class StepDetector {
    public static final String VERSION = "magnitude-peaks-v1";
    private double baseline, filtered, previous, beforePrevious;
    private long lastTime, warmUntil, previousTime, lastPeak;
    private boolean armed = true;
    private double threshold = 0.12;
    private int steps;
    private final ArrayDeque<Long> peaks = new ArrayDeque<>();

    public void setThreshold(double value) { threshold = Math.max(0.015, value); }
    public double threshold() { return threshold; }
    public double signal() { return filtered; }
    public int steps() { return steps; }
    public void restoreSteps(int value) { steps = Math.max(0, value); }
    public void resetSignal() {
        lastTime = previousTime = lastPeak = 0;
        filtered = previous = beforePrevious = 0;
        armed = true;
        peaks.clear();
    }
    public void reset() { steps = 0; resetSignal(); }

    public boolean add(long timeNs, double x, double y, double z, boolean count) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        if (lastTime != 0 && timeNs <= lastTime) return false;
        double magnitude = Math.sqrt(x*x + y*y + z*z);
        if (lastTime == 0 || timeNs - lastTime > 500_000_000L) {
            resetSignal();
            baseline = magnitude;
            lastTime = previousTime = timeNs;
            warmUntil = timeNs + 1_000_000_000L;
            return false;
        }
        double dt = (timeNs - lastTime) / 1e9;
        lastTime = timeNs;
        // Gravity / slow drift removal (~0.5 Hz), followed by an 8 Hz low-pass.
        baseline += (1 - Math.exp(-2*Math.PI*0.5*dt)) * (magnitude-baseline);
        filtered += (1 - Math.exp(-2*Math.PI*8*dt)) * (magnitude-baseline-filtered);
        boolean hit = false;
        if (filtered < threshold * 0.35) armed = true;
        if (count && timeNs >= warmUntil && armed && previous > threshold &&
                previous > beforePrevious && previous >= filtered && previousTime-lastPeak >= 250_000_000L) {
            steps++;
            lastPeak = previousTime;
            peaks.addLast(lastPeak);
            while (peaks.size() > 9) peaks.removeFirst();
            armed = false;
            hit = true;
        }
        beforePrevious = previous;
        previous = filtered;
        previousTime = timeNs;
        return hit;
    }

    public int cadence(long timeNs) {
        while (!peaks.isEmpty() && timeNs - peaks.peekFirst() > 5_000_000_000L) peaks.removeFirst();
        if (peaks.size() < 2 || timeNs-lastPeak > 2_000_000_000L) return 0;
        return (int)Math.round(60e9 * (peaks.size()-1) / (peaks.peekLast()-peaks.peekFirst()));
    }
}
