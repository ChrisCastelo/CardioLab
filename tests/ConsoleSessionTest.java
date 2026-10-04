import com.cardio.lab.ConsoleSession;

public final class ConsoleSessionTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected){check(Math.abs(actual-expected)<.00001,actual+" != "+expected);}
    public static void main(String[] args){
        ConsoleSession s=new ConsoleSession();
        try{s.start();throw new AssertionError("Unverified treadmill must never start");}catch(IllegalStateException expected){}
        s.running=true;s.advance(60);near(s.meters,0);s.running=false;
        s.preview=true;s.quick();s.advance(10);near(s.elapsed,10);near(s.meters,8.9408);
        s.pause();s.advance(100);near(s.elapsed,10);s.start();s.advance(5);near(s.elapsed,15);
        s.end();s.configure("time",2,6,10,20);s.start();s.advance(35);
        near(s.elapsed,35);near(s.meters,(15*2+20*6)*.44704);check(s.phase==0,"crosses multiple boundaries without losing time");near(s.phaseElapsed,5);
        s.end();s.configure("distance",2,6,10,20);s.start();s.advance(10/(2*.44704)+20/(6*.44704)+5/(2*.44704));near(s.meters,35);check(s.phase==0,"distance phases");near(s.phaseMeters,5);
        s.end();s.configure("manual",2,6,10,20);s.start();s.advance(600);check(s.phase==0,"manual does not auto switch");s.switchPhase();near(s.speed,6);
        s.pause();double meters=s.meters;s.advance(Double.NaN);near(s.meters,meters);
        for(double speed:new double[]{1,13,Double.NaN,Double.POSITIVE_INFINITY})try{s.setSpeed(speed);throw new AssertionError("Invalid speed accepted");}catch(IllegalArgumentException expected){}
        s.end();try{s.configure("time",2,6,0,20);throw new AssertionError("Zero duration accepted");}catch(IllegalArgumentException expected){}
        s.quick();s.setSpeed(12);s.advance(400/(12*.44704)+.001);check(s.laps()==1,"400m lap");
        System.out.println("Console session tests passed: disconnected gate, active time, timed/distance/manual intervals, bounds, lap count");
    }
}
