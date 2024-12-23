/*
    This file is part of the browser WebApp.

    browser WebApp is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    browser WebApp is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with the browser webview app.

    If not, see <http://www.gnu.org/licenses/>.
 */

package de.baumann.browser.unit;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Environment.DIRECTORY_DOCUMENTS;

import static androidx.constraintlayout.motion.utils.Oscillator.TAG;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.Patterns;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

import de.baumann.browser.R;
import de.baumann.browser.browser.List_protected;
import de.baumann.browser.browser.List_standard;
import de.baumann.browser.browser.List_trusted;
import de.baumann.browser.database.Record;
import de.baumann.browser.database.RecordAction;
import de.baumann.browser.view.NinjaToast;

public class BackupUnit {

    public static final int PERMISSION_REQUEST_CODE = 123;
    private static final String BOOKMARK_TYPE = "<DT><A HREF=\"{url}\" ADD_DATE=\"{time}\">{title}</A>";
    private static final String BOOKMARK_TYPE_SIMPLE = "<DT><A HREF=\"{url}\">{title}</A>";
    private static final String BOOKMARK_TITLE = "{title}";
    private static final String BOOKMARK_URL = "{url}";
    private static final String BOOKMARK_TIME = "{time}";

    public static boolean checkPermissionStorage(Context context) {
        if (SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        else {
            int result = ContextCompat.checkSelfPermission(context, READ_EXTERNAL_STORAGE);
            int result1 = ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE);
            return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED; }
    }

