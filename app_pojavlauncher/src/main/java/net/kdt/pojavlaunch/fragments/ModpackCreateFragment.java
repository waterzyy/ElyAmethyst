package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoUsableProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasUsableProfile;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.MineButton;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;

public class ModpackCreateFragment extends Fragment {
    public static final String TAG = "ModpackCreateFragment";
    public ModpackCreateFragment() {
        super(R.layout.fragment_create_modpack_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.button_browse_modpacks).setOnClickListener(v -> {
            tryInstall(SearchModFragment.class, SearchModFragment.TAG);
        });
        view.findViewById(R.id.button_import_modpack).setOnClickListener(v -> {
            Activity launcheractivity = requireActivity();
            if (!(launcheractivity instanceof LauncherActivity))
                    throw new IllegalStateException("Cannot import modpack without LauncherActivity");
            if(ProgressLayout.hasProcesses()){
                Toast.makeText(launcheractivity, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                return;
            }
            ((LauncherActivity) launcheractivity).modpackImportLauncher.launch(null);
        });;
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        // Installing a modpack is downloading and unpacking files, which an offline or an Ely.by
        // account is perfectly able to do
        if(!hasUsableProfile()){
            hasNoUsableProfileDialog(requireActivity());
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }
}
