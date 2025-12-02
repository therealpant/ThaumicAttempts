package therealpant.thaumicattempts.api;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Блоки, которые могут отдавать предпросмотры (иконки) для терминала заказов.
 */
public interface ITerminalOrderIconProvider {
    /**
     * @return список иконок с привязкой к конкретному слоту/блоку
     */
    List<ItemStack> listTerminalOrderIcons();
}
