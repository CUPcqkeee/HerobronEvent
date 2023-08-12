package me.cupcqkeee.herobrine;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Herobrine extends JavaPlugin implements Listener {
    private static Herobrine instance;
    private static boolean eventActive = false;
    private static List<NPC> activeNPCs = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    public static Herobrine getInstance() {
        return instance;
    }

    public static boolean isEventActive() {
        return eventActive;
    }

    public static void startEvent() {
        eventActive = true;
    }

    public static void endEvent() {
        eventActive = false;
    }

    public static void addActiveNPC(NPC npc) {
        activeNPCs.add(npc);
    }

    public static void removeActiveNPC(NPC npc) {
        activeNPCs.remove(npc);
    }

    public static boolean hasLivingNPCs() {
        return !activeNPCs.isEmpty();
    }

    private void removeNPCWithParticles(NPC npc) {
        Location npcLocation = npc.getEntity().getLocation();
        npcLocation.getWorld().spawnParticle(Particle.SMOKE_NORMAL, npcLocation, 30);
        npc.despawn();
        Bukkit.getLogger().info("NPC removed with particles.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        activeNPCs.removeIf(npc -> {
            if (npc.isSpawned()) {
                Location npcLocation = npc.getEntity().getLocation();
                double distance = npcLocation.distance(playerLocation);

                if (distance <= 4) {
                    removeNPCWithParticles(npc);
                    return true;
                }
            }
            return false;
        });

        if (!isEventActive() && !hasLivingNPCs() && Math.random() < getSpawnChance()) {
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "");
            registerTraits(npc);

            Location npcLocation = playerLocation.clone().add(
                    random.nextDouble() * getSpawnDistance() * 2 - getSpawnDistance(),
                    0,
                    random.nextDouble() * getSpawnDistance() * 2 - getSpawnDistance()
            );
            npc.spawn(npcLocation);

            npc.getTrait(SkinTrait.class).setSkinName(getNpcSkin());

            Location teleportLocation = playerLocation.clone().add(0, 1, 0)
                    .add(player.getLocation().getDirection().multiply(-getSpawnDistance()));
            npc.getEntity().teleport(teleportLocation);

            npc.faceLocation(player.getLocation().add(0, 0.3, 0));
            npc.getTrait(LookClose.class).lookClose(true);

            activeNPCs.add(npc);

            Sound stareSound = Sound.valueOf(getPluginConfig().getString("npcStareSound"));
            player.playSound(playerLocation, stareSound, SoundCategory.MASTER, 1, 1);

            List<String> actionBarMessages = getPluginConfig().getStringList("actionBarMessages");
            String randomMessage = actionBarMessages.get(random.nextInt(actionBarMessages.size()));
            player.sendActionBar(randomMessage);

            startEvent();
        }
    }


    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        if (clickedEntity instanceof LivingEntity && activeNPCs.contains(clickedEntity)) {
            Location npcLocation = clickedEntity.getLocation();
            Location playerLocation = player.getLocation();

            double distance = npcLocation.distance(playerLocation);

            if (distance <= 4) {
                removeNPCWithParticles((NPC) clickedEntity);
                activeNPCs.remove(clickedEntity);
            }
        }
    }

    private void registerTraits(NPC npc) {
        if (!npc.hasTrait(SkinTrait.class)) {
            npc.addTrait(new SkinTrait());
        }
    }

    private org.bukkit.configuration.file.FileConfiguration getPluginConfig() {
        return getConfig();
    }

    private double getSpawnChance() {
        return getPluginConfig().getDouble("spawnChance");
    }

    private double getSpawnDistance() {
        return getPluginConfig().getDouble("spawnDistance");
    }

    private String getNpcSkin() {
        return getPluginConfig().getString("npcSkin");
    }

    private int getEventDuration() {
        return getPluginConfig().getInt("eventDuration");
    }

    public static class SkinTrait extends Trait {
        private String skinName;

        public SkinTrait() {
            super("skintrait");
        }

        public void setSkinName(String skinName) {
            this.skinName = skinName;
        }

        @Override
        public void onSpawn() {
            if (skinName != null && !skinName.isEmpty()) {
                net.citizensnpcs.api.npc.NPC npc = getNPC();
                npc.data().setPersistent("player-skin-name", skinName);
                npc.data().setPersistent("player-skin-use-latest", false);
            }
        }
    }
}
