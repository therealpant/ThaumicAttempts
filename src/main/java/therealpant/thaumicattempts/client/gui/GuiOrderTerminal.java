package therealpant.thaumicattempts.client.gui;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import thaumcraft.client.gui.plugins.GuiImageButton;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.client.ClientCatalogCache;
import therealpant.thaumicattempts.golemnet.container.ContainerOrderTerminal;
import therealpant.thaumicattempts.golemnet.net.msg.C2S_RequestCatalogPage;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.golemnet.net.msg.C2S_OrderAdjust;
import therealpant.thaumicattempts.golemnet.net.msg.C2S_OrderSubmit;

import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;


import javax.annotation.Nullable;
import java.util.*;

/** Терминал без интерактива. Надёжная догрузка каталога. Версия со слоями Depth. */
public class GuiOrderTerminal extends GuiContainer {

    // ==== поиск ====
    private GuiTextField searchField;
    private String currentSearch = "";
    private String lastSearchSent = "";
    private long lastSearchEditTs = 0L;
    private static final long SEARCH_DEBOUNCE_MS = 180L; // тонкая задержка, чтобы не спамить сеть

    private long lastAutoRefreshMs = 0L;
    private static final long AUTO_REFRESH_MS = 600L;

    // ---- контроль снапшота и фильтра ----
    private long createdSnapId = -1L;   // id снапшота, который мы создали под currentSearch
    private boolean awaitingSnapId = false; // true, пока сервер не вернул >0 id


    // --- режим терминала ---
    private enum Mode { DELIVERY, CRAFT }
    private Mode mode = Mode.DELIVERY; // по умолчанию
    private boolean isCraftMode() { return mode == Mode.CRAFT; }

    // === Кнопки ===
    private static final int BTN_SUBMIT = 202;
    private static final int BTN_TOGGLE = 201;

    // Кнопки
    private GuiButton btnRequest;
    private MiniToggleButton btnModeToggle;

    class MiniToggleButton extends GuiButton {
        private boolean state; // true = CRAFT, false = STORAGE

        MiniToggleButton(int id, int x, int y, boolean initial) {
            super(id, x, y, 9, 9, ""); // кликабельная зона 9×9
            this.state = initial;
        }

        public void setState(boolean s) {
            this.state = s;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1F, 1F, 1F, 1F);

            mc.getTextureManager().bindTexture(TEX_CRAFTER);

            // выбираем участок спрайта по режиму
            int u = state ? MODE_BTN_U_CRAFT : MODE_BTN_U_STORAGE; // 9 или 0
            int v = MODE_BTN_V;                                    // 104

            // рисуем 8×8 пикселей из golem_crafter.png
            // полный размер спрайта — 354×256 (как у order_terminal)
            GuiOrderTerminal.this.drawModalRectWithCustomSizedTexture(
                    this.x, this.y,   // куда в GUI
                    u, v,             // откуда в текстуре
                    9, 9,             // размер вырезаемого участка
                    354, 256          // размер всего атласа golem_crafter.png
            );

            // hover-подсветка по всей зоне 9×9
            this.hovered = mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;

