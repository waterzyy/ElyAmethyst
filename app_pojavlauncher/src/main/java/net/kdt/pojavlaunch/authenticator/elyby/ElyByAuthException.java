package net.kdt.pojavlaunch.authenticator.elyby;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * An Ely.by login failure that is worth showing to the user verbatim, as opposed to a
 * transport level error that should be reported with its stack trace.
 * Yggdrasil servers reply with an {@code errorMessage} intended for human eyes, we just pass it through.
 */
public class ElyByAuthException extends RuntimeException {
	@StringRes
	private final int mMessageRes;
	@Nullable
	private final String mYggdrasilError;

	public ElyByAuthException(String message, @Nullable String yggdrasilError) {
		super(message);
		mMessageRes = 0;
		mYggdrasilError = yggdrasilError;
	}

	public ElyByAuthException(@StringRes int messageRes, @Nullable String yggdrasilError) {
		mMessageRes = messageRes;
		mYggdrasilError = yggdrasilError;
	}

	public ElyByAuthException(Throwable cause, @StringRes int messageRes, @Nullable String yggdrasilError) {
		super(cause);
		mMessageRes = messageRes;
		mYggdrasilError = yggdrasilError;
	}

	/** @return the message to display, resolving localization when needed */
	public String getMessage(Context context) {
		return mMessageRes != 0 ? context.getString(mMessageRes) : getMessage();
	}

	/** @return the raw Yggdrasil error identifier, eg {@code ForbiddenOperationException}, may be null */
	@Nullable
	public String getYggdrasilError() {
		return mYggdrasilError;
	}
}
