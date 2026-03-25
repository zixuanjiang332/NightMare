package jdd.nightMare.Shop;

import jdd.nightMare.Game.BrandType;
import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.PlayerSession;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class BrandGUI implements Listener {
    private final GameManager gameManager;
    private final NamespacedKey BRAND_ID_KEY = new NamespacedKey(NightMare.getInstance(), "brand_id");
    private final String GUI_TITLE = "§8§l选择你的烙印 (最多6个)";

    public BrandGUI(GameManager gameManager) {
        this.gameManager = gameManager;
    }
    /**
     * 打开烙印选择菜单
     */
    public void open(Player player) {
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(GUI_TITLE));

        // 1. 填充边框
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.empty());
        glass.setItemMeta(glassMeta);

        int[] borders = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int slot : borders) inv.setItem(slot, glass);

        // 2. 填充信息面板 (第 4 槽)
        inv.setItem(4, createInfoItem(session));

        // 3. 填充所有烙印图标 (从第 10 槽开始，避开边框)
        int slot = 10;
        for (BrandType type : BrandType.values()) {
            // 跳过边框位置
            while (isBorder(slot)) slot++;

            inv.setItem(slot, createBrandIcon(session, type));
            slot++;
        }

        player.openInventory(inv);
    }

    private boolean isBorder(int slot) {
        int[] borders = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int b : borders) if (b == slot) return true;
        return false;
    }

    /**
     * 创建信息面板图标
     */
    private ItemStack createInfoItem(PlayerSession session) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        int count = session.getActiveBrands().size();

        meta.displayName(Component.text("§6§l当前选择状态").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7已选数量: " + (count >= 6 ? "§c" : "§a") + count + " / 6").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("§e点击下方图标进行 勾选/取消").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建烙印图标 (烟火之星)
     */
    private ItemStack createBrandIcon(PlayerSession session, BrandType type) {
        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        boolean isSelected = session.hasBrand(type.id);

        // 设置显示名
        meta.displayName(Component.text(type.displayName).decoration(TextDecoration.ITALIC, false));
        // 设置 Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§8类型: " + (type.maxCooldown > 0 ? "主动触发/冷却" : "被动/周期")).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        // 自动换行处理描述 (简单处理)
        lore.add(Component.text(type.description).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        if (type.maxCooldown > 0) {
            lore.add(Component.text("§7冷却时间: §f" + type.maxCooldown + "s").decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        if (isSelected) {
            lore.add(Component.text("§a● 已选择 (点击取消)").decoration(TextDecoration.ITALIC, false));
            // 增加附魔光效
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(Component.text("§7○ 未选择 (点击勾选)").decoration(TextDecoration.ITALIC, false));
        }

        // 写入 PDC 数据
        meta.getPersistentDataContainer().set(BRAND_ID_KEY, PersistentDataType.STRING, type.id);

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 监听点击逻辑
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;
        event.setCancelled(true); // 禁止取走物品

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.FIREWORK_STAR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (!meta.getPersistentDataContainer().has(BRAND_ID_KEY, PersistentDataType.STRING)) return;

        String brandId = meta.getPersistentDataContainer().get(BRAND_ID_KEY, PersistentDataType.STRING);
        PlayerSession session = gameManager.getPlayerSession(player);
        if (session == null) return;

        // 执行切换逻辑
        boolean currentlySelected = session.hasBrand(brandId);

        if (!currentlySelected && session.getActiveBrands().size() >= 6) {
            player.sendMessage("§c[错误] 你的烙印槽位已满 (6/6)！");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        session.toggleBrand(brandId); // 这个方法在上一轮建议中已经写在 PlayerSession 里了

        // 播放反馈音效
        if (currentlySelected) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.5f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
        // 刷新 GUI (通过重新打开)
        open(player);
    }
}