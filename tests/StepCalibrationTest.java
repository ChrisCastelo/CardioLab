import com.cardio.lab.StepCalibration;
import com.cardio.lab.StepCalibration.Sample;
import java.util.*;

public class StepCalibrationTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static List<Sample> recording(int seconds,boolean walking,boolean echoes){
        ArrayList<Sample> data=new ArrayList<>();
        for(int i=0;i<(seconds+3)*100;i++){
            double t=i*.01;double value=9.81+.008*Math.sin(2*Math.PI*7*t);
            if(walking){
                double phase=t%(2.0/3);value+=.9*Math.exp(-Math.pow((phase-.12)/.04,2));
                if(echoes)value+=.24*Math.exp(-Math.pow((phase-.43)/.04,2));
            }
            data.add(new Sample(1_000_000_000L+i*10_000_000L,0,0,value,i>=300));
        }return data;
    }
    public static void main(String[] args){
        List<Sample> noise=recording(6,false,false),walking=recording(30,true,true);
        StepCalibration.Result r=StepCalibration.fit(noise,walking,45,.015);
        check(r.accepted,"Echo recording must fit: "+r.explanation);
        check(r.before>70,"Fixture should reproduce double counting: "+r.before);
        check(Math.abs(r.after-45)<=2,"Fit manual ground truth: "+r.after);
        check(r.noiseSteps==0&&r.threshold>.015,"Reject noise and avoid over-sensitive threshold");
        check(r.low<r.threshold&&r.threshold<r.high,"Choose inside stable band");
        check(StepCalibration.replay(walking,r.threshold)==r.after,"Suggested threshold uses production detector");
        check(!StepCalibration.fit(noise,recording(30,false,false),45,.12).accepted,"Reject motionless walking recording");
        check(!StepCalibration.fit(noise,walking,0,.12).accepted,"Reject zero manual count");
        check(!StepCalibration.fit(noise,walking,500,.12).accepted,"Reject implausible count");
        check(!StepCalibration.fit(noise,walking.subList(0,100),45,.12).accepted,"Reject short data");
        ArrayList<Sample> gap=new ArrayList<>(walking);gap.subList(700,760).clear();
        check(!StepCalibration.fit(noise,gap,45,.12).accepted,"Reject sensor interruption");
        ArrayList<Sample> invalid=new ArrayList<>(walking);invalid.set(700,new Sample(invalid.get(700).ns,0,0,Double.NaN,true));
        check(!StepCalibration.fit(noise,invalid,45,.12).accepted,"Reject invalid acceleration");
        List<Sample> unequal=recording(30,true,false);
        check(!StepCalibration.fit(noise,unequal,110,.12).accepted,"Reject target unattainable with a threshold");
        System.out.printf(Locale.US,"PASS: two-stage calibration; echo count %d -> %d for 45 contacts, threshold %.3f; invalid/flat/gapped/incompatible data rejected%n",r.before,r.after,r.threshold);
    }
}
