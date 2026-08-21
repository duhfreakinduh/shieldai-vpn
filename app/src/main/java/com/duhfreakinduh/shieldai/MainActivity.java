package com.duhfreakinduh.shieldai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int VPN_REQUEST = 1001;
    private static final String PREFS = "shieldai";
    private static final String CONFIG_KEY = "wireguard_config";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WebView webView;
    private GoBackend backend;
    private final ShieldTunnel tunnel = new ShieldTunnel();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backend = new GoBackend(getApplicationContext());
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new VpnBridge(this), "AndroidVPN");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) {
                connectTunnel();
            } else {
                notifyError("VPN permission was not granted.");
            }
        }
    }

    private void requestConnect() {
        runOnUiThread(() -> {
            Intent intent = VpnService.prepare(this);
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST);
            } else {
                connectTunnel();
            }
        });
    }

    private void connectTunnel() {
        final String rawConfig = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CONFIG_KEY, "").trim();
        if (rawConfig.isEmpty()) {
            notifyError("No WireGuard config saved. Open Settings and paste a client config first.");
            return;
        }

        executor.execute(() -> {
            try {
                Config config = Config.parse(new ByteArrayInputStream(rawConfig.getBytes(StandardCharsets.UTF_8)));
                Tunnel.State state = backend.setState(tunnel, Tunnel.State.UP, config);
                notifyState(state == Tunnel.State.UP ? "connected" : "disconnected");
            } catch (Exception e) {
                notifyError("Connection failed: " + safeMessage(e));
            }
        });
    }

    private void disconnectTunnel() {
        executor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                notifyState("disconnected");
            } catch (Exception e) {
                notifyError("Disconnect failed: " + safeMessage(e));
            }
        });
    }

    private String currentStatsJson() {
        try {
            Tunnel.State state = backend.getState(tunnel);
            long rx = 0L;
            long tx = 0L;
            if (state == Tunnel.State.UP) {
                Statistics stats = backend.getStatistics(tunnel);
                rx = stats.totalRx();
                tx = stats.totalTx();
            }
            JSONObject json = new JSONObject();
            json.put("state", state == Tunnel.State.UP ? "connected" : "disconnected");
            json.put("rxBytes", rx);
            json.put("txBytes", tx);
            return json.toString();
        } catch (Exception e) {
            return "{\"state\":\"error\",\"rxBytes\":0,\"txBytes\":0}";
        }
    }

    private void notifyState(String state) {
        evaluate("window.ShieldAI && ShieldAI.onNativeState(" + JSONObject.quote(state) + ");");
    }

    private void notifyError(String message) {
        evaluate("window.ShieldAI && ShieldAI.onNativeError(" + JSONObject.quote(message) + ");");
    }

    private void evaluate(String javascript) {
        runOnUiThread(() -> webView.evaluateJavascript(javascript, null));
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private final class ShieldTunnel implements Tunnel {
        @Override
        public String getName() {
            return "shieldai";
        }

        @Override
        public void onStateChange(State newState) {
            notifyState(newState == State.UP ? "connected" : "disconnected");
        }
    }

    private final class VpnBridge {
        private final Context context;

        private VpnBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void connect() {
            requestConnect();
        }

        @JavascriptInterface
        public void disconnect() {
            disconnectTunnel();
        }

        @JavascriptInterface
        public String getStats() {
            return currentStatsJson();
        }

        @JavascriptInterface
        public boolean hasConfig() {
            return !context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(CONFIG_KEY, "").trim().isEmpty();
        }

        @JavascriptInterface
        public void saveConfig(String config) {
            String candidate = config == null ? "" : config.trim();
            if (candidate.isEmpty()) {
                notifyError("Config is empty.");
                return;
            }
            try {
                Config.parse(new ByteArrayInputStream(candidate.getBytes(StandardCharsets.UTF_8)));
                context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CONFIG_KEY, candidate).apply();
                evaluate("window.ShieldAI && ShieldAI.onConfigSaved();");
            } catch (Exception e) {
                notifyError("Invalid WireGuard config: " + safeMessage(e));
            }
        }

        @JavascriptInterface
        public void clearConfig() {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(CONFIG_KEY).apply();
            evaluate("window.ShieldAI && ShieldAI.onConfigCleared();");
        }
    }
}
