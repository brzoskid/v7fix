package pl.brzoskid.webosiptv;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MainActivity extends AppCompatActivity {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private WebView webView;
    private String currentAspectMode = "fit";
    private volatile boolean jsInputActive = false;

    private SSLSocketFactory trustAllSslSocketFactory;
    private HostnameVerifier trustAllHostnameVerifier;
    private DefaultHttpDataSourceFactory httpDataSourceFactory;
    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        initTrustAllSsl();

        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        webView = findViewById(R.id.web_view);

        initExoPlayer();
        initWebView();
    }

    private void initTrustAllSsl() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            final SSLSocketFactory delegate = sslContext.getSocketFactory();

            trustAllSslSocketFactory = new SSLSocketFactory() {
                @Override
                public String[] getDefaultCipherSuites() {
                    return delegate.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    return delegate.getSupportedCipherSuites();
                }

                private Socket enableTls12(Socket s) {
                    if (s instanceof SSLSocket) {
                        try {
                            ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.1", "TLSv1.2"});
                        } catch (Exception ignored) {
                        }
                    }
                    return s;
                }

                @Override
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    return enableTls12(delegate.createSocket(s, host, port, autoClose));
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    return enableTls12(delegate.createSocket(host, port));
                }

                @Override
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    return enableTls12(delegate.createSocket(host, port, localHost, localPort));
                }

                @Override
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    return enableTls12(delegate.createSocket(host, port));
                }

                @Override
                public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                    return enableTls12(delegate.createSocket(address, port, localAddress, localPort));
                }
            };

            trustAllHostnameVerifier = (hostname, session) -> true;

            HttpsURLConnection.setDefaultSSLSocketFactory(trustAllSslSocketFactory);
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnameVerifier);
            SSLContext.setDefault(sslContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void initExoPlayer() {
        trackSelector = new DefaultTrackSelector(this);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(6000, 25000, 2500, 4000)
                .build();

        httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                USER_AGENT,
                12000,
                12000,
                true
        );

        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this, httpDataSourceFactory);

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);

        player = new SimpleExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();

        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setFocusable(false);

        player.addListener(new Player.EventListener() {
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                handleStateChange(playbackState);
            }

            public void onPlaybackStateChanged(int playbackState) {
                handleStateChange(playbackState);
            }

            public void onPlayerError(ExoPlaybackException error) {
                handlePlayerThrowable(error, error.type == ExoPlaybackException.TYPE_SOURCE ? error.getSourceException() : error.getCause());
            }
        });
    }

    private void handleStateChange(int playbackState) {
        if (playbackState == Player.STATE_READY) {
            notifyJs("window.onNativePlayerReady && window.onNativePlayerReady()");
        } else if (playbackState == Player.STATE_BUFFERING) {
            notifyJs("window.onNativePlayerBuffering && window.onNativePlayerBuffering()");
        }
    }

    private void handlePlayerThrowable(Throwable error, Throwable cause) {
        if (cause instanceof BehindLiveWindowException || (cause != null && cause.getCause() instanceof BehindLiveWindowException)) {
            player.seekToDefaultPosition();
            player.prepare();
            player.setPlayWhenReady(true);
            return;
        }
        String details = error.getClass().getSimpleName() + ": " + (error.getMessage() != null ? error.getMessage() : "");
        if (cause != null && cause != error) {
            details += " (" + cause.getClass().getSimpleName() + ": " + (cause.getMessage() != null ? cause.getMessage() : "") + ")";
        }
        String safeDetails = sanitizeForJs(details);
        notifyJs("showStatus && showStatus('Błąd ExoPlayer: " + safeDetails + "', false)");
    }

    private String sanitizeForJs(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("'", "")
                .replace("\"", "")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.requestFocus();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidPlayerBridge(), "AndroidPlayer");

        webView.loadUrl("file:///android_asset/index.html");
        webView.requestFocus();
    }

    private void notifyJs(final String script) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private void sendKeyToJs(String jsKey) {
        String safeKey = sanitizeForJs(jsKey);
        String script = "(function(){" +
                "document.dispatchEvent(new KeyboardEvent('keydown', { key: '" + safeKey + "', bubbles: true }));" +
                "})();";
        notifyJs(script);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_BUTTON_PRESS && ev.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
            sendKeyToJs("Escape");
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            if (jsInputActive && keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) {
                return super.dispatchKeyEvent(event);
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    sendKeyToJs("ArrowUp");
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    sendKeyToJs("ArrowDown");
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    sendKeyToJs("ArrowLeft");
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    sendKeyToJs("ArrowRight");
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    sendKeyToJs("Enter");
                    return true;

                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    sendKeyToJs("Escape");
                    return true;

                case KeyEvent.KEYCODE_APP_SWITCH:
                case KeyEvent.KEYCODE_WINDOW:
                case KeyEvent.KEYCODE_TV_ZOOM_MODE:
                    sendKeyToJs("z");
                    return true;

                case KeyEvent.KEYCODE_MENU:
                    sendKeyToJs("m");
                    return true;

                case KeyEvent.KEYCODE_DEL:
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    sendKeyToJs("a");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_F5:
                case KeyEvent.KEYCODE_GUIDE:
                    sendKeyToJs("e");
                    return true;

                case KeyEvent.KEYCODE_ALL_APPS:
                case KeyEvent.KEYCODE_INFO:
                case KeyEvent.KEYCODE_EXPLORER:
                    sendKeyToJs("i");
                    return true;

                case KeyEvent.KEYCODE_BOOKMARK:
                case KeyEvent.KEYCODE_STAR:
                    sendKeyToJs("f");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    togglePlayPause();
                    return true;

                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekRelativeMs(-30000);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    seekRelativeMs(30000);
                    return true;

                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    sendKeyToJs("ChannelUp");
                    return true;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    sendKeyToJs("ChannelDown");
                    return true;
            }

            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                sendKeyToJs(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
                return true;
            }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                sendKeyToJs(String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0));
                return true;
            }

            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar > 0) {
                char c = (char) unicodeChar;
                if (Character.isLetter(c) || c == ' ') {
                    sendKeyToJs(String.valueOf(c));
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void togglePlayPause() {
        runOnUiThread(() -> {
            if (player == null) return;
            boolean nextState = !player.getPlayWhenReady();
            player.setPlayWhenReady(nextState);
            notifyJs("showOsdToast && showOsdToast('" + (nextState ? "▶ Odtwarzanie" : "⏸ Pauza") + "')");
        });
    }

    private void seekRelativeMs(long deltaMs) {
        runOnUiThread(() -> {
            if (player == null || !player.isCurrentWindowSeekable()) return;
            long target = Math.max(0, player.getCurrentPosition() + deltaMs);
            player.seekTo(target);
            String label = deltaMs < 0 ? "⏪ -30 sek." : "⏩ +30 sek.";
            notifyJs("showOsdToast && showOsdToast('" + label + "')");
        });
    }

    private String downloadUrlWithSslBypass(String urlString) throws Exception {
        String currentUrl = urlString;
        int redirects = 0;

        while (redirects < 6) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection && trustAllSslSocketFactory != null) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(trustAllSslSocketFactory);
                ((HttpsURLConnection) conn).setHostnameVerifier(trustAllHostnameVerifier);
            }
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Encoding", "gzip, identity");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) throw new IOException("Pusty nagłówek Location przy przekierowaniu");
                currentUrl = new URL(url, location).toString();
                redirects++;
                continue;
            }

            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new IOException("HTTP " + code);
            }

            InputStream is = conn.getInputStream();
            String encoding = conn.getContentEncoding();
            if ((encoding != null && encoding.toLowerCase().contains("gzip")) || currentUrl.toLowerCase().endsWith(".gz")) {
                is = new GZIPInputStream(is);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[16384];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            is.close();
            conn.disconnect();
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
        throw new IOException("Zbyt wiele przekierowań HTTP");
    }

    public class AndroidPlayerBridge {

        @JavascriptInterface
        public void setInputFocused(boolean focused) {
            jsInputActive = focused;
        }

        @JavascriptInterface
        public void fetchUrlAsync(final String requestId, final String url) {
            networkExecutor.execute(() -> {
                try {
                    String content = downloadUrlWithSslBypass(url);
                    String b64 = Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    notifyJs("window.__onNativeFetchSuccess && window.__onNativeFetchSuccess('" + requestId + "', '" + b64 + "')");
                } catch (Exception e) {
                    String rawMsg = e.getMessage() != null ? e.getMessage() : "";
                    String errMsg = sanitizeForJs(e.getClass().getSimpleName() + ": " + rawMsg);
                    notifyJs("window.__onNativeFetchError && window.__onNativeFetchError('" + requestId + "', '" + errMsg + "')");
                }
            });
        }

        @JavascriptInterface
        public void playStream(final String url) {
            runOnUiThread(() -> {
                if (player == null || url == null || url.isEmpty()) return;
                if (trustAllSslSocketFactory != null) {
                    HttpsURLConnection.setDefaultSSLSocketFactory(trustAllSslSocketFactory);
                    HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnameVerifier);
                }

                player.stop(true);
                player.clearVideoSurface();
                playerView.setPlayer(null);
                playerView.setPlayer(player);

                Uri uri = Uri.parse(url);
                MediaItem mediaItem = MediaItem.fromUri(uri);

                if (url.toLowerCase().contains(".m3u8")) {
                    HlsMediaSource hlsSource = new HlsMediaSource.Factory(httpDataSourceFactory)
                            .setAllowChunklessPreparation(false)
                            .createMediaSource(mediaItem);
                    player.setMediaSource(hlsSource);
                } else {
                    player.setMediaItem(mediaItem);
                }
                player.prepare();
                player.setPlayWhenReady(true);
            });
        }

        @JavascriptInterface
        public void stopStream() {
            runOnUiThread(() -> {
                if (player != null) {
                    player.stop(true);
                }
            });
        }

        @JavascriptInterface
        public String cycleAspectRatio() {
            if ("fit".equals(currentAspectMode)) {
                currentAspectMode = "fill";
            } else if ("fill".equals(currentAspectMode)) {
                currentAspectMode = "zoom";
            } else {
                currentAspectMode = "fit";
            }
            applyAspectRatio(currentAspectMode);
            return currentAspectMode;
        }

        @JavascriptInterface
        public void setAspectRatio(final String mode) {
            currentAspectMode = mode;
            applyAspectRatio(mode);
        }

        private void applyAspectRatio(final String mode) {
            runOnUiThread(() -> {
                if (playerView == null) return;
                switch (mode) {
                    case "fill":
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                        break;
                    case "zoom":
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                        break;
                    case "fit":
                    default:
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                        break;
                }
            });
        }

        @JavascriptInterface
        public String getTracksJson() {
            JSONObject result = new JSONObject();
            JSONArray audioArr = new JSONArray();
            JSONArray textArr = new JSONArray();

            try {
                MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (trackInfo != null) {
                    for (int rendererIndex = 0; rendererIndex < trackInfo.getRendererCount(); rendererIndex++) {
                        int trackType = trackInfo.getRendererType(rendererIndex);
                        if (trackType != C.TRACK_TYPE_AUDIO && trackType != C.TRACK_TYPE_TEXT) continue;

                        TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
                        DefaultTrackSelector.Parameters params = trackSelector.getParameters();
                        boolean isDisabled = params.getRendererDisabled(rendererIndex);
                        DefaultTrackSelector.SelectionOverride override = params.getSelectionOverride(rendererIndex, trackGroups);

                        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                            TrackGroup group = trackGroups.get(groupIndex);
                            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                                Format format = group.getFormat(trackIndex);
                                JSONObject item = new JSONObject();
                                item.put("rendererIndex", rendererIndex);
                                item.put("groupIndex", groupIndex);
                                item.put("trackIndex", trackIndex);

                                String lang = format.language != null ? format.language.toUpperCase() : "UND";
                                String label = format.label != null ? format.label : ("Ścieżka " + (groupIndex + 1));
                                String codec = format.sampleMimeType != null ? format.sampleMimeType.replace("audio/", "").replace("application/", "").toUpperCase() : "";
                                item.put("title", lang + " • " + label + (codec.isEmpty() ? "" : " [" + codec + "]"));

                                boolean selected = !isDisabled && (
                                        (override != null && override.groupIndex == groupIndex && override.containsTrack(trackIndex))
                                                || (override == null && groupIndex == 0 && trackIndex == 0)
                                );
                                item.put("selected", selected);

                                if (trackType == C.TRACK_TYPE_AUDIO) {
                                    audioArr.put(item);
                                } else {
                                    textArr.put(item);
                                }
                            }
                        }
                    }
                }
                result.put("audio", audioArr);
                result.put("subtitles", textArr);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        @JavascriptInterface
        public void selectTrack(final int rendererIndex, final int groupIndex, final int trackIndex) {
            runOnUiThread(() -> {
                MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (trackInfo == null) return;

                TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
                DefaultTrackSelector.ParametersBuilder builder = trackSelector.buildUponParameters();

                if (trackIndex < 0) {
                    builder.setRendererDisabled(rendererIndex, true);
                } else {
                    builder.setRendererDisabled(rendererIndex, false);
                    builder.setSelectionOverride(
                            rendererIndex,
                            trackGroups,
                            new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex)
                    );
                }
                trackSelector.setParameters(builder);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (webView != null) webView.requestFocus();
        if (player != null) player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
