import com.cardio.lab.Workout;
import com.cardio.lab.HeartRatePacket;

public class WorkoutTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(HeartRatePacket.bpm(new byte[]{0,65})==65,"8-bit HR");
        check(HeartRatePacket.bpm(new byte[]{1,4,1})==260,"16-bit HR");
        check(HeartRatePacket.bpm(new byte[]{1,65})==-1,"truncated packet");
        check(HeartRatePacket.bpm(new byte[]{4,65})==-1,"contact not detected");
        check(HeartRatePacket.bpm(new byte[]{6,65})==65,"contact detected");
        check(HeartRatePacket.bpm(new byte[]{0,0})==-1,"zero measurement");
        check(HeartRatePacket.bpm(null)==-1,"null packet");
        Workout w=new Workout();w.heart(90);check(w.average()==0,"ignore preview HR");
        w.resume(1000);w.heart(80);w.heart(-1);w.heart(100);w.pause(11000);
        check(w.elapsed==10000,"active duration");check(w.average()==90&&w.hrCount==2,"valid HR average");
        w.heart(250);w.pause(18000);check(w.elapsed==10000&&w.average()==90,"pause excludes time and HR");
        w.resume(21000);w.resume(22000);check(w.duration(26000)==15000,"resume once");
        Workout checkpoint=w.snapshot(26000);check(!checkpoint.running()&&checkpoint.elapsed==15000,"recovery paused");
        check(w.running()&&w.duration(27000)==16000,"snapshot independent");
        checkpoint.resume(50000);checkpoint.pause(53000);check(checkpoint.elapsed==18000,"resume recovered session");
        System.out.println("Workout and Bluetooth packet tests passed");
    }
}
