/*
 * OITC - Kill your opponents and reach 25 points to win!
 * Copyright (C) 2024 Despical
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package me.despical.oitc.arena;

import me.despical.commons.miscellaneous.PlayerUtils;
import me.despical.commons.serializer.InventorySerializer;
import me.despical.oitc.ConfigPreferences;
import me.despical.oitc.Main;
import me.despical.oitc.api.StatsStorage;
import me.despical.oitc.api.events.game.OITCGameEndEvent;
import me.despical.oitc.api.events.game.OITCGameStartEvent;
import me.despical.oitc.api.events.game.OITCGameStateChangeEvent;
import me.despical.oitc.arena.managers.GameBarManager;
import me.despical.oitc.arena.managers.ScoreboardManager;
import me.despical.oitc.arena.options.ArenaOption;
import me.despical.oitc.handlers.ChatManager;
import me.despical.oitc.handlers.rewards.Reward;
import me.despical.oitc.user.User;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * @author Despical
 * <p>
 * Created at 02.07.2020
 */
public class Arena extends BukkitRunnable {

	private final static Main plugin = JavaPlugin.getPlugin(Main.class);
	private final static ChatManager chatManager = plugin.getChatManager();

	private final String id;
	private final GameBarManager gameBarManager;
	private final ScoreboardManager scoreboardManager;

	private final Set<Player> players;
	private final Map<ArenaOption, Integer> arenaOptions;
	private final Map<GameLocation, Location> gameLocations;

	private boolean forceStart;
	private boolean ready;
	private boolean taskStarted;
	private String mapName = "";
	private ArenaState arenaState = ArenaState.INACTIVE;
	private List<Location> playerSpawnPoints;

	public Arena(String id) {
		this.id = id;
		this.players = new HashSet<>();
		this.playerSpawnPoints = new ArrayList<>();
		this.arenaOptions = new EnumMap<>(ArenaOption.class);
		this.gameLocations = new EnumMap<>(GameLocation.class);
		this.scoreboardManager = new ScoreboardManager(plugin, this);
		this.gameBarManager = new GameBarManager(this, plugin);

		for (ArenaOption option : ArenaOption.values()) {
			arenaOptions.put(option, option.value());
		}
	}

	public String getId() {
		return id;
	}

