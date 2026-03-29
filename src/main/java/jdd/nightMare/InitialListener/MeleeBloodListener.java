package jdd.nightMare.InitialListener;
import jdd.nightMare.NightMare;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;

public class MeleeBloodListener implements Listener {
    private static final int PARTICLE_COUNT = 4;
    private static final double SPREAD = 0.15;
    private static final BlockData BLOOD_DATA = Bukkit.createBlockData(Material.REDSTONE_BLOCK);
    private final NightMare nightMare;
    public MeleeBloodListener(NightMare nightMare) {
        this.nightMare = nightMare;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        org.bukkit.Location bloodLocation = victim.getEyeLocation().subtract(0, 0.3, 0);

        victim.getWorld().spawnParticle(
                Particle.BLOCK,
                bloodLocation,
                PARTICLE_COUNT,
                SPREAD, SPREAD, SPREAD,
                0.1,
                BLOOD_DATA
        );
    }
}