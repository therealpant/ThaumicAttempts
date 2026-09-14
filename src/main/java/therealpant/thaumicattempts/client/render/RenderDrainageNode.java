package therealpant.thaumicattempts.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import thaumcraft.client.fx.ParticleEngine;
import software.bernie.geckolib3.geo.render.built.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoBlockRenderer;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.model.DrainageNodeModel;
import therealpant.thaumicattempts.world.tile.TileDrainageNode;

public class RenderDrainageNode extends GeoBlockRenderer<TileDrainageNode> {
    private static final ResourceLocation EMISSIVE =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/blocks/drenage_e.png");
    /** Midpoint between the updated model's bone (13 px) and bone2 (49 px). */
    private static final double ITEM_HEIGHT = 31.0D / 16.0D;

    public RenderDrainageNode() { super(new DrainageNodeModel()); }

    @Override
    public void render(TileDrainageNode tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile == null) return;
        float[] previousLight = RenderSafety.captureLightmap();
        try {
            super.render(tile, x, y, z, partialTicks, destroyStage, alpha);
            renderEmissive(tile, x, y, z, partialTicks);
        } finally {
            RenderSafety.restoreLightmap(previousLight);
            RenderSafety.resetGlState();
        }
        if (MinecraftForgeClient.getRenderPass() == 0) {
            renderSideRunes(tile,x,y,z,partialTicks);
            renderHeldItem(tile, x, y, z, partialTicks);
            renderImpactExtraction(tile, x, y, z, partialTicks);
            ParadoxPedestalTransferRenderer.render(tile, tile.getPedestalAutomation(),
                    x, y, z, partialTicks);
        }
    }

    private void renderSideRunes(TileDrainageNode tile,double x,double y,double z,float partialTicks) {
        if (!tile.isBarrierNode() && !tile.isWorking() && !tile.isRecovering()) return;
        double time=tile.getWorld()==null ? partialTicks
                : tile.getWorld().getTotalWorldTime()+partialTicks;
        boolean busy=tile.isWorking() || tile.isRecovering();
        boolean stabilizer=busy ? tile.getActivityType()==2 : tile.isDamper();
        float red=stabilizer ? .62F : .22F;
        float green=stabilizer ? .46F : .38F;
        float alpha=tile.isWorking() ? .95F : tile.isRecovering() ? .55F : .72F;
        GlStateManager.pushMatrix();
        try {
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            Minecraft.getMinecraft().getTextureManager().bindTexture(ParticleEngine.particleTexture);
            Tessellator tessellator=Tessellator.getInstance();
            BufferBuilder buffer=tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR);
            for (int face=0;face<4;face++) {
                Vec3d normal=face==0 ? new Vec3d(0,0,-1) : face==1 ? new Vec3d(1,0,0)
                        : face==2 ? new Vec3d(0,0,1) : new Vec3d(-1,0,0);
                Vec3d right=new Vec3d(-normal.z,0,normal.x);
                for (int row=0;row<5;row++) {
                    double phase;
                    double jitter=0;
                    if (busy) {
                        phase=tile.getRuneLockPhase()+row*.1875D+face*.071D;
                        phase-=Math.floor(phase);
                        jitter=.035D*Math.sin(time*.78D+row*2.3D+face*1.7D);
                    } else {
                        phase=time/(118.0D+row*3)+row/5.0D+face*.113D;
                        phase-=Math.floor(phase);
                    }
                    double across=(row%2==0 ? -.16D : .16D)+jitter;
                    Vec3d center=new Vec3d(.5D+normal.x*.506D+right.x*across,
                            .25D+phase*2.50D+(busy ? jitter*.7D : 0),
                            .5D+normal.z*.506D+right.z*across);
                    float fade=busy ? alpha : alpha*(float)Math.pow(Math.sin(Math.PI*phase),2);
                    int glyph=Math.floorMod((int)(tile.getPos().toLong()+face*7+row*11
                            +tile.getActivitySerial()),16);
                    addRune(buffer,x,y,z,center,right,glyph,.115D,red,green,fade);
                }
            }
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void addRune(BufferBuilder buffer,double x,double y,double z,Vec3d center,
                         Vec3d right,int glyph,double half,float red,float green,float alpha) {
        Vec3d up=new Vec3d(0,1,0);
        double u0=glyph/64.0D,u1=u0+1/64.0D,v0=6/64.0D,v1=v0+1/64.0D;
        Vec3d a=center.subtract(right.scale(half)).subtract(up.scale(half));
        Vec3d b=center.add(right.scale(half)).subtract(up.scale(half));
        Vec3d c=center.add(right.scale(half)).add(up.scale(half));
        Vec3d d=center.subtract(right.scale(half)).add(up.scale(half));
        buffer.pos(x+a.x,y+a.y,z+a.z).tex(u0,v1).color(red,green,1,alpha).endVertex();
        buffer.pos(x+b.x,y+b.y,z+b.z).tex(u1,v1).color(red,green,1,alpha).endVertex();
        buffer.pos(x+c.x,y+c.y,z+c.z).tex(u1,v0).color(red,green,1,alpha).endVertex();
        buffer.pos(x+d.x,y+d.y,z+d.z).tex(u0,v0).color(red,green,1,alpha).endVertex();
    }

    private void renderEmissive(TileDrainageNode tile, double x, double y, double z,
                                float partialTicks) {
        GeoModel model = getGeoModelProvider().getModel(
                getGeoModelProvider().getModelLocation(tile));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + 0.0015D, z + 0.5D);
            RenderSafety.setFullBrightLightmap();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            Minecraft.getMinecraft().getTextureManager().bindTexture(EMISSIVE);
            render(model, tile, partialTicks, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void renderHeldItem(TileDrainageNode tile, double x, double y, double z,
                                float partialTicks) {
        ItemStack held = tile.getHeldItem();
        if (held.isEmpty()) return;
        double ticks = tile.getWorld() == null ? partialTicks
                : tile.getWorld().getTotalWorldTime() + partialTicks;
        double boneBob = (Math.sin(Math.toRadians(ticks * 6.0D))
                + Math.sin(Math.toRadians(ticks * 6.0D + 30.0D))) * 0.5D / 16.0D;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + 0.5D, y + ITEM_HEIGHT + boneBob, z + 0.5D);
            GlStateManager.rotate((float) (ticks * 2.0D), 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(0.6D, 0.6D, 0.6D);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(
                    held, ItemCameraTransforms.TransformType.FIXED);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            RenderSafety.resetGlState();
        }
    }

    private void renderImpactExtraction(TileDrainageNode tile, double x, double y, double z, float partialTicks) {
        net.minecraft.util.math.BlockPos wellPos = tile.getWellPosition();
        if (wellPos == null || tile.getWorld()==null || !tile.getWorld().isBlockLoaded(wellPos)) return;
        double age=tile.getWorld().getTotalWorldTime()+partialTicks-tile.getLastImpactTick();
        if (age<0 || age>12) return;
        net.minecraft.tileentity.TileEntity raw = tile.getWorld().getTileEntity(wellPos);
        if (!(raw instanceof therealpant.thaumicattempts.world.tile.TileRiftWell)) return;
        therealpant.thaumicattempts.world.tile.TileRiftWell well = (therealpant.thaumicattempts.world.tile.TileRiftWell) raw;
        if (well.isContaminated() || well.getRiftSize() <= 0) return;
        java.util.Random random=new java.util.Random(tile.getPos().toLong()^tile.getImpactPulse()*0x9E3779B97F4A7C15L);
        double relativeX=tile.getPos().getX()-wellPos.getX();
        double relativeZ=tile.getPos().getZ()-wellPos.getZ();
        double angle=Math.atan2(relativeZ,relativeX);
        double faceStep=Math.PI*2.0D/RenderRiftWell.BARRIER_SIDES;
        double faceNormal=Math.round(angle/faceStep)*faceStep;
        double boundaryRadius=RenderRiftWell.BARRIER_APOTHEM/Math.max(.966D,Math.cos(angle-faceNormal));
        double startX=wellPos.getX()-tile.getPos().getX()+.5D
                +Math.cos(angle)*boundaryRadius;
        double startY=wellPos.getY()-tile.getPos().getY()+.8D
                +random.nextDouble()*(RenderRiftWell.BARRIER_HEIGHT-1.6D);
        double startZ=wellPos.getZ()-tile.getPos().getZ()+.5D
                +Math.sin(angle)*boundaryRadius;
        double head=age/12.0D;
        for (int i=0;i<5;i++) {
            double t=head-i*.035D;
            if (t<0 || t>1) continue;
            double fade=Math.sin(t*Math.PI);
            RenderRiftWell.renderParadoxumBillboard(
                    x+startX+(.5D-startX)*t,
                    y+startY+(ITEM_HEIGHT-startY)*t+Math.sin(t*Math.PI)*.75D,
                    z+startZ+(.5D-startZ)*t,.58F,(float)fade);
        }
    }
}
