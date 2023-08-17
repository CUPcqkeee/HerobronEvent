package me.cupcqkeee.heroevent;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.event.NPCDamageEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HeroEvent extends JavaPlugin implements Listener {

    private BukkitTask eventTask;
    private FileConfiguration config;
    private List<String> actionBarMessages;
    private Map<UUID, NPC> activeNpcs = new HashMap<>();
    private boolean eventInProgress = false;
    private Particle particleType;
    private Sound ambientSound = Sound.AMBIENT_CAVE;
    private NPC currentEventNPC;


    @Override
    public void onEnable() {
//        actionBarMessages = config.getStringList("actionBarMessages");
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();

        Objects.requireNonNull(getCommand("start")).setExecutor(this);
        Objects.requireNonNull(getCommand("reloadconfig")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        actionBarMessages = config.getStringList("actionBarMessages");

    }

    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        // Отключаем задачу при отключении плагина
        if (eventTask != null) {
            eventTask.cancel();
        }
    }

    private void startHerobrineEvent() {
        if (!eventInProgress) {
            eventInProgress = true;
            int chanceSpawn = config.getInt("chanceSpawn", 0);
            int randomValue = new Random().nextInt(100) + 1;

            if (randomValue <= chanceSpawn) {

                Player targetPlayer = getRandomOnlinePlayer();
                if (targetPlayer != null) {
                    Location targetLocation = targetPlayer.getLocation();

                    // Получаем значения из конфига
                    int minSpawnDistance = config.getInt("minSpawnDistance");
                    int maxSpawnDistance = config.getInt("maxSpawnDistance");
                    int durationEvent = config.getInt("durationEvent");

                    // Генерируем случайное расстояние для спавна
                    int spawnDistance = getRandomInRange(minSpawnDistance, maxSpawnDistance);

                    // Генерируем случайный угол для направления спавна
                    double angle = Math.toRadians(new Random().nextInt(360));

                    // Рассчитываем координаты для спавна НПС
                    double spawnX = targetLocation.getX() + spawnDistance * Math.cos(angle);
                    double spawnZ = targetLocation.getZ() + spawnDistance * Math.sin(angle);

                    Location npcSpawnLocation = new Location(targetLocation.getWorld(), spawnX, targetLocation.getY(), spawnZ);

                    // Создание NPC с использованием Citizens API
                    NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "");
                    npc.setName("");

                    SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
                    if (skinTrait == null) {
                        npc.addTrait(SkinTrait.class);
                    }


                    String Skin = getConfig().getString("skin");
                    assert skinTrait != null;
                    skinTrait.setSkinName(Skin);

                    npc.spawn(npcSpawnLocation);
                    activeNpcs.put(targetPlayer.getUniqueId(), npc);

                    npc.getOrAddTrait(LookClose.class).lookClose(true);


                    targetPlayer.playSound(targetLocation, ambientSound, 1.0f, 1.0f);

                    String randomMessage = getRandomActionBarMessage();
                    sendActionBarMessage(targetPlayer, randomMessage);

                    new BukkitRunnable() {
                        @Override
                        public void run() {

                            // Создаем эффект частиц порошка с замедлением
                            for (int i = 0; i < 100; i++) {
                                double offsetX = 0.2 * (Math.random() - 0.5);
                                double offsetY = 0.7 * (Math.random() - 0.5);
                                double offsetZ = 0.2 * (Math.random() - 0.5);

//                                targetLocation.getWorld().spawnParticle(particleType, targetLocation, 1, offsetX, offsetY, offsetZ, 0.05);
                                try {
                                    Particle particleType = Particle.valueOf(config.getString("particle-type"));
                                    targetLocation.getWorld().spawnParticle(particleType, targetLocation, 1, offsetX, offsetY, offsetZ, 0.05);
                                } catch (IllegalArgumentException e) {
                                    return;
                                }

                            }

                            npc.despawn();
                            npc.destroy();
                            eventInProgress = false;
                        }
                    }.runTaskLater(this, 20 * durationEvent); // 1 минута в тиках
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (eventInProgress && activeNpcs.containsKey(playerUUID)) {
            NPC npc = activeNpcs.get(playerUUID);
            Location npcLocation = npc.getStoredLocation();
            Location playerLocation = player.getLocation();

            double distanceSquared = playerLocation.distanceSquared(npcLocation); // Квадрат расстояния

            if (distanceSquared < 16.0) { // 4 блока в квадрате
                // Удаляем НПС
                npc.despawn();
                npc.destroy();
                activeNpcs.remove(playerUUID);


                try {
                    Particle particleType = Particle.valueOf(config.getString("particle-type"));
                    playerLocation.getWorld().spawnParticle(particleType, npcLocation, 250, 0.5, 0.5, 0.5, 0.05);
                } catch (IllegalArgumentException e) {
                    return;
                }

                // Воспроизведение звука
                Sound deathSound = Sound.valueOf(getConfig().getString("deathSound"));
                playerLocation.getWorld().playSound(npcLocation, deathSound, 1.0f, 1.0f);
            }
        }
    }


    private class LookCloseTrait extends Trait {
        private final Player targetPlayer;

        public LookCloseTrait(Player targetPlayer) {
            super("lookclosetrait");
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void onAttach() {
            // Добавляем трейт LookClose и устанавливаем цель наблюдения на игрока
            npc.getOrAddTrait(LookClose.class).lookClose(true);
        }
    }

    private int getRandomInRange(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

    private void sendActionBarMessage(Player player, String message) {
        message = ChatColor.translateAlternateColorCodes('&', message);
        player.sendActionBar(message);
    }

    private String getRandomActionBarMessage() {
        Random random = new Random();
        return actionBarMessages.get(random.nextInt(actionBarMessages.size()));
    }

    private Player getRandomOnlinePlayer() {
        Player[] onlinePlayers = Bukkit.getServer().getOnlinePlayers().toArray(new Player[0]);
        if (onlinePlayers.length > 0) {
            return onlinePlayers[getRandomInt(0, onlinePlayers.length)];
        }
        return null;
    }

    private int getRandomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min));
    }

    private long getRandomTime(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("start")) {
            if (sender instanceof Player) {
                startHerobrineEvent();
            } else {
                sender.sendMessage("Команда предназначена только для игроков");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("reloadconfig")) {
            if (sender instanceof Player || sender.isOp()) {
                reloadConfig();
                config = getConfig();
                actionBarMessages = config.getStringList("actionBarMessages");
                sender.sendMessage(getName() + " Успешно перезагружен");
            } else {
                sender.sendMessage("У вас недостаточно прав для использования этой команды");
            }
            return true;
        }
        return false;
    }
}