            if (hovered) {
                // лёгкая подсветка поверх
                drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x40FFFFFF);
            }
        }
    }

    class RequestButton extends GuiButton {
        RequestButton(int id, int x, int y) {
            // по ТЗ: текстурное поле 18:104–37:112 → ширина 19, высота 8
            super(id, x, y, 20, 9, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1F, 1F, 1F, 1F);

            mc.getTextureManager().bindTexture(TEX_CRAFTER);

            // базовая текстура кнопки: 18:104–37:112
            int u = 17;
            int v = 104;

            GuiOrderTerminal.this.drawModalRectWithCustomSizedTexture(
                    this.x, this.y,
                    u, v,
                    this.width, this.height,   // 19×8
                    354, 256                   // размер golem_crafter.png
            );

            // логика наведения
            this.hovered = mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= this.y && mouseY < this.y + this.height;

            if (hovered) {
                // "характерное" выделение — лёгкая светлая вуаль
                drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x40FFFFFF);
            }
        }
    }

    private void sendAdjust(ItemStack st, int delta) {
        ThaumicAttempts.NET.sendToServer(
                new therealpant.thaumicattempts.golemnet.net.msg.C2S_OrderAdjust(
                        terminalPos, st, delta, isCraftMode()
                )
        );
    }
    private static int incByMods(boolean shift, boolean ctrl) {
        if (shift) return 64;
        if (ctrl)  return 10;
        return 1;
    }


    /* ===== ресурсы ===== */
    private static final ResourceLocation TEX_BASE_TC =
            new ResourceLocation("thaumcraft","textures/gui/gui_base.png");

    // общий фон GUI 354×256
    private static final ResourceLocation TEX_BG =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/order_terminal.png");

    // бумага страниц каталога – наш paper.png 74×94
    private static final ResourceLocation TEX_PAGE_PAPER =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/paper.png");

    // спрайты маленькой кнопки режима из golem_crafter.png
    private static final ResourceLocation TEX_CRAFTER =
            new ResourceLocation(ThaumicAttempts.MODID, "textures/gui/golem_crafter.png");

    // координаты в спрайте golem_crafter.png
    private static final int MODE_BTN_U_STORAGE = 0;   // хранилище
    private static final int MODE_BTN_U_CRAFT   = 9;   // крафт
    private static final int MODE_BTN_V        = 104;

    // Геометрия «листа» для страниц
    private static final int PAGE_PAPER_W = 74;
    private static final int PAGE_PAPER_H = 94;
    private static final int PAGE_PAPER_MARGIN = 8; // можно 8–10, не критично
    private static final int STACK_EXTRA_PAD = 100;
    private int hiddenLeftTop = -1;

    // --- queued exact take (фикс "пропуска центра" при чередовании кликов)
    private GrabSource queuedFrom = GrabSource.NONE; // откуда потом взять ровно тот лист
    private int queuedPageNo = -1;                   // какой именно номер страницы взять в центр

    private int pendingHideLeftTop = -1; // что спрятать, но только с началом анимации
    private boolean centerOnOpen = true; // p1 сразу в центр при первом же получении

    private static final int STACK_DY = 0;       // следующий лист ниже на 4px
    private static final int STACK_SHIFT_X = 10; // вся правая стопка сдвинута вправо на 10px

    // откуда брать страницу после увода активной влево
    private enum GrabSource { NONE, RIGHT, LEFT }
    private GrabSource pendingGrab = GrabSource.NONE;

    // для анимации "активная прилетела слева"
    private boolean activeFromLeft = false;

    // ---- Depth slicing ----
    // Сколько "ломтиков" на буфер. 512 хватает с большим запасом.
    private static final double DR_NEAR = 0.0;
    private static final double DR_FAR  = 1.0;
    private static final double SLICE   = 1.0 / 512.0; // толщина одного ломтика

    private void pushDepthSlice(int order) {
        // меньший order → ближе (верх), больший → ниже (дальше)
        double n = DR_NEAR + order * SLICE;
        double f = n + SLICE * 0.9; // немного не до конца, чтобы не касаться соседей
        GL11.glDepthRange(n, f);
    }
    private void popDepthSlice() { GL11.glDepthRange(0.0, 1.0); }

    /* ---- Базовые порядки слоёв ---- */
    private int orderActive() { return 0; }           // активная всегда ближе всех
    private static final int RIGHT_BASE_ORDER = 1;    // верх правой стопки
    private static final int LEFT_BASE_ORDER  = 256;  // верх левой стопки

    /* ===== унифицированная логика для Z и Y ===== */

    /** 0 = верх правой (pageNo = baseStackOffset + 1), 1 — следующая вниз и т.д. */
    private int indexRightFromTop(int pageNo) {
        return Math.max(0, pageNo - (baseStackOffset + 1));
    }
    /** 0 = верх левой (pageNo = baseStackOffset), 1 — следующая вниз и т.д. */
    private int indexLeftFromTop(int pageNo) {
        return Math.max(0, baseStackOffset - pageNo);
    }
    /** Depth-order для любой страницы. Меньше — ближе к камере. */
    private int depthOrderForPage(int pageNo) {
        if (pageNo == takenPageNo &&
                (animState == AnimState.TO_CENTER || animState == AnimState.CENTERED ||
                        animState == AnimState.TO_LEFT   || animState == AnimState.TO_RIGHT)) {
            return orderActive();
        }
        if (pageNo <= baseStackOffset) {
            return LEFT_BASE_ORDER + indexLeftFromTop(pageNo);
        } else {
            return RIGHT_BASE_ORDER + indexRightFromTop(pageNo);
        }
    }
    /** top-left Y для правой стопки. 0 (верх) = baseY. */
    private int rightStackYForPage(int pageNo, int baseY) {
        return baseY + indexRightFromTop(pageNo) * STACK_DY;
    }
    /** top-left Y для левой стопки. 0 (верх) = baseY. */
    private int leftStackYForPage(int pageNo, int baseY) {
        return baseY + indexLeftFromTop(pageNo) * STACK_DY;
    }

    private static final float Z_ACTIVE_BASE    = 400f;
    private static final float Z_PAPER_LOCAL = 0f;
    private static final float Z_GRID_LOCAL  = 0f;
    private static final float Z_ICON_BIAS   = 32f;
    private static final float Z_TEXT_OVER   = Z_ICON_BIAS + 6f; // 38f


    private static final int VISIBLE_COLS_COUNT = 5;
    private static final int VISIBLE_ROWS_COUNT = 7;
    private static final int WINDOW_SIZE = VISIBLE_COLS_COUNT*VISIBLE_ROWS_COUNT; // 35
    private static final int cell = 18;


    private static final float PAGE_SCALE = 1.0f;
    // Сетка предметов на странице: 10×10 иконка + ~2px зазор,
    // при базовом cell=18 даёт масштаб 12/18 = 2/3
    private static final float PAGE_GRID_SCALE = 2.0f / 3.0f;

    // Левый "лист заказа" 3×3 (под книгой)
    private static final int DRAFT_ICON_SIZE = 12;
    private static final int DRAFT_ICON_GAP  = 4;
    private static final int DRAFT_GRID_W    = DRAFT_ICON_SIZE * 3 + DRAFT_ICON_GAP * 2; // 44
    private static final int DRAFT_GRID_H    = DRAFT_ICON_SIZE * 3 + DRAFT_ICON_GAP * 2; // 44

    // Координаты левого верхнего угла сетки в PNG (от guiLeft/guiTop)
    private static final int DRAFT_GRID_X = 20;
    private static final int DRAFT_GRID_Y = 41;

    private static final boolean DEV_OVERLAY = true;

    private int visibleWidth() { return VISIBLE_COLS_COUNT*cell; }
    private int visibleHeight(){ return VISIBLE_ROWS_COUNT*cell; }

    /* ===== локальный кэш страниц ===== */
    private static final class PageSnap {
        List<ItemStack> items;
        List<Integer> counts;
        List<Integer> makeCounts;
        List<Boolean> makePossible;
        int itemCount;
        boolean partial;
    }
    private final Map<Integer, PageSnap> pageCache = new HashMap<>();

    /* ===== сетевые маркеры/окно полёта ===== */
    private final Map<Integer, Long> inflight = new HashMap<>(); // page(1-based) -> ts
    private static final int MAX_INFLIGHT = 3;
    private static final long REQ_TIMEOUT_MS = 1500L;

    /* ===== декор слева ===== */
    private static final int PAPER_W=128, PAPER_H=128, PAPER_MARGIN=10;
    private static final float GRID_ADJ_X=-1.75f, GRID_ADJ_Y=-1f;
    private int paperX, paperY, gridX, gridY;

    /* ===== позиции GUI ===== */
    private int leftLeft, leftTop;
    private int centerLeft, centerTop;
    private int playerLeft, playerTop;

    /* ===== TE ===== */
    private final TileOrderTerminal te;
    private final BlockPos terminalPos;


    public GuiOrderTerminal(InventoryPlayer inv, TileOrderTerminal te) {
        super(new ContainerOrderTerminal(inv, te));
        this.te=te; this.terminalPos=te.getPos();
        this.xSize=354; this.ySize=256;
    }


    /** Рисует текст с 1px контуром (8 направлений) в правом-нижнем углу слота. */
    private void drawTextOutlinedRightBottom(String txt, int slotX, int slotY,
                                             float scale, int fillColor, int outlineColor) {
        if (txt == null || txt.isEmpty()) return;

        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GlStateManager.pushMatrix();
        GlStateManager.translate(slotX + 16F, slotY + 16F, 0F);
        GlStateManager.scale(scale, scale, 1F);

        int w = this.fontRenderer.getStringWidth(txt);
        int x = -w, y = -8;

        // контур 8 направлений
        final int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        final int[] dy = {-1,-1,-1,  0, 0,  1, 1, 1};
        for (int i=0;i<8;i++) this.fontRenderer.drawString(txt, x+dx[i], y+dy[i], outlineColor, false);

        // заливка
        this.fontRenderer.drawString(txt, x, y, fillColor, false);

        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
    }

    /** То же самое, но с относительным Z, чтобы оказаться поверх иконки. */
    private void drawTextOutlinedRightBottomZ(String txt, int slotX, int slotY,
                                              float scale, int fillColor, int outlineColor, float relZ) {
        if (txt == null || txt.isEmpty()) return;

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GlStateManager.pushMatrix();
        GlStateManager.translate(slotX + 16F, slotY + 16F, relZ);
        GlStateManager.scale(scale, scale, 1F);

        int w = this.fontRenderer.getStringWidth(txt);
        int x = -w, y = -8;

        final int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        final int[] dy = {-1,-1,-1,  0, 0,  1, 1, 1};
        for (int i=0;i<8;i++) this.fontRenderer.drawString(txt, x+dx[i], y+dy[i], outlineColor, false);
        this.fontRenderer.drawString(txt, x, y, fillColor, false);

        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
    }



    /* ===== lifecycle ===== */
    @Override
    public void initGui() {
        super.initGui();

        this.guiTop += 12;

        this.buttonList.clear();

        // 1) Сначала координаты экранных зон
        leftLeft   = this.guiLeft - 20;
        leftTop    = this.guiTop  + 90;
        centerLeft = this.guiLeft + 69;
        centerTop  = this.guiTop  + 24;
        playerLeft = this.guiLeft + 69;
        playerTop  = this.guiTop  + ContainerOrderTerminal.PLAYER_INV_TOP;

        // 2) Затем лейаут бумаги (он использует координаты выше)
        layoutDraftPaper();                  // ← сначала бумага
        layoutDraftPaperAndButtons();        // ← затем кнопки, завязаны на бумагу

        // 3) Теперь создаём кнопки (координаты уже корректные)
        // Request-кнопка из golem_crafter.png
        btnRequest = new RequestButton(BTN_SUBMIT, 0, 0);
        this.buttonList.add(btnRequest);

        // Миникнопка режима
        btnModeToggle = new MiniToggleButton(BTN_TOGGLE, 0, 0, isCraftMode());
        this.buttonList.add(btnModeToggle);

        // Request: левый верхний угол в координатах GUI 32:98
        if (btnRequest != null) {
            btnRequest.x = this.guiLeft + 32;
            btnRequest.y = this.guiTop  + 98;
        }

        // Миникнопка режима уже чуть выше выставлена в 58:98
        if (btnModeToggle != null) {
            btnModeToggle.x = this.guiLeft + 58;
            btnModeToggle.y = this.guiTop  + 98;
            btnModeToggle.setState(isCraftMode());
        }
        // ===== Позиционирование кнопок =====

        // 1) Кнопка заказа по центру поля 33:99 - 50:105 (координаты в системе GUI)
        {
            int fieldX1 = this.guiLeft + 33;
            int fieldY1 = this.guiTop  + 99;
            int fieldX2 = this.guiLeft + 50;
            int fieldY2 = this.guiTop  + 105;

            int centerX = (fieldX1 + fieldX2) / 2;
            int centerY = (fieldY1 + fieldY2) / 2;

            if (btnRequest != null) {
                // размер 40×13 остаётся как у таумовской кнопки,
                // мы только двигаем её так, чтобы центр совпал
                btnRequest.x = centerX - btnRequest.width  / 2;
                btnRequest.y = centerY - btnRequest.height / 2;
            }
        }
        // 2) Маленькая кнопка режима: левый верхний угол в координатах GUI 58:98
        if (btnModeToggle != null) {
            btnModeToggle.x = this.guiLeft + 58;
            btnModeToggle.y = this.guiTop  + 98;
            btnModeToggle.setState(isCraftMode());
        }
        // 4) Прочая инициализация снапшота/кэша
        ClientCatalogCache.guiOpened(this.isCraftMode());
        pageCache.clear();
        inflight.clear();
        createdSnapId  = -1L;
        awaitingSnapId = true;
        lastSearchSent = currentSearch;

        sendRequestPage(-1L, 0); // создаст снапшот с фильтром

        // 5) Поисковая строка (после корректного layout)
        int[] sb = computeSearchBoxXYWH();
        searchField = new GuiTextField(0, this.fontRenderer, sb[0], sb[1], sb[2], sb[3]);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setMaxStringLength(64);
        searchField.setText(currentSearch);
        searchField.setFocused(false);
    }

    /** Привязка листка, сетки и кнопок к области «Черновик» (3×3), как в архивной версии. */
    private void layoutDraftPaperAndButtons() {
        // целевой размер сетки = 3 * cell
        final int targetW = 3 * cell;
        final int targetH = 3 * cell;

        // рабочая область листка (без полей)
        final int contentW = PAPER_W - 2 * PAPER_MARGIN;
        final int contentH = PAPER_H - 2 * PAPER_MARGIN;
        final int topTwoH  = (contentH * 2) / 3;      // верхние 2/3
        final int bottomH  = contentH - topTwoH;      // нижняя треть

        // сетка должна точно закрывать слоты 3×3
        gridX = Math.round(leftLeft + GRID_ADJ_X);
        gridY = Math.round(leftTop  + GRID_ADJ_Y);

        // размещаем листок так, чтобы сетка была по центру верхних 2/3 листка
        int offsetX = PAPER_MARGIN + (contentW - targetW) / 2;
        int offsetY = PAPER_MARGIN + (topTwoH  - targetH) / 2;
        paperX = gridX - offsetX;
        paperY = gridY - offsetY;

        // КНОПКИ: большая — по центру нижней трети, маленькая — справа на 6px, без выхода за край бумаги
        final int bigW   = (btnRequest != null) ? btnRequest.width  : 40;
        final int bigH   = (btnRequest != null) ? btnRequest.height : 13;
        final int smallW = 13, smallH = 13;
        final int gap    = 6;

        int buttonsAreaX = paperX + PAPER_MARGIN;
        int buttonsAreaY = paperY + PAPER_MARGIN + topTwoH;
        int buttonsAreaW = contentW;
        int buttonsAreaH = bottomH;

        int btnY = buttonsAreaY + (buttonsAreaH - bigH) / 2;
        int btnX = buttonsAreaX + (buttonsAreaW - bigW) / 2;

        if (btnRequest != null) {
            btnRequest.x = btnX;
            btnRequest.y = btnY;
        }

        int smallX = Math.min(paperX + PAPER_MARGIN + contentW - smallW, btnX + bigW + gap);
        if (btnModeToggle != null) {
            btnModeToggle.x = smallX;
            btnModeToggle.y = btnY;
        }

        // --- поиск под активной страницей ---
        int[] sb = computeSearchBoxXYWH(); // ниже метод
        searchField = new GuiTextField(0, this.fontRenderer, sb[0], sb[1], sb[2], sb[3]);
        searchField.setEnableBackgroundDrawing(false); // рисуем свой «тонкий» стиль
        searchField.setMaxStringLength(64);
        searchField.setText(currentSearch);
        searchField.setFocused(false);

    }

    /** Геометрия строки поиска под активной страницей (если не CENTERED — по центру экрана). */
    private int[] computeSearchBoxXYWH() {
        final float s = TARGET_SCALE;
        int gx = centerGridXForScale(s);
        int centerYLine = centerLineYForScale(s);
        int gridH = Math.round(visibleHeight() * s);
        int gy = centerYLine - gridH/2 - 15;

        // ширина = ширине видимой сетки (5*18) с небольшими полями
        int w = Math.round(visibleWidth() * s);
        int x = gx;
        // тонкий бокс на 14 px высотой, отступ 6px под бумагой
        int y = gy + gridH + 6;
        int h = 10;
        return new int[]{ x, y, w, h };
    }

    /** Переразметить поле поиска при ресайзе/анимации */
    private void layoutSearchField() {
        if (searchField == null) return;
        int[] sb = computeSearchBoxXYWH();
        searchField.x = sb[0];
        searchField.y = sb[1];
        searchField.width  = sb[2];
        searchField.height = sb[3];
    }

    // Полная замена
    // Полная замена
    @Override
    public void updateScreen() {
        super.updateScreen();

        final long nowMs = System.currentTimeMillis();

        // 0) Зафиксировать id снапшота один раз, как только сервер его сообщит
        final boolean craft = isCraftMode();
        long snapNow = ClientCatalogCache.getActiveSnapshotId(craft);
        if (awaitingSnapId && snapNow > 0) {
            createdSnapId = snapNow;
            awaitingSnapId = false;
        }

        // 1) Импорт страниц ТОЛЬКО когда наш снапшот стабилен
        boolean ready = (!awaitingSnapId && createdSnapId > 0 && snapNow == createdSnapId);
        if (ready) {
            boolean updated = ClientCatalogCache.consumeUpdatedFlag(craft);

            // если флаг уже «съели» в неготовом состоянии — все равно пытаемся импортировать,
            // когда видим, что на стороне кэша уже есть известные страницы
            if (updated || (pageCache.isEmpty() && !ClientCatalogCache.getKnownPages(craft).isEmpty())) {
                importAllKnownPages();
            }
        }

        // 2) Debounce поиска → пересоздание снапшота с новым фильтром
        if (!Objects.equals(currentSearch, lastSearchSent)) {
            long now = System.currentTimeMillis();
            if (now - lastSearchEditTs >= SEARCH_DEBOUNCE_MS) {
                pageCache.clear();
                inflight.clear();
                baseStackOffset = 0;
                takenPageNo = -1;
                animState = AnimState.IDLE;
                centerOnOpen = true;

                createdSnapId = -1L;
                awaitingSnapId = true;
                lastSearchSent = currentSearch;

                ClientCatalogCache.guiOpened(craft);
                sendRequestPage(-1L, 0);   // создаст снапшот с фильтром
            }
        }

        // 3) Чистка просроченных in-flight
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Long>> it = inflight.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            if (now - e.getValue() > REQ_TIMEOUT_MS) it.remove();
        }

        // 4) Догрузка страниц только в рамках нашего снапшота
        if (ready) pumpRequests();

        // 5) Первая страница в центр, когда готова
        if (centerOnOpen && animState == AnimState.IDLE) {
            PageSnap p1 = pageCache.get(1);
            if (p1 != null) {
                takenPageNo = 1;           // теперь разрешаем и partial
                animState = AnimState.CENTERED;
                centerOnOpen = false;
            }
        }

        // 6) Анимации (без изменений)

        if (animState != AnimState.IDLE) {

            float t = Math.min(1f, (nowMs - animStartMs) / (float) ANIM_MS);
            animT = t * t * (3f - 2f * t);

            if (hiddenLeftTop == -1 && pendingHideLeftTop >= 1) {
                if (animState == AnimState.TO_CENTER && activeFromLeft) hiddenLeftTop = pendingHideLeftTop;
            }

            if (t >= 1f) {
                switch (animState) {
                    case TO_CENTER:
                        animState = AnimState.CENTERED;
                        animT = 1f;
                        hiddenLeftTop = -1;
                        pendingGrab = GrabSource.NONE;
                        break;

                    case TO_LEFT: {
                        baseStackOffset++;
                        takenPageNo = -1;
                        animT = 0f;

                        int max = maxPageToDraw();
                        if (baseStackOffset < 1 || max <= 0) {
                            animState = AnimState.IDLE;
                            hiddenLeftTop = -1;
                            queuedFrom = GrabSource.NONE;
                            queuedPageNo = -1;
                            break;
                        }

                        if (queuedFrom != GrabSource.NONE && queuedPageNo >= 1) {
                            takenPageNo = queuedPageNo;
                            activeFromLeft = (queuedFrom == GrabSource.LEFT);
                            if (activeFromLeft) pendingHideLeftTop = queuedPageNo;

                            animState = AnimState.TO_CENTER;
                            animStartMs = System.currentTimeMillis();

                            queuedFrom = GrabSource.NONE;
                            queuedPageNo = -1;
                        } else {
                            int candidate = Math.min(max, baseStackOffset + 1);
                            if (candidate >= (baseStackOffset + 1)) {
                                takenPageNo = candidate;
                                activeFromLeft = false;
                                animState = AnimState.TO_CENTER;
                                animStartMs = System.currentTimeMillis();
                            } else {
                                animState = AnimState.IDLE;
                            }
                        }
                        pendingHideLeftTop = -1;
                        break;
                    }

                    case TO_RIGHT: {
                        baseStackOffset = Math.max(0, baseStackOffset - 1);
                        takenPageNo = -1;
                        animT = 0f;

                        int max = maxPageToDraw();
                        if (max <= 0) {
                            animState = AnimState.IDLE;
                            hiddenLeftTop = -1;
                            queuedFrom = GrabSource.NONE;
                            queuedPageNo = -1;
                            break;
                        }

                        if (queuedFrom != GrabSource.NONE && queuedPageNo >= 1) {
                            takenPageNo = queuedPageNo;
                            activeFromLeft = (queuedFrom == GrabSource.LEFT);
                            animState = AnimState.TO_CENTER;
                            animStartMs = System.currentTimeMillis();
                            queuedFrom = GrabSource.NONE;
                            queuedPageNo = -1;
                        } else {
                            int candidate = Math.min(max, baseStackOffset + 1);
                            if (candidate >= (baseStackOffset + 1)) {
                                takenPageNo = candidate;
                                activeFromLeft = false;
                                animState = AnimState.TO_CENTER;
                                animStartMs = System.currentTimeMillis();
                            } else {
                                animState = AnimState.IDLE;
                            }
                        }
                        pendingHideLeftTop = -1;
                        break;
                    }
                    case CENTERED:
                    case IDLE:
                    default:
                        break;
                }
            }
        }

        // 7) Авто-рефреш ...
        if (nowMs - lastAutoRefreshMs >= AUTO_REFRESH_MS) {
            lastAutoRefreshMs = nowMs;

            // УБРАТЬ это условие целиком:
            // if (currentSearch == null || currentSearch.trim().isEmpty()) {

            if (!awaitingSnapId && createdSnapId > 0 &&
                    ClientCatalogCache.getActiveSnapshotId(isCraftMode()) == createdSnapId) {

                int max = maxPageToDraw();
                if (max > 0) {
                    int rightTop = Math.min(max, baseStackOffset + 1);
                    int leftTop = Math.max(1, baseStackOffset);

                    if (pageCache.containsKey(rightTop)) sendRequestPage(createdSnapId, rightTop - 1);
                    if (leftTop >= 1 && pageCache.containsKey(leftTop)) sendRequestPage(createdSnapId, leftTop - 1);
                    if (takenPageNo >= 1 && pageCache.containsKey(takenPageNo))
                        sendRequestPage(createdSnapId, takenPageNo - 1);
                }
            }
            // }  ← и вот эту закрывающую для условия — тоже убрать
        }

    }

    @Override
    protected void actionPerformed(GuiButton button) throws java.io.IOException {
        if (button == btnModeToggle) {
            // переключаем режим
            mode = isCraftMode() ? Mode.DELIVERY : Mode.CRAFT;

            // обновляем визуальное состояние маленькой кнопки
            if (btnModeToggle != null) {
                btnModeToggle.setState(isCraftMode());
            }

            // перезагрузка каталога под новый режим
            pageCache.clear();
            inflight.clear();
            baseStackOffset = 0;
            takenPageNo = -1;
            animState = AnimState.IDLE;
            centerOnOpen = true;

            createdSnapId  = -1L;
            awaitingSnapId = true;
            lastSearchSent = currentSearch;

            ClientCatalogCache.guiOpened(isCraftMode());
            sendRequestPage(-1L, 0);
            return;
        }

        if (button.id == BTN_SUBMIT) {
            ThaumicAttempts.NET.sendToServer(
                    new therealpant.thaumicattempts.golemnet.net.msg.C2S_OrderSubmit(terminalPos, isCraftMode())
            );
            return;
        }

        super.actionPerformed(button);
    }

    /** Возвращает геометрию активной страницы (gridX, gridY, scale) если страница по центру; иначе null. */
    private ActiveGridParams getActiveGridParamsIfCentered() {
        if (takenPageNo < 1) return null;
        if (animState != AnimState.CENTERED) return null;

        final float s = TARGET_SCALE;
        final int gridX = centerGridXForScale(s);

        // Горизонтальная линия центров — как в рендере
        final int centerYLine = centerLineYForScale(s);
        final int gridH       = Math.round(visibleHeight() * s);
        final int gridY       = centerYLine - gridH / 2;

        return new ActiveGridParams(gridX, gridY, s);
    }

    private static final class ActiveGridParams {
        final int gridX, gridY;
        final float scale;
        ActiveGridParams(int x, int y, float s){ this.gridX = x; this.gridY = y; this.scale = s; }
    }

    /** Мэппинг XY курсора в индекс ячейки 0..34 в активной сетке. Возвращает -1 если мимо сетки. */
    private int activeGridIndexAt(int mouseX, int mouseY) {
        ActiveGridParams p = getActiveGridParamsIfCentered();
        if (p == null) return -1;

        // "полный" размер, как если бы сетка была без ужатия
        float fullW = visibleWidth()  * p.scale;
        float fullH = visibleHeight() * p.scale;

        // фактический размер отрисованной сетки
        float gridWf = fullW * PAGE_GRID_SCALE;
        float gridHf = fullH * PAGE_GRID_SCALE;

        // смещение внутрь "полного" прямоугольника, чтобы сетка была по центру
        float offX = (fullW - gridWf) / 2.0f;
        float offY = (fullH - gridHf) / 2.0f;

        int gridW = Math.round(gridWf);
        int gridH = Math.round(gridHf);

        // фактический левый верх сетки (как мы её рисуем)
        int gx = p.gridX + Math.round(offX);
        int gy = p.gridY + Math.round(offY);

        if (mouseX < gx || mouseX >= gx + gridW || mouseY < gy || mouseY >= gy + gridH) return -1;

        int cellW = Math.round(cell * p.scale * PAGE_GRID_SCALE);
        int cellH = Math.round(cell * p.scale * PAGE_GRID_SCALE);

        int relX = mouseX - gx;
        int relY = mouseY - gy;

        int col = relX / cellW;
        int row = relY / cellH;

        if (col < 0 || col >= VISIBLE_COLS_COUNT || row < 0 || row >= VISIBLE_ROWS_COUNT) return -1;

        return row * VISIBLE_COLS_COUNT + col;
    }

    private void adjustDraftFor(ItemStack stack, int delta) {
        if (stack == null || stack.isEmpty() || delta == 0) return;
        // Ваша серверная сторона обычно принимает ItemKey/ItemStack + delta + craftMode + pos
        ThaumicAttempts.NET.sendToServer(new C2S_OrderAdjust(terminalPos, stack, delta, isCraftMode()));
    }


    /* ===== сеть ===== */
    // Всегда передаём currentSearch (и при создании, и при дозапросах/автообновлении)
    private void sendRequestPage(long snapshotId, int pageIndex0) {
        final int idx0 = Math.max(0, pageIndex0);

        // если снапшот ещё не создан — создаём его с фильтром
        if (createdSnapId <= 0 || awaitingSnapId || snapshotId <= 0) {
            ThaumicAttempts.NET.sendToServer(new C2S_RequestCatalogPage(
                    terminalPos,
                    -1L,
                    idx0,
                    currentSearch,     // ← фильтр
                    isCraftMode()
            ));
            inflight.put(idx0 + 1, System.currentTimeMillis());
            lastSearchSent   = currentSearch;
            lastSearchEditTs = System.currentTimeMillis();
            awaitingSnapId   = true;
            createdSnapId    = -1L;
            return;
        }

        // догрузка/рефреш по уже созданному снапшоту — тоже с фильтром
        ThaumicAttempts.NET.sendToServer(new C2S_RequestCatalogPage(
                terminalPos,
                createdSnapId,
                idx0,
                currentSearch,         // ← НЕ "" !
                isCraftMode()
        ));
        inflight.put(idx0 + 1, System.currentTimeMillis());
    }

    private void pumpRequests() {
        if (awaitingSnapId || createdSnapId <= 0) return;

        // Если вообще ничего нет — дёрнем первую страницу
        if (pageCache.isEmpty() && inflight.isEmpty()) {
            sendRequestPage(createdSnapId, 0);
            return;
        }

        final boolean totalKnown = ClientCatalogCache.isTotalKnown(isCraftMode());
        final int totalPages     = ClientCatalogCache.getTotalPages(isCraftMode());
        final boolean hasMore    = ClientCatalogCache.getHasMoreFlag(isCraftMode());

        while (inflight.size() < MAX_INFLIGHT) {
            Integer next = firstMissingIndex(totalKnown ? totalPages : 9999);
            if (next == null) break;

            if (!totalKnown && !hasMore && next > maxContiguous()) break;

            int idx0 = next - 1;
            if (!inflight.containsKey(next)) {
                sendRequestPage(createdSnapId, idx0);
            } else break;
        }
    }



    private Integer firstMissingIndex(int cap) {
        int hard = Math.max(1, cap);
        for (int p = 1; p <= hard; p++) {
            PageSnap s = pageCache.get(p);
            if (s == null) return p;
            if (s.partial) return p;
        }
        if (!ClientCatalogCache.isTotalKnown(isCraftMode()) && ClientCatalogCache.getHasMoreFlag(isCraftMode())) {
            return Math.max(1, pageCache.size()) + 1;
        }
        return null;
    }

    private int maxContiguous() {
        int p=0;
        while (true) {
            PageSnap s = pageCache.get(p+1);
            if (s==null || s.partial) break;
            p++;
        }
        return p;
    }

    private void importAllKnownPages() {
        long snapNow = ClientCatalogCache.getActiveSnapshotId(isCraftMode());
        if ((awaitingSnapId || createdSnapId <= 0) && snapNow > 0) {
            createdSnapId  = snapNow;
            awaitingSnapId = false;
        }
        if (awaitingSnapId || createdSnapId <= 0 || snapNow != createdSnapId) return;

        final boolean craft = isCraftMode();

        java.util.Set<Integer> known = ClientCatalogCache.getKnownPages(craft);
        if (known == null || known.isEmpty()) return;

        for (Integer raw : known) {
            if (raw == null) continue;
            // Нормализация: если пришёл 0 — считаем его 1-й страницей
            int p = (raw <= 0) ? (raw + 1) : raw;

            PageSnap snap = new PageSnap();
            // Пробуем и raw, и p на случай, если API по-разному индексирует методы:
            List<ItemStack> items = ClientCatalogCache.getPage35ByNumber(craft, p);
            if ((items == null || items.isEmpty()) && raw != p) {
                items = ClientCatalogCache.getPage35ByNumber(craft, raw);
                if (items != null && !items.isEmpty()) p = raw; // если сработало с raw — используем его
            }

            snap.items        = cloneItems(items);
            snap.counts       = cloneInts (ClientCatalogCache.getCountsByNumber(craft, p));
            snap.makeCounts   = cloneInts (ClientCatalogCache.getMakeCountsByNumber(craft, p));
            snap.makePossible = cloneBools(ClientCatalogCache.getMakePossibleByNumber(craft, p));
            Integer ic = ClientCatalogCache.getItemCountOnPage(craft, p);
            snap.itemCount    = (ic == null ? 0 : ic);
            snap.partial      = ClientCatalogCache.isPagePartial(craft, p);

            pageCache.put(p, snap);
            inflight.remove(p);
        }

        inflight.keySet().removeIf(k -> {
            PageSnap s = pageCache.get(k);
            return s != null && !s.partial;
        });
    }

    /* ===== рендер ===== */
    private int maxPageToDraw() {
        int p = 0;
        final boolean totalKnown = ClientCatalogCache.isTotalKnown(isCraftMode());
        final int totalPages     = ClientCatalogCache.getTotalPages(isCraftMode());

        while (true) {
            PageSnap s = pageCache.get(p + 1);
            if (s == null || s.partial) break;          // нет снапшота или страница ещё догружается
            p++;
            if (totalKnown && p >= Math.max(1, totalPages)) break; // уперлись в известный предел
        }
        return p;
    }



    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1F,1F,1F,1F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );

        // ---- Рисуем общий фон GUI (order_terminal.png), центрируется за счёт xSize/ySize ----
        mc.getTextureManager().bindTexture(TEX_BG);
        drawModalRectWithCustomSizedTexture(
                this.guiLeft, this.guiTop,
                0, 0,
                this.xSize, this.ySize,
                354, 256
        );

        // ---- Превью заказа слева (3×3, иконки 16×16), начало в (17,39) относительно PNG ----
        List<ItemStack> leftNine = isFrozen()
                ? ClientCatalogCache.getPending(isCraftMode())
                : ClientCatalogCache.getDraft(isCraftMode());
        List<Integer> leftCnt = isFrozen()
                ? ClientCatalogCache.getPendingCounts(isCraftMode())
                : ClientCatalogCache.getDraftCounts(isCraftMode());

        int previewX = this.guiLeft + DRAFT_GRID_X;
        int previewY = this.guiTop  + DRAFT_GRID_Y;
        int previewW = DRAFT_GRID_W;
        int previewH = DRAFT_GRID_H;

        render3x3CenteredInRect(leftNine, leftCnt, previewX, previewY, previewW, previewH);

        // === СТРАНИЦЫ С ГЛУБИНОЙ ===
        beginDepthLayeringForGui();
        drawPagesWithRealZ();
        endDepthLayeringForGui();
        // --- ТОНКАЯ строка поиска ---
        layoutSearchField();
        {
            // фон: тонкая полоса + светлая подложка 1px
            int x = searchField.x, y = searchField.y, w = searchField.width, h = searchField.height;
            int base = 0x66000000;          // полупрозрачная подложка
            int line = 0xFF8A8A8A;          // тонкая линия
            int caret = 0xFFCCCCCC;

            // лёгкая капля-фон (не как ванилла, очень тонко)
            drawRect(x, y, x + w, y + h, base);
            // нижняя линия
            drawRect(x, y + h - 1, x + w, y + h, line);

            // текст
            String s = searchField.getText();
            int tx = x + 3;
            int ty = y + (h - this.fontRenderer.FONT_HEIGHT) / 2;
            this.fontRenderer.drawString(s.isEmpty() ? "search..." : s, tx, ty, 0xFFEFEFEF, false);

            // курсор (если сфокусировано)
            if (searchField.isFocused() && (System.currentTimeMillis()/500)%2==0) {
                int tw = this.fontRenderer.getStringWidth(s.substring(0, Math.min(searchField.getCursorPosition(), s.length())));
                drawRect(tx + tw, y + 3, tx + tw + 1, y + h - 3, caret);
            }
        }
    }

    /** Находит 3 уникальных X и 3 уникальных Y правой «приёмной» 3×3, строго по координатам слотов. */
    @Nullable
    private int[][] findRightReceiverGridXY() {
        int invTopY = this.playerTop - 8;                   // верх инвентаря игрока (фон)
        int screenMidX = this.guiLeft + this.xSize / 2;     // правая половина GUI

        java.util.List<Integer> xs = new java.util.ArrayList<>();
        java.util.List<Integer> ys = new java.util.ArrayList<>();

        for (net.minecraft.inventory.Slot s : this.inventorySlots.inventorySlots) {
            int sx = s.xPos + this.guiLeft;
            int sy = s.yPos + this.guiTop;
            // правее середины и выше инвентаря — это «приёмная» область
            if (sx >= screenMidX && sy < invTopY) {
                xs.add(sx);
                ys.add(sy);
            }
        }
        if (xs.size() < 9 || ys.size() < 9) return null;

        // уникальные и отсортированные X и Y
        java.util.Set<Integer> xu = new java.util.TreeSet<>(xs);
        java.util.Set<Integer> yu = new java.util.TreeSet<>(ys);
        if (xu.size() < 3 || yu.size() < 3) return null;

        int[] ux = new int[3], uy = new int[3];
        int i = 0; for (Integer v : xu) { if (i<3) ux[i++] = v; else break; }
        i = 0; for (Integer v : yu) { if (i<3) uy[i++] = v; else break; }

        // гарантируем порядок «слева-направо / сверху-вниз»
        java.util.Arrays.sort(ux);
        java.util.Arrays.sort(uy);

        return new int[][]{ ux, uy }; // [0]=X[3], [1]=Y[3]
    }

    /** Прямоугольник внутренней области (объединение 9 иконок 16×16) по найденной 3×3. */
    @Nullable
    private int[] rightReceiverInnerRect() {
        int[][] xy = findRightReceiverGridXY();
        if (xy == null) return null;
        int[] xs = xy[0], ys = xy[1];
        int minX = xs[0], minY = ys[0];
        int maxX = xs[2] + 16; // правая грань третьей ячейки
        int maxY = ys[2] + 16;
        return new int[]{ minX, minY, maxX - minX, maxY - minY }; // x,y,w,h
    }



    /** Полная отрисовка страниц c разделением depth-буфера на "ломтики" и поворотом в стопках. */
    private void drawPagesWithRealZ() {
        if (pageCache.isEmpty()) return;

        final float sStack = PAGE_SCALE;

        // Одна и та же линия центра для стопок и активной
        final int centerYLineStack = this.guiTop + 49;
        final int stackGridH       = Math.round(visibleHeight() * sStack);
        final int baseY            = centerYLineStack - stackGridH / 2;

        // Центры стопок по требованию
        int leftCenterX  = this.guiLeft + 128;
        int rightCenterX = this.guiLeft + 210;

        // gridX – это левый край сетки, поэтому смещаем от центра на половину ширины сетки
        final int leftBaseX  = leftCenterX  - Math.round(visibleWidth() * sStack) / 2;
        final int rightBaseX = rightCenterX - Math.round(visibleWidth() * sStack) / 2;

        int max = maxPageToDraw();
        if (max <= 0) return;

        final boolean liftingFromLeft  = (animState == AnimState.TO_CENTER && activeFromLeft);
        final boolean liftingFromRight = (animState == AnimState.TO_CENTER && !activeFromLeft);

        // ---------- 1) Левая стопка ----------
        for (int pageNo = 1; pageNo <= baseStackOffset; pageNo++) {
            if ((liftingFromLeft && pageNo == takenPageNo) || pageNo == hiddenLeftTop) continue;

            PageSnap snap = pageCache.get(pageNo);
            if (snap == null) continue;

            final int gx = leftBaseX;
            final int gy = leftStackYForPage(pageNo, baseY);

            pushDepthSlice(depthOrderForPage(pageNo));

            int[] c = new int[2];
            paperCenterFor(gx, gy, sStack, c);
            GlStateManager.pushMatrix();
            GlStateManager.translate(c[0], c[1], 0);
            GlStateManager.rotate(angleForPage(pageNo, true), 0, 0, 1);
            GlStateManager.translate(-c[0], -c[1], 0);

            drawPagePaperUnderGridZ(gx, gy, sStack, 0f, true);
            drawPageGridOnlyZ   (snap, gx, gy, sStack, /*showCounts=*/true, 0f, /*isActive=*/false);

            GlStateManager.popMatrix();
            popDepthSlice();
        }

        // ---------- 2) Правая стопка ----------
// БЫЛО: снизу рассчитывался order, но рисовали СВЕРХУ ВНИЗ (pageNo растёт)
// for (int pageNo = baseStackOffset + 1; pageNo <= max; pageNo++) {

        for (int pageNo = max; pageNo >= baseStackOffset + 1; pageNo--) { // ← РИСУЕМ СНИЗУ ВВЕРХ
            // активную рисуем отдельно
            if (pageNo == takenPageNo &&
                    (animState == AnimState.TO_CENTER || animState == AnimState.CENTERED ||
                            animState == AnimState.TO_LEFT   || animState == AnimState.TO_RIGHT)) continue;

            if ((liftingFromLeft && pageNo == takenPageNo) ||
                    (liftingFromRight && pageNo == takenPageNo)) continue;

            PageSnap snap = pageCache.get(pageNo);
            if (snap == null) continue;

            final int gx = rightBaseX;
            final int gy = rightStackYForPage(pageNo, baseY); // 0 = верх (baseStackOffset+1), ниже растёт

            pushDepthSlice(depthOrderForPage(pageNo));        // правый верх получает наименьший order

            int[] c = new int[2];
            paperCenterFor(gx, gy, sStack, c);
            GlStateManager.pushMatrix();
            GlStateManager.translate(c[0], c[1], 0);
            GlStateManager.rotate(angleForPage(pageNo, false), 0, 0, 1);
            GlStateManager.translate(-c[0], -c[1], 0);

            drawPagePaperUnderGridZ(gx, gy, sStack, 0f, true);
            drawPageGridOnlyZ   (snap, gx, gy, sStack, /*showCounts=*/true, 0f, /*isActive=*/false);

            GlStateManager.popMatrix();
            popDepthSlice();
        }


        // ---------- 3) Активная страница ----------
        if (takenPageNo >= 1 && takenPageNo <= max &&
                (animState == AnimState.TO_CENTER || animState == AnimState.CENTERED ||
                        animState == AnimState.TO_LEFT   || animState == AnimState.TO_RIGHT)) {

            PageSnap snap = pageCache.get(takenPageNo);
            if (snap != null) {
                // Откуда летим: ВЕРХ соответствующей стопки по той же линии центра
                final int fromTopNo = activeFromLeft ? Math.max(1, baseStackOffset) : (baseStackOffset + 1);
                final int fromBaseX = activeFromLeft ? leftBaseX : rightBaseX;
                final int fromBaseY = baseY; // top-left Y верха стопки; у нас один для обеих стопок

                final float s0 = PAGE_SCALE;

                // Куда в центр (та же линия центра, но другая высота из-за масштаба)
                final float s1 = TARGET_SCALE;
                final int centerYLineActive = centerLineYForScale(s1);                 // та же горизонтальная линия
                final int activeGridH = Math.round(visibleHeight() * s1);
                final int toXc = centerGridXForScale(s1);
                final int toYc = centerYLineActive - activeGridH / 2;     // top-left для активной в центре

                // Куда «садиться» обратно в стопку — на ВЕРХ стопки по той же линии центра
                final int toXl = leftBaseX;
                final int toYl = baseY;  // верх левой стопки
                final int toXr = rightBaseX;
                final int toYr = baseY;  // верх правой стопки

                float xf, yf, sc;
                if (animState == AnimState.TO_CENTER) {
                    xf = lerp(fromBaseX, toXc, animT);
                    // ДЕРЖИМ ЦЕНТР ПО Y: от fromBaseY к toYc, но эти два значения подобраны так,
                    // чтобы центр оставался одинаковым при смене масштаба
                    yf = lerp(fromBaseY, toYc, animT);
                    sc = lerp(s0, s1, animT);
                } else if (animState == AnimState.CENTERED) {
                    xf = toXc;
                    yf = toYc;
                    sc = s1;
                } else if (animState == AnimState.TO_LEFT) {
                    xf = lerp(toXc, toXl, animT);
                    yf = lerp(toYc, toYl, animT);   // центр сохраняется, топ-левый корректируется под масштаб
                    sc = lerp(s1, PAGE_SCALE, animT);
                } else { // TO_RIGHT
                    xf = lerp(toXc, toXr, animT);
                    yf = lerp(toYc, toYr, animT);
                    sc = lerp(s1, PAGE_SCALE, animT);
                }

                int xi = Math.round(xf);
                int yi = Math.round(yf);

                pushDepthSlice(orderActive()); // активная всегда ближе всех
                drawPagePaperUnderGridZ(xi, yi, sc, 0f, true);
                drawPageGridOnlyZ   (snap, xi, yi, sc, /*showCounts=*/true, 0f, /*isActive=*/true);
                popDepthSlice();
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        // клики по строке поиска
        if (searchField != null) {
            // собственная проверка, чтобы не мешать остальным зонам
            int mx = mouseX, my = mouseY;
            if (mx >= searchField.x && mx < searchField.x + searchField.width &&
                    my >= searchField.y && my < searchField.y + searchField.height) {
                searchField.setFocused(true);
            } else {
                // не снимаем фокус, чтобы позволить печатать; снимешь если нужно:
                // searchField.setFocused(false);
            }
        }

        // ---- [A] СНАЧАЛА: клик по левой 3×3 "заказной" сетке (ЛКМ +, ПКМ −, Ctrl ×10, Shift ×64) ----
        {
            int[] r = new int[4];
            getDraftGridDrawXY(r);
            int gx = r[0], gy = r[1], gw = r[2], gh = r[3];

            int step = DRAFT_ICON_SIZE + DRAFT_ICON_GAP; // 16 (шаг по сетке)

            if (mouseX >= gx && mouseX < gx + gw && mouseY >= gy && mouseY < gy + gh) {
                int cx = (mouseX - gx) / step; // 0..2
                int cy = (mouseY - gy) / step; // 0..2
                if (cx >= 0 && cx < 3 && cy >= 0 && cy < 3) {
                    int slot = cy * 3 + cx;

                    // что сейчас в черновике:
                    final boolean craft = isCraftMode();
                    List<ItemStack> order  = isFrozen() ? ClientCatalogCache.getPending(craft)
                            : ClientCatalogCache.getDraft(craft);
                    List<Integer>   counts = isFrozen() ? ClientCatalogCache.getPendingCounts(craft)
                            : ClientCatalogCache.getDraftCounts(craft);

                    if (order != null && slot < order.size()) {
                        ItemStack st = order.get(slot);
                        if (st != null && !st.isEmpty()) {
                            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                            boolean ctrl  = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

                            int amount = incByMods(shift, ctrl); // 1 / 10 / 64
                            int sign   = (mouseButton == 1) ? -1 : +1; // ЛКМ +, ПКМ −
                            sendAdjust(st, sign * amount);

                            mc.player.playSound(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK,
                                    0.5f, (sign > 0) ? 1.1f : 0.9f);
                            return; // не пропускаем к обработке стопок/активной страницы
                        }
                    }
                }
            }
        }

        // ---- [B] Клики по активной странице (инк/дек по видимой сетке каталога в центре) ----
        if (animState == AnimState.CENTERED && takenPageNo >= 1) {
            int idx = activeGridIndexAt(mouseX, mouseY);
            if (idx >= 0) {
                PageSnap snap = pageCache.get(takenPageNo);
                if (snap != null && snap.items != null && idx < snap.items.size()) {
                    ItemStack st = snap.items.get(idx);
                    if (st != null && !st.isEmpty()) {
                        boolean lmb = (mouseButton == 0);
                        boolean rmb = (mouseButton == 1);
                        if (lmb || rmb) {
                            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                            boolean ctrl  = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                            int base = incByMods(shift, ctrl);
                            int delta = lmb ? base : -base;
                            adjustDraftFor(st, delta);
                            return;
                        }
                    }
                }
            }
        }

        // ---- [C] Перелистывание стопок (только ЛКМ) ----
        if (mouseButton != 0) return;

        int max = maxPageToDraw();
        if (max <= 0) return;

        final int topRight = (baseStackOffset + 1) <= max ? (baseStackOffset + 1) : -1;
        final int topLeft  = baseStackOffset;

        // Прямоугольники стопок в координатах экрана
        int leftX1  = this.guiLeft + 85;
        int leftY1  = this.guiTop  + 1;
        int leftX2  = this.guiLeft + 133;
        int leftY2  = this.guiTop  + 102;

        int rightX1 = this.guiLeft + 204;
        int rightY1 = this.guiTop  + 1;
        int rightX2 = this.guiLeft + 252;
        int rightY2 = this.guiTop  + 102;

        boolean hitLeft  = (topLeft  >= 1) &&
                mouseX >= leftX1 && mouseX <= leftX2 &&
                mouseY >= leftY1 && mouseY <= leftY2;

        boolean hitRight = (topRight >= 1) &&
                mouseX >= rightX1 && mouseX <= rightX2 &&
                mouseY >= rightY1 && mouseY <= rightY2;

        switch (animState) {
            case IDLE: {
                if (hitRight) {
                    takenPageNo = topRight;
                    activeFromLeft = false;
                    pendingGrab = GrabSource.NONE;
                    queuedFrom = GrabSource.NONE; queuedPageNo = -1;
                    pendingHideLeftTop = -1;
                    animState = AnimState.TO_CENTER;
                    animStartMs = System.currentTimeMillis(); animT = 0f;
                    return;
                }
                if (hitLeft) {
                    takenPageNo = topLeft;
                    activeFromLeft = true;
                    pendingGrab = GrabSource.NONE;
                    queuedFrom = GrabSource.NONE; queuedPageNo = -1;
                    pendingHideLeftTop = topLeft;
                    animState = AnimState.TO_CENTER;
                    animStartMs = System.currentTimeMillis(); animT = 0f;
                    return;
                }
                break;
            }
            case CENTERED: {
                if (hitRight) {
                    int nextRightAfterShift = baseStackOffset + 2;
                    if (nextRightAfterShift <= max) {
                        queuedFrom  = GrabSource.RIGHT;
                        queuedPageNo = nextRightAfterShift;
                        pendingHideLeftTop = -1;
                        pendingGrab = GrabSource.NONE;
                        animState   = AnimState.TO_LEFT;
                        animStartMs = System.currentTimeMillis(); animT = 0f;
                    }
                    return;
                }
                if (hitLeft) {
                    if (topLeft >= 1) {
                        queuedFrom   = GrabSource.LEFT;
                        queuedPageNo = topLeft;
                        pendingHideLeftTop = -1;
                        pendingGrab = GrabSource.NONE;
                        animState   = AnimState.TO_RIGHT;
                        animStartMs = System.currentTimeMillis(); animT = 0f;
                    }
                    return;
                }
                return;
            }
            case TO_CENTER:
            case TO_LEFT:
            case TO_RIGHT:
                // игнор во время анимации
                break;
        }
    }
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (searchField != null && searchField.isFocused()) {
            // поддержка Backspace/строкового ввода
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchField.setFocused(false);
                return;
            }
            searchField.textboxKeyTyped(typedChar, keyCode);
            currentSearch = searchField.getText();
            lastSearchEditTs = System.currentTimeMillis();
            return; // не пропускаем дальше, чтобы не ловить хоткеи GUI
        }
        super.keyTyped(typedChar, keyCode);
    }


    /** Координаты фактической отрисовки 3×3 превью заказа. */
    private void getDraftGridDrawXY(int[] out) { // out[0]=x, out[1]=y, out[2]=w, out[3]=h
        out[0] = this.guiLeft + DRAFT_GRID_X;
        out[1] = this.guiTop  + DRAFT_GRID_Y;
        out[2] = DRAFT_GRID_W;
        out[3] = DRAFT_GRID_H;
    }

    /** Прямоугольник активной страницы в центре (без EXTRA_PAD). null если не CENTERED. */
    @Nullable
    private int[] getActivePageRectIfCentered(){
        if (animState != AnimState.CENTERED || takenPageNo < 1) return null;
        final float s = TARGET_SCALE;
        int gx = centerGridXForScale(s);
        int centerYLine = centerLineYForScale(s);
        int gridH = Math.round(visibleHeight()*s);
        int gy = centerYLine - gridH/2;
        int[] r = new int[4];
        computePaperRectForGridHit(gx, gy, s, r);
        return r;
    }



    /* ===== Z-слои: страничные методы ===== */
    private void drawPagePaperUnderGridZ(int gridX, int gridY, float scale, float baseZ, boolean depthByAlpha) {
        // ширина/высота сетки каталога при данном масштабе
        int gridW = Math.round(visibleWidth()  * scale);
        int gridH = Math.round(visibleHeight() * scale);

        // центр сетки
        int centerX = gridX + gridW / 2;
        int centerY = gridY + gridH / 2;

        // реальный размер страницы = 74×94 с учётом масштаба
        int paperDrawW = Math.round(PAGE_PAPER_W * scale); // 74 * scale
        int paperDrawH = Math.round(PAGE_PAPER_H * scale); // 94 * scale

        int paperDrawX = centerX - paperDrawW / 2;
        int paperDrawY = centerY - paperDrawH / 2;

        mc.getTextureManager().bindTexture(TEX_PAGE_PAPER);

        GlStateManager.pushMatrix();
        glResetForUi();

        // ===== PASS 1: depth pre-pass =====
        GlStateManager.translate(0, 0, baseZ + Z_PAPER_LOCAL);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(false, false, false, false);

        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        boolean disabledAlpha = false;

        if (depthByAlpha) {
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0f);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            disabledAlpha = true;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(paperDrawX, paperDrawY, 0);
        GlStateManager.scale(paperDrawW / (float) PAGE_PAPER_W,
                paperDrawH / (float) PAGE_PAPER_H,
                1f);
        drawModalRectWithCustomSizedTexture(0, 0, 0, 0, PAGE_PAPER_W, PAGE_PAPER_H, PAGE_PAPER_W, PAGE_PAPER_H);
        GlStateManager.popMatrix();

        if (disabledAlpha) GL11.glEnable(GL11.GL_ALPHA_TEST);

        // ===== PASS 2: цвет по GL_EQUAL =====
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthMask(false);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glDepthFunc(GL11.GL_EQUAL);

        GlStateManager.pushMatrix();
        GlStateManager.translate(paperDrawX, paperDrawY, 0);
        GlStateManager.scale(paperDrawW / (float) PAGE_PAPER_W,
                paperDrawH / (float) PAGE_PAPER_H,
                1f);
        drawModalRectWithCustomSizedTexture(0, 0, 0, 0, PAGE_PAPER_W, PAGE_PAPER_H, PAGE_PAPER_W, PAGE_PAPER_H);
        GlStateManager.popMatrix();

        // restore
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(prevDepthFunc);
        GlStateManager.popMatrix();
    }


    private void drawPageGridOnlyZ(PageSnap p, int x, int y, float scale,
                                   boolean showCounts, float baseZ, boolean isActive) {
        if (p == null || p.items == null) return;

        final float usedBaseZ = isActive ? zForActivePage() : baseZ;

        // "полный" размер сетки (как раньше)
        float fullGridW = visibleWidth()  * scale; // 5 * cell * scale
        float fullGridH = visibleHeight() * scale; // 7 * cell * scale

        // ужатая сетка: 2/3 от прежней (10px иконка + 2px зазор)
        float gridW = fullGridW * PAGE_GRID_SCALE;
        float gridH = fullGridH * PAGE_GRID_SCALE;

        // чтобы сетка была по центру страницы — смещаем внутрь на половину разницы
        float offsetX = (fullGridW - gridW) / 2.0f;
        float offsetY = (fullGridH - gridH) / 2.0f;

        GlStateManager.pushMatrix();
        // x,y — это левый верх "полной" сетки; добавляем смещение вовнутрь
        GlStateManager.translate(x + offsetX, y + offsetY, usedBaseZ + Z_GRID_LOCAL);
        GlStateManager.scale(scale * PAGE_GRID_SCALE, scale * PAGE_GRID_SCALE, 1f);

        RenderHelper.enableGUIStandardItemLighting();

        float prevZ = this.itemRender.zLevel;
        this.itemRender.zLevel = 0.0F;

        int shown = Math.min(p.items.size(), WINDOW_SIZE);
        for (int i = 0; i < shown; i++) {
            ItemStack st = p.items.get(i);
            if (st == null || st.isEmpty()) continue;

            int vcol = i % VISIBLE_COLS_COUNT, vrow = i / VISIBLE_COLS_COUNT;
            int cx = vcol * cell;
            int cy = vrow * cell;

            itemRender.renderItemAndEffectIntoGUI(st, cx, cy);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, st, cx, cy, "");

            if (showCounts) {
                int cnt = (p.counts != null && i < p.counts.size() && p.counts.get(i) != null)
                        ? Math.max(0, p.counts.get(i))
                        : Math.max(1, st.getCount());
                if (cnt > 1) {
                    final float REL_Z_TEXT = Z_TEXT_OVER;
                    drawTextOutlinedRightBottomZ(
                            formatCount(cnt),
                            cx, cy,
                            0.50f,
                            0xFFF8E1, 0xC0000000,
                            REL_Z_TEXT
                    );
                }

                if (isCraftMode() && p.makeCounts != null) {
                    int craftCnt = (i < p.makeCounts.size() && p.makeCounts.get(i) != null)
                            ? Math.max(0, p.makeCounts.get(i))
                            : 0;
                    if (craftCnt > 0) {
                        final float REL_Z_TEXT = Z_TEXT_OVER;
                        final int MAIN_BLUE    = 0xFF3A8AE6;
                        final int OUTLINE_COL  = 0xFF000000;

                        drawTextOutlinedRightTopZC(
                                formatCount(craftCnt),
                                cx, cy,
                                0.50f,
                                MAIN_BLUE, OUTLINE_COL,
                                REL_Z_TEXT
                        );
                    }
                }
            }
        }

        this.itemRender.zLevel = prevZ;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    /** Текст с контуром в правом-верхнем углу слота (над иконкой), с относительным Z. */
    private void drawTextOutlinedRightTopZC(String txt, int slotX, int slotY,
                                            float scale, int mainColor, int outlineColor, float relZ) {
        if (txt == null || txt.isEmpty()) return;

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GlStateManager.pushMatrix();
        // якорь — правый верх слота
        GlStateManager.translate(slotX + 16F, slotY + 0F, relZ);
        GlStateManager.scale(scale, scale, 1F);

        int w = this.fontRenderer.getStringWidth(txt);

        // контур (крест + диагонали, как у нижнего)
        this.fontRenderer.drawString(txt, -w - 1,  0,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w + 1,  0,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w,     -1,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w,      1,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w - 1, -1,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w + 1, -1,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w - 1,  1,     outlineColor, false);
        this.fontRenderer.drawString(txt, -w + 1,  1,     outlineColor, false);

        // основной
        this.fontRenderer.drawString(txt, -w, 0, mainColor, false);

        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
    }

    private boolean isFrozen() {
        List<ItemStack> p = ClientCatalogCache.getPending(isCraftMode());
        if (p==null) return false;
        for (ItemStack s : p) if (s!=null && !s.isEmpty()) return true;
        return false;
    }

    private static String formatCount(int n) {
        if (n<0) n=0;
        if (n>=1_000_000){ int w=n/1_000_000, t=(n%1_000_000)/100_000; return (t>0)?(w+"."+t+"m"):(w+"m"); }
        if (n>=1_000){ int w=n/1_000, t=(n%1_000)/100; return (t>0)?(w+"."+t+"k"):(w+"k"); }
        return Integer.toString(n);
    }

    private void render3x3CenteredInRect(List<ItemStack> nine, List<Integer> counts,
                                         int rectX, int rectY, int rectW, int rectH) {
        if (nine == null) return;

        RenderHelper.enableGUIStandardItemLighting();

        final int icon = DRAFT_ICON_SIZE;                    // 12
        final int step = DRAFT_ICON_SIZE + DRAFT_ICON_GAP;   // 12 + 4 = 16
        final float iconScale = icon / 16.0f;                // 12/16 = 0.75

        for (int i = 0; i < Math.min(9, nine.size()); i++) {
            ItemStack st = nine.get(i);
            if (st == null || st.isEmpty()) continue;

            int col = i % 3;
            int row = i / 3;

            // Левый верхний угол ячейки 12×12:
            int cx = rectX + col * step; // X = 20, 36, 52...
            int cy = rectY + row * step; // Y = 41, 57, 73...

            // --- иконка 12×12 ---
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0F);
            GlStateManager.scale(iconScale, iconScale, 1F); // 16 → 12
            itemRender.renderItemAndEffectIntoGUI(st, 0, 0);
            itemRender.renderItemOverlayIntoGUI(this.fontRenderer, st, 0, 0, "");
            GlStateManager.popMatrix();

            // --- счётчик под 12×12, а не 16×16 ---
            // drawTextOutlinedRightBottom опирается на (slotX + 16, slotY + 16),
            // поэтому подвинем слот так, чтобы этот угол стал (cx + 12, cy + 12).
            int slotX = cx - (16 - icon); // cx - 4
            int slotY = cy - (16 - icon); // cy - 4

            int cnt = (counts != null && i < counts.size() && counts.get(i) != null)
                    ? counts.get(i)
                    : 1;

            if (cnt > 1) {
                drawTextOutlinedRightBottom(
                        formatCount(cnt),
                        slotX, slotY,
                        0.45f,                // чуть меньше, чем 0.5, чтобы аккуратно влезло в 12×12
                        0xFFF8E1, 0xC0000000
                );
            }
        }

        RenderHelper.disableStandardItemLighting();
    }

    private void layoutDraftPaper() {
        final int targetW = 3*cell, targetH = 3*cell;
        final int contentW = PAPER_W - 2*PAPER_MARGIN;
        final int contentH = PAPER_H - 2*PAPER_MARGIN;
        final int topTwoH  = (contentH*2)/3;

        gridX = Math.round(leftLeft + GRID_ADJ_X);
        gridY = Math.round(leftTop  + GRID_ADJ_Y);

        int offsetX = PAPER_MARGIN + (contentW - targetW)/2;
        int offsetY = PAPER_MARGIN + (topTwoH  - targetH)/2;
        paperX = gridX - offsetX;
        paperY = gridY - offsetY;
    }

    /* clones */
    private static List<ItemStack> cloneItems(List<ItemStack> in){
        if (in==null) return Collections.emptyList();
        ArrayList<ItemStack> out=new ArrayList<>(in.size());
        for (ItemStack s: in) out.add(s==null? ItemStack.EMPTY : s.copy());
        return out;
    }
    private static List<Integer> cloneInts(List<Integer> in){ return (in==null)? Collections.emptyList(): new ArrayList<>(in); }
    private static List<Boolean> cloneBools(List<Boolean> in){ return (in==null)? Collections.emptyList(): new ArrayList<>(in); }

    private static void glResetForUi() {
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** Геометрия бумаги для данной страницы — в координатах GUI */
    private void computePaperRectForGrid(int gridX, int gridY, float scale, int[] outXYWH) {
        int gridW = Math.round(visibleWidth()  * scale);
        int gridH = Math.round(visibleHeight() * scale);

        int centerX = gridX + gridW / 2;
        int centerY = gridY + gridH / 2;

        // базовый размер бумаги
        int paperW = Math.round(PAGE_PAPER_W * scale);
        int paperH = Math.round(PAGE_PAPER_H * scale);

        // добавим запас для "стопки", чтобы при повороте края не обрезались
        paperW += 2 * STACK_EXTRA_PAD;
        paperH += 2 * STACK_EXTRA_PAD;

        int paperX = centerX - paperW / 2;
        int paperY = centerY - paperH / 2;

        outXYWH[0] = paperX;
        outXYWH[1] = paperY;
        outXYWH[2] = paperW;
        outXYWH[3] = paperH;
    }

    /** Геометрия бумаги БЕЗ расширений для хит-теста (без STACK_EXTRA_PAD). */
    private void computePaperRectForGridHit(int gridX, int gridY, float scale, int[] outXYWH) {
        int gridW = Math.round(visibleWidth()  * scale);
        int gridH = Math.round(visibleHeight() * scale);

        int centerX = gridX + gridW / 2;
        int centerY = gridY + gridH / 2;

        int paperW = Math.round(PAGE_PAPER_W * scale); // ровно 74 * scale
        int paperH = Math.round(PAGE_PAPER_H * scale); // ровно 94 * scale

        int paperX = centerX - paperW / 2;
        int paperY = centerY - paperH / 2;

        outXYWH[0] = paperX;
        outXYWH[1] = paperY;
        outXYWH[2] = paperW;
        outXYWH[3] = paperH;
    }

    // ---- состояние "выпрыгивания" страницы ----
    private enum AnimState { IDLE, TO_CENTER, CENTERED, TO_LEFT, TO_RIGHT }
    private AnimState animState = AnimState.IDLE;

    private int baseStackOffset = 0;  // сколько страниц уже «снято» вправо и отложено влево
    private int takenPageNo = -1;     // номер активной страницы

    private float animT = 0f;         // 0..1
    private long  animStartMs = 0L;
    private static final long  ANIM_MS = 280L;
    private static final float TARGET_SCALE = 1.15f*PAGE_SCALE;

    private static float lerp(float a, float b, float t){ return a + (b - a) * t; }

    private int centerGridXForScale(float s){
        // Центры стопок в локальных координатах GUI
        int leftCenterX  = this.guiLeft + 128;
        int rightCenterX = this.guiLeft + 210;

        // Центр активной страницы – ровно посередине между стопками
        int centerX = (leftCenterX + rightCenterX) / 2;

        int gridW = Math.round(visibleWidth() * s);
        return centerX - gridW / 2;
    }


    // depth режим: включить/выключить на время страниц
    private void beginDepthLayeringForGui() {
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.depthFunc(GL11.GL_LESS);   // ← вернуть строгое сравнение
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.disableCull();
    }
    private void endDepthLayeringForGui() {
        GlStateManager.disableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
    }
    /** Активная — фиксированно ближе всех */
    private float zForActivePage() { return Z_ACTIVE_BASE; }

    /** Случайный детерминированный угол поворота [-8..+8] для страницы. */
    private float angleForPage(int pageNo, boolean left) {
        int h = pageNo * 1103515245 + (left ? 0x9E3779B9 : 0x3C6EF372);
        h ^= (h >>> 16);
        float u = (h & 0x7fffffff) / 2147483647f; // [0..1)
        return u * 16f - 8f; // [-8..+8]
    }
    /** Центр бумаги (cx, cy) для страницы. */
    private void paperCenterFor(int gridX, int gridY, float scale, int[] outXY) {
        int[] r = new int[4];
        computePaperRectForGrid(gridX, gridY, scale, r);
        outXY[0] = r[0] + r[2] / 2;
        outXY[1] = r[1] + r[3] / 2;
    }

    /** Y-координата линии центра страниц при любом масштабе */
    private int centerLineYForScale(float s) {
        // Книга: 1..102, центр около 49 — привязываемся к нему
        return this.guiTop + 49;
    }

}
