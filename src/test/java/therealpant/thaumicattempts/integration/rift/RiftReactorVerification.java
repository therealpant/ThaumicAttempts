package therealpant.thaumicattempts.integration.rift;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Deterministic balance and world-data regression checks, without launching Minecraft. */
public final class RiftReactorVerification {
    private static int assertions;

    public static void main(String[] args) {
        near(RiftReactor.pearlEfficiency(8),1,"whole pearl");
        near(RiftReactor.pearlEfficiency(1),.55,"last pearl use");
        near(RiftReactor.pearlEfficiency(0),0,"destroyed pearl");
        for (int i=2;i<=8;i++) check(RiftReactor.pearlEfficiency(i)>RiftReactor.pearlEfficiency(i-1),
                "pearl wear is monotonic");

        near(RiftReactor.sourceSize(0),5,"no links have no active source");
        near(RiftReactor.sourceSize(1),61.25,"one link potential");
        near(RiftReactor.sourceSize(8),140,"eight links reach the hard cap");
        near(RiftReactor.collectorSizeLimit(0),60,"empty circuit size ceiling");
        near(RiftReactor.collectorSizeLimit(7),123,"seven collector ceiling");
        near(RiftReactor.collectorSizeLimit(9),140,"nine collectors reach cap");
        near(RiftReactor.equilibriumSize(8,7),123,"collectors cap source size");
        near(RiftReactor.equilibriumSize(4,12),95,"links cap an oversized circuit");
        near(RiftReactor.eventInterval(1),18.5,"weak source cycle");
        near(RiftReactor.eventInterval(8),8,"maximum source cycle");

        check(RiftReactor.rawBurstCount(8,123)==15,"maximum source raw bursts at size 123");
        check(RiftReactor.burstCount(8,123,5)==5,"five whole pearls suppress ten bursts");
        check(RiftReactor.burstCount(8,132,4)==8,"four pearls leave eight bursts");
        check(RiftReactor.burstCount(8,140,3)==10,"three pearls cannot protect capped rift");
        near(RiftReactor.burstStrength(8,123,7),9.72D/1.63D,"7+5 burst strength");
        near(RiftReactor.burstStrength(8,132,8),10.08D/1.72D,"8+4 burst strength");
        check(RiftReactor.configurationLoad(5,RiftReactor.burstStrength(8,123,7),7)<1,
                "7+5 is stable at its ceiling");
        near(RiftReactor.configurationLoad(8,RiftReactor.burstStrength(8,132,8),8),1,
                "8+4 is the productive edge");
        check(RiftReactor.configurationLoad(10,RiftReactor.burstStrength(8,140,9),9)>1,
                "9+3 is not viable at size 140");

        RiftReactor weak=reactor(50);
        simulate(weak,1,12,0,180);
        near(weak.size,61.25,"one link eventually reaches its potential");
        check(!weak.breached(),"weak linked reactor remains safe with collectors");

        RiftReactor capped=reactor(50);
        for (int i=0;i<4;i++) capped.step(8,12,0,1,false,new IdealCircuit(12,1));
        near(capped.targetSize,140,"eight links and enough collectors target size 140");
        check(capped.size>50,"maximum source starts growing towards the cap");

        RiftReactor stable=reactor(123);
        simulate(stable,8,7,5,1800);
        check(!stable.breached() && stable.tension<1,"7 collectors and 5 pearls remain stable");

        RiftReactor edge=reactor(132);
        simulate(edge,8,8,4,1800);
        check(!edge.breached() && edge.tension<1,"8+4 remains on the equilibrium edge");

        RiftReactor unstable=reactor(140);
        simulate(unstable,8,9,3,300);
        check(unstable.breached(),"9+3 deterministically starts emergency shutdown");

        RiftReactor worn=reactor(123);
        double wornPearls=5*RiftReactor.pearlEfficiency(1);
        simulateWithEfficiency(worn,8,7,wornPearls,300);
        check(worn.breached(),"worn pearls can make a previously safe layout fail");

        RiftReactor blocked=reactor(100);
        IdealCircuit noStorage=new IdealCircuit(8,0);
        for (int i=0;i<80 && !blocked.breached();i++) blocked.step(8,8,4,1,false,noStorage);
        check(blocked.breached(),"full containers count as an unavailable outlet");

        RiftReactor idle=reactor(50);
        simulate(idle,0,12,0,600);
        near(idle.size,5,"an unlinked rift settles to dormant size");
        check(idle.pulseSerial==0 && !idle.breached(),"an unlinked well emits no free energy");

        RiftReactor awakening=reactor(5);
        awakening.step(8,6,6,30,true,new IdealCircuit(6,1));
        near(awakening.size,5,"awakening size remains controlled by the tile");
        near(awakening.pulseClock,0,"awakening cannot precharge a cycle");

        verifyBarrierContacts();
        verifyWorldData();
        System.out.println("Verified "+assertions+" reactor balance/persistence assertions.");
    }

