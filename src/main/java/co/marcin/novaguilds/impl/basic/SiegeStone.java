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

import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.enums.StoneWager;
import co.marcin.novaguilds.manager.GuildManager;
import co.marcin.novaguilds.util.NumberUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SiegeStone extends AbstractResource implements ConfigurationSerializable {
    public static class Warmup implements ConfigurationSerializable {
        private int startTime;
        private NovaGuild attacker;

        /**
         * The constructor
         */
        protected Warmup() {

        }

        /**
         * Constructor for deserialization
         *
         * @param map map
         */
        public Warmup(Map<String, Object> map) {
            startTime = (int) map.get("start");
            attacker = GuildManager.getGuild(UUID.fromString((String) map.get("attacker")));
        }

        /**
         * Gets the warmup start time
         *
         * @return unixtime
         */
        public int getStartTime() {
            return startTime;
        }

        /**
         * Gets the attacker
         *
         * @return attacker guild
         */
        public NovaGuild getAttacker() {
            return attacker;
        }

        public int getTimeLeft() {
            return Config.CAVERSIA_WARMUP.getSeconds() - ((int) NumberUtils.systemSeconds() - getStartTime());
        }

        /**
         * Sets the warmup start time
         *
         * @param startTime unixtime
         */
        public void setStartTime(int startTime) {
            this.startTime = startTime;
        }

        /**
         * Sets the attacker guild
         *
         * @param attacker attacker guild
         */
        public void setAttacker(NovaGuild attacker) {
            this.attacker = attacker;
        }

        @Override
        public Map<String, Object> serialize() {
            final Map<String, Object> map = new HashMap<>();

            map.put("start", startTime);
            map.put("attacker", attacker.getUUID().toString());

            return map;
        }
    }

    private Block block;
    private StoneWager stoneWager;
    private String name = "";
    private int lastAttackTime;
    private Warmup warmup;

    /**
     * The constructor
     *
     * @param uuid the uuid
     */
    public SiegeStone(UUID uuid) {
        super(uuid);
    }

    /**
     * Constructor for deserialization
     *
     * @param map map
     */
    public SiegeStone(Map<String, Object> map) {
        super(UUID.fromString((String) map.get("uuid")));
        setBlock(Location.deserialize(map).getBlock());
        setStoneWager(StoneWager.valueOf((String) map.get("stonewager")));
        setName((String) map.get("name"));

        if(map.containsKey("lastattacktime")) {
            setLastAttackTime((int) map.get("lastattacktime"));
        }

        if(map.containsKey("warmup")) {
            warmup = new Warmup(((MemorySection) map.get("warmup")).getValues(true));
        }
        else {
            getWarmup();
        }
    }

    @Override
    public Map<String, Object> serialize() {
        final Map<String, Object> map = new HashMap<>();

        map.put("uuid", getUUID().toString());
        map.putAll(getBlock().getLocation().serialize());
        map.put("stonewager", getStoneWager().name());
        map.put("name", getName());

        if(getLastAttackTime() > 0) {
            map.put("lastattacktime", getLastAttackTime());
        }

        if(warmup != null && getWarmup().getStartTime() > 0 && getWarmup().getTimeLeft() > 0) {
            map.put("warmup", getWarmup().serialize());
        }

        return map;
    }

    @Override
    public boolean isChanged() {
        return super.isChanged() || hasWarmup();
    }

    /**
     * Gets the block
     *
     * @return block
     */
    public Block getBlock() {
        return block;
    }

    /**
     * Gets the stone wager
     *
     * @return stone wager
     */
    public StoneWager getStoneWager() {
        return stoneWager;
    }

    /**
     * Gets the name
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets last attack time
     *
     * @return unixtime
     */
    public int getLastAttackTime() {
        return lastAttackTime;
    }

    public Warmup getWarmup() {
        if(warmup == null) {
            warmup = new Warmup();
        }

        return warmup;
    }

    public boolean hasWarmup() {
        return warmup != null && warmup.getStartTime() > 0 && warmup.getTimeLeft() > 0;
    }

    /**
     * Checks if a region is vulnerable
     *
     * @return true if vulnerable
     */
    public boolean isVulnerable() {
        return NumberUtils.systemSeconds() - getLastAttackTime() > Config.RAID_TIMEREST.getSeconds();
    }

    /**
     * Sets the block
     *
     * @param block block
     */
    public void setBlock(Block block) {
        this.block = block;
        setChanged();
    }

    /**
     * Sets the stone wager
     *
     * @param stoneWager stone wager
     */
    public void setStoneWager(StoneWager stoneWager) {
        this.stoneWager = stoneWager;
        setChanged();
    }

    /**
     * Sets the name
     *
     * @param name name
     */
    public void setName(String name) {
        this.name = name;
        setChanged();
    }

    /**
     * Sets last attack time
     *
     * @param lastAttackTime unixtime
     */
    public void setLastAttackTime(int lastAttackTime) {
        this.lastAttackTime = lastAttackTime;
        setChanged();
    }

    /**
     * Updates the last attack time to current time
     */
    public void updateLastAttackTime() {
        setLastAttackTime((int) NumberUtils.systemSeconds());
    }

    public void apply(SiegeStone siegeStone) {
        block = siegeStone.getBlock();
        stoneWager = siegeStone.getStoneWager();
        name = siegeStone.getName();
        lastAttackTime = siegeStone.getLastAttackTime();
        warmup = siegeStone.getWarmup();
        setChanged();
    }
}
