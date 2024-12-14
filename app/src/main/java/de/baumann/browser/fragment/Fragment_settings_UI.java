package de.baumann.browser.fragment;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.preferences.BasePreferenceFragment;
import de.baumann.browser.unit.HelperUnit;

public class Fragment_settings_UI extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int REQUEST_CODE_ASK_PERMISSIONS_4 = 1234567;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preference_ui, rootKey);
        Context context = getContext();
        assert context != null;
        initSummary(getPreferenceScreen());

        Preference settings_background = findPreference("sp_tabBackground");
        assert settings_background != null;
        settings_background.setOnPreferenceClickListener(preference -> {
            if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int notificationAllowed = requireActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
                if (notificationAllowed != PackageManager.PERMISSION_GRANTED) {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
                    builder.setIcon(R.drawable.icon_alert);
                    builder.setMessage(R.string.app_permission);
                    builder.setTitle(R.string.app_permission_notification);
                    builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> requireActivity().requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_ASK_PERMISSIONS_4));
                    builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    HelperUnit.setupDialog(requireActivity(), dialog);
                }
            }
            return false;
        });
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    private void updatePrefSummary(Preference p) {

        Context context = getContext();
        assert context != null;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useDynamicColor = sp.getBoolean("useDynamicColor", false);
        ListPreference theme;
        theme = findPreference("sp_theme");
        assert theme != null;
        theme.setEnabled(useDynamicColor);


        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int notificationAllowed = requireActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
            if (notificationAllowed != PackageManager.PERMISSION_GRANTED) {
                Preference sp_tabBackground;
                sp_tabBackground = findPreference("sp_tabBackground");
                assert sp_tabBackground != null;
                theme.setDefaultValue(false);
            }
        }


        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            if (p.getSummaryProvider() == null) p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            if (Objects.requireNonNull(p.getTitle()).toString().toLowerCase().contains("password")) {
                p.setSummary("******");
            } else {
                p.setSummary(editTextPref.getText());
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sp, String key) {
        assert key != null;
        if (key.equals("sp_exit") || key.equals("sp_toggle") || key.equals("sp_add") || key.equals("sp_theme") || key.equals("useOLED")
                || key.equals("nav_position") || key.equals("sp_hideOmni") || key.equals("start_tab") || key.equals("sp_hideSB")
                || key.equals("overView_place") || key.equals("overView_hide") || key.equals("hideToolbar") || key.equals("useDynamicColor")) {
            sp.edit().putInt("restart_changed", 1).apply();
        }
        updatePrefSummary(findPreference(key));
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);
    }
}
