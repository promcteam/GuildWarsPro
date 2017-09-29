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

package co.marcin.novaguilds.impl.basic;

import co.marcin.novaguilds.NovaGuilds;
import co.marcin.novaguilds.api.basic.ConfigWrapper;
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.api.basic.NovaPlayer;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.enums.VarKey;
import co.marcin.novaguilds.manager.GuildManager;
import co.marcin.novaguilds.util.NumberUtils;
import co.marcin.novaguilds.util.StringUtils;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ControlPoint extends AbstractResource implements ConfigurationSerializable {
    private String name;
    private int takeOverTime;
    private Location location;
    private NovaGuild owningGuild;
    private Raid raid;

    public static class Raid extends NovaRaidImpl {
        private final ControlPoint controlPoint;
        private final Collection<NovaPlayer> participants = new HashSet<>();

        /**
         * The constructor
         *
         * @param guild the guild who started a raid
         */
        public Raid(ControlPoint controlPoint, NovaGuild guild) {
            super(null, guild);
            this.controlPoint = controlPoint;
        }

        /**
         * Gets participant list
         * Participants are the players who joined the raid
         * even if they left the area
         *
         * @return participants
         */
        public Collection<NovaPlayer> getParticipants() {
            return participants;
        }

        /**
         * Just for clarification, gets the
         * guild which is trying to take over the CP
         *
         * @return the guild
         */
        public NovaGuild getGuild() {
            return getGuildDefender();
        }

        /**
         * Adds a participant
         *
         * @param nPlayer participant
         */
        public void addParticipant(NovaPlayer nPlayer) {
            if(!participants.contains(nPlayer)) {
                participants.add(nPlayer);
            }
        }

        /**
         * Sets the guild
         * Just for clarification
         *
         * @param guild the guild
         */
        public void setGuild(NovaGuild guild) {
            setGuildDefender(guild);
        }

        /**
         * Gets player who are trying to capture the CP
         *
         * @return the list
         */
        @Override
        public List<NovaPlayer> getPlayersOccupying() {
            final List<NovaPlayer> list = new ArrayList<>();

            for(NovaPlayer nPlayer : getPlayersInArea()) {
                if(getGuild().isMember(nPlayer)) {
                    list.add(nPlayer);
                }
            }

            return list;
        }

        /**
         * Gets players in the radius of the control point
         *
         * @return list of players
         */
        public List<NovaPlayer> getPlayersInArea() {
            final List<NovaPlayer> list = new ArrayList<>();

            for(NovaPlayer nPlayer : getParticipants()) {
                if(nPlayer.isOnline() && nPlayer.getPlayer().getLocation().distance(controlPoint.getLocation()) <= Config.CAVERSIA_CONTROLPOINT_RADIUS.getInt()) {
                    list.add(nPlayer);
                }
            }

            return list;
        }
    }

    /**
     * The constructor
     *
     * @param uuid the uuid
     */
    public ControlPoint(UUID uuid) {
        super(uuid);
    }

    public ControlPoint() {
        super(UUID.randomUUID());
    }

    public ControlPoint(Map<String, Object> map) {
        super(UUID.fromString((String) map.get("uuid")));
        setLocation(Location.deserialize(map));
        setName((String) map.get("name"));

        if(map.containsKey("guild")) {
            setOwningGuild(GuildManager.getGuild(UUID.fromString((String) map.get("guild"))));
        }

        if(map.containsKey("takeovertime")) {
            setTakeOverTime((Integer) map.get("takeovertime"));
        }

        setUnchanged();
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public int getTakeOverTime() {
        return takeOverTime;
    }

    public Location getLocation() {
        return location;
    }

    public NovaGuild getOwningGuild() {
        return owningGuild;
    }

    public boolean isOwned() {
        return getOwningGuild() != null;
    }

    public boolean isVulnerable() {
        return getTimeLeft() <= 0;
    }

    /**
     * Gets how much time is left till
     * the control point is vulnerable again
     *
     * @return time left in seconds
     */
    public int getTimeLeft() {
        return (int) (getTakeOverTime() - NumberUtils.systemSeconds() + Config.CAVERSIA_CONTROLPOINT_COOLDOWN.getSeconds());
    }

    public Raid getRaid() {
        return raid;
    }

    public boolean isRaid() {
        return raid != null;
    }

    public void setName(String name) {
        this.name = name;
        setChanged();
    }

    public void setTakeOverTime(int takeOverTime) {
        this.takeOverTime = takeOverTime;
        setChanged();
    }

    public void setLocation(Location location) {
        this.location = location;
        setChanged();
    }

    public void setOwningGuild(NovaGuild owningGuild) {
        this.owningGuild = owningGuild;
        setChanged();
    }

    public void setRaid(Raid raid) {
        this.raid = raid;
    }

    public void updateTakeOverTime() {
        setTakeOverTime((int) NumberUtils.systemSeconds());
    }

    public void updateBlock() {
        ConfigWrapper type = isVulnerable()
                ? Config.CAVERSIA_CONTROLPOINT_BLOCK_VULNERABLE
                : Config.CAVERSIA_CONTROLPOINT_BLOCK_NONVULNERABLE;

        getLocation().getBlock().setType(type.getMaterial());
        getLocation().getBlock().setData(type.getMaterialData());
    }

    @Override
    public Map<String, Object> serialize() {
        final Map<String, Object> map = new HashMap<>();

        map.put("uuid", getUUID().toString());
        map.putAll(getLocation().serialize());
        map.put("name", getName());

        if(isOwned()) {
            map.put("guild", getOwningGuild().getUUID().toString());
        }

        if(getTakeOverTime() > 0) {
            map.put("takeovertime", getTakeOverTime());
        }

        return map;
    }

    public void scheduleSoonVulnerableTask() {
        NovaGuilds.runTaskLater(new Runnable() {
            @Override
            public void run() {
                Message.CHAT_CAVERSIA_CONTROLPOINT_BROADCAST_SOONVULNERABLE
                        .setVar(VarKey.NAME, getName())
                        .setVar(VarKey.TIME, StringUtils.secondsToString(getTimeLeft(), TimeUnit.MINUTES))
                        .broadcast();

            }
        }, getTimeLeft() - Config.CAVERSIA_CONTROLPOINT_VULNERABLENOTIFY.getSeconds(), TimeUnit.SECONDS);
    }

    public void scheduleVulnerableTask() {
        NovaGuilds.runTaskLater(new Runnable() {
            @Override
            public void run() {
                updateBlock();
                Message.CHAT_CAVERSIA_CONTROLPOINT_BROADCAST_VULNERABLE
                        .setVar(VarKey.NAME, getName())
                        .broadcast();
            }
        }, getTimeLeft(), TimeUnit.SECONDS);
    }

    public Raid createRaid(NovaGuild guild) {
        return new Raid(this, guild);
    }
}
