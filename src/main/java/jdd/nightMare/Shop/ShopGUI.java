package jdd.nightMare.Shop;

import jdd.nightMare.Game.GameManager;
import jdd.nightMare.Game.GameTeam;
import jdd.nightMare.NightMare;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

public class ShopGUI {
    // 边框玻璃板颜色
    private final GameManager gameManager;
    private final NamespacedKey PRICE_KEY = new NamespacedKey(NightMare.getInstance(), "shop_price");
    private final NamespacedKey CURRENCY_KEY = new NamespacedKey(NightMare.getInstance(), "shop_currency");
    private static final Material BORDER_GLASS = Material.GRAY_STAINED_GLASS_PANE;
    private static final org.bukkit.NamespacedKey HEALTH_KEY = new org.bukkit.NamespacedKey("nightmare", "health_boost");
    public ShopGUI(GameManager gameManager){
        this.gameManager=gameManager;
    }
    public void handlePurchaseLogic(Player player, ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(PRICE_KEY, PersistentDataType.INTEGER)) return;
        int price = pdc.get(PRICE_KEY, PersistentDataType.INTEGER);
        Material currency = Material.valueOf(pdc.get(CURRENCY_KEY, PersistentDataType.STRING));
        Material type = clicked.getType();
        // 【关键修复 1：血量购买的“前置拦截”】
        // 提前检查玩家是否已经满级，如果满级直接 Return，避免扣了钱再退款的麻烦！
        if (type == Material.APPLE || type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE) {
            // 获取当前的加成数值 (0.0, 4.0, 8.0 或 12.0)
            double currentBonus = getCurrentHealthBonus(player);
            // 定义本次点击想要达到的目标加成
            double targetBonus = (type == Material.APPLE) ? 4.0 :
                    (type == Material.GOLDEN_APPLE ? 8.0 : 12.0);
            // 如果目标加成已经达到或低于当前水平，直接拦截
            if (targetBonus <= currentBonus) {
                player.sendMessage("§c你已经拥有该级别或更高的永久生命提升！");
                return;
            }
        }
        // 通过了上方的拦截，现在开始正常检查余额并扣款
        if (!hasEnough(player, currency, price)) {
            player.sendMessage("§c资源不足！需要 " + price + " 个 " + (currency == Material.IRON_INGOT ? "铁锭" : "金锭"));
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.7f);
        removeCurrency(player, currency, price);
        // 发放商品逻辑
        if (type == Material.SPONGE) {
            executeItemLottery(player);
        } else if (type == Material.ENCHANTED_BOOK) {
            executeBookLottery(player);
        }else if (type == Material.APPLE || type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE) {
            upgradeHealth(player, type);
        } else {
            ItemStack itemToGive = clicked.clone();
            itemToGive.setAmount(clicked.getAmount());
            ItemMeta mainMeta = itemToGive.getItemMeta();
            mainMeta.lore(null);

            mainMeta.getPersistentDataContainer().remove(PRICE_KEY);
            mainMeta.getPersistentDataContainer().remove(CURRENCY_KEY);

            if (type == Material.ELYTRA && mainMeta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setDamage(type.getMaxDurability() - 10);
            }
            itemToGive.setItemMeta(mainMeta);

            Map<Enchantment, Integer> oldEnchants = getAndRemoveOldTier(player, type);
            if (!oldEnchants.isEmpty()) {
                itemToGive.addUnsafeEnchantments(oldEnchants);
            }

            if (type.name().endsWith("_LEGGINGS")) {
                player.getInventory().setLeggings(itemToGive);
                Material bootsType = Material.valueOf(type.name().replace("_LEGGINGS", "_BOOTS"));
                ItemStack bootsItem = new ItemStack(bootsType);
                ItemMeta bootsMeta = bootsItem.getItemMeta();
                bootsMeta.setUnbreakable(true);
                bootsItem.setItemMeta(bootsMeta);
                Map<Enchantment, Integer> oldBootsEnchants = getAndRemoveOldTier(player, bootsType);
                if (!oldBootsEnchants.isEmpty()) {
                    bootsItem.addUnsafeEnchantments(oldBootsEnchants);
                }
                player.getInventory().setBoots(bootsItem);
            } else {
                player.getInventory().addItem(itemToGive);
            }
        }

