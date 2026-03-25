package jdd.nightMare.InitialListener;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class DragEnchantListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack cursorItem = event.getCursor();   // 鼠标抓着的附魔书
        ItemStack currentItem = event.getCurrentItem(); // 槽位里的目标物品
        // 基础检查
        if (cursorItem == null || cursorItem.getType() != Material.ENCHANTED_BOOK) return;
        if (currentItem == null || currentItem.getType() == Material.AIR) return;

        // 获取 Meta
        if (!(cursorItem.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) return;
        ItemMeta targetMeta = currentItem.getItemMeta();
        if (targetMeta == null) return;

        Map<Enchantment, Integer> enchants = bookMeta.getStoredEnchants();
        if (enchants.isEmpty()) return;

        boolean appliedAny = false;

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int bookLevel = entry.getValue();

            // 检查物品是否可以接受该附魔
            if (enchant.canEnchantItem(currentItem)) {
                // 获取物品上已有的附魔等级（如果没有该附魔，则返回 0）
                int currentLevel = targetMeta.getEnchantLevel(enchant);
                int finalLevel = currentLevel;
                if (currentLevel == 0 || bookLevel > currentLevel) {
                    finalLevel = bookLevel;
                } else if (currentLevel == bookLevel) {
                    finalLevel = currentLevel + 1;
                } else {

                    continue;
                }
                targetMeta.addEnchant(enchant, finalLevel, true);
                appliedAny = true;
            }
        }

        if (appliedAny) {
            event.setCancelled(true);
            currentItem.setItemMeta(targetMeta);
            ItemStack newCursor = cursorItem.clone();
            if (newCursor.getAmount() > 1) {
                newCursor.setAmount(newCursor.getAmount() - 1);
                event.getView().setCursor(newCursor);
            } else {
                event.getView().setCursor(null);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            player.updateInventory();
        }
    }
}
