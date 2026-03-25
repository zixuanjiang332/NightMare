package jdd.nightMare.Game;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

public class ResourceSpawner {
    public enum Type {
        IRON(1, 1),      // 1秒1个
        SIDE_IRON(1, 2), // 1秒2个
        GOLD(30, 1);     // 30秒1个

        final int interval;
        final int amount;
        Type(int interval, int amount) {
            this.interval = interval;
            this.amount = amount;
        }
    }

    private final Location location;
    private final Type type;
    private final Material material;
    private int timer;
    private TextDisplay display;

    public ResourceSpawner(Location location, Type type) {
        this.location = location;
        this.type = type;
        this.material = (type == Type.GOLD) ? Material.GOLD_INGOT : Material.IRON_INGOT;
        this.timer = type.interval;

        // 如果是金锭，初始化倒计时全息图
        if (type == Type.GOLD) {
            createHologram();
        }
    }

    private void createHologram() {
        // 在资源点上方 2.0 格生成文本展示实体
        display = location.getWorld().spawn(location.clone().add(0, 2, 0), TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setText("§e金锭生成倒计时: §f" + timer + "s");
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // 背景透明
        });
    }

    public void tick(GamePhase phase) {
            if (phase.isNight() && type == Type.SIDE_IRON) {
                return;
            }
            if (phase.isNight()&&type==Type.GOLD)return;
            if (phase == GamePhase.NIGHT_3 && type == Type.SIDE_IRON) {
                return;
            }
        timer--;
        if (type == Type.GOLD && display != null) {
            display.setText("§e金锭生成倒计时: §f" + timer + "s");
        }
        if (timer <= 0) {
            spawnItem();
            timer = type.interval; // 重置计时
        }
    }

    private void spawnItem() {
        location.getWorld().dropItem(location, new ItemStack(material, type.amount));
    }

    public void remove() {
        if (display != null) display.remove();
    }
}