/*
 *     NovaGuilds - Bukkit plugin
 *     Copyright (C) 2017 Marcin (CTRL) Wieczorek
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package co.marcin.novaguilds.runnable;

import co.marcin.novaguilds.NovaGuilds;
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.api.basic.NovaPlayer;
import co.marcin.novaguilds.api.basic.NovaRaid;
import co.marcin.novaguilds.api.event.GuildAbandonEvent;
import co.marcin.novaguilds.enums.AbandonCause;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.enums.VarKey;
import co.marcin.novaguilds.impl.basic.ControlPoint;
import co.marcin.novaguilds.impl.util.bossbar.BossBarUtils;
import co.marcin.novaguilds.listener.ControlPointListener;
import co.marcin.novaguilds.manager.ListenerManager;
import co.marcin.novaguilds.manager.MessageManager;
import co.marcin.novaguilds.util.LoggerUtils;
import co.marcin.novaguilds.util.NumberUtils;
import com.gotofinal.darkrise.economy.DarkRiseItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RunnableRaid implements Runnable {
	private static final NovaGuilds plugin = NovaGuilds.getInstance();
	private final UUID taskUUID = UUID.randomUUID();
	private static UUID scheduledUUID;

	@Override
	public void run() {
		boolean renewTask = false;
		ControlPointListener controlPointListener = plugin.getListenerManager().getListener(ControlPointListener.class);

		for(NovaGuild guildDefender : new ArrayList<>(plugin.getGuildManager().getGuilds())) {
			if(!guildDefender.isRaid()) {
				continue;
			}

			NovaRaid raid = guildDefender.getRaid();

			LoggerUtils.debug(guildDefender.getName() + " raid scheduler working " + raid.getProgress());

			if(!raid.getPlayersOccupying().isEmpty()) {
				//stepping progress
				float progress = (float) (raid.getPlayersOccupying().size() * Config.RAID_MULTIPLER.getDouble());

				if(progress > Config.RAID_MAXMULTIPLIER.getInt()) {
					progress = Config.RAID_MAXMULTIPLIER.getInt();
				}

				raid.addProgress(progress);

				//players raiding, update inactive time
				raid.updateInactiveTime();
			}
			else {
				raid.resetProgress();
			}

			//vars map
			Map<VarKey, String> vars = new HashMap<>();
			vars.put(VarKey.ATTACKER, raid.getGuildAttacker().getName());
			vars.put(VarKey.DEFENDER, guildDefender.getName());

			if(NumberUtils.systemSeconds() - raid.getStartTime() > Config.RAID_MAXDURATION.getSeconds()) {
				raid.setResult(NovaRaid.Result.TIMEOUT);
			}

			if(raid.isProgressFinished()) {
				if(guildDefender.getLives() > 1) {
					raid.setResult(NovaRaid.Result.SUCCESS);
				}
				else {
					raid.setResult(NovaRaid.Result.DESTROYED);
				}
			}

			//finishing raid
			if(raid.getResult() != NovaRaid.Result.DURING) {
				int pointsTake = Config.RAID_POINTSTAKE.getInt();

				switch(raid.getResult()) {
					case DESTROYED:
						raid.getGuildAttacker().addPoints(pointsTake);

						GuildAbandonEvent guildAbandonEvent = new GuildAbandonEvent(guildDefender, AbandonCause.RAID);
						ListenerManager.getLoggedPluginManager().callEvent(guildAbandonEvent);

						if(!guildAbandonEvent.isCancelled()) {
							vars.put(VarKey.GUILD_NAME, guildDefender.getName());
							Message.BROADCAST_GUILD_DESTROYED.clone().vars(vars).broadcast();
							plugin.getGuildManager().delete(guildAbandonEvent);
						}
						break;
					case SUCCESS:
						Message.BROADCAST_GUILD_RAID_FINISHED_ATTACKERWON.clone().vars(vars).broadcast();
						guildDefender.updateTimeRest();
						raid.getRegion().getSiegeStone().updateLastAttackTime();
						guildDefender.updateLostLive();
						guildDefender.takePoints(pointsTake);
						guildDefender.addPoints(pointsTake);

						//Transfer the region
						guildDefender.removeRegion(raid.getRegion());
						raid.getGuildAttacker().addRegion(raid.getRegion());
						break;
					case TIMEOUT:
						Message.BROADCAST_GUILD_RAID_FINISHED_DEFENDERWON.clone().vars(vars).broadcast();
						break;
				}
			}
			else if(!renewTask) {
				renewTask = true;
			}

			raidBar(raid);
		}

		//Control points
		for(ControlPoint controlPoint : controlPointListener.getControlPoints()) {
			if(!controlPoint.isRaid()) {
				continue;
			}

			ControlPoint.Raid raid = controlPoint.getRaid();
			LoggerUtils.debug(String.format("Control points '%s' runnable running. Progress: %f", controlPoint.getName(), raid.getProgress()));

			if(!raid.getPlayersOccupying().isEmpty()) {
				float progress = (float) (raid.getPlayersOccupying().size() * Config.CAVERSIA_CONTROLPOINT_MULTIPLIER.getDouble());

				if(progress > Config.CAVERSIA_CONTROLPOINT_MAXMULTIPLIER.getInt()) {
					progress = Config.CAVERSIA_CONTROLPOINT_MAXMULTIPLIER.getInt();
				}

				raid.addProgress(progress);
			}
			else {
				raid.resetProgress();

				boolean guildAlone = true;
				List<NovaPlayer> playersInArea = raid.getPlayersInArea();

				if(!playersInArea.isEmpty()) {
					NovaGuild guild = playersInArea.get(0).getGuild();
					for(NovaPlayer occupyingPlayer : playersInArea) {
						if(!occupyingPlayer.hasGuild() || !occupyingPlayer.getGuild().equals(guild)) {
							guildAlone = false;
							break;
						}
					}

					if(guildAlone && guild != null) {
						raid.setGuild(guild);
						LoggerUtils.debug("Raid guild changed to: " + guild.getName());
					}
				}
			}

			if(raid.isProgressFinished()) {
				raid.setResult(NovaRaid.Result.SUCCESS);
				controlPoint.updateTakeOverTime();
				controlPoint.setRaid(null);
				controlPoint.updateBlock();
				controlPoint.scheduleSoonVulnerableTask();
				controlPoint.scheduleVulnerableTask();
				controlPoint.setOwningGuild(raid.getGuild());

				Message.CHAT_CAVERSIA_CONTROLPOINT_BROADCAST_CAPTURED
					   .setVar(VarKey.NAME, controlPoint.getName())
					   .setVar(VarKey.GUILD_NAME, raid.getGuild().getName())
					   .broadcast();

				//Fireworks!
				Location location = controlPoint.getLocation().getWorld().getHighestBlockAt(controlPoint.getLocation()).getLocation();
				location.add(0.5, 0, 0.5);
				for(FireworkMeta effect : plugin.getListenerManager().getListener(ControlPointListener.class).fireworkEffects) {
					Firework entity = (Firework) controlPoint.getLocation().getWorld().spawnEntity(location, EntityType.FIREWORK);
					entity.setFireworkMeta(effect);
				}

				final Map<VarKey, String> vars = new HashMap<>();
				for(NovaPlayer nPlayer : raid.getParticipants()) {
					if(nPlayer.isOnline()) {
						continue;
					}

					vars.clear();
					vars.put(VarKey.PLAYER_NAME, nPlayer.getName());

					if(raid.getGuild().isMember(nPlayer)) {
						for(String command : Config.CAVERSIA_CONTROLPOINT_COMMANDS_WINNERS.vars(vars).getStringList()) {
							Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
						}

						for(Map.Entry<DarkRiseItem, Integer> item : controlPointListener.getRewardsWinners().entrySet()) {
							nPlayer.getPlayer().getInventory().addItem(item.getKey().getItem(item.getValue()));
						}
					}
					else {
						for(String command : Config.CAVERSIA_CONTROLPOINT_COMMANDS_LOSERS.vars(vars).getStringList()) {
							Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
						}

						for(Map.Entry<DarkRiseItem, Integer> item : controlPointListener.getRewardsLosers().entrySet()) {
							nPlayer.getPlayer().getInventory().addItem(item.getKey().getItem(item.getValue()));
						}
					}
				}

				for(String command : Config.CAVERSIA_CONTROLPOINT_COMMANDS_GLOBAL.getStringList()) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
				}
			}
			else if(!renewTask) {
				renewTask = true;
			}

			for(NovaPlayer participant : raid.getParticipants()) {
				if(!participant.isOnline()) {
					continue;
				}

				if(raid.isProgressFinished()) {
					BossBarUtils.removeBar(participant.getPlayer());
				}
				else {
					BossBarUtils.setMessage(
							participant.getPlayer(),
							Message.CHAT_CAVERSIA_CONTROLPOINT_BROADCAST_BOSSBAR
									.clone()
									.setVar(VarKey.GUILD_NAME, raid.getGuild().getName())
									.setVar(VarKey.TAG, raid.getGuild().getTag())
									.setVar(VarKey.NAME, controlPoint.getName())
									.get(),
							raid.getProgress());
				}
				//FIXME only one can be shown :(
			}
		}

		if(renewTask && plugin.isEnabled()) {
			schedule();
		}
		else {
			scheduledUUID = null;
		}
	}

	/**
	 * Displays the raid bar to all players taking part
	 *
	 * @param raid raid instance
	 */
	private void raidBar(NovaRaid raid) {
		if(raid.getResult() != NovaRaid.Result.DURING) {
			raid.getGuildAttacker().removeRaidBar();
			raid.getGuildDefender().removeRaidBar();
		}
		else {
			List<Player> players = raid.getGuildAttacker().getOnlinePlayers();
			players.addAll(raid.getGuildDefender().getOnlinePlayers());

			for(Player player : players) {
				if(Config.BOSSBAR_ENABLED.getBoolean()) {
					BossBarUtils.setMessage(player, Message.BARAPI_WARPROGRESS.clone().setVar(VarKey.DEFENDER, raid.getGuildDefender().getName()).get(), raid.getProgress());
				}
				else {
					//TODO
					if(raid.getProgress() == 0 || raid.getProgress() % 10 == 0 || raid.getProgress() >= 90) {
						String lines;
						if(raid.getProgress() == 0) {
							lines = "&f";
						}
						else {
							lines = "&4";
						}

						for(int i = 1; i <= 100; i++) {
							lines += "|";
							if(i == raid.getProgress()) {
								lines += "&f";
							}
						}

						MessageManager.sendPrefixMessage(player, lines);
					}
				}
			}
		}
	}

	/**
	 * Checks if this task is already running
	 *
	 * @return true if is running
	 */
	public static boolean isRaidRunnableRunning() {
		return scheduledUUID != null;
	}

	/**
	 * Schedules the runnable so it will run in a second
	 */
	public void schedule() {
		if(scheduledUUID == null) {
			scheduledUUID = taskUUID;
		}

		if(!scheduledUUID.equals(taskUUID)) {
			return;
		}

		NovaGuilds.runTaskLater(this, 1, TimeUnit.SECONDS);
	}
}
