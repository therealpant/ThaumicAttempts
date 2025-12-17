package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandler;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemcraft.container.ContainerGolemCrafter;
import therealpant.thaumicattempts.golemcraft.item.ItemArcanePattern;
import therealpant.thaumicattempts.golemcraft.item.ItemCraftPattern;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityArcaneCrafter;
import therealpant.thaumicattempts.golemcraft.tile.TileEntityGolemCrafter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class GuiGolemCrafter extends GuiContainer {

    private static final int TEX_W = 354;
    private static final int TEX_H = 256;

    private static final int PREVIEW_X = 242;
    private static final int PREVIEW_Y = 35;

    private static final int GRID_LEFT_GOLEM = 141;
    private static final int GRID_TOP_GOLEM = 44;
    private static final int GRID_STEP_GOLEM = 20; // 16 + 4 gap

    private static final int GRID_LEFT_ARCANE = 143;
    private static final int GRID_TOP_ARCANE = 46;
    private static final int GRID_STEP_ARCANE = 18; // стандартный 16 + 2

    private static final int PATTERN_STEP = 18;

    private static final int[][] ARCANE_CRYSTAL_POINTS = new int[][]{
            {161, 26},
            {124, 47},
            {124, 81},
            {161, 102},
            {199, 81},
            {199, 47}
    };

    private final TileEntityGolemCrafter te;
    private final ResourceLocation backgroundTex;
    private final boolean isArcaneCrafter;
    private IItemHandler patterns;

    // цикл предпросмотра
    private int tickCounter = 0;
    private int lastSwitchTick = 0;
    private int currentSlot = -1;
    private final int switchPeriod = 30; // 1.5 сек @20 TPS

    public GuiGolemCrafter(InventoryPlayer playerInv, TileEntityGolemCrafter te) {
        super(new ContainerGolemCrafter(playerInv, te));
        this.te = te;
        this.isArcaneCrafter = te instanceof TileEntityArcaneCrafter;
        this.backgroundTex = new ResourceLocation(ThaumicAttempts.MODID,
                "textures/gui/" + (isArcaneCrafter ? "a_crafter.png" : "crafter.png"));

        this.xSize = TEX_W;
        this.ySize = TEX_H;

        this.patterns = tryGetPatternHandlerReflective(te);
    }

    private static IItemHandler tryGetPatternHandlerReflective(TileEntityGolemCrafter te) {
        try {
            Method m = te.getClass().getMethod("getPatternHandler");
            Object res = m.invoke(te);
            if (res instanceof IItemHandler) return (IItemHandler) res;
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tickCounter++;

        boolean powered = te.getWorld() != null && te.getWorld().isBlockPowered(te.getPos());
        Integer activeIdx = powered ? tryGetActiveIndex(te) : null;

        if (powered) {
            if (activeIdx != null) currentSlot = activeIdx; // зафиксироваться на активе
            return; // пауза автоперебора при редстоуне
        }

        if (tickCounter - lastSwitchTick >= switchPeriod) {
            lastSwitchTick = tickCounter;
            nextNonEmptyPattern();
        }
    }

    private static Integer tryGetActiveIndex(TileEntityGolemCrafter te) {
        try {
            Method m = te.getClass().getMethod("getActivePatternIndex");
            Object res = m.invoke(te);
            if (res instanceof Integer) return (Integer) res;
        } catch (Exception ignored) {}
        return null;
    }

    private void nextNonEmptyPattern() {
        if (patterns == null) { currentSlot = -1; return; }
        int slots = Math.min(15, patterns.getSlots());
        if (slots <= 0) { currentSlot = -1; return; }

        int start = (currentSlot + 1 + slots) % slots;
        int i = start;
        do {
            ItemStack st = patterns.getStackInSlot(i);
            if (!st.isEmpty()) { currentSlot = i; return; }
            i = (i + 1) % slots;
        } while (i != start);

        currentSlot = -1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F,1F,1F,1F);

        final int x = (width - xSize) / 2;
        final int y = (height - ySize) / 2;

        mc.getTextureManager().bindTexture(backgroundTex);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        renderRecipeGrid(x, y);
        renderResultPreview(x, y);
        renderArcaneCrystals(x, y);
    }

    private void highlightCurrentPatternSlot(int guiLeft, int guiTop) {
        if (currentSlot < 0) return;


        final int baseX = guiLeft + ContainerGolemCrafter.PANEL_LEFT;
        final int baseY = guiTop  + ContainerGolemCrafter.PANEL_TOP;

        int col = currentSlot % ContainerGolemCrafter.PANEL_COLS; // 0..2
        int row = currentSlot / ContainerGolemCrafter.PANEL_COLS; // 0..4

        int sx = baseX + col * PATTERN_STEP;
        int sy = baseY + row * PATTERN_STEP;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        drawRect(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
        GlStateManager.disableBlend();
    }

    private void renderRecipeGrid(int guiLeft, int guiTop) {
        List<ItemStack> grid = getPreviewForCurrent();
        if (grid == null) grid = java.util.Collections.emptyList();

        // внутренняя область сетки (после рамки 4 px)
        final int left = guiLeft + (isArcaneCrafter ? GRID_LEFT_ARCANE : GRID_LEFT_GOLEM);
        final int top  = guiTop  + (isArcaneCrafter ? GRID_TOP_ARCANE  : GRID_TOP_GOLEM);
        final int step = isArcaneCrafter ? GRID_STEP_ARCANE : GRID_STEP_GOLEM;

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < Math.min(9, grid.size()); i++) {
            ItemStack st = grid.get(i);
            if (st == null || st.isEmpty()) continue;

            int col = i % 3;
            int row = i / 3;

            int cx = left + col * step;
            int cy = top  + row * step;

            itemRender.renderItemAndEffectIntoGUI(st, cx, cy);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, st, cx, cy, null);
        }
        RenderHelper.disableStandardItemLighting();
    }

    private void renderResultPreview(int guiLeft, int guiTop) {
        ItemStack preview = getResultPreviewForCurrent();
        if (preview == null || preview.isEmpty()) return;

        RenderHelper.enableGUIStandardItemLighting();
        int itemX = guiLeft + PREVIEW_X;
        int itemY = guiTop  + PREVIEW_Y;
        itemRender.renderItemAndEffectIntoGUI(preview, itemX, itemY);
        itemRender.renderItemOverlayIntoGUI(this.fontRenderer, preview, itemX, itemY, null);
        RenderHelper.disableStandardItemLighting();
    }

    private void renderArcaneCrystals(int guiLeft, int guiTop) {
        if (!isArcaneCrafter) return;

        ItemStack pattern = getCurrentPatternStack();
        if (pattern.isEmpty()) return;

        int[] counts = ItemArcanePattern.getCrystalCounts(pattern);
        Aspect[] primals = ItemArcanePattern.PRIMALS;

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5F, 0F, 0F);
        for (int i = 0; i < ARCANE_CRYSTAL_POINTS.length && i < primals.length; i++) {
            if (counts == null || i >= counts.length || counts[i] <= 0) continue;

            ItemStack icon = ThaumcraftApiHelper.makeCrystal(primals[i], counts[i]);
            int cx = guiLeft + ARCANE_CRYSTAL_POINTS[i][0];
            int cy = guiTop  + ARCANE_CRYSTAL_POINTS[i][1];

            itemRender.renderItemAndEffectIntoGUI(icon, cx, cy);
            String overlay = counts[i] > 1 ? String.valueOf(Math.min(99, counts[i])) : null;
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, icon, cx, cy, overlay);
        }
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
    }


    private ItemStack getCurrentPatternStack() {
        if (patterns == null || currentSlot < 0) return ItemStack.EMPTY;
        return patterns.getStackInSlot(currentSlot);
    }

    /* ---------- Источники превью (тот же порядок, что и раньше) ---------- */

    private List<ItemStack> getPreviewForCurrent() {
        if (patterns == null || currentSlot < 0) return java.util.Collections.emptyList();

        ItemStack pat = patterns.getStackInSlot(currentSlot);
        if (pat.isEmpty()) return java.util.Collections.emptyList();

        @SuppressWarnings("unchecked")
        List<ItemStack> viaTE = tryGetPreviewFromTE(currentSlot);
        if (viaTE != null && !viaTE.isEmpty()) return padTo9(viaTE);

        IItemHandler h = pat.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (h != null) {
            List<ItemStack> out = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) out.add(i < h.getSlots() ? h.getStackInSlot(i) : ItemStack.EMPTY);
            return out;
        }

        net.minecraft.nbt.NBTTagCompound tag = pat.getTagCompound();
        if (tag != null && tag.hasKey("Grid", net.minecraftforge.common.util.Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList lst = tag.getTagList("Grid", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            List<ItemStack> out = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                ItemStack st = ItemStack.EMPTY;
                if (i < lst.tagCount()) {
                    net.minecraft.nbt.NBTTagCompound c = lst.getCompoundTagAt(i);
                    if (!c.getKeySet().isEmpty()) st = new ItemStack(c);
                }
                out.add(st);
            }
            return out;
        }

        return java.util.Collections.emptyList();
    }

    private ItemStack getResultPreviewForCurrent() {
        if (patterns == null || currentSlot < 0) return ItemStack.EMPTY;
        ItemStack pat = getCurrentPatternStack();
        if (pat.isEmpty()) return ItemStack.EMPTY;

        if (pat.getItem() instanceof ItemArcanePattern) {
            ItemStack r = ItemArcanePattern.calcArcaneResultPreview(pat, mc.world);
            if (r != null && !r.isEmpty()) return r;
        }
        if (pat.getItem() instanceof ItemCraftPattern) {
            ItemStack r = ItemCraftPattern.calcResultPreview(pat, mc.world);
            if (r != null && !r.isEmpty()) return r;
        }

        net.minecraft.nbt.NBTTagCompound t = pat.getTagCompound();
        if (t != null) {
            if (t.hasKey("Result", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
                ItemStack r = new ItemStack(t.getCompoundTag("Result"));
                if (!r.isEmpty()) return r;
            }
            if (t.hasKey(ItemArcanePattern.TAG_RESULT, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
                ItemStack r = new ItemStack(t.getCompoundTag(ItemArcanePattern.TAG_RESULT));
                if (!r.isEmpty()) return r;
            }
        }

        List<ItemStack> grid = getPreviewForCurrent();
        if (grid != null && !grid.isEmpty()) {
            InventoryCrafting inv = new InventoryCrafting(new Container() {
                @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p) { return false; }
            }, 3, 3);
            for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, i < grid.size() ? grid.get(i).copy() : ItemStack.EMPTY);
            ItemStack r = CraftingManager.findMatchingResult(inv, mc.world);
            if (r != null) return r;
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> tryGetPreviewFromTE(int slot) {
        try {
            Method m = te.getClass().getMethod("getPatternPreview", int.class);
            Object res = m.invoke(te, slot);
            if (res instanceof List) return (List<ItemStack>) res;
        } catch (Exception ignored) {}
        return null;
    }

    private static List<ItemStack> padTo9(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) out.add(i < in.size() ? (in.get(i) == null ? ItemStack.EMPTY : in.get(i)) : ItemStack.EMPTY);
        return out;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Пауза перебора при редстоуне и показ активного паттерна
        boolean powered = te.getWorld() != null && te.getWorld().isBlockPowered(te.getPos());
        if (powered) {
            Integer activeIdx = tryGetActiveIndex(te);
            if (activeIdx != null) currentSlot = activeIdx;
        }

        // Если редстоун не зафиксировал слот — показываем ПЕРВЫЙ НЕПУСТОЙ сразу
        if (currentSlot < 0) nextNonEmptyPattern();

        // Чтобы автоперебор не щёлкал сразу после открытия
        lastSwitchTick = tickCounter;
    }
}
