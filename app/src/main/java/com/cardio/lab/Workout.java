package com.cardio.lab;

import java.util.UUID;

/** Active time excludes pauses. HR average includes valid notifications received while running. */
public final class Workout {
    public String id=UUID.randomUUID().toString();
    public long date, elapsed, hrTotal, hrCount;
    public int steps;
    public double threshold;
    public String source="";
    private long resumed;
    private boolean running;
    public void resume(long now) { if(!running){resumed=now;running=true;} }
    public void pause(long now) { if(running){elapsed=duration(now);running=false;} }
    public long duration(long now) { return elapsed+(running?Math.max(0,now-resumed):0); }
    public boolean running() { return running; }
    public void heart(int bpm) { if(running&&bpm>0&&bpm<=300){hrTotal+=bpm;hrCount++;} }
    public int average() { return hrCount==0?0:(int)Math.round((double)hrTotal/hrCount); }
    public Workout snapshot(long now) {
        Workout c=new Workout();c.id=id;c.date=date;c.elapsed=duration(now);c.hrTotal=hrTotal;c.hrCount=hrCount;c.steps=steps;c.threshold=threshold;c.source=source;return c;
    }
}
