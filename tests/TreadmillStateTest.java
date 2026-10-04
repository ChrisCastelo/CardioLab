import com.cardio.lab.TreadmillState;
import com.cardio.lab.TreadmillState.Phase;

/** Frames are copied from the 2026-10-04 supervised capture (stock FitOS logs). */
public final class TreadmillStateTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected){check(Math.abs(actual-expected)<.00001,actual+" != "+expected);}
    private static byte[] hex(String h){byte[] b=new byte[h.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(h.substring(i*2,i*2+2),16);return b;}
    private static String rcv(String hex){return "10-04 15:19:42.855   954  1437 I SearialPortManager: RCV "+hex;}
    public static void main(String[] args){
        TreadmillState t=new TreadmillState();
        check(!t.live(0),"no data is not live");
        check(!t.line("10-04 15:19:42.856   954  1436 I SearialPortManager: SNT F0D00111D2",1000),"transmit echoes are ignored");
        check(!t.line("10-04 15:19:42.856   954  1437 I SerialCommManager: RCV FFFFFFF0FFFFFFD00111FFFFFFD2",1000),"sign-extended duplicate tag is ignored");
        t.line(rcv("F0D00111D2"),1000);check(t.phase==Phase.COUNTDOWN,"countdown");check(t.moving(),"countdown counts as moving");
        t.line(rcv("F0D30201F4BA"),1000);near(t.speedMph(),0);
        t.line(rcv("F0D00101C2"),4000);check(t.phase==Phase.RUNNING,"start");near(t.speedMph(),.5);check(t.live(6000),"fresh");check(!t.live(7001),"stale after 3 s");
        t.line(rcv("F0D30205DCA6"),5000);near(t.speedMph(),1.5);
        t.line(rcv("F0D109010600000078000D0056"),6000);
        check(t.elapsed==262,"elapsed seconds above 255");near(t.distanceMiles(),.120);check(t.calories==13,"calories");check(t.heartRate==0,"no strap");
        t.line(rcv("F0D00102C3"),7000);t.line(rcv("F0D3020000C5"),7000);check(t.phase==Phase.PAUSED,"pause");near(t.speedMph(),0);
        t.line(rcv("F0D00100C1"),8000);check(t.phase==Phase.STOPPED,"stop");
        t.line(rcv("F0D001AA6B"),9000);check(t.phase==Phase.SAFETY_KEY_OUT,"safety key");check(!t.moving(),"key out is not moving");
        long before=t.frames;t.line(rcv("F0D001AA6C"),9000);check(t.frames==before&&t.badFrames==1,"bad checksum rejected");
        TreadmillState f=new TreadmillState();f.line(rcv("F0D10900"),1);check(f.frames==0,"partial frame waits");
        f.line(rcv("1E00000004000000EC"),2);check(f.elapsed==30&&f.distanceRaw==4,"fragments reassemble");
        f.line(rcv("F0A3070C002EE001F400A9"),3);check(f.maxSpeedRaw==12000&&f.minSpeedRaw==500&&f.maxIncline==12,"limits");
        f.line(rcv("F0A10723DD00E70BFF018A"),4);check(f.miles,"unit code 1 is miles");
        check(java.util.Arrays.equals(TreadmillState.heartbeat(0x0C),hex("F0A0010C9D")),"heartbeat matches the stock frame");
        check(java.util.Arrays.equals(TreadmillState.heartbeat(0xFF),hex("F0A001FF90")),"heartbeat counter wraps in one byte");
        TreadmillState e=new TreadmillState();byte[] rx=hex("F0D109000000000000000000CAF0D00102C3F0D3020000C5F0A0010C9D");
        java.util.List<byte[]> echoes=e.bytes(rx,rx.length,1);
        check(echoes.size()==2&&java.util.Arrays.equals(echoes.get(0),hex("F0D00102C3"))&&java.util.Arrays.equals(echoes.get(1),hex("F0D3020000C5")),"only D0 and D3 are echoed, verbatim");
        check(e.phase==Phase.PAUSED&&e.frames==4,"raw bytes update state");
        TreadmillState c=new TreadmillState();
        check(c.setSpeed(2,0)==null&&c.setIncline(1,0)==null&&c.setState(2,0)==null&&c.setState(0,0)==null,"only start is allowed before running");
        check(java.util.Arrays.equals(c.setState(1,100),hex("F0B00101A2")),"B0 start frame");
        check(c.confirmPending(2100)&&!c.confirmFailed(2100)&&c.confirmFailed(2101),"start waits 2 s for confirmation");
        c.line(rcv("F0D00111D2"),300);check(c.pendingKind==null,"countdown confirms start");
        c.line(rcv("F0D00101C2"),3300);
        check(java.util.Arrays.equals(c.setSpeed(2,4000),hex("F0B20207D580")),"B2 2.0 mph adds the stock +5");
        c.line(rcv("F0D30207D09C"),4100);check(c.pendingKind==null,"D3 2000 confirms 2.0 mph");
        check(c.setSpeed(12.5,5000)==null&&c.setSpeed(0.4,5000)==null&&c.setSpeed(Double.NaN,5000)==null,"speed outside controller limits refused");
        check(java.util.Arrays.equals(c.setIncline(3,5000),hex("F0B10103A5")),"B1 incline frame");
        c.line(rcv("F0D20103C6"),5100);check(c.pendingKind==null&&c.incline==3,"D2 confirms incline");
        check(c.setIncline(13,5200)==null&&c.setIncline(-1,5200)==null,"incline outside limits refused");
        check(java.util.Arrays.equals(c.setState(2,6000),hex("F0B00102A3")),"B0 pause frame");
        c.line(rcv("F0D00102C3"),6100);check(c.pendingKind==null&&c.setSpeed(3,6200)==null,"paused belt refuses speed");
        check(java.util.Arrays.equals(c.setState(0,7000),hex("F0B00100A1")),"B0 stop from pause");
        java.util.List<byte[]> h=TreadmillState.handshake();String[] stock={"F0A10091","F0A30093","F0A70097","F072010164","F0A30093","F0730063"};
        for(int i=0;i<stock.length;i++)check(java.util.Arrays.equals(h.get(i),hex(stock[i])),"handshake frame "+i+" matches the stock capture");
        System.out.println("Treadmill state tests passed: phases, speed, D1 fields, freshness, checksum, fragments, limits, units, heartbeat, echoes, commands, confirmation, bounds, handshake");
    }
}
