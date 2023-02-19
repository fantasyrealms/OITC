/*
 * OITC - Kill your opponents and reach 25 points to win!
 * Copyright (C) 2023 Despical
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

package me.despical.oitc.events;

import me.despical.commons.util.LogUtils;
import me.despical.oitc.ConfigPreferences;
import me.despical.oitc.Main;
import me.despical.oitc.events.spectator.SpectatorEvents;
import me.despical.oitc.events.spectator.SpectatorItemEvents;
import me.despical.oitc.handlers.ChatManager;
import me.despical.oitc.user.UserManager;
import org.bukkit.event.Listener;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Despical
 * <p>
 * Created at 31.07.2022
 */
public abstract class ListenerAdapter implements Listener {

	protected final Main plugin;
	protected final ChatManager chatManager;
	protected final UserManager userManager;
	protected final ConfigPreferences preferences;

	public ListenerAdapter(Main plugin) {
		this.plugin = plugin;
		this.chatManager = plugin.getChatManager();
		this.userManager = plugin.getUserManager();
		this.preferences = plugin.getConfigPreferences();
		this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	protected void registerIf(boolean cond, Supplier<Listener> supplier) {
		if (!cond) return;

		plugin.getServer().getPluginManager().registerEvents(supplier.get(), plugin);
	}

	public static void registerEvents(Main plugin) {
		final Class<?>[] listenerAdapters = {SpectatorEvents.class, ChatEvents.class, Events.class, SpectatorItemEvents.class};

		try {
			for (Class<?> listenerAdapter : listenerAdapters) {
				listenerAdapter.getConstructor(Main.class).newInstance(plugin);

				LogUtils.log("[Listener Adapter] Registering new listener class: {0}", listenerAdapter.getSimpleName());
			}
		} catch (Exception ignored) {
			LogUtils.sendConsoleMessage("&cAn exception occured on event registering.");
		}
	}
}