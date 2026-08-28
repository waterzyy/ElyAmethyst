package net.kdt.pojavlaunch.authenticator.elyby;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;
import static net.kdt.pojavlaunch.authenticator.elyby.ElyByConstants.AGENT_NAME;
import static net.kdt.pojavlaunch.authenticator.elyby.ElyByConstants.AGENT_VERSION;
import static net.kdt.pojavlaunch.authenticator.elyby.ElyByConstants.AUTH_SERVER_ROOT;
import static net.kdt.pojavlaunch.authenticator.elyby.ElyByConstants.TOKEN_LIFETIME_MILLIS;
import static net.kdt.pojavlaunch.authenticator.elyby.ElyByConstants.TWO_FACTOR_HINT;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Background login against Ely.by's Yggdrasil authentication server.
 * <p>
 * Yggdrasil is the protocol Minecraft used to talk to {@code authserver.mojang.com} before the
 * Microsoft migration, so this is structurally the "old" Mojang login with the URLs swapped:
 * a nickname/e-mail plus a password buys an {@code accessToken} that the game later validates
 * itself (through authlib-injector, see {@link net.kdt.pojavlaunch.utils.AuthlibInjectorUtils}).
 * <p>
 * The user's password is only ever used to obtain the token, it is never written to disk.
 */
public class ElyByBackgroundLogin {
	private static final String TAG = "ElyByAuth";
	private static final int CONNECT_TIMEOUT = 15000;
	private static final int READ_TIMEOUT = 20000;

	private final boolean mIsRefresh;
	@Nullable private final String mUsername;
	@Nullable private final String mPassword;
	@Nullable private final String mTotpCode;
	@Nullable private String mAccessToken;
	@Nullable private String mClientToken;

	/* Fields filled in by the login, describing the account that will be saved */
	public String mcName;
	public String mcUuid;
	public String mcToken;
	public long expiresAt;

	private ElyByBackgroundLogin(boolean isRefresh, @Nullable String username, @Nullable String password,
								  @Nullable String totpCode, @Nullable String accessToken, @Nullable String clientToken) {
		mIsRefresh = isRefresh;
		mUsername = username;
		mPassword = password;
		mTotpCode = totpCode;
		mAccessToken = accessToken;
		mClientToken = clientToken;
	}

	/**
	 * Log in with an Ely.by account.
	 * @param username Ely.by nickname or e-mail
	 * @param password Ely.by password
	 * @param totpCode optional 6-digit two factor code, asked for after the first refusal
	 */
	@NonNull
	public static ElyByBackgroundLogin withPassword(@NonNull String username, @NonNull String password,
													 @Nullable String totpCode) {
		// Keep the client token of an already known account, Yggdrasil ties access tokens to it.
		MinecraftAccount existing = MinecraftAccount.load(username.trim());
		String clientToken = existing != null && isValidToken(existing.clientToken)
				? existing.clientToken
				: UUID.randomUUID().toString();
		return new ElyByBackgroundLogin(false, username.trim(), password, totpCode, null, clientToken);
	}

	/** Re-authenticate a stored account using its accessToken/clientToken pair, no password needed. */
	@NonNull
	public static ElyByBackgroundLogin withTokens(@NonNull MinecraftAccount account) {
		return new ElyByBackgroundLogin(true, null, null, null, account.accessToken, account.clientToken);
	}

