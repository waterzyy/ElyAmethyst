package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

/**
 * Login with an [Ely.by](https://ely.by) account.
 * <p>
 * The credentials are only handed over to {@link net.kdt.pojavlaunch.authenticator.elyby.ElyByBackgroundLogin},
 * which performs the request in the background and never stores the password. Ely.by accounts do
 * not require a Minecraft purchase, and get their skins plus online-mode server authentication
 * through authlib-injector.
 */
public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private EditText mTotpEditText;

    public ElyByLoginFragment(){
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.elyby_login_edit_username);
        mPasswordEditText = view.findViewById(R.id.elyby_login_edit_password);
        mTotpEditText = view.findViewById(R.id.elyby_login_edit_totp);

        view.findViewById(R.id.elyby_login_button).setOnClickListener(v -> {
            if(!checkEditText()) {
                Context context = v.getContext();
                Tools.dialog(context, context.getString(R.string.global_error),
                        context.getString(R.string.elyby_login_missing_fields));
                return;
            }

            ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_TODO, new String[]{
                    mUsernameEditText.getText().toString().trim(),
                    mPasswordEditText.getText().toString(),
                    mTotpEditText.getText().toString().trim() });

            mPasswordEditText.getText().clear();
            mTotpEditText.getText().clear();

            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        });
    }

    /** @return Whether the fields are eligible to make an authentication request with */
    private boolean checkEditText(){
        return !mUsernameEditText.getText().toString().trim().isEmpty()
                && !mPasswordEditText.getText().toString().isEmpty();
    }
}