	public boolean isReady() {
		return ready;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public void setForceStart(boolean forceStart) {
		this.forceStart = forceStart;
	}

	public boolean isForceStart() {
		return forceStart;
	}

	public ScoreboardManager getScoreboardManager() {
		return scoreboardManager;
	}

	public GameBarManager getGameBar() {
		return this.gameBarManager;
	}

	public int getMinimumPlayers() {
		return getOption(ArenaOption.MINIMUM_PLAYERS);
	}

	public void setMinimumPlayers(int minimumPlayers) {
		setOptionValue(ArenaOption.MINIMUM_PLAYERS, Math.max(2, minimumPlayers));
	}

	public String getMapName() {
		return mapName;
	}

	public void setMapName(String mapName) {
		this.mapName = mapName;
	}

	public int getTimer() {
		return getOption(ArenaOption.TIMER);
	}

	public void setTimer(int timer) {
		setOptionValue(ArenaOption.TIMER, timer);
	}

	public int getMaximumPlayers() {
		return getOption(ArenaOption.MAXIMUM_PLAYERS);
	}

	public void setMaximumPlayers(int maximumPlayers) {
		setOptionValue(ArenaOption.MAXIMUM_PLAYERS, maximumPlayers);
	}

	public Location getLobbyLocation() {
		return gameLocations.get(GameLocation.LOBBY);
	}

	public void setLobbyLocation(Location loc) {
		gameLocations.put(GameLocation.LOBBY, loc);
	}

	public Location getEndLocation() {
		return gameLocations.get(GameLocation.END);
	}

	public void setEndLocation(Location endLoc) {
		gameLocations.put(GameLocation.END, endLoc);
	}

	public int getGameplayTime() {
		return getOption(ArenaOption.GAMEPLAY_TIME);
	}

	public ArenaState getArenaState() {
		return arenaState;
	}

	public void setArenaState(ArenaState arenaState) {
		this.arenaState = arenaState;
		this.gameBarManager.handleGameBar();
		plugin.getServer().getPluginManager().callEvent(new OITCGameStateChangeEvent(this, arenaState));
		this.updateSigns();
	}

	public boolean isArenaState(ArenaState first, ArenaState... others) {
		if (arenaState == first) return true;

		for (ArenaState state : others) {
			if (arenaState == state) return true;
		}

		return false;
	}

	private int getOption(ArenaOption option) {
		return arenaOptions.get(option);
	}

	private void setOptionValue(ArenaOption option, int value) {
		arenaOptions.put(option, value);
	}

	public void teleportToLobby(Player player) {
		player.setFoodLevel(20);
		player.setFlying(false);
		player.setAllowFlight(false);
		player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
		player.setFlySpeed(.1F);
		player.setWalkSpeed(.2F);

		Location location = getLobbyLocation();

		if (location == null) {
			plugin.getLogger().warning("Lobby location isn't initialized for arena " + id);
			return;
		}

		player.teleport(location);
	}

	public Location getRandomSpawnPoint() {
		return playerSpawnPoints.get(ThreadLocalRandom.current().nextInt(playerSpawnPoints.size()));
	}

	public void teleportToStartLocation(Player player) {
		player.teleport(getRandomSpawnPoint());
	}

	public void teleportAllToStartLocation() {
		int i = 0, size = this.playerSpawnPoints.size();

		for (final Player player : this.getPlayers()) {
			if (i + 1 > size) {
				plugin.getLogger().warning("There aren't enough spawn points to teleport players!");
				plugin.getLogger().warning("We are teleporting player to a random location for now!");

				player.teleport(this.playerSpawnPoints.get(ThreadLocalRandom.current().nextInt(size)));
				break;
			}

			player.teleport(this.playerSpawnPoints.get(i++));
		}
	}

	public void teleportAllToEndLocation() {
		for (Player player : players) {
			teleportToEndLocation(player);
		}
	}

	public void broadcastMessage(String message) {
		for (Player player : players) {
			player.sendMessage(message);
		}
	}

	public void updateSigns() {
		Optional.ofNullable(plugin.getSignManager()).ifPresent(signManager -> signManager.updateSign(this));
	}

	public void teleportToEndLocation(Player player) {
		if (plugin.getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
			plugin.getBungeeManager().connectToHub(player);
			return;
		}

		Location location = getEndLocation();

		if (location == null) {
			location = getLobbyLocation();
			plugin.getLogger().warning(String.format("Couldn't teleport %s to end location for arena %s because it isn't initialized!", player.getName(), id));
		}

		if (location != null) {
			player.teleport(location);
		}
	}

	public List<Location> getPlayerSpawnPoints() {
		return playerSpawnPoints;
	}

	public void setPlayerSpawnPoints(List<Location> playerSpawnPoints) {
		this.playerSpawnPoints = playerSpawnPoints;
	}

	public void start() {
		if (taskStarted) return;

		this.taskStarted = true;
		this.runTaskTimer(plugin, 20L, 20L);
		this.setArenaState(ArenaState.RESTARTING);
	}

	public void stop() {
		if (taskStarted) {
			cancel();
		}
	}

	public void addPlayer(Player player) {
		players.add(player);
	}

	public void removePlayer(Player player) {
		players.remove(player);
	}

	public Set<Player> getPlayersLeft() {
		return plugin.getUserManager().getUsers(this).stream().filter(user -> !user.isSpectator()).map(User::getPlayer).collect(Collectors.toSet());
	}

	public List<Player> getPlayers() {
		return this.players.stream().filter(player -> player != null && player.isOnline()).collect(Collectors.toList());
	}

	public void showPlayers() {
		if (!ArenaUtils.shouldHide()) return;

		List<Player> onlinePlayers = this.getPlayers();

		for (Player player : onlinePlayers) {
			for (Player p : onlinePlayers) {
				if (player.equals(p)) continue;

				PlayerUtils.showPlayer(player, p, plugin);
				PlayerUtils.showPlayer(p, player, plugin);
			}
		}
	}

	@Override
	public void run() {
		if (players.isEmpty() && arenaState == ArenaState.WAITING_FOR_PLAYERS) {
			return;
		} else if (arenaState != ArenaState.RESTARTING) {
			gameBarManager.handleGameBar();
		}

		final int minPlayers = getMinimumPlayers(), waitingTime = getOption(ArenaOption.LOBBY_WAITING_TIME), startingTime = getOption(ArenaOption.LOBBY_STARTING_TIME);

		switch (arenaState) {
			case WAITING_FOR_PLAYERS:
				if (players.size() < minPlayers) {
					if (getTimer() <= 0) {
						setTimer(45);
						broadcastMessage(chatManager.formatMessage(this, "in_game.messages.lobby_messages.waiting_for_players"));
					}
				} else {
					showPlayers();
					setTimer(waitingTime);
					setArenaState(ArenaState.STARTING);
					broadcastMessage(chatManager.message("in_game.messages.lobby_messages.enough_players_to_start"));
					break;
				}

				setTimer(getTimer() - 1);
				break;
			case STARTING:
				if (plugin.getOption(ConfigPreferences.Option.LEVEL_COUNTDOWN_ENABLED)) {
					for (Player player : this.getPlayers()) {
						player.setLevel(getTimer());
					}
				}

				if (players.size() < minPlayers) {
					setTimer(waitingTime);
					setArenaState(ArenaState.WAITING_FOR_PLAYERS);
					broadcastMessage(chatManager.prefixedFormattedMessage(this, "in_game.messages.lobby_messages.waiting_for_players", minPlayers));

					for (Player player : this.getPlayers()) {
						player.setExp(1F);
						player.setLevel(0);
					}

					break;
				}

				if (players.size() >= getMaximumPlayers() && getTimer() >= startingTime && !forceStart) {
					setTimer(startingTime);

					if (getTimer() == 15 || getTimer() == 10 || getTimer() <= 5) {
						broadcastMessage(chatManager.prefixedMessage("in_game.messages.lobby_messages.start_in", getTimer()));
					}
				}

				if (getTimer() == 0 || forceStart) {
					setArenaState(ArenaState.IN_GAME);

					plugin.getServer().getPluginManager().callEvent(new OITCGameStartEvent(this));

					setTimer(getGameplayTime());
					teleportAllToStartLocation();

					for (Player player : this.getPlayers()) {
						ArenaUtils.updateNameTagsVisibility(player);
						ArenaUtils.hidePlayersOutsideTheGame(player, this);

						plugin.getUserManager().getUser(player).addStat(StatsStorage.StatisticType.GAMES_PLAYED, 1);

						player.setGameMode(GameMode.ADVENTURE);
						player.sendMessage(chatManager.prefixedMessage("in_game.messages.lobby_messages.game_started"));

						plugin.getGameItemManager().giveKit(player, this);
					}

					if (forceStart) {
						forceStart = false;
					}
				}

				setTimer(getTimer() - 1);
				break;
			case IN_GAME:
				int playerSize = getPlayersLeft().size();

				if (playerSize < 2 || getTimer() <= 0) {
					ArenaManager.stopGame(false, this);
					return;
				}

				if (getTimer() == 30 || getTimer() == 60) {
					String title = chatManager.message("in_game.messages.seconds_left_title").replace("%time%", Integer.toString(getTimer()));
					String subtitle = chatManager.message("in_game.messages.seconds_left_subtitle").replace("%time%", Integer.toString(getTimer()));

					plugin.getUserManager().getUsers(this).forEach(user -> user.sendTitle(title, subtitle));
				}

				setTimer(getTimer() - 1);
				break;
			case ENDING:
				if (getTimer() != 0) {
					setTimer(getTimer() - 1);
					return;
				}

				scoreboardManager.stopAllScoreboards();
				gameBarManager.removeAll();

				for (Player player : this.getPlayers()) {
					ArenaUtils.showPlayersOutsideTheGame(player, this);

					for (final User users : plugin.getUserManager().getUsers()) {
						final Player usersPlayer = users.getPlayer();

						if (usersPlayer == null) continue;

						PlayerUtils.showPlayer(player, usersPlayer, plugin);

						if (!plugin.getArenaRegistry().isInArena(usersPlayer) || players.contains(usersPlayer)) {
							PlayerUtils.showPlayer(usersPlayer, player, plugin);
						}
					}

					player.setGameMode(GameMode.SURVIVAL);
					player.setFlySpeed(.1f);
					player.setWalkSpeed(.2f);
					player.setFlying(false);
					player.setAllowFlight(false);
					player.getInventory().clear();
					player.getInventory().setArmorContents(null);
					player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

					teleportToEndLocation(player);

					User user = plugin.getUserManager().getUser(player);
					user.resetAttackCooldown();
					user.performReward(Reward.RewardType.END_GAME);
					user.removeScoreboard();

					gameBarManager.doBarAction(user, 0);
				}

				if (plugin.getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
					this.getPlayers().forEach(player -> InventorySerializer.loadInventory(plugin, player));
				}

				if (plugin.getOption(ConfigPreferences.Option.BUNGEE_ENABLED) && plugin.getBungeeManager().isShutdownWhenGameEnds()) {
					plugin.getServer().shutdown();
				}

				setArenaState(ArenaState.RESTARTING);
				break;
			case RESTARTING:
				OITCGameEndEvent endEvent = new OITCGameEndEvent(this, new HashSet<>(players));
				plugin.getUserManager().getUsers(this).forEach(user -> user.setSpectator(false));
				players.clear();

				plugin.getServer().getPluginManager().callEvent(endEvent);

				if (plugin.getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
					final ArenaRegistry arenaRegistry = plugin.getArenaRegistry();

					arenaRegistry.shuffleBungeeArena();

					for (final Player player : plugin.getServer().getOnlinePlayers()) {
						ArenaManager.joinAttempt(player, arenaRegistry.getBungeeArena());
					}
				}

				setArenaState(ArenaState.WAITING_FOR_PLAYERS);
				break;
		}
	}

	public enum GameLocation {
		LOBBY, END
	}
}