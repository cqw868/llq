package de.baumann.browser.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridView;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.activity.Settings_ProfileList;
import de.baumann.browser.activity.Settings_Profile;
import de.baumann.browser.browser.AdBlock;
import de.baumann.browser.dialogs.CustomHostsDialog;
import de.baumann.browser.dialogs.CustomRedirectsDialog;
import de.baumann.browser.preferences.BasePreferenceFragment;
import de.baumann.browser.view.GridAdapter;
import de.baumann.browser.view.GridItem;

public class Fragment_settings_Privacy extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preference_privacy, rootKey);
        Context context = getContext();
        assert context != null;
        initSummary(getPreferenceScreen());
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        Preference sp_ad_block = findPreference("sp_ad_block");
        assert sp_ad_block != null;
        sp_ad_block.setSummary(getString(R.string.setting_summary_adblock) + "\n\n" + AdBlock.getHostsDate(getContext()));

        Preference settings_profile = findPreference("settings_profile");
        assert settings_profile != null;
        settings_profile.setOnPreferenceClickListener(preference -> {

            GridItem item_01 = new GridItem(getString(R.string.setting_title_profiles_trusted), R.drawable.icon_profile_trusted);
            GridItem item_02 = new GridItem( getString(R.string.setting_title_profiles_standard), R.drawable.icon_profile_standard);
            GridItem item_03 = new GridItem( getString(R.string.setting_title_profiles_protected), R.drawable.icon_profile_protected);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            View dialogView = View.inflate(context, R.layout.dialog_menu, null);
            CardView cardView = dialogView.findViewById(R.id.albumCardView);
            cardView.setVisibility(View.GONE);
            builder.setTitle(R.string.setting_title_profiles_edit);
            builder.setIcon(R.drawable.icon_edit);
            builder.setView(dialogView);
            AlertDialog dialog = builder.create();
            dialog.show();

            Objects.requireNonNull(dialog.getWindow()).setGravity(Gravity.BOTTOM);
            GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
            final List<GridItem> gridList = new LinkedList<>();
            gridList.add(gridList.size(), item_01);
            gridList.add(gridList.size(), item_02);
            gridList.add(gridList.size(), item_03);
            GridAdapter gridAdapter = new GridAdapter(context, gridList);
            menu_grid.setAdapter(gridAdapter);
            gridAdapter.notifyDataSetChanged();
            menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                switch (position) {
                    case 0:
                        sp.edit().putString("profileToEdit", "profileTrusted").apply();
                        dialog.cancel();
                        break;
                    case 1:
                        sp.edit().putString("profileToEdit", "profileStandard").apply();
                        dialog.cancel();
                        break;
                    case 2:
                        sp.edit().putString("profileToEdit", "profileProtected").apply();
                        dialog.cancel();
                        break;
                }
                Intent intent = new Intent(getActivity(), Settings_Profile.class);
                requireActivity().startActivity(intent);
            });
            return false;
        });

        Preference edit_trusted = findPreference("edit_trusted");
        assert edit_trusted != null;
        edit_trusted.setOnPreferenceClickListener(preference -> {
            sp.edit().putString("listToLoad", "trusted").apply();
            Intent intent = new Intent(getActivity(), Settings_ProfileList.class);
            requireActivity().startActivity(intent);
            return false;
        });
        Preference edit_standard = findPreference("edit_standard");
        assert edit_standard != null;
        edit_standard.setOnPreferenceClickListener(preference -> {
            sp.edit().putString("listToLoad", "standard").apply();
            Intent intent = new Intent(getActivity(), Settings_ProfileList.class);
            requireActivity().startActivity(intent);
            return false;
        });
        Preference edit_protected = findPreference("edit_protected");
        assert edit_protected != null;
        edit_protected.setOnPreferenceClickListener(preference -> {
            sp.edit().putString("listToLoad", "protected").apply();
            Intent intent = new Intent(getActivity(), Settings_ProfileList.class);
            requireActivity().startActivity(intent);
            return false;
        });
        Preference custom_redirects = findPreference("custom_redirects");
        assert custom_redirects != null;
        custom_redirects.setOnPreferenceClickListener(preference -> {
            CustomRedirectsDialog dialog = new CustomRedirectsDialog();
            dialog.show(getChildFragmentManager(), null);
            return false;
        });
        Preference custom_adblock = findPreference("custom_adblock");
        assert custom_adblock != null;
        custom_adblock.setOnPreferenceClickListener(preference -> {
            CustomHostsDialog dialog = new CustomHostsDialog();
            dialog.show(getChildFragmentManager(), null);
            return false;
        });
        Preference refreshHosts = findPreference("refreshHosts");
        assert refreshHosts != null;
        refreshHosts.setOnPreferenceClickListener(preference -> {
            AdBlock.downloadHosts(getContext());
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
    public void onSharedPreferenceChanged(final SharedPreferences sp, String key) {
        assert key != null;
        if (key.equals("sp_ad_block") || key.equals("ab_hosts") || key.equals("custom_adblock")) {
            AdBlock.downloadHosts(getActivity());
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
