package net.kdt.pojavlaunch.authenticator.elyby;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.AuthlibInjectorUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Glue between an Ely.by account and the game launch.
 * <p>
 * An Ely.by access token is only meaningful to Ely.by, but the client validates it against
 * {@code sessionserver.mojang.com} and downloads skins from Mojang unless told otherwise, which
 * shows up as "Failed to login: Invalid session" plus a Steve/Alex avatar. authlib-injector fixes
 * both by re-pointing Minecraft's authlib at Ely.by's API, and it is passed in as a java agent.
 */
public final class ElyByLaunchHelper {
	private static final String TAG = "ElyByLaunch";

	private ElyByLaunchHelper() {}

	/** @return whether this launch has to be routed through Ely.by */
	public static boolean shouldInjectAuthlib(MinecraftAccount account) {
		return account != null && account.isElyBy;
	}

	/**
	 * Appends the authlib-injector java agent to the JVM arguments, downloading the agent if this
	 * build does not ship it. Never throws: when the agent cannot be obtained the game is still
	 * launched, so single player and online-mode=false servers keep working, and the user is told
	 * why skins and Ely.by servers won't.
	 *
	 * @param ctx used for the toast and the string resources
	 * @param javaArgs the JVM argument list, the main class must not be appended yet
	 * @param account the account the game is being launched with
	 * @return whether the agent was added
	 */
	public static boolean appendAuthlibInjector(@NonNull Context ctx, @NonNull List<String> javaArgs,
												@NonNull MinecraftAccount account) {
		if (!shouldInjectAuthlib(account)) return false;
		try {
			File agentJar = AuthlibInjectorUtils.ensureAgentJar();
			AuthlibInjectorUtils.appendAgentArgs(javaArgs, agentJar, ElyByConstants.AUTHLIB_INJECTOR_API_ROOT);
			log("Ely.by: injecting authlib-injector (" + agentJar.getName() + ") for " + account.username
					+ ", API root " + ElyByConstants.AUTHLIB_INJECTOR_API_ROOT);
			return true;
		} catch (IOException | RuntimeException e) {
			Log.e(TAG, "Failed to make authlib-injector available", e);
			log("Ely.by: authlib-injector is unavailable, skins and Ely.by servers will not work "
					+ "this launch. Reason: " + e);
			final String message = ctx.getString(R.string.elyby_agent_missing);
			Tools.runOnUiThread(() -> Toast.makeText(ctx, message, Toast.LENGTH_LONG).show());
			return false;
		}
	}

	/**
	 * Writes to logcat and to the launcher log. The log file is managed by native code, so a
	 * failure there must never be able to take the launch down with it.
	 */
	private static void log(String message) {
		Log.i(TAG, message);
		try {
			Logger.appendToLog(message);
		} catch (Throwable ignored) {
			// Being unable to log is not worth interrupting a launch over
		}
	}
}
