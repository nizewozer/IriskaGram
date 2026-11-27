/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.LaunchActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UpdaterUtils {

    @SuppressLint("StaticFieldLeak")
    private static AlertDialog dialog;

    private static final String GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/nizewozer/IriskaGram/releases/latest";
    private static volatile String activeDownloadFile = null;
    private static volatile float activeDownloadProgress = 0f;
    private static volatile boolean cancelRequested = false;

    public static boolean isDownloadingGithubApk(String fileName) {
        return fileName != null && fileName.equals(activeDownloadFile);
    }

    public static float getGithubDownloadProgress(String fileName) {
        return isDownloadingGithubApk(fileName) ? activeDownloadProgress : 0f;
    }

    public static void cancelGithubDownload() {
        cancelRequested = true;
    }

    public static void downloadGithubApk(int account, TLRPC.TL_help_appUpdate update) {
        if (update == null || update.document == null || TextUtils.isEmpty(update.url)) {
            return;
        }
        final String fileName = FileLoader.getAttachFileName(update.document);
        if (isDownloadingGithubApk(fileName)) {
            return;
        }
        final File destFile = FileLoader.getInstance(account).getPathToAttach(update.document, true);
        if (destFile == null) {
            return;
        }
        if (destFile.exists()) {
            AndroidUtilities.runOnUIThread(() ->
                    org.telegram.messenger.NotificationCenter.getInstance(account)
                            .postNotificationName(org.telegram.messenger.NotificationCenter.fileLoaded, fileName, destFile));
            return;
        }

        activeDownloadFile = fileName;
        activeDownloadProgress = 0f;
        cancelRequested = false;

        Utilities.globalQueue.postRunnable(() -> {
            File tmpFile = new File(destFile.getParentFile(), destFile.getName() + ".tmp");
            if (destFile.getParentFile() != null) {
                destFile.getParentFile().mkdirs();
            }
            HttpURLConnection conn = null;
            try {
                String currentUrl = update.url;
                for (int redirects = 0; redirects < 6; redirects++) {
                    conn = (HttpURLConnection) new URL(currentUrl).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", "IriskaGram");
                    int code = conn.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (TextUtils.isEmpty(loc)) break;
                        currentUrl = loc;
                    } else {
                        break;
                    }
                }
                if (conn == null || conn.getResponseCode() != 200) {
                    throw new IOException("HTTP error");
                }
                long total = conn.getContentLengthLong();
                if (total <= 0 && update.document.size > 0) {
                    total = update.document.size;
                }
                final long totalFinal = Math.max(1L, total);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tmpFile)) {
                    byte[] buf = new byte[16384];
                    long loaded = 0;
                    long lastNotify = 0;
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        if (cancelRequested) {
                            throw new IOException("Cancelled");
                        }
                        out.write(buf, 0, read);
                        loaded += read;
                        long now = System.currentTimeMillis();
                        if (now - lastNotify > 150) {
                            lastNotify = now;
                            final long loadedFinal = loaded;
                            activeDownloadProgress = Math.min(1f, loadedFinal / (float) totalFinal);
                            AndroidUtilities.runOnUIThread(() ->
                                    org.telegram.messenger.NotificationCenter.getInstance(account)
                                            .postNotificationName(org.telegram.messenger.NotificationCenter.fileLoadProgressChanged, fileName, loadedFinal, totalFinal));
                        }
                    }
                }
                if (!tmpFile.renameTo(destFile)) {
                    throw new IOException("Rename failed");
                }
                activeDownloadFile = null;
                activeDownloadProgress = 1f;
                AndroidUtilities.runOnUIThread(() ->
                        org.telegram.messenger.NotificationCenter.getInstance(account)
                                .postNotificationName(org.telegram.messenger.NotificationCenter.fileLoaded, fileName, destFile));
            } catch (Exception e) {
                FileLog.e(e);
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
                activeDownloadFile = null;
                activeDownloadProgress = 0f;
                AndroidUtilities.runOnUIThread(() ->
                        org.telegram.messenger.NotificationCenter.getInstance(account)
                                .postNotificationName(org.telegram.messenger.NotificationCenter.fileLoadFailed, fileName, 0));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    public static void getAppUpdate(Utilities.Callback2<TLRPC.TL_help_appUpdate, TLRPC.TL_error> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(GITHUB_LATEST_RELEASE_URL).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "IriskaGram");
                if (conn.getResponseCode() != 200) {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "GITHUB_HTTP_" + conn.getResponseCode();
                    callback.run(null, error);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject rel = new JSONObject(sb.toString());
                String tag = rel.optString("tag_name", "").replaceFirst("^[vV]", "").trim();
                String body = rel.optString("body", "");
                JSONArray assets = rel.optJSONArray("assets");
                JSONObject apkAsset = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            apkAsset = a;
                            break;
                        }
                    }
                }
                if (TextUtils.isEmpty(tag) || apkAsset == null) {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "NO_UPDATE_METADATA";
                    callback.run(null, error);
                    return;
                }

                TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
                update.id = (int) (rel.optLong("id", 1L) & 0x7fffffff);
                if (update.id <= 0) update.id = 1;
                update.version = tag;
                update.text = body;
                update.url = apkAsset.optString("browser_download_url", "");
                update.flags |= 2 | 4;

                TLRPC.TL_document doc = new TLRPC.TL_document();
                doc.id = apkAsset.optLong("id", update.id);
                doc.size = apkAsset.optLong("size", 0L);
                doc.dc_id = 1;
                doc.mime_type = "application/vnd.android.package-archive";
                TLRPC.TL_documentAttributeFilename attr = new TLRPC.TL_documentAttributeFilename();
                attr.file_name = apkAsset.optString("name", "IriskaGram.apk");
                doc.attributes.add(attr);
                update.document = doc;

                callback.run(update, null);
            } catch (Exception e) {
                FileLog.e(e);
                TLRPC.TL_error error = new TLRPC.TL_error();
                error.text = e.getMessage() != null ? e.getMessage() : "UPDATE_CHECK_FAILED";
                callback.run(null, error);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static TLRPC.TL_help_appUpdate parseUpdateResponse(TLRPC.messages_Messages res) {
        TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();

        int documentId = -1, stickerId = -1, textId = -1;

        for (TLRPC.Message msg : res.messages) {
            if (!(msg instanceof TLRPC.TL_message)) {
                continue;
            }
            if (msg.message.startsWith("update_test") && BadgesController.INSTANCE.isDeveloper()
                    || msg.message.startsWith("update_lite_beta") && BuildVars.isBetaApp() && BuildVars.IS_LITE_VERSION
                    || msg.message.startsWith("update_lite_stable") && !BuildVars.isBetaApp() && BuildVars.IS_LITE_VERSION
                    || msg.message.startsWith("update_beta") && BuildVars.isBetaApp() && !BuildVars.IS_LITE_VERSION
                    || msg.message.startsWith("update_stable") && !BuildVars.isBetaApp() && !BuildVars.IS_LITE_VERSION) {
                String[] split = msg.message.split("\n");

                if (split.length < 6) {
                    continue;
                }

                for (String line : split) {
                    String[] keyValue = line.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        switch (key) {
                            case "can_not_skip" ->
                                    update.can_not_skip = Boolean.parseBoolean(value);
                            case "version" -> update.version = value;
                            case "document" -> documentId = Integer.parseInt(value);
                            case "sticker" -> stickerId = Integer.parseInt(value);
                            case "text" -> textId = Integer.parseInt(value);
                        }
                    }
                }
                update.id = msg.id;
                break;
            }
        }

        for (TLRPC.Message msg : res.messages) {
            if (!(msg instanceof TLRPC.TL_message)) {
                continue;
            }
            if (msg.id == textId) {
                update.entities = msg.entities;
                update.text = msg.message;
                continue;
            }
            if (msg.media != null) {
                TLRPC.Document document = msg.media.getDocument();
                if (msg.id == documentId && !document.attributes.isEmpty() && document.attributes.get(0).file_name.endsWith(".apk")) {
                    update.document = document;
                    update.flags |= 2;
                    continue;
                }
                if (msg.id == stickerId && MessageObject.isStickerDocument(document)) {
                    update.sticker = document;
                    update.flags |= 8;
                }
            }
        }
        return update;
    }

    public static void installUpdate(Activity context, TLRPC.Document document) {
        if (context == null || document == null) {
            return;
        }
        if (XiaomiUtilities.isMIUI()) {
            AndroidUtilities.openForView(document, context);
            return;
        }
        var apk = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
        if (apk == null) {
            return;
        }
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        showInstallDialog(context);
        Utilities.globalQueue.postRunnable(() -> {
            var receiver = register(context, () -> {
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
            });
            installApk(context, apk);
            Intent intent = receiver.waitIntent();
            if (intent != null) {
                context.startActivity(intent);
            }
        });
    }

    private static void showInstallDialog(Activity context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 4, 4, 4, 4));

        StickerImageView imageView = new StickerImageView(context, UserConfig.selectedAccount);
        imageView.setStickerPackName("UtyaDuckFull");
        imageView.setStickerNum(0);
        imageView.getImageReceiver().setAutoRepeat(1);
        linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 24, 17, 0));

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(LocaleController.getString(R.string.UpdateInstalling));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 20, 17, 0));

        TextView textView2 = new TextView(context);
        textView2.setGravity(Gravity.CENTER);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView2.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        textView2.setText(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context) ?
                LocaleController.getString(R.string.UpdateInstallingRelaunch) :
                LocaleController.getString(R.string.UpdateInstallingNotification));
        linearLayout.addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 4, 17, 24));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(linearLayout);
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.show();
    }

    private static void installApk(Activity context, File apk) {
        var flag = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        var action = UpdaterUtils.class.getName();
        var intent = new Intent(action).setPackage(context.getPackageName());
        var pending = PendingIntent.getBroadcast(context, 0, intent, flag);

        var installer = context.getPackageManager().getPackageInstaller();
        var params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        try (PackageInstaller.Session session = installer.openSession(installer.createSession(params))) {
            try (OutputStream out = session.openWrite(apk.getName(), 0, apk.length());
                 FileInputStream in = new FileInputStream(apk)) {
                transfer(in, out);
            }
            session.commit(pending.getIntentSender());
        } catch (IOException e) {
            FileLog.e(e);
            handleInstallError(context, apk, e);
        }
    }

    private static void handleInstallError(Activity context, File apk, IOException e) {
        AndroidUtilities.runOnUIThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
        AlertsCreator.createSimpleAlert(context, LocaleController.getString(R.string.ErrorOccurred) + "\n" + e.getLocalizedMessage()).show();
        AndroidUtilities.openForView(apk, "install.apk", "application/vnd.android.package-archive", context, null, false);
    }

    private static void transfer(InputStream in, OutputStream out) throws IOException {
        int size = 8192;
        var buffer = new byte[size];
        int read;
        while ((read = in.read(buffer, 0, size)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private static InstallReceiver register(Context context, Runnable onSuccess) {
        var receiver = new InstallReceiver(context, ApplicationLoader.getApplicationId(), onSuccess);
        ContextCompat.registerReceiver(context, receiver, new IntentFilter(UpdaterUtils.class.getName()), ContextCompat.RECEIVER_NOT_EXPORTED);
        return receiver;
    }

    private static class InstallReceiver extends BroadcastReceiver {
        private final Context context;
        private final String packageName;
        private final Runnable onSuccess;
        private final CountDownLatch latch = new CountDownLatch(1);
        private Intent intent = null;

        private InstallReceiver(Context context, String packageName, Runnable onSuccess) {
            this.context = context;
            this.packageName = packageName;
            this.onSuccess = onSuccess;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())) {
                Uri data = i.getData();
                if (data == null || onSuccess == null) return;
                String pkg = data.getSchemeSpecificPart();
                if (pkg.equals(packageName)) {
                    onSuccess.run();
                    context.unregisterReceiver(this);
                }
                return;
            }
            handlePackageInstallerResult(i);
        }

        @SuppressWarnings("fallthrough")
        private void handlePackageInstallerResult(Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_INVALID);
            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    intent = IntentCompat.getParcelableExtra(i, Intent.EXTRA_INTENT, Intent.class);
                    break;
                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    handleFailure(i);
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_SUCCESS:
                default:
                    if (onSuccess != null) onSuccess.run();
                    context.unregisterReceiver(this);
            }
            latch.countDown();
        }

        private void handleFailure(Intent i) {
            int id = i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0);
            if (id > 0) {
                var installer = context.getPackageManager().getPackageInstaller();
                var info = installer.getSessionInfo(id);
                if (info != null) {
                    installer.abandonSession(info.getSessionId());
                }
            }
            if (context instanceof LaunchActivity) {
                ((LaunchActivity) context).showBulletin(factory -> factory.createErrorBulletin(LocaleController.formatString(R.string.UpdateFailedToInstall, i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE))));
            }
        }

        public Intent waitIntent() {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            return intent;
        }
    }

    public static class UpdateReceiver extends BroadcastReceiver {
        @SuppressWarnings("deprecation")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

            var packageName = context.getPackageName();
            var installer = context.getPackageManager().getInstallerPackageName(packageName);
            if (!packageName.equals(installer)) return;

            launchApp(context);
        }

        private void launchApp(Context context) {
            var startIntent = new Intent(context, LaunchActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)) {
                context.startActivity(startIntent);
            } else {
                showNotification(context, startIntent);
            }
        }

        private void showNotification(Context context, Intent startIntent) {
            var channel = new NotificationChannelCompat.Builder("updated", NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(LocaleController.getString(R.string.UpdateApp))
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .setSound(null, null)
                    .build();
            var notificationManager = NotificationManagerCompat.from(context);
            notificationManager.createNotificationChannel(channel);
            var pendingIntent = PendingIntent.getActivity(context, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notificationManager.notify(8732833,
                    new NotificationCompat.Builder(context, "updated")
                            .setSmallIcon(IconManager.getNotificationIcon())
                            .setColor(AppUtils.getNotificationColor())
                            .setShowWhen(false)
                            .setContentText(LocaleController.getString(R.string.UpdateInstalledNotification))
                            .setCategory(NotificationCompat.CATEGORY_STATUS)
                            .setContentIntent(pendingIntent)
                            .build());
        }
    }
}
