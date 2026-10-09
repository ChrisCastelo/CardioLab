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
    /** 45 contacts in 30 s with a lighter foot (half strength), random strike spread, belt echo and noisy motor. */
    static List<Sample> uneven(int seconds,boolean walking,long seed){
        Random random=new Random(seed);ArrayList<Sample> data=new ArrayList<>();double period=30.0/45;double[] amplitude=new double[400];
        for(int k=0;k<amplitude.length;k++)amplitude[k]=(k%2==0?1:.5)*Math.exp(.25*random.nextGaussian());
        for(int i=0;i<(seconds+3)*100;i++){
            double t=i*.01,value=9.81+.09*(Math.sin(2*Math.PI*7*t)+.6*random.nextGaussian());
            if(walking){int k=(int)(t/period);double phase=t-k*period;value+=amplitude[k]*(.9*Math.exp(-Math.pow((phase-.12)/.04,2))+.3*Math.exp(-Math.pow((phase-.35)/.04,2)));}
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
        // Uneven feet over a noisy belt: exact-count bands are narrow and the lighter foot sits near the noise floor.
        for(long seed=1;seed<=20;seed++){
            StepCalibration.Result u=StepCalibration.fit(uneven(6,false,seed),uneven(30,true,seed),45,.328);
            check(u.accepted,"Uneven walk "+seed+" must fit: "+u.explanation);
            check(Math.abs(u.after-45)<=2&&u.noiseSteps==0,"Uneven walk "+seed+" within 5% without empty-belt steps: "+u.after);
        }
        System.out.printf(Locale.US,"PASS: two-stage calibration; echo count %d -> %d for 45 contacts, threshold %.3f; uneven feet over a noisy belt fit; invalid/flat/gapped/incompatible data rejected%n",r.before,r.after,r.threshold);
    }
}