    public static void requestPermission(Activity activity) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setIcon(R.drawable.icon_alert);
        builder.setTitle(R.string.app_warning);
        builder.setMessage(R.string.app_permission);
        builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {
            dialog.cancel();
            if (SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", activity.getPackageName())));
                    activity.startActivity(intent); }
                catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivity(intent); }}
            else {
                //below android 11
                ActivityCompat.requestPermissions(activity, new String[]{WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE); }
        });
        builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(activity, dialog);
    }

    public static void makeBackupDir() {
        File backupDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//");
        boolean wasSuccessful = backupDir.mkdirs();
        if (!wasSuccessful) System.out.println("was not successful.");
    }

    public static void backupData(Activity context, int i) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            //Background work here
            switch (i) {
                case 1:
                    exportList(context, 1);
                    break;
                case 3:
                    exportList(context, 3);
                    break;
                case 4:
                    exportBookmarks(context);
                    break;
                case 5:
                    exportBookmarksSimple(context);
                    break;
                default:
                    exportList(context, 2);
                    break;
            }
            handler.post(() -> {
                //UI Thread work here
                NinjaToast.show(context, context.getString(R.string.app_done));
            });
        });
    }

    public static void restoreData(Activity context, int i) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            //Background work here
            switch (i) {
                case 1:
                    importList(context, 1);
                    break;
                case 3:
                    importList(context, 3);
                    break;
                case 4:
                    importBookmarks(context);
                    break;
                case 5:
                    importBookmarksSimple(context);
                    break;
                default:
                    importList(context, 2);
                    break;
            }
            handler.post(() -> {
                //UI Thread work here
                NinjaToast.show(context, context.getString(R.string.app_done));
            });
        });
    }

    public static void exportList(Context context, int i) {
        RecordAction action = new RecordAction(context);
        List<String> list;
        String filename;
        action.open(false);
        switch (i) {
            case 1:
                list = action.listDomains(RecordUnit.TABLE_TRUSTED);
                filename = "list_trusted.txt";
                break;
            case 3:
                list = action.listDomains(RecordUnit.TABLE_STANDARD);
                filename = "list_standard.txt";
                break;
            default:
                list = action.listDomains(RecordUnit.TABLE_PROTECTED);
                filename = "list_protected.txt";
                break;
        }
        action.close();
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//" + filename);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            for (String domain : list) {
                writer.write(domain);
                writer.newLine();
            }
            writer.close();
            String wasSuccessful = file.getAbsolutePath();
            if (wasSuccessful.isEmpty()) System.out.println("was not successful."); }
        catch (Exception ignored) { }
    }

    public static void importList(Context context, int i) {
        try {
            String filename;
            List_trusted listTrusted = null;
            List_protected listProtected = null;
            List_standard listStandard = null;
            switch (i) {
                case 1:
                    listTrusted = new List_trusted(context);
                    filename = "list_trusted.txt";
                    break;
                case 3:
                    listStandard = new List_standard(context);
                    filename = "list_standard.txt";
                    break;
                default:
                    listProtected = new List_protected(context);
                    filename = "list_protected.txt";
                    break;
            }
            File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//" + filename);
            RecordAction action = new RecordAction(context);
            action.open(true);

            switch (i) {
                case 1:
                    listTrusted.clearDomains();
                    break;
                case 3:
                    listStandard.clearDomains();
                    break;
                default:
                    listProtected.clearDomains();
                    break;
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                switch (i) {
                    case 1:
                        if (!action.checkDomain(line, RecordUnit.TABLE_TRUSTED)) listTrusted.addDomain(line);
                        break;
                    case 3:
                        if (!action.checkDomain(line, RecordUnit.TABLE_STANDARD)) listStandard.addDomain(line);
                        break;
                    default:
                        if (!action.checkDomain(line, RecordUnit.TABLE_PROTECTED)) listProtected.addDomain(line);
                        break;
                }
            }
            reader.close();
            action.close(); }
        catch (Exception e) {
            Log.w("browser", "Error reading file", e);
        }
    }

    public static void exportBookmarks(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(false);
        List<Record> list = action.listBookmark(context, false, 0);
        action.close();
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//list_bookmarks.html");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            for (Record record : list) {
                String type = BOOKMARK_TYPE;
                type = type.replace(BOOKMARK_TITLE, record.getTitle());
                type = type.replace(BOOKMARK_URL, record.getURL());
                type = type.replace(BOOKMARK_TIME, String.valueOf(record.getIconColor() + (long) (record.getDesktopMode() ? 16 : 0) + (long) (record.getNightMode() ? 32 : 0)));
                writer.write(type);
                writer.newLine();
            }
            writer.close();
            String wasSuccessful = file.getAbsolutePath();
            if (wasSuccessful.isEmpty()) {System.out.println("was not successful.");}
        } catch (Exception ignored) { }
    }

    public static void exportBookmarksSimple(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(false);
        List<Record> list = action.listBookmark(context, false, 0);
        action.close();
        File fileTxt = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//list_bookmarks_simple.html");

        try {
            BufferedWriter writerTxt = new BufferedWriter(new FileWriter(fileTxt, false));
            for (Record record : list) {
                String type = BOOKMARK_TYPE_SIMPLE;
                type = type.replace(BOOKMARK_TITLE, record.getTitle());
                type = type.replace(BOOKMARK_URL, record.getURL());
                writerTxt.write(type);
                writerTxt.newLine();
            }
            writerTxt.close();
            String wasSuccessfulTxt = fileTxt.getAbsolutePath();
            if (wasSuccessfulTxt.isEmpty()) {System.out.println("was not successful."); }
        } catch (Exception ignored) { }
    }

    public static void importBookmarks(Context context) {
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//list_bookmarks.html");
        List<Record> list = new ArrayList<>();
        try {
            RecordAction action = new RecordAction(context);
            action.open(true);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!((line.startsWith("<dt><a ") && line.endsWith("</a>")) || (line.startsWith("<DT><A ") && line.endsWith("</A>")))) {
                    continue; }
                String title = getBookmarkTitle(line);
                String url = getBookmarkURL(line);
                long date = getBookmarkDate(line);
                if (date > 123)
                    date = 11;
                //if no color defined yet set it red (123 is max: 11 for color + 16 for desktop mode + 32 for List_trusted + 64 for List_standard Content
                if (title.trim().isEmpty() || url.trim().isEmpty()) {
                    continue; }
                Record record = new Record();
                record.setTitle(title);
                record.setURL(url);
                record.setIconColor(date & 15);
                record.setDesktopMode((date & 16) == 16);
                record.setNightMode(!((date & 32) == 32));

                if (!action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) list.add(record);}
            reader.close();
            list.sort(Comparator.comparing(Record::getTitle));
            for (Record record : list) {action.addBookmark(record);}
            action.close();
        } catch (Exception ignored) { }
        list.size();
    }

    public static void importBookmarksSimple(Context context) {
        File file = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup//list_bookmarks_simple.html");
        List<Record> list = new ArrayList<>();
        try {
            RecordAction action = new RecordAction(context);
            action.open(true);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!((line.startsWith("<dt><a ") && line.endsWith("</a>")) || (line.startsWith("<DT><A ") && line.endsWith("</A>")))) {
                    continue; }
                String title = getBookmarkTitle(line);
                String url = extractLinks(line);
                //if no color defined yet set it red (123 is max: 11 for color + 16 for desktop mode + 32 for List_trusted + 64 for List_standard Content
                if (title.trim().isEmpty() || url.trim().isEmpty()) {
                    continue; }
                Record record = new Record();
                record.setTitle(title);
                record.setURL(url);
                record.setIconColor(1);
                record.setDesktopMode(false);
                record.setNightMode(false);

                if (!action.checkUrl(url, RecordUnit.TABLE_BOOKMARK)) list.add(record);}
            reader.close();
            list.sort(Comparator.comparing(Record::getTitle));
            for (Record record : list) {action.addBookmark(record);}
            action.close();
        } catch (Exception ignored) { }
        list.size();
    }
    private static long getBookmarkDate(String line) {
        for (String string : line.split(" +")) {
            if (string.startsWith("ADD_DATE=\"")) {
                int index = string.indexOf("\">");
                return Long.parseLong(string.substring(10, index)); }
        }
        return 0;
    }

    private static String getBookmarkTitle(String line) {
        // Remove last </a>
        line = line.substring(0, line.length() - 4);
        int index = line.lastIndexOf(">");
        return line.substring(index + 1);
    }

    private static String getBookmarkURL(String line) {
        // Remove href=\" and \"
        for (String string : line.split(" +")) {
            if (string.startsWith("href=\"") || string.startsWith("HREF=\"")) return string.substring(6, string.length() - 1);
        }
        return "";
    }

    public static String extractLinks(String text) {
        List<String> links = new ArrayList<>();
        String link = null;
        Matcher m = Patterns.WEB_URL.matcher(text);
        while (m.find()) {
            String url = m.group();
            Log.d(TAG, "URL extracted: " + url);
            if (links.isEmpty()) {
                links.add(url);
                link = url;
            }
        }
        return link;
    }
}