        // 确保这部分代码能顺利执行，刷新 GUI 状态
        Bukkit.getScheduler().runTask(NightMare.getInstance(), () -> {
            player.updateInventory();
            Component titleComp = player.getOpenInventory().title();
            String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(titleComp);
            for (ShopCategory cat : ShopCategory.values()) {
                if (title.contains(cat.name)) {
                    gameManager.getShopGUI().open(player, cat);
                    break;
                }
            }
        });
    }
    // 辅助方法：手动检查资源（避开原版 containsAtLeast 的 Meta 坑）
    private boolean hasEnough(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    // 辅助方法：手动扣款（确保扣除正确数量）
    private void removeCurrency(Player player, Material material, int amount) {
        int toRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() > toRemove) {
                    item.setAmount(item.getAmount() - toRemove);
                    break;
                } else {
                    toRemove -= item.getAmount();
                    contents[i] = null;
                }
            }
            if (toRemove <= 0) break;
        }
        player.getInventory().setContents(contents);
    }
    private void upgradeHealth(Player player, Material appleType) {
        double targetBonus = (appleType == Material.APPLE) ? 4.0 :
                (appleType == Material.GOLDEN_APPLE ? 8.0 : 12.0);
        // 直接通过属性修饰符提升
        applyHealthModifier(player, targetBonus);
        // 反馈
        int displayLevel = (int)(targetBonus / 4);
        player.sendMessage("§c❤ 你的永久生命等级已提升至 §l" + displayLevel + " §c级！");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }
    private Map<Enchantment, Integer> getAndRemoveOldTier(Player player, Material newMat) {
        Map<Enchantment, Integer> accumulatedEnchants = new HashMap<>();
        Inventory inv = player.getInventory();

        List<Material> targets = new ArrayList<>();
        String name = newMat.name();

        // 判定进化链，确保包含钻石级，否则升到满级时无法继承前一级的附魔
        if (name.contains("AXE") && !name.contains("PICKAXE")) {
            targets.addAll(List.of(Material.WOODEN_AXE, Material.GOLDEN_AXE, Material.IRON_AXE, Material.DIAMOND_AXE));
        } else if (name.contains("SWORD")) {
            targets.addAll(List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD));
        } else if (name.contains("PICKAXE")) {
            targets.addAll(List.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE));
        } else if (name.contains("LEGGINGS")) {
            targets.addAll(List.of(Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS));
        } else if (name.contains("BOOTS")) {
            targets.addAll(List.of(Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS, Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS));
        }

        if (targets.isEmpty()) return accumulatedEnchants;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && targets.contains(item.getType())) {
                // 提取附魔
                item.getEnchantments().forEach((ench, lvl) -> {
                    accumulatedEnchants.merge(ench, lvl, Math::max);
                });
                // 清除该物品（无论是背包里的还是穿在身上的）
                inv.setItem(i, null);
            }
        }
        return accumulatedEnchants;
    }
    private void executeItemLottery(Player player) {
        Random random = new Random();
        int roll = random.nextInt(100); // 0-99 的随机数
        ItemStack reward;
        String rewardName;
        if (roll<5){
            reward = new ItemStack(Material.SLIME_BALL, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6行走平台"));
            reward.setItemMeta(meta);
            rewardName = "行走平台 x1";
        }
        else if (roll<10){
            reward = new ItemStack(Material.FIRE_CHARGE, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6火焰弹"));
            reward.setItemMeta(meta);
            rewardName = "火焰弹 x1";
        }
        else if (roll<15){
            reward = new ItemStack(Material.POTION,1);
            PotionMeta meta = (PotionMeta) reward.getItemMeta();
            meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 0, false, false),true);
            meta.displayName(Component.text("§6生命药水"));
            meta.setColor(PotionEffectType.REGENERATION.getColor());
            reward.setItemMeta( meta);
            rewardName = "生命药水 x1";
        }
        else if(roll<21){
            reward = new ItemStack(Material.STRING, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6陷阱"));
            reward.setItemMeta(meta);
            rewardName = "陷阱 x1";
        }
        else if (roll<28){
            reward = new ItemStack(Material.CHORUS_FRUIT,2);
            rewardName = "紫菘果 x2";
        }
        else if (roll<42){
            reward = new ItemStack(Material.GUNPOWDER, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6回城卷轴"));
            reward.setItemMeta(meta);
            rewardName = "回城卷轴x1";
        }
        else if (roll<54){
            reward = new ItemStack(Material.EGG, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6搭桥蛋"));
            reward.setItemMeta(meta);
            rewardName = "搭桥蛋 x1";
        }
        else if(roll<64){
            reward = new ItemStack(Material.SHULKER_SHELL, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6蹦床"));
            reward.setItemMeta(meta);
            rewardName = "蹦床 x1";
        }
        else if (roll<72){
            reward = new ItemStack(Material.FEATHER, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6降落伞"));
            reward.setItemMeta(meta);
            rewardName = "降落伞 x1";
        }
       else if (roll < 80) { // 40% 概率
            reward = new ItemStack(Material.TNT, 1);
            rewardName = "TNT x1";
        } else if (roll < 85) { // 30% 概率
            reward = new ItemStack(Material.ENDER_PEARL, 1);
            rewardName = "末影珍珠 x1";
        } else if (roll < 90) { // 15% 概率
            reward = new ItemStack(Material.GOLDEN_APPLE, 1);
            rewardName = "金苹果 x1";
        } else if (roll < 97) { // 10% 概率
            reward = new ItemStack(Material.BLAZE_ROD, 1);
            ItemMeta meta = reward.getItemMeta();
            meta.displayName(Component.text("§6自救平台"));
            reward.setItemMeta(meta);
            rewardName = "自救平台";
        } else {
            reward = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
            rewardName = "§e不死图腾";
        }

        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        if (!leftovers.isEmpty()) {
            for (ItemStack leftoverItem : leftovers.values()) {
                player.getWorld().dropItem(player.getLocation(), leftoverItem);
            }
        }
        player.sendMessage("§6[神秘抽奖] §a恭喜你抽中了: §f" + rewardName);
    }
    private void executeBookLottery(Player player) {
        // 1. 定义奖池
        List<Enchantment> pool = List.of(
                Enchantment.SHARPNESS,      // 锋利
                Enchantment.PROTECTION, // 保护
                Enchantment.POWER,
                Enchantment.EFFICIENCY,
                Enchantment.PROJECTILE_PROTECTION
        );

        // 2. 随机抽取附魔和等级
        Random random = new Random();
        Enchantment selectedEnchant = pool.get(random.nextInt(pool.size()));
        // 随机等级 (1 到 该附魔的最大等级，如果是锋利就是 1-5)
        int maxLevel = selectedEnchant.getMaxLevel();
        int randomLevel = random.nextInt(2)+1;
        if (selectedEnchant==Enchantment.POWER){
            randomLevel= (random.nextDouble() > 0.9) ? 2 : 1;
        }
        else if (selectedEnchant==Enchantment.SHARPNESS){
            randomLevel= (random.nextDouble() > 0.7) ? 2 : 1;
        }


        // 3. 生成附魔书
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        // 注意：附魔书必须用 addStoredEnchant，用 addEnchant 会报错
        meta.addStoredEnchant(selectedEnchant, randomLevel, true);
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
        player.sendMessage("§b[抽奖] 你获得了随机附魔书！");
    }
    public void open(Player player, ShopCategory category) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("商店 - " + category.name));

        // 1. 填充分类切换图标 (第 1 行)
        for (ShopCategory cat : ShopCategory.values()) {
            inv.setItem(cat.ordinal(), createIcon(cat));
        }
        // 2. 填充装饰边框
        int[] borders = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int slot : borders) {
            inv.setItem(slot, new ItemStack(BORDER_GLASS));
        }

        // 3. 根据分类加载内容
        switch (category) {
            case WEAPONS -> renderWeapons(inv, player);
            case BLOCKS -> renderBlocks(inv, player);
            case HEALTH -> renderHealth(inv, player);
            case MISC->renderMisc(inv, player);
        }

        player.openInventory(inv);
    }
    private Material getWoolMaterial(String color) {
        if (color == null) return Material.WHITE_WOOL;
        return switch (color.toLowerCase()) {
            case "red" -> Material.RED_WOOL;
            case "blue" -> Material.BLUE_WOOL;
            case "yellow" -> Material.YELLOW_WOOL;
            case "green" -> Material.LIME_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }
    private ItemStack createLotteryIcon() {
        ItemStack sponge = new ItemStack(Material.SPONGE);
        ItemMeta meta = sponge.getItemMeta();
        meta.displayName(Component.text("§6§l神秘道具抽奖", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7价格: §f32 铁锭").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("§e[点击抽取]").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§8可能的奖励:").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7- TNT, 末影珍珠, 不死图腾").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7- 各种位移平台与降落伞").decoration(TextDecoration.ITALIC, false));
        // 【修复】加上价格数据，使得它能被 handlePurchaseLogic 识别
        meta.getPersistentDataContainer().set(PRICE_KEY, PersistentDataType.INTEGER, 32);
        meta.getPersistentDataContainer().set(CURRENCY_KEY, PersistentDataType.STRING, Material.IRON_INGOT.name());

        meta.lore(lore);
        sponge.setItemMeta(meta);
        return sponge;
    }
    private void renderMisc(Inventory inv, Player player) {
        inv.setItem(getSlot(3, 2), createShopItem(Material.COMPASS, "指南针", 1, 1, Material.GOLD_INGOT));
        inv.setItem(getSlot(3, 5), createLotteryIcon());

        // 【修改】瞬间治疗药水 (瞬间治疗 I)
        inv.setItem(getSlot(4, 2), createPotionItem("瞬间治疗药水", PotionType.HEALING, 2, 12, Material.IRON_INGOT, false));
        // 速度药水 (速度 III)
        inv.setItem(getSlot(4, 3), createCustomPotion("速度药水 (速度 III)", org.bukkit.potion.PotionEffectType.SPEED, 600, 4, 1, Material.GOLD_INGOT));
        // 跳跃药水 (跳跃 V)
        inv.setItem(getSlot(4, 4), createCustomPotion("跳跃药水 (跳跃 V)", org.bukkit.potion.PotionEffectType.JUMP_BOOST, 600, 4, 1, Material.GOLD_INGOT));
        inv.setItem(getSlot(5, 2), createShopItem(Material.ENCHANTED_BOOK, "随机附魔书", 1, 200, Material.IRON_INGOT));
    }
    // 3. 辅助方法：创建真正有效果的药水
    private ItemStack createCustomPotion(String name, org.bukkit.potion.PotionEffectType type, int durationTicks, int amplifier, int price, Material currency) {
        ItemStack potion = createShopItem(Material.POTION, name, 1, price, currency);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new org.bukkit.potion.PotionEffect(type, durationTicks, amplifier), true);
        // 设置药水颜色
        meta.setColor(type.getColor());
        potion.setItemMeta(meta);
        return potion;
    }

    private ItemStack createPotionItem(String name, org.bukkit.potion.PotionType type, int level, int price, Material currency, boolean extended) {
        ItemStack potion = createShopItem(Material.POTION, name, 1, price, currency);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
        meta.setBasePotionType(type);
        potion.setItemMeta(meta);
        return potion;
    }
    private ItemStack createIcon(ShopCategory cat) {
        ItemStack item = new ItemStack(cat.icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§e" + cat.name));
        item.setItemMeta(meta);
        return item;
    }
    public ItemStack createShopItem(Material type, String name, int amount, int price, Material currency) {
        ItemStack item = new ItemStack(type, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§e" + name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        String currencyName = (currency == Material.IRON_INGOT) ? "§f铁锭" : "§6金锭";
        lore.add(Component.text("§7价格: " + (currency == Material.IRON_INGOT ? "§f" : "§6") + price + " " + currencyName).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("§e右键点击购买").decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(PRICE_KEY, PersistentDataType.INTEGER, price);
        meta.getPersistentDataContainer().set(CURRENCY_KEY, PersistentDataType.STRING, currency.name());
        meta.lore(lore);
        // 【新增逻辑】只要是有耐久度的物品（排除鞘翅），全部设置为无法破坏
        if (type.getMaxDurability() > 0 && type != Material.ELYTRA) {
            meta.setUnbreakable(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void renderWeapons(Inventory inv, Player player) {
        // 斧头
        if (hasItem(player, Material.IRON_AXE)) {
            inv.setItem(21, createMaxLevelItem(Material.IRON_AXE, "铁斧"));
        } else if (hasItem(player, Material.GOLDEN_AXE)) {
            inv.setItem(21, createShopItem(Material.IRON_AXE, "铁斧", 1, 6, Material.GOLD_INGOT));
        } else if (hasItem(player, Material.WOODEN_AXE)) {
            inv.setItem(21, createShopItem(Material.GOLDEN_AXE, "金斧", 1, 60, Material.IRON_INGOT));
        } else {
            inv.setItem(21, createShopItem(Material.WOODEN_AXE, "木斧", 1, 30, Material.IRON_INGOT));
        }
        // 剑
        if (hasItem(player, Material.DIAMOND_SWORD)) {
            inv.setItem(22, createMaxLevelItem(Material.DIAMOND_SWORD, "钻石剑"));
        } else if (hasItem(player, Material.IRON_SWORD)) {
            inv.setItem(22, createShopItem(Material.DIAMOND_SWORD, "钻石剑", 1, 4, Material.GOLD_INGOT));
        } else if (hasItem(player, Material.STONE_SWORD)) {
            inv.setItem(22, createShopItem(Material.IRON_SWORD, "铁剑", 1, 40, Material.IRON_INGOT));
        } else {
            inv.setItem(22, createShopItem(Material.STONE_SWORD, "石剑", 1, 20, Material.IRON_INGOT));
        }
        if (hasItem(player, Material.DIAMOND_PICKAXE)) {
            ItemStack pickaxe = createMaxLevelItem(Material.DIAMOND_PICKAXE, "钻石镐");
            pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
            inv.setItem(23, pickaxe);
        } else if (hasItem(player, Material.IRON_PICKAXE)) {
            ItemStack pickaxe = createShopItem(Material.DIAMOND_PICKAXE, "钻石镐", 1, 72, Material.IRON_INGOT);
            pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
            inv.setItem(23,pickaxe );
        } else if (hasItem(player, Material.STONE_PICKAXE)) {
            ItemStack pickaxe = createShopItem(Material.IRON_PICKAXE, "铁镐", 1, 24, Material.IRON_INGOT);
            pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 2);
            inv.setItem(23,pickaxe );
        } else {
            ItemStack pickaxe = createShopItem(Material.STONE_PICKAXE, "石镐", 1, 12, Material.IRON_INGOT);
            pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
            inv.setItem(23,pickaxe);
        }

        if (hasItem(player, Material.SHEARS)) {
            inv.setItem(24, createMaxLevelItem(Material.SHEARS, "剪刀"));
        } else {
            ItemStack shears = createShopItem(Material.SHEARS, "剪刀 (效率 Ⅴ)", 1, 40, Material.IRON_INGOT);
            shears.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
            inv.setItem(24, shears);
        }
        if (hasItem(player, Material.DIAMOND_LEGGINGS)) {
            inv.setItem(25, createMaxLevelItem(Material.DIAMOND_LEGGINGS, "钻石下装 "));
        } else if (hasItem(player, Material.IRON_LEGGINGS)) {
            inv.setItem(25, createShopItem(Material.DIAMOND_LEGGINGS, "钻石下装", 1, 12, Material.GOLD_INGOT));
        } else if (hasItem(player, Material.CHAINMAIL_LEGGINGS)) {
            inv.setItem(25, createShopItem(Material.IRON_LEGGINGS, "铁质下装 ", 1, 60, Material.IRON_INGOT));
        } else {
            // 基础默认套装
            inv.setItem(25, createShopItem(Material.CHAINMAIL_LEGGINGS, "锁链下装", 1, 30, Material.IRON_INGOT));
        }
        // 鞘翅保持原样
        inv.setItem(26, createShopItem(Material.FISHING_ROD, "噩梦空间冠军钓竿", 1, 40, Material.IRON_INGOT));
        inv.setItem(32, createShopItem(Material.BOW, "噩梦长弓", 1, 7, Material.GOLD_INGOT));
        inv.setItem(33, createShopItem(Material.ARROW, "箭矢", 8, 16, Material.IRON_INGOT));
        inv.setItem(34, createShopItem(Material.ELYTRA, "限时鞘翅", 1, 16, Material.GOLD_INGOT));
    }
    private ItemStack createMaxLevelItem(Material type, String name) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§a" + name + " (已达最高等级)", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("§7你已经解锁了最高级别的装备！").decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }
    private boolean hasItem(Player player, Material material) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private int getSlot(int row, int col) {
        return (row - 1) * 9 + (col - 1);
    }
    private void renderBlocks(Inventory inv, Player player) {
        GameTeam team = gameManager.getPlayerSession(player).getGame().getTeam(player);
        Material wool = getWoolMaterial(team.getTeamName());
        inv.setItem(getSlot(3, 2), createShopItem(wool, "羊毛", 8, 4, Material.IRON_INGOT));
        inv.setItem(getSlot(3, 3), createShopItem(Material.SANDSTONE, "沙石", 8, 10, Material.IRON_INGOT));
        inv.setItem(getSlot(3, 4), createShopItem(Material.DIORITE, "闪长岩", 8, 20, Material.IRON_INGOT));
        inv.setItem(getSlot(3, 5), createShopItem(Material.LAPIS_BLOCK, "青金石块", 4, 20, Material.IRON_INGOT));
        // 【新增】岩浆块：2 个 / 16 铁锭
        inv.setItem(getSlot(3, 6), createShopItem(Material.MAGMA_BLOCK, "岩浆块", 2, 16, Material.IRON_INGOT));
        inv.setItem(getSlot(4, 2), createShopItem(Material.OAK_PLANKS, "木板", 4, 20, Material.IRON_INGOT));
        inv.setItem(getSlot(4, 3), createShopItem(Material.GLASS, "防爆玻璃", 8, 24, Material.IRON_INGOT));

        // 【新增】梯子：8 个 / 16 铁锭
        inv.setItem(getSlot(4, 4), createShopItem(Material.LADDER, "梯子", 8, 16, Material.IRON_INGOT));

        inv.setItem(getSlot(4, 5), createShopItem(Material.OBSIDIAN, "黑曜石", 1, 3, Material.GOLD_INGOT));
    }


    private double getCurrentHealthBonus(Player player) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return 0;
        // 寻找我们的专属修饰符
        return attr.getModifiers().stream()
                .filter(m -> m.getKey().equals(HEALTH_KEY))
                .mapToDouble(org.bukkit.attribute.AttributeModifier::getAmount)
                .findFirst()
                .orElse(0.0);
    }
    private void applyHealthModifier(Player player, double amount) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;
        // 【关键修复：安全移除旧修饰符】
        // 收集需要移除的修饰符，避免 ConcurrentModificationException
        List<org.bukkit.attribute.AttributeModifier> toRemove = new ArrayList<>();
        for (org.bukkit.attribute.AttributeModifier m : attr.getModifiers()) {
            if (m.getKey().equals(HEALTH_KEY)) {
                toRemove.add(m);
            }
        }
        // 统一执行移除
        toRemove.forEach(attr::removeModifier);
        // 添加新的修饰符 (ADD_NUMBER 模式：直接加血量上限)
        org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                HEALTH_KEY, amount, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER
        );
        attr.addModifier(modifier);
    }
    private void renderHealth(Inventory inv, Player player) {
        double currentBonus = getCurrentHealthBonus(player);
        // 根据加成数值判定等级 (4=I, 8=II, 12=III)
        if (currentBonus >= 12.0) {
            // 已达 III 级
            inv.setItem(19, createMaxLevelItem(Material.ENCHANTED_GOLDEN_APPLE, "§d生命提升 III (MAX)"));
        } else if (currentBonus >= 8.0) {
            // 当前 II 级，显示提升至 III 级 (从 8 提升到 12)
            inv.setItem(19, createShopItem(Material.ENCHANTED_GOLDEN_APPLE, "§d生命提升 III (+12血量)", 1, 8, Material.GOLD_INGOT));
        } else if (currentBonus >= 4.0) {
            // 当前 I 级，显示提升至 II 级 (从 4 提升到 8)
            inv.setItem(19, createShopItem(Material.GOLDEN_APPLE, "§e生命提升 II (+8血量)", 1, 6, Material.GOLD_INGOT));
        } else {
            // 初始状态，显示提升至 I 级 (+4)
            inv.setItem(19, createShopItem(Material.APPLE, "§a生命提升 I (+4血量)", 1, 4, Material.GOLD_INGOT));
        }
    }
}