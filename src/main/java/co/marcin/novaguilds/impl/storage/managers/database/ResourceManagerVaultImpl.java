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

package co.marcin.novaguilds.impl.storage.managers.database;

import co.marcin.novaguilds.NovaGuilds;
import co.marcin.novaguilds.api.basic.NovaGuild;
import co.marcin.novaguilds.api.storage.PreparedStatementBuilder;
import co.marcin.novaguilds.api.util.reflect.MethodInvoker;
import co.marcin.novaguilds.enums.Config;
import co.marcin.novaguilds.impl.basic.GuildVault;
import co.marcin.novaguilds.impl.storage.AbstractDatabaseStorage;
import co.marcin.novaguilds.listener.SiegeStoneListener;
import co.marcin.novaguilds.util.LoggerUtils;
import co.marcin.novaguilds.util.reflect.Reflections;
import com.gotofinal.darkrise.economy.DarkRiseItem;
import com.gotofinal.darkrise.economy.DarkRiseItems;
import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ResourceManagerVaultImpl
        extends AbstractDatabaseResourceManager<GuildVault> {
    private static MethodInvoker<ItemMeta> deserializeMethod;
    private static DarkRiseItems economyItems = NovaGuilds.getInstance().getListenerManager().getListener(SiegeStoneListener.class).CAVERSIA_ECONOMY.getItems();

    static {
        try {
            Class<?> craftMetaItemSerializableMetaClass = Reflections.getBukkitClass("inventory.CraftMetaItem$SerializableMeta");
            deserializeMethod = Reflections.getMethod(craftMetaItemSerializableMetaClass, ItemMeta.class, "deserialize");
        }
        catch(ClassNotFoundException | NoSuchMethodException e) {
            LoggerUtils.exception(e);
        }
    }


    public ResourceManagerVaultImpl(AbstractDatabaseStorage storage) {
        super(storage, GuildVault.class, "vault");

        final int returnKeys = storage.isStatementReturnGeneratedKeysSupported()
                ? Statement.RETURN_GENERATED_KEYS
                : Statement.NO_GENERATED_KEYS;

        storage.registerStatementBuilder("VAULT_INSERT", new PreparedStatementBuilder() {
            public PreparedStatement build(Connection connection) throws SQLException {
                String ranksInsertSQL = String.format("INSERT INTO `%svault` VALUES(?,?,?);", Config.MYSQL_PREFIX.getString());
                return connection.prepareStatement(ranksInsertSQL, returnKeys);
            }
        });

        storage.registerStatementBuilder("VAULT_SELECT", new PreparedStatementBuilder() {
            public PreparedStatement build(Connection connection) throws SQLException {
                String ranksInsertSQL = String.format("SELECT * FROM `%svault`;", Config.MYSQL_PREFIX.getString());
                return connection.prepareStatement(ranksInsertSQL, returnKeys);
            }
        });

        storage.registerStatementBuilder("VAULT_UPDATE", new PreparedStatementBuilder() {
            public PreparedStatement build(Connection connection) throws SQLException {
                String ranksInsertSQL = String.format("UPDATE `%svault` SET `guild`=?, `content`=? WHERE `uuid`=?;", Config.MYSQL_PREFIX.getString());
                return connection.prepareStatement(ranksInsertSQL, returnKeys);
            }
        });

        storage.registerStatementBuilder("VAULT_DELETE", new PreparedStatementBuilder() {
            public PreparedStatement build(Connection connection) throws SQLException {
                String ranksInsertSQL = String.format("DELETE FROM `%svault` WHERE `uuid`=?", Config.MYSQL_PREFIX.getString());
                return connection.prepareStatement(ranksInsertSQL, returnKeys);
            }
        });
    }

    public int executeSave() {
        int count = super.executeSave();

        for(NovaGuild guild : this.plugin.getGuildManager().getGuilds()) {
            GuildVault vault = guild.getVault();

            if((vault != null) && ((vault.isChanged()) || (!vault.isAdded())) && (!vault.isUnloaded())) {


                if(vault.isAdded()) {
                    if(save(vault)) {
                        count++;
                    }
                }
                else {
                    add(vault);
                    count++;
                }
            }
        }
        return count;
    }


    public List<GuildVault> load() {
        getStorage().connect();
        List<GuildVault> list = new ArrayList<>();
        try {
            PreparedStatement statement = getStorage().getPreparedStatement("VAULT_SELECT");
            ResultSet res = statement.executeQuery();

            while(res.next()) {
                try {
                    list.add(new GuildVault(res));
                }
                catch(Exception e) {
                    LoggerUtils.error("Failed while deserializing a vault.");
                    LoggerUtils.exception(e);
                }
            }
        }
        catch(SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public boolean save(GuildVault vault) {
        if(((!vault.isChanged()) && (!isInSaveQueue(vault))) || (vault.isUnloaded()) || (isInRemovalQueue(vault))) {
            return false;
        }

        if(!vault.isAdded()) {
            add(vault);
            return true;
        }

        getStorage().connect();
        try {
            PreparedStatement statement = getStorage().getPreparedStatement("VAULT_UPDATE");

            statement.setString(1, vault.getGuild().getUUID().toString());
            statement.setString(2, serializeContent(vault.getContent()));
            statement.setString(3, vault.getUUID().toString());
            statement.executeUpdate();
            vault.setUnchanged();
        }
        catch(SQLException e) {
            LoggerUtils.exception(e);
        }

        return false;
    }

    public void add(GuildVault vault) {
        Validate.notNull(vault);
        Validate.notNull(vault.getUUID());
        Validate.notNull(vault.getGuild());
        getStorage().connect();
        try {
            PreparedStatement statement = getStorage().getPreparedStatement("VAULT_INSERT");

            statement.setString(1, vault.getUUID().toString());
            statement.setString(2, vault.getGuild().getUUID().toString());
            statement.setString(3, serializeContent(vault.getContent()));
            statement.execute();


            vault.setUnchanged();
            vault.setAdded();
        }
        catch(SQLException e) {
            LoggerUtils.exception(e);
        }
    }


    public boolean remove(GuildVault guildVault) {
        getStorage().connect();
        try {
            PreparedStatement statement = getStorage().getPreparedStatement("VAULT_DELETE");
            statement.setString(0, guildVault.getUUID().toString());
            statement.execute();
        }
        catch(SQLException e) {
            LoggerUtils.exception(e);
        }

        return false;
    }


    public static String serializeContent(Map<Integer, ItemStack> map) {
        JSONObject jsonObject = new JSONObject();

        for(Map.Entry<Integer, ItemStack> entry : map.entrySet()) {
            ItemStack item = entry.getValue();
            if((item != null) && (item.getType() != Material.AIR)) {


                Map<String, Object> result = new LinkedHashMap<>();

                DarkRiseItem darkRiseItem = economyItems.getItemByStack(item);

                if(darkRiseItem != null) {
                    result.put("econitem", darkRiseItem.getName());
                    result.put("amount", item.getAmount());
                }
                else {
                    result.put("type", item.getType().name());

                    if(item.getDurability() != 0) {
                        result.put("damage", item.getDurability());
                    }

                    if(item.getAmount() != 1) {
                        result.put("amount", item.getAmount());
                    }

                    if(item.hasItemMeta()) {
                        result.put("meta", item.getItemMeta().serialize());
                    }
                }

                jsonObject.append(String.valueOf(entry.getKey()), result);
            }
        }
        return jsonObject.toString();
    }


    public static Map<Integer, ItemStack> deserializeContent(String string) {
        JSONObject jsonObject = new JSONObject(string);
        Map<Integer, ItemStack> map = new HashMap<>();

        for(String key : jsonObject.keySet()) {
            int slot = Integer.parseInt(key);
            Map<String, Object> jsonMap = jsonToMap(jsonObject);
            //noinspection unchecked
            Map<String, Object> data = (Map) ((List) jsonMap.get(key)).get(0);

            ItemStack item;
            if(data.containsKey("econitem")) {
                DarkRiseItem econItem = economyItems.getItemByIdOrName((String) data.get("econitem"));

                if(econItem == null) {
                    LoggerUtils.error(String.format("Invalid item: %s", data.get("econitem")));
                    continue;
                }

                item = econItem.getItem(data.containsKey("amount") ? (Integer) data.get("amount") : 1);
            }
            else {
                item = ItemStack.deserialize(data);

                if(data.containsKey("meta")) {
                    ItemMeta meta = deserializeMethod.invoke(null, data.get("meta"));
                    item.setItemMeta(meta);
                }
            }

            map.put(slot, item);
        }

        return map;
    }


    protected static Map<String, Object> jsonToMap(JSONObject json)
            throws JSONException {
        Map<String, Object> retMap = new HashMap<>();

        if(json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }


    protected static Map<String, Object> toMap(JSONObject object)
            throws JSONException {
        Map<String, Object> map = new HashMap<>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if((value instanceof JSONArray)) {
                value = toList((JSONArray) value);

            }
            else if((value instanceof JSONObject)) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }


    protected static List<Object> toList(JSONArray array)
            throws JSONException {
        List<Object> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if((value instanceof JSONArray)) {
                value = toList((JSONArray) value);

            }
            else if((value instanceof JSONObject)) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    protected void updateUUID(GuildVault resource) {
    }
}
