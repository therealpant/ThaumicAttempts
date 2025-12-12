package therealpant.thaumicattempts.client.gui;

public class PatternGuiLayout {
    private PatternGuiLayout() {}

    public static final int GUI_WIDTH = 194;

    public static final int BACKGROUND_U = 118;
    public static final int BACKGROUND_V = 138;
    public static final int BACKGROUND_W = 170;
    public static final int BACKGROUND_H = 111;

    public static final int BACKGROUND_LEFT = (GUI_WIDTH - BACKGROUND_W) / 2;
    public static final int BACKGROUND_TOP = 12;

    public static final int PAPER_W = 98;
    public static final int PAPER_H = 118;

    public static final int PAPER_DRAW_W = PAPER_W;
    public static final int PAPER_DRAW_H = PAPER_H;

    public static final int PAPER_LEFT = BACKGROUND_LEFT + (BACKGROUND_W - PAPER_DRAW_W) / 2;
    public static final int PAPER_TOP = BACKGROUND_TOP + (BACKGROUND_H - PAPER_DRAW_H) / 2;

    public static final int GRID_LEFT = PAPER_LEFT + 24;
    public static final int GRID_TOP = PAPER_TOP + 35;

    public static final int PREVIEW_LEFT = PAPER_LEFT + 42;
    public static final int PREVIEW_TOP = PAPER_TOP + 11;

    public static final int INFUSION_CENTER_LEFT = PAPER_LEFT + 42;
    public static final int INFUSION_CENTER_TOP = PAPER_TOP + 52;

    public static final int CELL = 18;

    public static final int PLAYER_INV_U = 83;
    public static final int PLAYER_INV_V = 158;
    public static final int PLAYER_INV_W = 172;
    public static final int PLAYER_INV_H = 86;
    public static final int PLAYER_INV_BG_LEFT = (GUI_WIDTH - PLAYER_INV_W) / 2;
    public static final int PLAYER_INV_BG_TOP = BACKGROUND_TOP + BACKGROUND_H + 18;
    public static final int PLAYER_INV_LEFT = PLAYER_INV_BG_LEFT + 6;
    public static final int PLAYER_INV_TOP = PLAYER_INV_BG_TOP + 6;

    public static final int GUI_HEIGHT = PLAYER_INV_TOP + PLAYER_INV_H + 12;
}