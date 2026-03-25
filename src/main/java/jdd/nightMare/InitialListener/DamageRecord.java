package jdd.nightMare.InitialListener;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import java.util.UUID;

// 使用 Java Record 创建一个不可变的数据载体
public record DamageRecord(UUID attackerId, long timestamp, DamageCause cause) {
    // 设定判定有效时间为 15 秒（15000毫秒）
    public boolean isValid() {
        return (System.currentTimeMillis() - timestamp) <= 15000L;
    }
}