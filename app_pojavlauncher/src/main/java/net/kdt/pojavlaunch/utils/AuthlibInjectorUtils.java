package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provisions <a href="https://github.com/yushijinhun/authlib-injector">authlib-injector</a> and
 * turns it into JVM arguments.
 * <p>
 * authlib-injector is a java agent that rewrites the authorization, session and texture server
 * URLs inside Minecraft's {@code authlib} at class load time, so the game authenticates and
 * resolves skins against a third party Yggdrasil server instead of Mojang, without the game or
 * its jar being modified in any way. That is what makes Ely.by skins and online-mode server joins
 * work, and it is the only supported way to do it on Minecraft 1.7.2 and newer.
 * <p>
 * Usage as documented upstream is {@code -javaagent:/path/authlib-injector.jar=<API root>}, so the
 * agent needs a jar on disk. It is looked up in, and installed into, the launcher's private data
 * directory; the component can either be shipped in {@code assets/components/authlib-injector}
 * (see {@link #unpackBundledJar(Context)}) or downloaded on demand.
 * <p>
 * Licensed under AGPLv3 <i>with the authlib-injector exception</i>, which explicitly allows both
 * shipping the unmodified binary inside another program and loading it as a java agent.
 */
public final class AuthlibInjectorUtils {
	private static final String TAG = "AuthlibInjector";

	/** Directory name of the component, both inside the APK assets and in the private data dir. */
	public static final String COMPONENT_NAME = "authlib-injector";

	/** File name we install the agent under, and the one we prefer when several jars are present. */
	public static final String JAR_NAME = "authlib-injector.jar";

	/**
	 * Pinned authlib-injector release, downloaded when the launcher was built without the
	 * component bundled. Bump this together with the asset if the build script fetches it.
	 */
	public static final String VERSION = "1.2.8";
	public static final String DOWNLOAD_URL = "https://github.com/yushijinhun/authlib-injector/releases/download/v"
			+ VERSION + "/authlib-injector-" + VERSION + ".jar";

	private AuthlibInjectorUtils() {}

	/** @return the directory the agent jar lives in */
	@NonNull
	public static File getComponentDir() {
		return new File(Tools.DIR_DATA, COMPONENT_NAME);
	}

	/** @return where a build that ships the agent keeps it inside the APK assets */
	@NonNull
	public static String getComponentAssetPath() {
		return "components/" + COMPONENT_NAME;
	}

	/**
	 * @return the agent jar to hand to {@code -javaagent}, or null if it is not available yet.
	 * Any jar placed in the component directory is picked up, so dropping a newer
	 * authlib-injector build in there by hand works too.
	 */
	@Nullable
	public static File findAgentJar() {
		File preferred = new File(getComponentDir(), JAR_NAME);
		if (isUsableAgent(preferred)) return preferred;
		File[] jars = getComponentDir().listFiles(file -> file.getName().endsWith(".jar"));
		if (jars == null) return null;
		for (File jar : jars) {
			if (isUsableAgent(jar)) return jar;
		}
		return null;
	}

	/** @return whether an agent jar is already present, no matter its origin */
	public static boolean isAgentAvailable() {
		return findAgentJar() != null;
	}

	/**
	 * @return a usable agent jar, downloading it into the component directory if necessary
	 * @throws IOException if the jar could neither be found nor downloaded
	 */
	@NonNull
	public static File ensureAgentJar() throws IOException {
		File existing = findAgentJar();
		if (existing != null) return existing;

		File target = new File(getComponentDir(), JAR_NAME);
		FileUtils.ensureParentDirectory(target);
		Log.i(TAG, "authlib-injector is not installed, downloading " + VERSION);
		DownloadUtils.downloadFile(DOWNLOAD_URL, target);
		if (!isUsableAgent(target)) {
			//noinspection ResultOfMethodCallIgnored
			target.delete();
			throw new IOException("The downloaded authlib-injector build is not a usable java agent, " +
					"place " + JAR_NAME + " into " + getComponentDir().getAbsolutePath() + " manually");
		}
		return target;
	}

	/**
	 * Copies the agent jar out of the APK, if this build ships it. Only writes anything when the
	 * component directory has no jar in it yet, so a manually installed one is never clobbered.
	 * @return whether a jar was copied
	 */
	public static boolean unpackBundledJar(@NonNull Context ctx) throws IOException {
		if (isAgentAvailable()) return false;
		String[] bundledFiles;
		try {
			bundledFiles = ctx.getAssets().list(getComponentAssetPath());
		} catch (IOException e) {
			return false; // this build of the launcher does not ship the component
		}
		if (bundledFiles == null || bundledFiles.length == 0) return false;
		FileUtils.ensureDirectory(getComponentDir());
		boolean copied = false;
		for (String fileName : bundledFiles) {
			if (!fileName.endsWith(".jar")) continue;
			Tools.copyAssetFile(ctx, getComponentAssetPath() + "/" + fileName,
					getComponentDir().getAbsolutePath(), true);
			copied = true;
		}
		if (copied) Log.i(TAG, "Unpacked authlib-injector from the launcher assets");
		return copied;
	}

	/**
	 * Adds the agent to the JVM argument list.
	 * @param javaArgs the JVM argument list, main class not appended yet
	 * @param agentJar the agent jar to load
	 * @param apiRoot root of the Yggdrasil API the game should be pointed at
	 */
	public static void appendAgentArgs(@NonNull List<String> javaArgs, @NonNull File agentJar,
									   @NonNull String apiRoot) {
		javaArgs.add("-javaagent:" + agentJar.getAbsolutePath() + "=" + apiRoot);
		// authlib-injector would otherwise drop an authlib-injector.log next to the game files
		javaArgs.add("-Dauthlibinjector.noLogFile");
	}

	/**
	 * A jar is only usable as an agent if it is a real archive declaring {@code Premain-Class}.
	 * Checking this keeps a truncated download or an HTML error page saved under a {@code .jar}
	 * name from taking the whole game down at launch.
	 */
	private static boolean isUsableAgent(@Nullable File file) {
		if (file == null || !file.isFile() || file.length() < 1024) return false;
		try (ZipFile zip = new ZipFile(file)) {
			ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
			if (manifestEntry == null) return false;
			try (InputStream is = zip.getInputStream(manifestEntry)) {
				String manifest = IOUtils.toString(is, StandardCharsets.UTF_8).replace("\r", "");
				return manifest.contains("Premain-Class:");
			}
		} catch (IOException e) {
			return false;
		}
	}
}
