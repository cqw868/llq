package de.baumann.browser.view;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;

import java.util.ArrayList;

import de.baumann.browser.R;
import de.baumann.browser.objects.CustomHostsHelper;
import de.baumann.browser.objects.CustomRedirect;
import de.baumann.browser.unit.HelperUnit;

public class AdapterCustomHost extends RecyclerView.Adapter<RedirectsViewHolder> {
    final private ArrayList<CustomRedirect> redirects;
    private final Context context;

    public AdapterCustomHost(ArrayList<CustomRedirect> redirects, Context context) {
        super();
        this.redirects = redirects;
        this.context = context;
    }

    @NonNull
    @Override
    public RedirectsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.custom_hosts_row, parent, false);

        return new RedirectsViewHolder(view);

    }

    @Override
    public void onBindViewHolder(@NonNull RedirectsViewHolder holder, int position) {
        CustomRedirect current = redirects.get(position);
        TextView target = holder.itemView.findViewById(R.id.redirect_target);
        ImageView remove = holder.itemView.findViewById(R.id.remove_redirect);
        target.setText(current.getTarget());
        remove.setOnClickListener((iV) -> {
            MaterialAlertDialogBuilder builderSubMenu = new MaterialAlertDialogBuilder(context);
            builderSubMenu.setTitle(R.string.menu_delete);
            builderSubMenu.setMessage(R.string.hint_database);
            builderSubMenu.setIcon(R.drawable.icon_delete);
            builderSubMenu.setPositiveButton(R.string.app_ok, (dialog2, whichButton) -> {
                redirects.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, getItemCount());
                try {
                    CustomHostsHelper.saveRedirects(context, redirects);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
            builderSubMenu.setNegativeButton(R.string.app_cancel, (dialog2, whichButton) -> builderSubMenu.setCancelable(true));
            Dialog dialogSubMenu = builderSubMenu.create();
            dialogSubMenu.show();
            HelperUnit.setupDialog(context, dialogSubMenu);
        });
    }

    @Override
    public int getItemCount() {
        return redirects.size();
    }

    public ArrayList<CustomRedirect> getRedirects() {
        return redirects;
    }

    public void addRedirect(CustomRedirect redirect) {
        redirects.add(redirect);
        notifyItemInserted(getItemCount() - 1);
    }
}

