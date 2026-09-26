package nimiyo.litedownloader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private String lastPastedUrl = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaSaverPlugin.class);
        super.onCreate(savedInstanceState);

        // Auto request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // Auto request storage permissions on Android 10 and below (API <= 29)
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, 102);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Memeriksa clipboard setiap kali aplikasi dibuka/di-fokuskan
        checkClipboardForUrl();
    }

    private void checkClipboardForUrl() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence pasteData = clipData.getItemAt(0).getText();
                    if (pasteData != null) {
                        String url = pasteData.toString().trim();
                        // Cek apakah berupa URL dan belum pernah di-prompt sebelumnya
                        if ((url.startsWith("http://") || url.startsWith("https://")) && !url.equals(lastPastedUrl)) {
                            showClipboardDialog(url);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showClipboardDialog(final String url) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Link Terdeteksi!")
                .setMessage("Eh ada URL di clipboard kamu, mau langsung tempel?\n\n" + url)
                .setPositiveButton("Ya", (dialog, which) -> {
                    lastPastedUrl = url;
                    // Inject nilai URL langsung ke kolom input aplikasi
                    if (bridge != null && bridge.getWebView() != null) {
                        bridge.getWebView().evaluateJavascript(
                            "(function() { " +
                            "  var input = document.querySelector('input[type=\"text\"]') || document.querySelector('input'); " +
                            "  if(input) { " +
                            "    input.value = '" + url + "'; " +
                            "    input.dispatchEvent(new Event('input', {bubbles: true})); " +
                            "    input.dispatchEvent(new Event('change', {bubbles: true})); " +
                            "  } " +
                            "})()", null
                        );
                    }
                })
                .setNegativeButton("Tidak", (dialog, which) -> {
                    lastPastedUrl = url;
                    dialog.dismiss();
                })
                .show();
        });
    }

    @Override
    public void onBackPressed() {
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().evaluateJavascript(
                "(function() { if (typeof window.handleAppBackButton === 'function') { return window.handleAppBackButton(); } return false; })()",
                (result) -> {
                    if ("false".equals(result) || "null".equals(result) || result == null) {
                        runOnUiThread(() -> super.onBackPressed());
                    }
                }
            );
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        try {
            android.content.Intent intent = new android.content.Intent(this, MusicPlaybackService.class);
            intent.setAction(MusicPlaybackService.ACTION_CLEAR);
            startService(intent);
        } catch (Exception ignored) {}
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(MusicPlaybackService.MUSIC_NOTIFICATION_ID);
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
