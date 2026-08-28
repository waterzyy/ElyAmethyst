package net.kdt.pojavlaunch.authenticator.elyby;

/**
 * Every <a href="https://ely.by">Ely.by</a> endpoint this launcher talks to lives here, so a
 * server-side reorganization only ever needs a single change.
 * <p>
 * Ely.by runs a Yggdrasil (the protocol of the former {@code authserver.mojang.com}) compatible
 * authentication service. Accounts are authenticated with an Ely.by nickname or e-mail instead of
 * a Microsoft account, which is what lets people with an Ely.by profile play without owning
 * Minecraft Java Edition, and their access tokens are long lived unlike Mojang's old ones.
 */
public final class ElyByConstants {
	private ElyByConstants() {}

	/** Root of the Ely.by authentication service. */
	public static final String AUTH_SERVER_ROOT = "https://authserver.ely.by";

	/**
	 * Paths (relative to {@link #AUTH_SERVER_ROOT}) of the Yggdrasil {@code authenticate}
	 * endpoint, in the order they are tried.
	 * <p>
	 * {@code /authserver/authenticate} mirrors Mojang's legacy authserver layout, which is what
	 * authlib-injector based clients speak, while {@code /auth/authenticate} is the path
	 * currently advertised in the Ely.by docs. Both are served by the same backend, and the
	 * second one is only attempted when the first answers "not found", so an endpoint migration on
	 * their side does not lock our users out.
	 */
	public static final String[] AUTHENTICATE_PATHS = {"/authserver/authenticate", "/auth/authenticate"};

	/** Paths (relative to {@link #AUTH_SERVER_ROOT}) of the Yggdrasil {@code refresh} endpoint. */
	public static final String[] REFRESH_PATHS = {"/authserver/refresh", "/auth/refresh"};

	/**
	 * API root handed to authlib-injector as {@code -javaagent:authlib-injector.jar=<root>}.
	 * <p>
	 * This is what actually makes Ely.by skins, capes and online-mode server joins work: it
	 * re-points the authlib bundled inside Minecraft at Ely.by instead of at Mojang.
	 * Ely.by redirects the short {@code ely.by} alias documented on their side to this very root,
	 * pinning the resolved URL saves a redirect round-trip on every launch.
	 */
	public static final String AUTHLIB_INJECTOR_API_ROOT = "https://account.ely.by/api/authlib-injector";

	/**
	 * Skin system ("Chrly") root, used to fetch the player head shown in the account spinner.
	 * Docs: <a href="https://docs.ely.by/en/skins-system.html">docs.ely.by/en/skins-system.html</a>
	 */
	public static final String SKIN_SYSTEM_ROOT = "https://skinsystem.ely.by";

	/** Builds the URL of the flat 64x64 skin texture of the given player. */
	public static String skinTextureUrl(String username) {
		return SKIN_SYSTEM_ROOT + "/skins/" + username + ".png";
	}

	/**
	 * Substring of the errorMessage Ely.by replies with (status 401) when the account is
	 * protected by two factor auth and no TOTP code was supplied.
	 */
	public static final String TWO_FACTOR_HINT = "Account protected with two factor auth.";

	/**
	 * How long an access token is considered usable before it is proactively refreshed.
	 * Ely.by does not publish a hard lifetime for launcher tokens, so this stays well inside the
	 * usual validity window instead of gambling on launch day.
	 */
	public static final long TOKEN_LIFETIME_MILLIS = 7L * 24 * 60 * 60 * 1000;

	/** The {@code agent.name} every Yggdrasil request advertises. */
	public static final String AGENT_NAME = "Minecraft";

	/** The {@code agent.version} Ely.by expects for modern clients. */
	public static final int AGENT_VERSION = 1;
}