	/** Performs the login on the global executor, calling the listeners back on the UI thread. */
	public void performLogin(@Nullable final ProgressListener progressListener,
							 @Nullable final DoneListener doneListener,
							 @Nullable final ErrorListener errorListener) {
		sExecutorService.execute(() -> {
			try {
				notifyProgress(progressListener, 1);
				JSONObject response = mIsRefresh ? requestRefresh() : requestAuthenticate();
				notifyProgress(progressListener, 2);

				mcToken = response.getString("accessToken");
				if (response.has("clientToken")) mClientToken = response.getString("clientToken");
				JSONObject selectedProfile = response.getJSONObject("selectedProfile");
				mcName = selectedProfile.getString("name");
				// Ely.by hands out dash-less UUIDs, the launcher stores the dashed form everywhere.
				mcUuid = dashifyUuid(selectedProfile.getString("id"));
				expiresAt = System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS;

				MinecraftAccount acc = MinecraftAccount.load(mcName);
				if (acc == null) acc = new MinecraftAccount();
				acc.username = mcName;
				acc.profileId = mcUuid;
				acc.accessToken = mcToken;
				acc.clientToken = mClientToken;
				acc.expiresAt = expiresAt;
				acc.isElyBy = true;
				acc.isMicrosoft = false;
				acc.updateSkinFace();
				acc.save();

				Log.i(TAG, "Successfully authenticated " + mcName + " against Ely.by");
				if (doneListener != null) {
					MinecraftAccount finalAcc = acc;
					Tools.runOnUiThread(() -> doneListener.onLoginDone(finalAcc));
				}
			} catch (Exception e) {
				Log.e(TAG, "Exception thrown during Ely.by authentication", e);
				if (errorListener != null)
					Tools.runOnUiThread(() -> errorListener.onLoginError(e));
			}
			ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_ELYBY);
		});
	}

	/** {@code POST /authserver/authenticate}, the login the user actually performs. */
	private JSONObject requestAuthenticate() throws IOException {
		JSONObject body = new JSONObject();
		try {
			body.put("agent", new JSONObject().put("name", AGENT_NAME).put("version", AGENT_VERSION));
			body.put("username", mUsername);
			body.put("password", withTotpSuffix(mPassword, mTotpCode));
			body.put("clientToken", mClientToken);
			body.put("requestUser", true);
		} catch (JSONException e) {
			throw new IOException("Failed to build the Ely.by request", e);
		}
		return postYggdrasilRequest(ElyByConstants.AUTHENTICATE_PATHS, body);
	}

	/** {@code POST /authserver/refresh}, extends the lifetime of a stored access token. */
	private JSONObject requestRefresh() throws IOException {
		JSONObject body = new JSONObject();
		try {
			body.put("accessToken", mAccessToken);
			body.put("clientToken", mClientToken);
			body.put("requestUser", true);
		} catch (JSONException e) {
			throw new IOException("Failed to build the Ely.by request", e);
		}
		return postYggdrasilRequest(ElyByConstants.REFRESH_PATHS, body);
	}

	/**
	 * Sends a Yggdrasil request, walking over the known endpoint paths until one of them exists.
	 * @param relativePaths candidate paths of the endpoint, tried in order
	 * @param body JSON payload of the request
	 */
	private JSONObject postYggdrasilRequest(String[] relativePaths, JSONObject body) throws IOException {
		String payload = body.toString();
		IOException lastException = null;
		for (int i = 0; i < relativePaths.length; i++) {
			HttpURLConnection conn = (HttpURLConnection) new URL(AUTH_SERVER_ROOT + relativePaths[i]).openConnection();
			conn.setConnectTimeout(CONNECT_TIMEOUT);
			conn.setReadTimeout(READ_TIMEOUT);
			setCommonProperties(conn, payload);
			conn.connect();
			try (OutputStream wr = conn.getOutputStream()) {
				wr.write(payload.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode;
			String responseBody;
			try {
				responseCode = conn.getResponseCode();
				responseBody = readBody(conn, responseCode);
			} finally {
				conn.disconnect();
			}

			if (responseCode >= 200 && responseCode < 300) {
				try {
					return new JSONObject(responseBody);
				} catch (JSONException e) {
					throw new IOException("Ely.by sent a response that wasn't JSON:\n" + responseBody, e);
				}
			}

			IOException exception = toException(responseCode, responseBody);
			// Endpoint moved? Try the other documented path before giving up.
			boolean endpointMissing = responseCode == HttpURLConnection.HTTP_NOT_FOUND
					|| responseCode == 405 /* METHOD_NOT_FOUND */;
			if (endpointMissing && i < relativePaths.length - 1) {
				Log.w(TAG, "Ely.by does not serve " + relativePaths[i] + ", trying the fallback endpoint");
				lastException = exception;
				continue;
			}
			throw exception;
		}
		// Unreachable unless every candidate path 404'd, in which case the last error is the useful one
		throw lastException != null ? lastException : new IOException("Ely.by auth server did not answer");
	}

	/** Turns an error response into an exception carrying a message that is worth showing to the user. */
	@NonNull
	private IOException toException(int responseCode, String responseBody) {
		String error = null, errorMessage = null;
		try {
			JSONObject json = new JSONObject(responseBody);
			error = json.optString("error", null);
			errorMessage = json.optString("errorMessage", null);
		} catch (JSONException ignored) {
			// Not every error is JSON, e.g. a reverse proxy complaining
		}
		if (responseCode == 429) {
			return new ElyByAuthException("Ely.by is rate limiting this request, wait a moment and try again.", error);
		}
		if (errorMessage != null && errorMessage.contains(TWO_FACTOR_HINT)) {
			// Documented way of passing a TOTP code: append it to the password with a colon
			return new ElyByAuthException(R.string.elyby_2fa_required, error);
		}
		if (errorMessage != null) return new ElyByAuthException(errorMessage, error);
		if (error != null) return new ElyByAuthException(error, error);
		return new IOException("Ely.by returned HTTP " + responseCode);
	}

	/** Yggdrasil accepts {@code password:token} for accounts protected by two factor auth. */
	@NonNull
	private static String withTotpSuffix(@Nullable String password, @Nullable String totpCode) {
		if (totpCode == null || totpCode.trim().isEmpty()) return password == null ? "" : password;
		return (password == null ? "" : password) + ":" + totpCode.trim();
	}

	/** Adds the dashes Mojang-style UUIDs are stored with inside this launcher. */
	@NonNull
	public static String dashifyUuid(@NonNull String dashlessUuid) {
		if (dashlessUuid.length() == 32 && !dashlessUuid.contains("-")) {
			return dashlessUuid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
					"$1-$2-$3-$4-$5");
		}
		return dashlessUuid;
	}

	private static boolean isValidToken(@Nullable String token) {
		return token != null && !token.isEmpty() && !token.equals("0");
	}

	private static String readBody(HttpURLConnection conn, int responseCode) throws IOException {
		InputStream is = null;
		try {
			is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
			if (is == null) return "";
			return Tools.read(is);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	/** All Yggdrasil endpoints are JSON POSTs, so they all get the same treatment. */
	private static void setCommonProperties(HttpURLConnection conn, String payload) {
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "application/json");
		conn.setRequestProperty("charset", "utf-8");
		conn.setRequestProperty("Content-Length", Integer.toString(payload.getBytes(StandardCharsets.UTF_8).length));
		try {
			conn.setRequestMethod("POST");
		} catch (java.net.ProtocolException e) {
			Log.e(TAG, e.toString());
		}
		conn.setUseCaches(false);
		conn.setDoInput(true);
		conn.setDoOutput(true);
	}

	/** Wrapper to ease notifying the listener */
	private void notifyProgress(@Nullable ProgressListener listener, int step) {
		if (listener != null) {
			Tools.runOnUiThread(() -> listener.onLoginProgress(step));
		}
		ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_ELYBY, step * 50);
	}
}
