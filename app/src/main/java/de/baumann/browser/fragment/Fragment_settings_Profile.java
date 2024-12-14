package de.baumann.browser.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.browser.BannerBlock;
import de.baumann.browser.preferences.BasePreferenceFragment;

public class Fragment_settings_Profile extends BasePreferenceFragment  implements SharedPreferences.OnSharedPreferenceChangeListener  {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        assert context != null;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String profile = sp.getString("profileToEdit", "profileStandard");
        getPreferenceScreen();

        switch (Objects.requireNonNull(profile)) {
            case "profileStandard":
                setPreferencesFromResource(R.xml.preference_profile_standard, rootKey);
                PreferenceManager.setDefaultValues(context, R.xml.preference_profile_standard, false);
                break;
            case "profileTrusted":
                setPreferencesFromResource(R.xml.preference_profile_trusted, rootKey);
                PreferenceManager.setDefaultValues(context, R.xml.preference_profile_trusted, false);
                break;
            case "profileProtected":
                setPreferencesFromResource(R.xml.preference_profile_protected, rootKey);
                PreferenceManager.setDefaultValues(context, R.xml.preference_profile_protected, false);
                break;
        }
        initSummary(getPreferenceScreen());
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sp, String key) {
        updatePrefSummary(findPreference(key));
        String profile = sp.getString("profileToEdit", "profileStandard");
        if (key.equals(profile + "_sp_deny_cookie_banners")) {
            if (sp.getBoolean(profile + "_sp_deny_cookie_banners",false)) BannerBlock.downloadBanners(getActivity());
        }
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
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            if (Objects.requireNonNull(p.getTitle()).toString().toLowerCase().contains("password")) {
                p.setSummary("******");
            } else {
                if (p.getSummaryProvider() == null) p.setSummary(editTextPref.getText());
            }
        }
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