// src/main/java/therealpant/thaumicattempts/golemnet/link/BellLinkingHandler.java
package therealpant.thaumicattempts.golemnet.link;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import therealpant.thaumicattempts.ThaumicAttempts;
import therealpant.thaumicattempts.golemnet.tile.TileMirrorManager;
import therealpant.thaumicattempts.golemnet.tile.TileOrderTerminal;
import therealpant.thaumicattempts.golemnet.tile.TilePatternRequester;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ThaumicAttempts.MODID)
public class BellLinkingHandler {

    private static final Set<ResourceLocation> BELL_IDS = new HashSet<>(Arrays.asList(
            new ResourceLocation("thaumcraft", "golembell"),
            new ResourceLocation("thaumcraft", "golem_bell")
    ));

    private static final String TAG_ROOT  = "thaumicattempts";
    private static final String TAG_LINK  = "bell_link_mgr";
    private static final String TAG_DIM   = "dim";
    private static final String TAG_GLINT = "bell_glint";

    /* -------------------- Утилиты -------------------- */

    private static boolean isGolemBell(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == null) return false;
        ResourceLocation id = item.getRegistryName();
        if (id != null && BELL_IDS.contains(id)) return true;
        String key = item.getTranslationKey();
        return key != null && key.toLowerCase().contains("golem") && key.toLowerCase().contains("bell");
    }

    private static void msgChat(EntityPlayer p, String text) {
        // строго в чат (без хотбара)
        p.sendMessage(new TextComponentString(text));
    }

    /* -------------------- Эффект зачарования на колокольчике -------------------- */

    private static void setBellGlint(ItemStack bell, boolean on) {
        if (bell == null || bell.isEmpty()) return;
        NBTTagCompound root = bell.getOrCreateSubCompound(TAG_ROOT);

        if (on) {
            if (!root.getBoolean(TAG_GLINT)) {
                // 1.12: визуальный блеск даёт наличие тега "ench" (можно пустого).
                // Дополнительно добавим Unbreaking I, но скроем в тултипе.
                bell.addEnchantment(Enchantments.UNBREAKING, 1);
                NBTTagCompound tag = bell.getTagCompound();
                if (tag == null) tag = new NBTTagCompound();

                // скрыть энчанты в тултипе (HideFlags |= 1)
                NBTTagCompound display = tag.getCompoundTag("display");
                int hide = display.getInteger("HideFlags");
                display.setInteger("HideFlags", hide | 1);
                tag.setTag("display", display);

                // гарантируем наличие "ench" (чтобы всегда был глиттер)
                if (!tag.hasKey("ench", 9)) tag.setTag("ench", new NBTTagList());

                bell.setTagCompound(tag);
                root.setBoolean(TAG_GLINT, true);
            }
        } else {
            NBTTagCompound tag = bell.getTagCompound();
            if (tag != null) {
                // убрать список "ench" => убрать глиттер
                if (tag.hasKey("ench", 9)) tag.removeTag("ench");
                // вернуть HideFlags как были (снять бит показа энчантов)
                if (tag.hasKey("display", 10)) {
                    NBTTagCompound display = tag.getCompoundTag("display");
                    int hide = display.getInteger("HideFlags");
                    display.setInteger("HideFlags", (hide & ~1));
                    tag.setTag("display", display);
                }
                bell.setTagCompound(tag);
            }
            root.removeTag(TAG_GLINT);
        }
    }

    private static boolean hasBellGlint(ItemStack bell) {
        NBTTagCompound root = bell.getSubCompound(TAG_ROOT);
        return root != null && root.getBoolean(TAG_GLINT);
    }

    /* -------------------- Сохранение выбранного менеджера в колокол -------------------- */

    private static void putLink(ItemStack bell, BlockPos pos, int dim) {
        if (bell == null || bell.isEmpty()) return;
        NBTTagCompound root = bell.getOrCreateSubCompound(TAG_ROOT);
        NBTTagCompound link = new NBTTagCompound();
        link.setLong("pos", pos.toLong());
        link.setInteger(TAG_DIM, dim);
        root.setTag(TAG_LINK, link);

        // гарантируем "ench", чтобы глиттер оставался (если его нет)
        NBTTagCompound tag = bell.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();
        if (!tag.hasKey("ench", 9)) tag.setTag("ench", new NBTTagList());
        bell.setTagCompound(tag);
    }

    @Nullable
    private static BlockPos getLinkedPos(ItemStack bell) {
        if (bell == null || bell.isEmpty() || !bell.hasTagCompound()) return null;
        NBTTagCompound root = bell.getSubCompound(TAG_ROOT);
        if (root == null || !root.hasKey(TAG_LINK, 10)) return null;
        NBTTagCompound link = root.getCompoundTag(TAG_LINK);
        return link.hasKey("pos") ? BlockPos.fromLong(link.getLong("pos")) : null;
    }

    private static int getLinkedDim(ItemStack bell, int fallback) {
        if (bell == null || bell.isEmpty() || !bell.hasTagCompound()) return fallback;
        NBTTagCompound root = bell.getSubCompound(TAG_ROOT);
        if (root == null || !root.hasKey(TAG_LINK, 10)) return fallback;
        NBTTagCompound link = root.getCompoundTag(TAG_LINK);
        return link.hasKey(TAG_DIM) ? link.getInteger(TAG_DIM) : fallback;
    }

    private static void clearLink(ItemStack bell) {
        if (bell == null || bell.isEmpty()) return;
        NBTTagCompound root = bell.getSubCompound(TAG_ROOT);
        if (root != null) root.removeTag(TAG_LINK);

        // убрать визуальный блеск
        NBTTagCompound tag = bell.getTagCompound();
        if (tag != null && tag.hasKey("ench", 9)) {
            tag.removeTag("ench");
            bell.setTagCompound(tag);
        }
    }

    /* -------------------- Снятие ссылки ПКМ по воздуху -------------------- */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        ItemStack held = e.getItemStack();
        if (!isGolemBell(held)) return;

        // считаем «по воздуху», если луч не попал в блок
        net.minecraft.util.math.RayTraceResult rt = e.getEntityPlayer().rayTrace(5.0D, 1.0F);
        boolean air = (rt == null || rt.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.MISS);
        if (!air) return;

        if (!e.getWorld().isRemote) {
            if (getLinkedPos(held) != null || hasBellGlint(held)) {
                clearLink(held);
                setBellGlint(held, false); // снять визуальный эффект
            }
        }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty e) {
        ItemStack held = e.getItemStack();
        if (!isGolemBell(held)) return;
        if (getLinkedPos(held) != null || hasBellGlint(held)) {
            if (!e.getWorld().isRemote) {
                clearLink(held);
                setBellGlint(held, false);
            }
            e.setCanceled(true);
        }
    }

    /* -------------------- Работа по блокам -------------------- */

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != EnumHand.MAIN_HAND) return;

        EntityPlayer player = e.getEntityPlayer();
        World world = e.getWorld();
        ItemStack held = e.getItemStack();
        boolean bellInHand = isGolemBell(held);
        boolean sneaking = player.isSneaking();

        TileEntity te = world.getTileEntity(e.getPos());
        if (te == null) return;

        // === 1) Менеджер: Shift+ПКМ колокольчиком — выбрать менеджер, включить “глиттер”
        if (te instanceof TileMirrorManager) {
            if (bellInHand && sneaking) {
                if (!world.isRemote) {
                    TileMirrorManager mgr = (TileMirrorManager) te;
                    String playerUuid = player.getUniqueID().toString();
                    String owner = mgr.getOwnerUuid();
                    if (owner == null || owner.isEmpty()) {
                        mgr.setOwnerUuid(playerUuid);
                    } else if (!owner.equals(playerUuid)) {
                        // чужой менеджер — не даём выбрать
                        msgChat(player, "§cNot Linked");
                        denyAndCancel(e);
                        return;
                    }
                    putLink(held, e.getPos(), player.dimension);
                    setBellGlint(held, true);  // чисто визуал, без сообщений
                }
                denyAndCancel(e);
                return;
            }
            // остальные клики по менеджеру игнорируем
        }

        // дальше работаем только с терминалом/реквестером, и только если в руке колокол ИЛИ игрок присел
        if (!(te instanceof TileOrderTerminal) && !(te instanceof TilePatternRequester)) return;
        if (!(bellInHand || sneaking)) return;

        // АВТОПОИСК ЗАПРЕЩЁН — используем только сохранённый в колоколе менеджер
        BlockPos linkedMgrPos = getLinkedPos(held);

        if (!world.isRemote) {
            // Проверка измерения при наличии ссылки
            if (linkedMgrPos != null) {
                int linkDim = getLinkedDim(held, player.dimension);
                if (linkDim != player.dimension) {
                    msgChat(player, "§cNot Linked");
                    denyAndCancel(e);
                    return;
                }
            }

            /* ---------- Терминал ---------- */
            if (te instanceof TileOrderTerminal) {
                TileOrderTerminal term = (TileOrderTerminal) te;

                // Идемпотентность: уже привязан к этому же менеджеру
                if (linkedMgrPos != null && linkedMgrPos.equals(term.getManagerPos())) {
                    msgChat(player, "§aLinked");
                    denyAndCancel(e);
                    return;
                }

                // Отвязка: Shift+ПКМ без выбранного менеджера
                if (sneaking && term.getManagerPos() != null && linkedMgrPos == null) {
                    BlockPos oldMgr = term.getManagerPos();
                    term.setManagerPos(null);
                    if (oldMgr != null) {
                        TileEntity mte = world.getTileEntity(oldMgr);
                        if (mte instanceof TileMirrorManager) {
                            ((TileMirrorManager) mte).unbind(term.getPos());
                            ((TileMirrorManager) mte).cancelAllForDestination(term.getPos());
                        }
                    }
                    msgChat(player, "§cNot Linked");
                    denyAndCancel(e);
                    return;
                }

                // Привязка: есть выбранный менеджер в колоколе
                if (linkedMgrPos != null) {
                    TileEntity mte = world.getTileEntity(linkedMgrPos);
                    if (mte instanceof TileMirrorManager) {
                        TileMirrorManager mgr = (TileMirrorManager) mte;

                        // если терминал уже где-то привязан (к другому менеджеру) — аккуратно отцепим
                        if (term.getManagerPos() != null && !linkedMgrPos.equals(term.getManagerPos())) {
                            TileEntity old = world.getTileEntity(term.getManagerPos());
                            if (old instanceof TileMirrorManager) {
                                ((TileMirrorManager) old).unbind(term.getPos());
                                ((TileMirrorManager) old).cancelAllForDestination(term.getPos());
                            }
                            term.setManagerPos(null);
                        }

                        // Сначала проверим, не был ли уже зарегистрирован этот терминал в наборе менеджера
                        // (если да — не требуем свободное зеркало, просто подтверждаем)
                        boolean alreadyBound = false;
                        // Прямого API нет — пробуем через tryBindTerminal, но сделаем идемпотентность сами:
                        // Если term уже указывает на этот менеджер — мы бы вернулись выше.
                        // Здесь просто попытаемся.
                        if (mgr.tryBindTerminal(term.getPos())) {
                            // OK — привяжем терминал к менеджеру
                            term.setManagerPos(linkedMgrPos);
                            term.markDirty();
                            world.notifyBlockUpdate(term.getPos(),
                                    world.getBlockState(term.getPos()),
                                    world.getBlockState(term.getPos()), 3);

                            mgr.allowOwner(player.getUniqueID());
                            msgChat(player, "§aLinked");
                        } else {
                            // Варианты отказа: нет свободного реального зеркала, вне радиуса и т.п.
                            msgChat(player, "§cNot Linked");
                        }
                    } else {
                        msgChat(player, "§cNot Linked");
                    }
                    denyAndCancel(e);
                    return;
                }

                // сюда не доходим (прочие случаи отсеяны)
                return;
            }

            /* ---------- Реквестер ---------- */
            if (te instanceof TilePatternRequester) {
                TilePatternRequester req = (TilePatternRequester) te;

                // Идемпотентность: уже привязан к этому же менеджеру
                if (linkedMgrPos != null && linkedMgrPos.equals(req.getManagerPos())) {
                    msgChat(player, "§aLinked");
                    denyAndCancel(e);
                    return;
                }

                // Отвязка: Shift+ПКМ без выбранного менеджера
                if (sneaking && req.getManagerPos() != null && linkedMgrPos == null) {
                    BlockPos oldMgr = req.getManagerPos();
                    req.setManagerPos(null);
                    if (oldMgr != null) {
                        TileEntity mte = world.getTileEntity(oldMgr);
                        if (mte instanceof TileMirrorManager) {
                            ((TileMirrorManager) mte).unbind(req.getPos());
                            ((TileMirrorManager) mte).unregisterRequester(req.getPos());
                        }
                    }
                    msgChat(player, "§cNot Linked");
                    denyAndCancel(e);
                    return;
                }

                // Привязка к выбранному менеджеру
                if (linkedMgrPos != null) {
                    TileEntity mte = world.getTileEntity(linkedMgrPos);
                    if (mte instanceof TileMirrorManager) {
                        TileMirrorManager mgr = (TileMirrorManager) mte;

                        // если реквестер уже привязан к другому менеджеру — отцепим
                        if (req.getManagerPos() != null && !linkedMgrPos.equals(req.getManagerPos())) {
                            TileEntity old = world.getTileEntity(req.getManagerPos());
                            if (old instanceof TileMirrorManager) {
                                ((TileMirrorManager) old).unbind(req.getPos());
                                ((TileMirrorManager) old).unregisterRequester(req.getPos());
                            }
                            req.setManagerPos(null);
                        }

                        // пробуем занять зеркало и вычислительную ячейку
                        if (mgr.tryBindRequester(req.getPos())) {
                            // регистрируем реквестера (чтобы Craft не был пустым)
                            mgr.registerRequester(req.getPos());
                            mgr.allowOwner(player.getUniqueID());
                            mgr.markDirty();
                            world.notifyBlockUpdate(mgr.getPos(),
                                    world.getBlockState(mgr.getPos()),
                                    world.getBlockState(mgr.getPos()), 3);

                            // сохраняем ссылку в реквестере
                            req.setManagerPos(linkedMgrPos);
                            req.markDirty();
                            world.notifyBlockUpdate(req.getPos(),
                                    world.getBlockState(req.getPos()),
                                    world.getBlockState(req.getPos()), 3);

                            msgChat(player, "§aLinked");
                        } else {
                            msgChat(player, "§cNot Linked");
                        }
                    } else {
                        msgChat(player, "§cNot Linked");
                    }
                    denyAndCancel(e);
                    return;
                }

                // сюда не доходим
                return;
            }
        }
    }

    /* -------------------- Хелпер отмены клика по блоку -------------------- */

    private static void denyAndCancel(PlayerInteractEvent.RightClickBlock e) {
        e.setUseBlock(Event.Result.DENY);
        e.setUseItem(Event.Result.DENY);
        e.setCanceled(true);
        e.setCancellationResult(EnumActionResult.SUCCESS);
    }
}
