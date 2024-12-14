package de.baumann.browser.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Objects;

import de.baumann.browser.R;
import de.baumann.browser.objects.CustomHostsHelper;
import de.baumann.browser.objects.CustomRedirect;
import de.baumann.browser.unit.BrowserUnit;
import de.baumann.browser.unit.HelperUnit;
import de.baumann.browser.view.AdapterCustomHost;
import de.baumann.browser.view.NinjaToast;

public class CustomHostsDialog extends DialogFragment {
    AdapterCustomHost adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = View.inflate(getContext(), R.layout.custom_redirects_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.redirects_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());

        ArrayList<CustomRedirect> redirects = new ArrayList<>();
        try {
            redirects = CustomHostsHelper.getRedirects(sp);
        } catch (JSONException e) {
            Log.e("Redirects parsing", e.toString());
        }

        adapter = new AdapterCustomHost(redirects, requireContext());
        recyclerView.setAdapter(adapter);

        builder.setTitle(R.string.custom_domains);
        builder.setIcon(R.drawable.icon_adblock);
        builder.setPositiveButton(R.string.app_ok, null);
        builder.setNeutralButton(R.string.create_new, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        HelperUnit.setupDialog(requireContext(), dialog);
        // when the button to create a new entry is clicked, don't close the dialog
        dialog.setOnShowListener(dI -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            b.setOnClickListener(view -> showCreateNewDialog());
        });
        return dialog;
    }

    private void showCreateNewDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = View.inflate(getContext(), R.layout.create_new_redirect, null);
        TextInputEditText source = dialogView.findViewById(R.id.source);
        source.setVisibility(View.GONE);
        TextInputEditText target = dialogView.findViewById(R.id.target);

        builder.setTitle("Custom domains");
        builder.setIcon(R.drawable.icon_adblock);
        builder.setNegativeButton(R.string.app_cancel, null);
        builder.setPositiveButton(R.string.app_ok, ((dialogInterface, i) -> {
            String targetText = Objects.requireNonNull(target.getText()).toString();
            if (!BrowserUnit.isURL(targetText)) {
                NinjaToast.show(getContext(), R.string.toast_invalid_domain);
            } else {
                adapter.addRedirect(new CustomRedirect("domain", targetText));
                try {
                    CustomHostsHelper.saveRedirects(requireContext(), adapter.getRedirects());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }));
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(requireContext(), dialog);
    }
}
