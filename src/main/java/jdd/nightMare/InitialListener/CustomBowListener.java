package jdd.nightMare.InitialListener;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;

public class CustomBowListener implements Listener {
    private final Plugin plugin;
    private static final double SPEED_MULTIPLIER = 1.3;
    private static final double EXTRA_KNOCKBACK = 0.47;

    public CustomBowListener(Plugin plugin) {
        this.plugin = plugin;
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Arrow arrow) {
            if (event.getEntity() instanceof Player) {
                Vector currentVelocity = arrow.getVelocity();
                arrow.setVelocity(currentVelocity.multiply(SPEED_MULTIPLIER));
                double currentBaseDamage = arrow.getDamage();
                arrow.setDamage(currentBaseDamage / SPEED_MULTIPLIER);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && event.getEntity() instanceof LivingEntity target) {
            if (arrow.getShooter() instanceof Player shooter) {
                shooter.playSound(shooter.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.8F);
                target.getWorld().spawnParticle(
                        org.bukkit.Particle.DAMAGE_INDICATOR,
                        target.getLocation().add(0, 1, 0),
                        5, 0.2, 0.3, 0.2, 0.1
                );

                double finalHealth = Math.max(0, target.getHealth() - event.getFinalDamage());
                String healthStr = String.format("%.1f", finalHealth);
                shooter.sendActionBar(
                        net.kyori.adventure.text.Component.text("命中! 目标剩余: ")
                                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                                .append(net.kyori.adventure.text.Component.text(healthStr + "❤")
                                        .color(net.kyori.adventure.text.format.NamedTextColor.RED))
                );
                Vector arrowDirection = arrow.getVelocity().normalize();
                Vector knockbackVector = new Vector(
                        arrowDirection.getX() * EXTRA_KNOCKBACK,
                        0.08,
                        arrowDirection.getZ() * EXTRA_KNOCKBACK
                );

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!target.isDead()) {
                        target.setVelocity(target.getVelocity().add(knockbackVector));
                    }
                }, 1L);
            }
        }
    }

}