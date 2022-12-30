package me.humandavey.minigame.instance;

import com.google.common.collect.TreeMultimap;
import me.humandavey.minigame.game.Game;
import me.humandavey.minigame.game.GameState;
import me.humandavey.minigame.game.GameType;
import me.humandavey.minigame.manager.ConfigManager;
import me.humandavey.minigame.team.Team;
import me.humandavey.minigame.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Arena {

	private final int id;
	private final Location spawn;
	private final GameType gameType;

	private GameState gameState;
	private ArrayList<UUID> players;
	private ArrayList<UUID> spectators;
	private HashMap<UUID, Team> teams;
	private Countdown countdown;
	private Game game;

	public Arena(int id, Location spawn, GameType gameType) {
		this.id = id;
		this.spawn = spawn;
		this.gameType = gameType;

		this.gameState = GameState.WAITING;
		this.players = new ArrayList<>();
		this.spectators = new ArrayList<>();
		this.teams = new HashMap<>();
		this.countdown = new Countdown(this);
		this.game = gameType.getNewInstance(this);
	}

	public void start() {
		game.start();
	}

	public void reset(boolean kickPlayers) {
		if (kickPlayers) {
			setState(GameState.RESETTING);
			for (Player player : getPlayers()) {
				player.teleport(ConfigManager.getLobbySpawn());
				Util.resetPlayer(player);
			}
			for (UUID uuid : spectators) {
				Bukkit.getPlayer(uuid).teleport(ConfigManager.getLobbySpawn());
				Util.resetPlayer(Bukkit.getPlayer(uuid));
			}
			players.clear();
			teams.clear();
			spectators.clear();
		}
		countdown.cancel();
		countdown = new Countdown(this);
		game.unregister();
		game = gameType.getNewInstance(this);
		setState(GameState.WAITING);
	}

	public boolean addPlayer(Player player) {
		if (players.size() < gameType.getMaxPlayers()) {
			players.add(player.getUniqueId());
			player.teleport(spawn);
			Util.resetPlayer(player);
			sendMessage(Util.colorize("&7" + player.getName() + " &ehas joined (&b" + players.size() + "&e/&b" + gameType.getMaxPlayers() + "&e)!"));

			TreeMultimap<Integer, Team> count = TreeMultimap.create();
			for (int i = 0; i < gameType.getNumTeams(); i++) {
				count.put(getTeamCount(Team.values()[i]), Team.values()[i]);
			}

			Team lowest = (Team) count.values().toArray()[0];
			setTeam(player, lowest);

			if (gameState == GameState.WAITING && numTeamsWithMoreThan(0) >= gameType.getMinTeams()) {
				countdown.start();
			}
			return true;
		}
		return false;
	}

	public void removePlayer(Player player) {
		players.remove(player.getUniqueId());
		spectators.remove(player.getUniqueId());
		player.teleport(ConfigManager.getLobbySpawn());
		sendMessage(Util.colorize("&7" + player.getName() + " &ehas quit!"));

		removeTeam(player);
		Util.resetPlayer(player);

		if (gameState == GameState.COUNTDOWN && numTeamsWithMoreThan(0) < gameType.getMinTeams()) {
			sendMessage(Util.colorize("&cNot enough teams to continue, countdown stopped!"));
			reset(false);
			return;
		}
		if (gameState == GameState.LIVE && numTeamsWithMoreThan(0) < gameType.getMinTeams()) {
			game.end();
		}
	}

	public ArrayList<Player> getAlivePlayers() {
		ArrayList<Player> alive = new ArrayList<>();
		for (Player player : getPlayers()) {
			if (!spectators.contains(player.getUniqueId())) {
				alive.add(player);
			}
		}
		return alive;
	}

	public int numTeamsWithMoreThan(int x) {
		int i = 0;
		for (Team team : Team.values()) {
			if (getPlayers(team).size() > x) {
				i++;
			}
		}
		return i;
	}

	public ArrayList<Team> getTeamsWithMoreThan(int x) {
		ArrayList<Team> te = new ArrayList<>();
		for (Team team : Team.values()) {
			if (getPlayers(team).size() > x) {
				te.add(team);
			}
		}
		return te;
	}

	public void setSpectator(Player player) {
		player.setGameMode(GameMode.SPECTATOR);
		spectators.add(player.getUniqueId());
		player.sendMessage(Util.colorize("&cYou are now spectating!"));
	}

	public boolean sameTeam(Player x, Player y) {
		return getTeam(x) == getTeam(y);
	}

	public void setTeam(Player player, Team team) {
		removeTeam(player);
		teams.put(player.getUniqueId(), team);

		if (gameState == GameState.COUNTDOWN && numTeamsWithMoreThan(0) < gameType.getMinTeams()) {
			sendMessage(Util.colorize("&cNot enough teams to continue, countdown stopped!"));
			reset(false);
		}
		if (gameState == GameState.WAITING && numTeamsWithMoreThan(0) >= gameType.getMinTeams()) {
			countdown.start();
		}
	}

	public Team getTeam(Player player) {
		return teams.get(player.getUniqueId());
	}

	public void removeTeam(Player player) {
		if (teams.containsKey(player.getUniqueId())) {
			teams.remove(player.getUniqueId());
		}
	}

	public int getTeamCount(Team team) {
		int amount = 0;
		for (Team t : teams.values()) {
			if (t == team) {
				amount++;
			}
		}
		return amount;
	}

	public Countdown getCountdown() {
		return countdown;
	}

	public ArrayList<Player> getPlayers(Team team) {
		ArrayList<Player> teamPlayers = new ArrayList<>();
		for (UUID uuid : teams.keySet()) {
			if (teams.get(uuid).equals(team)) {
				teamPlayers.add(Bukkit.getPlayer(uuid));
			}
		}
		return teamPlayers;
	}

	public void sendMessage(String message) {
		for (UUID uuid : players) {
			Bukkit.getPlayer(uuid).sendMessage(message);
		}
	}

	public void sendTitle(String title, String subtitle) {
		for (UUID uuid : players) {
			Bukkit.getPlayer(uuid).sendTitle(title, subtitle);
		}
	}

	public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		for (UUID uuid : players) {
			Bukkit.getPlayer(uuid).sendTitle(title, subtitle, fadeIn, stay, fadeOut);
		}
	}

	public int getID() {
		return id;
	}

	public GameState getState() {
		return gameState;
	}

	public GameType getGameType() {
		return gameType;
	}

	public Game getGame() {
		return game;
	}

	public ArrayList<Player> getPlayers() {
		ArrayList<Player> players = new ArrayList<>();
		for (UUID uuid : this.players) {
			players.add(Bukkit.getPlayer(uuid));
		}
		return players;
	}

	public void setState(GameState gameState) {
		this.gameState = gameState;
	}
}