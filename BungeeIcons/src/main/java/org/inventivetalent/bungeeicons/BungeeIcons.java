package org.inventivetalent.bungeeicons;

import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.Favicon;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class BungeeIcons extends Plugin implements Listener {

	// http://stackoverflow.com/questions/475074/regex-to-parse-or-validate-base64-data/475217#475217
	public static final Pattern BASE64_PATTERN = Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");

	Configuration        config;
	File                 iconDirectory;
	Map<String, Favicon> iconMap          = new HashMap<>();
	boolean              ignorePort       = true;
	boolean              ignoreCase       = true;
	String               emptyPlaceholder = "%empty%";

	@Override
	public void onEnable() {
		iconDirectory = new File(getDataFolder(), "icons");
		if (!iconDirectory.exists()) {
			iconDirectory.mkdirs();
		}
		reloadIcons();

		getProxy().getPluginManager().registerListener(this, this);
		getProxy().getPluginManager().registerCommand(this, new Command("bungeeicons", "bungeeicons.admin", "bi", "bicon") {
			@Override
			public void execute(CommandSender sender, String[] args) {
				if (args.length == 0) {
					sender.sendMessage(new TextComponent("§e/bungeeicons reload"));
					return;
				}
				if ("reload".equals(args[0])) {
					reloadIcons();
					sender.sendMessage(new TextComponent("§aReloaded icons."));
				}
			}
		});

	}

	void reloadIcons() {
		try {
			config = loadConfig();
		} catch (Exception e) {
			throw new RuntimeException("Could not load config", e);
		}
		ignorePort = config.getBoolean("ignorePort");
		ignoreCase = config.getBoolean("ignoreCase", false);
		emptyPlaceholder = config.getString("emptyPlaceholder");

		iconMap.clear();
		Configuration iconSection = config.getSection("icons");
		if (iconSection != null) {
			Collection<String> keys = iconSection.getKeys();
			getLogger().info("Loading " + keys.size() + " icons...");
			for (String key : keys) {
				String value = iconSection.getString(key);
				getLogger().fine("Loading " + key + " : " + value);

				if (emptyPlaceholder.equals(value)) {
					iconMap.put(key, null);
				}

				File iconFile = new File(iconDirectory, value);
				if (iconFile.exists()) {
					try {
						iconMap.put(key, Favicon.create(ImageIO.read(iconFile)));
					} catch (IOException e) {
						getLogger().log(Level.WARNING, "Failed to read image " + iconFile, e);
					}
				} else if (BASE64_PATTERN.matcher(value).matches()) {
					iconMap.put(key, Favicon.create(value));
				} else {
					getLogger().warning("File '" + value + "' not found in /BungeeIcons/icons and it does not appear to be a Base64-String.");
				}
			}
			getLogger().info("Loaded " + iconMap.size() + " icons.");
		}

	}

	@EventHandler
	public void on(ProxyPingEvent event) {
		InetSocketAddress address = event.getConnection().getVirtualHost();
		if (address == null) { return; }
		String host = ignorePort ? address.getHostName() : address.toString();
		if (ignoreCase) { host = host.toLowerCase(); }
		if (iconMap.containsKey(host)) {
			Favicon favicon = iconMap.get(host);
			event.getResponse().setFavicon(favicon);
		}
	}

	Configuration loadConfig() throws IOException {
		if (!getDataFolder().exists()) { getDataFolder().mkdirs(); }

		File configFile = new File(getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			configFile.createNewFile();
			try (InputStream inputStream = getResourceAsStream("config.yml")) {
				OutputStream outputStream = new FileOutputStream(configFile);
				ByteStreams.copy(inputStream, outputStream);
			}
		}

		return ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
	}

}