    private static void simulate(RiftReactor reactor,int links,int collectors,int pearls,int seconds) {
        simulateWithEfficiency(reactor,links,collectors,pearls,seconds);
    }

    private static void simulateWithEfficiency(RiftReactor reactor,int links,int collectors,
                                               double pearlEfficiency,int seconds) {
        IdealCircuit circuit=new IdealCircuit(collectors,1);
        for (int i=0;i<seconds && !reactor.breached();i++)
            reactor.step(links,collectors,pearlEfficiency,1,false,circuit);
    }

    private static final class IdealCircuit implements RiftReactor.CycleHandler {
        private final int collectors;
        private final double storageScale;
        private int used;
        private IdealCircuit(int collectors,double storageScale) {
            this.collectors=collectors;
            this.storageScale=storageScale;
        }
        @Override public void beginCycle(int burstCount,double burstStrength) { used=0; }
        @Override public double collect(double burstStrength,int burstIndex,int burstCount) {
            if (used++>=collectors) return 0;
            return Math.min(RiftReactor.COLLECTOR_PULSE_CAPACITY,burstStrength)*storageScale;
        }
    }

    private static void verifyWorldData() {
        RiftWellWorldData data=new RiftWellWorldData();
        BlockPos well=new BlockPos(-37,85,123),monolith=new BlockPos(17,90,-46);
        UUID id=UUID.randomUUID();
        data.contaminate(well,id); data.burn(monolith);
        RiftWellWorldData restored=new RiftWellWorldData();
        restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        check(restored.isContaminated(well)&&id.equals(restored.rift(well)),
                "infection and rift UUID survive restart");
        check(restored.consumeBurn(monolith),"legacy deferred burn survives restart");
        check(!restored.consumeBurn(monolith),"legacy burn is consumed exactly once");
        restored.clean(well);
        RiftWellWorldData clean=new RiftWellWorldData();
        clean.readFromNBT(restored.writeToNBT(new NBTTagCompound()));
        check(!clean.isContaminated(well),"cleanup persists");
    }

    private static void verifyBarrierContacts() {
        Vec3d inside=new Vec3d(.5D,2,.5D);
        for (int face=0;face<RiftBarrierGeometry.SIDES;face++) {
            Vec3d normal=RiftBarrierGeometry.normal(face);
            Vec3d outside=inside.add(normal.scale(RiftBarrierGeometry.RADIUS+2));
            RiftBarrierGeometry.Contact hit=RiftBarrierGeometry.intersect(inside,outside);
            check(hit!=null && hit.face==face,"ray reaches barrier face "+face);
            check(RiftBarrierGeometry.contains(hit.point,1.0E-6D),"face contact stays on finite prism");
        }
        check(RiftBarrierGeometry.intersect(inside,new Vec3d(.5D,RiftBarrierGeometry.HEIGHT+2,.5D)).face
                ==RiftBarrierGeometry.SIDES,"ray reaches barrier roof");
        check(RiftBarrierGeometry.intersect(inside,new Vec3d(.5D,-2,.5D)).face
                ==RiftBarrierGeometry.SIDES+1,"ray reaches barrier floor");
    }

    private static RiftReactor reactor(double size) {
        RiftReactor reactor=new RiftReactor();
        reactor.size=size;
        return reactor;
    }
    private static void near(double actual,double expected,String message) {
        check(Math.abs(actual-expected)<1.0E-7D,message+": "+actual);
    }
    private static void check(boolean condition,String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
