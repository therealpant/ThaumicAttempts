package therealpant.thaumicattempts.golemcraft.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import therealpant.thaumicattempts.ThaumicAttempts;

/**
 * Client-side renderer that overlays pattern ingredient icons inside tooltips when Shift is held.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ThaumicAttempts.MODID)
public final class PatternTooltipRenderer {

    private static final int CELL_SPACING = 18;
    private static final int GRID_COLS = 3;

    private PatternTooltipRenderer() {}

    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.PostText event) {
        if (!GuiScreen.isShiftKeyDown()) return;

        ItemStack stack = event.getStack();
        if (stack == null || stack.isEmpty()) return;

        Item item = stack.getItem();
        if (!(item instanceof ItemBasePattern)) return;

        NonNullList<ItemStack> grid = ItemBasePattern.readGrid(stack);
        if (!ItemBasePattern.hasAnyStack(grid)) return;

        boolean isArcane = item instanceof ItemArcanePattern;
        boolean drawCrystals = false;
        int[] crystals = null;
        if (isArcane) {
            crystals = ItemArcanePattern.getCrystalCounts(stack);
            drawCrystals = hasCrystals(crystals);
        }

        int iconRows = 3 + (drawCrystals ? 1 : 0);
        int reservedLines = iconRows * 2;
        int firstPlaceholderIndex = Math.max(0, event.getLines().size() - reservedLines);

        FontRenderer font = event.getFontRenderer();
        RenderItem renderer = Minecraft.getMinecraft().getRenderItem();

        int startX = event.getX() + 6;
        int startY = event.getY() + 4 + font.FONT_HEIGHT * firstPlaceholderIndex
                - (16 - font.FONT_HEIGHT) / 2; // nudge up so icons sit tighter to the result line

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                ItemStack cell = grid.get(row * GRID_COLS + col);
                if (cell == null || cell.isEmpty()) continue;

                int drawX = startX + col * CELL_SPACING;
                int drawY = startY + row * CELL_SPACING;
                renderer.renderItemAndEffectIntoGUI(cell, drawX, drawY);
                renderer.renderItemOverlayIntoGUI(font, cell, drawX, drawY, null);
            }
        }

        if (isArcane && drawCrystals) {
            int crystalY = startY + 3 * CELL_SPACING;
            for (int i = 0; i < ItemArcanePattern.PRIMALS.length; i++) {
                int amount = crystals[i];
                if (amount <= 0) continue;

                Aspect aspect = ItemArcanePattern.PRIMALS[i];
                ItemStack crystal = ThaumcraftApiHelper.makeCrystal(aspect, amount);
                if (crystal == null || crystal.isEmpty()) continue;

                int drawX = startX + i * CELL_SPACING;
                renderer.renderItemAndEffectIntoGUI(crystal, drawX, crystalY);
                renderer.renderItemOverlayIntoGUI(font, crystal, drawX, crystalY, String.valueOf(amount));
            }
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
    }

    private static boolean hasCrystals(int[] crystals) {
        if (crystals == null) return false;
        for (int v : crystals) {
            if (v > 0) return true;
        }
        return false;
    }
}
