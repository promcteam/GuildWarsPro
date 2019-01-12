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
import co.marcin.novaguilds.enums.Message;
import co.marcin.novaguilds.impl.storage.managers.database.ResourceManagerVaultImpl;
import co.marcin.novaguilds.manager.GuildManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuildVault extends AbstractResource {
    private NovaGuild guild;
    private final Map<Integer, ItemStack> content = new HashMap<>();

    public GuildVault(NovaGuild guild) {
        super(UUID.randomUUID());
        setGuild(guild);
    }

    public GuildVault(ResultSet result) throws SQLException {
        super(UUID.fromString(result.getString("uuid")));
        setGuild(GuildManager.getGuild(UUID.fromString(result.getString("guild"))));
        this.guild.setVault(this);
        this.content.putAll(ResourceManagerVaultImpl.deserializeContent(result.getString("content")));
        setUnchanged();
        setAdded();
    }

    public NovaGuild getGuild() {
        return this.guild;
    }

    public void setGuild(NovaGuild guild) {
        this.guild = guild;
    }

    public Map<Integer, ItemStack> getContent() {
        return this.content;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, Config.VAULT_SIZE.getInt(), Message.CHAT_CAVERSIA_GUI_VAULT_NAME.get());

        for(Map.Entry<Integer, ItemStack> entry : getContent().entrySet()) {
            if(entry.getKey() < inv.getSize()) {
                inv.setItem(entry.getKey(), entry.getValue());
            }
        }

        player.openInventory(inv);
    }

    public void removeItem(Integer slot) {
        if(getContent().containsKey(slot)) {
            getContent().remove(slot);
            setChanged();
        }
    }

    public void addItem(Integer slot, ItemStack item) {
        if((item == null) || (item.getType() == Material.AIR)) {
            return;
        }

        getContent().put(slot, item.clone());
        setChanged();
    }
}
