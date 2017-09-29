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
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.enums.StoneWager;
import co.marcin.novaguilds.impl.util.AbstractChangeable;
import co.marcin.novaguilds.listener.SiegeStoneListener;
import co.marcin.novaguilds.manager.GuildManager;
import co.marcin.novaguilds.util.NumberUtils;
import com.gotofinal.darkrise.economy.DarkRiseItem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jsoup.helper.Validate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

    public static class UpkeepFeeWorker {
        private final SiegeStone siegeStone;

        public UpkeepFeeWorker(SiegeStone siegeStone) {
            this.siegeStone = siegeStone;
        }

        public int getDue() {
            int amount = 0;

            for(UpkeepFee upkeepFee : siegeStone.getUpkeepFees()) {
                if(upkeepFee.isPaid()
                        || NumberUtils.systemSeconds() - upkeepFee.getTimeCreated() < Config.CAVERSIA_UPKEEP_TIME.getSeconds()) {
                    continue;
                }

                amount += upkeepFee.getAmount();
            }

            return amount;
        }

        public int getTotalDue() {
            int total = 0;

            for(UpkeepFee upkeepFee : siegeStone.getUpkeepFees()) {
                if(upkeepFee.isPaid()) {
                    continue;
                }

                total += upkeepFee.getAmount();
            }

            return total;
        }

        public int getTimeToDisband() {
            List<UpkeepFee> list = new ArrayList<>();

            for(UpkeepFee upkeepFee : siegeStone.getUpkeepFees()) {
                if(upkeepFee.isPaid()) {
                    continue;
                }

                list.add(upkeepFee);
            }

            if(list.isEmpty()) {
                return 0;
            }

            Collections.sort(list, new Comparator<UpkeepFee>() {
                @Override
                public int compare(UpkeepFee o1, UpkeepFee o2) {
                    return o1.getTimeCreated() - o2.getTimeCreated();
                }
            });

            UpkeepFee oldest = list.get(0);

            if(oldest == null) {
                return 0;
            }

            return Config.CAVERSIA_UPKEEP_TIMEOUT.getSeconds() - ((int) NumberUtils.systemSeconds() - oldest.getTimeCreated());
        }
    }

    public static class UpkeepFee extends AbstractChangeable implements ConfigurationSerializable {
        private boolean paid;
        private DarkRiseItem item;
        private int amount;
        private int timeCreated, timePaid;

        /**
         * The constructor
         */
        public UpkeepFee() {
            setItem(Config.CAVERSIA_UPKEEP_ITEM.get());
            setAmount(Config.CAVERSIA_UPKEEP_AMOUNT.getInt());
            setTimeCreated((int) NumberUtils.systemSeconds());
        }

        /**
         * Deserializes the object
         * from a map
         *
         * @param map the map
         */
        public UpkeepFee(Map<String, Object> map) {
            setPaid((Boolean) map.get("paid"));
            setItem(NovaGuilds.getInstance().getListenerManager().getListener(SiegeStoneListener.class).CAVERSIA_ECONOMY.getItems().getItemByIdOrName((String) map.get("item")));
            setAmount((Integer) map.get("amount"));
            setTimeCreated((Integer) map.get("time.created"));

            if(isPaid()) {
                setTimePaid((Integer) map.get("time.paid"));
            }

            setUnchanged();
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();

            map.put("paid", isPaid());
            map.put("item", getItem().getName());
            map.put("amount", getAmount());
            map.put("time.created", getTimeCreated());

            if(isPaid()) {
                map.put("time.paid", getTimePaid());
            }

            return map;
        }

        /**
         * Gets if the fee has
         * been paid already
         *
         * @return boolean
         */
        public boolean isPaid() {
            return paid;
        }

        /**
         * Gets the item
         *
         * @return the item
         */
        public DarkRiseItem getItem() {
            return item;
        }

        /**
         * Gets the amount
         *
         * @return the amount
         */
        public int getAmount() {
            return amount;
        }

        /**
         * Gets the time when the fee
         * has been created
         *
         * @return unixtime
         */
        public int getTimeCreated() {
            return timeCreated;
        }

        /**
         * Gets time when the fee has been paid
         *
         * @return unixtime
         */
        public int getTimePaid() {
            return timePaid;
        }

        /**
         * Sets if paid
         *
         * @param paid boolean
         */
        public void setPaid(boolean paid) {
            this.paid = paid;
            setChanged();
        }

        /**
         * Sets paid to true
         */
        public void setPaid() {
            this.paid = true;
            setChanged();
        }

        /**
         * Sets the item
         *
         * @param item  the items
         */
        public void setItem(DarkRiseItem item) {
            this.item = item;
            setChanged();
        }

        /**
         * Sets the amount
         *
         * @param amount the amount
         */
        public void setAmount(int amount) {
            this.amount = amount;
            setChanged();
        }

        /**
         * Sets the time the
         * fee has been created
         *
         * @param timeCreated unixtime
         */
        public void setTimeCreated(int timeCreated) {
            this.timeCreated = timeCreated;
            setChanged();
        }

        /**
         * Sets the time when the fee has been paid
         *
         * @param timePaid unixtime
         */
        public void setTimePaid(int timePaid) {
            this.timePaid = timePaid;
            setChanged();
        }

        /**
         * Updates the paid time to current unixtime
         */
        public void updateTimePaid() {
            setTimePaid((int) NumberUtils.systemSeconds());
        }
    }

    private Block block;
    private StoneWager stoneWager;
    private String name = "";
    private int lastAttackTime;
    private Warmup warmup;
    private Collection<UpkeepFee> upkeepFees = new ArrayList<>();

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

        if(map.containsKey("upkeep")) {
            if(!(map.get("upkeep") instanceof List)) {
                throw new IllegalArgumentException("The data of a siege stone has been corrupted. The upkeep list is not actually a list.");
            }

            //noinspection unchecked
            List<Map<String, Object>> upkeepList = (List<Map<String, Object>>) map.get("upkeep");

            for(Map<String, Object> upkeepFeeEntry : upkeepList) {
                upkeepFees.add(new UpkeepFee(upkeepFeeEntry));
            }
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

        if(!getUpkeepFees().isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();

            for(UpkeepFee upkeepFee: getUpkeepFees()) {
                if(upkeepFee.isPaid()) {
                    continue;
                }

                list.add(upkeepFee.serialize());
            }

            map.put("upkeep", list);
        }

        return map;
    }

    @Override
    public boolean isChanged() {
        if(super.isChanged() || hasWarmup()) {
            return true;
        }

        for(UpkeepFee upkeepFee : getUpkeepFees()) {
            if(upkeepFee.isChanged()) {
                return true;
            }
        }

        return false;
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

    /**
     * Gets the warmup
     * If null creates a new one
     *
     * @return the warmup
     */
    public Warmup getWarmup() {
        if(warmup == null) {
            warmup = new Warmup();
        }

        return warmup;
    }

    /**
     * Gets upkeep fees
     *
     * @return set of upkeep fees
     */
    public Collection<UpkeepFee> getUpkeepFees() {
        return upkeepFees;
    }

    /**
     * Adds an upkeep fee
     *
     * @param upkeepFee upkeep fee instance
     */
    public void addUpkeepFee(UpkeepFee upkeepFee) {
        Validate.notNull(upkeepFee);
        upkeepFees.add(upkeepFee);
        setChanged();
    }

    /**
     * Removes an upkeep fee
     *
     * @param upkeepFee upkeep fee
     */
    public void removeUpkeepFee(UpkeepFee upkeepFee) {
        Validate.notNull(upkeepFee);
        upkeepFees.remove(upkeepFee);
        setChanged();
    }

    /**
     * Checks if the siege stone has a warmup
     *
     * @return boolean
     */
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

    /**
     * Applies data to the instance
     * from an existing siege stone instance
     *
     * @param siegeStone siege stone instance
     */
    public void apply(SiegeStone siegeStone) {
        block = siegeStone.getBlock();
        stoneWager = siegeStone.getStoneWager();
        name = siegeStone.getName();
        lastAttackTime = siegeStone.getLastAttackTime();
        warmup = siegeStone.getWarmup();
        setChanged();
    }
}
