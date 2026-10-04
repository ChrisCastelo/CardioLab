package com.cardio.lab;

/** Console session and interval model, simulated in preview or driven by treadmill telemetry. No transport is reachable here. */
public final class ConsoleSession {
    public boolean preview, running, started, intervals;
    public double speed=2, meters, elapsed, phaseElapsed, phaseMeters;
    public double speedA=2, speedB=6, limitA=60, limitB=60;
    public int incline, phase;
    public String mode="time";

    public void start() {
        if (!preview) throw new IllegalStateException("Treadmill connection has not been verified");
        if (!started) { elapsed=meters=phaseElapsed=phaseMeters=0; phase=0; if(intervals)speed=speedA; }
        started=running=true;
    }
    public void pause() { running=false; }
    public void end() { running=false; started=false; }
    public void quick() { end(); intervals=false; speed=2; start(); }
    public void configure(String mode,double a,double b,double la,double lb) {
        if (!mode.equals("time")&&!mode.equals("distance")&&!mode.equals("manual")) throw new IllegalArgumentException("Choose an interval mode");
        if (!validSpeed(a)||!validSpeed(b)) throw new IllegalArgumentException("Speeds must be 2–12 mph");
        if (!Double.isFinite(la)||!Double.isFinite(lb)||la<10||lb<10||la>36000||lb>36000) throw new IllegalArgumentException("Interval limits must be 10–36,000 seconds or meters");
        if(started)throw new IllegalStateException("End the current session before changing the workout");
        this.mode=mode;speedA=a;speedB=b;limitA=la;limitB=lb;intervals=true;
    }
    private static boolean validSpeed(double s) {return Double.isFinite(s)&&s>=2&&s<=12;}
    public void setSpeed(double value) { if(!validSpeed(value))throw new IllegalArgumentException("Speed must be 2–12 mph");speed=value; }
    public void setIncline(int value) { if(value<0||value>12)throw new IllegalArgumentException("Preview incline must be 0–12");incline=value; }
    public void switchPhase() { phase=1-phase;phaseElapsed=phaseMeters=0;speed=phase==0?speedA:speedB; }
    public void advance(double seconds) {
        if(!preview||!running||!Double.isFinite(seconds)||seconds<=0)return;
        double remaining=seconds;
        while(remaining>1e-8) {
            double segment=remaining, mps=speed*.44704;
            if(intervals&&!mode.equals("manual")) {
                double limit=phase==0?limitA:limitB;
                segment=Math.min(segment,mode.equals("distance")?Math.max(0,(limit-phaseMeters)/mps):Math.max(0,limit-phaseElapsed));
            }
            elapsed+=segment;meters+=segment*mps;phaseElapsed+=segment;phaseMeters+=segment*mps;remaining-=segment;
            if(intervals&&!mode.equals("manual")&&((mode.equals("time")?phaseElapsed:phaseMeters)>=(phase==0?limitA:limitB)-1e-7))switchPhase();
        }
    }
    /** Live treadmill intervals: advances by the controller's measured time and distance; true when the phase switched. */
    public boolean measured(double seconds,double metersMoved){
        if(!intervals||!running||!Double.isFinite(seconds)||!Double.isFinite(metersMoved)||seconds<0||metersMoved<0)return false;
        elapsed+=seconds;meters+=metersMoved;phaseElapsed+=seconds;phaseMeters+=metersMoved;
        if(mode.equals("manual"))return false;
        if((mode.equals("time")?phaseElapsed:phaseMeters)<(phase==0?limitA:limitB)-1e-7)return false;
        switchPhase();return true;
    }
    /** A treadmill workout began (countdown from stop): intervals restart at phase A. */
    public void begin(){elapsed=meters=phaseElapsed=phaseMeters=0;phase=0;speed=intervals?speedA:2;started=true;running=false;}
    public int laps(){return (int)(meters/400);}
}
