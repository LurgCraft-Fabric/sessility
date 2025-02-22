package net.bytzo.sessility;

import java.util.Properties;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.dedicated.Settings;

public class SessilityProperties extends Settings<SessilityProperties> {
	public final int sessileTimeout = this.get("sessile-timeout", 120);
	public final long afkTimeout = this.get("afk-timeout", 0);
	public final String messageAfkKick = this.get("message-afk-kick", "");
	public final String messageSessile = this.get("message-sessile", "");
	public final String messageMotile = this.get("message-motile", "");

	public SessilityProperties(Properties properties) {
		super(properties);
	}

	@Override
	protected SessilityProperties reload(RegistryAccess registryAccess, Properties properties) {
		return new SessilityProperties(properties);
	}
}
