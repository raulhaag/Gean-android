package ar.rulosoft.gean;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_MUTE;
import static android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
import static android.view.KeyEvent.KEYCODE_VOLUME_MUTE;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;

import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.ui.AppBarConfiguration;

import java.io.IOException;
import java.net.SocketException;
import java.util.Objects;

import ar.rulosoft.gean.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity implements KeyEvent.Callback {
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    String ipaddres = "", lip;
    WebView webView;
    FrameLayout loadScreen;
    long lastTimeBackPressed = 0;

    @JavascriptInterface
    public void onData(String value) {
        Log.i("onData", value);
        if (!"0".equals(value)) {
            new Handler(Looper.getMainLooper()).post(() -> webView.loadUrl("javascript:backClick()"));
        } else {
            long cTime = System.currentTimeMillis();
            if(cTime - lastTimeBackPressed < 1500) {
                finish();
            }else{
                lastTimeBackPressed = cTime;
                Toast.makeText(getApplicationContext(),"Presiona dos veces seguidas para salir de la aplicación", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    MainActivity.this.startActivity(
                            new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
                }catch (Exception ignored){

                }
            }
        }
        Updates.path = getFilesDir();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        webView = binding.webview;
        loadScreen = binding.loadScreen;
        webView.setWebViewClient(new WebViewClient(){
            public void onPageFinished(WebView view, String url) {
                loadScreen.setVisibility(View.GONE);
            }
        });
        webView.setWebChromeClient(new MyChrome());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setSaveFormData(true);
        Log.e("USER AGENT", webView.getSettings().getUserAgentString());
        webView.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        webView.addJavascriptInterface(this, "android");
        new AsyncStart().execute();
        setContentView(binding.getRoot());
        try {
            lip = Server.getFirstNonLoopbackAddress(true, false).toString().replace("/", "");
            ipaddres = "http://" + lip + ":8080/";
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        OnBackPressedCallback callback = new OnBackPressedCallback(
                true // default to enabled
        ) {
            @Override
            public void handleOnBackPressed() {
//              webView.loadUrl("javascript:openPlayer({'video':'http://127.0.0.1:8080/file/'})");
                webView.loadUrl("javascript:android.onData(window.backStack.length)");
            }
        };
        getOnBackPressedDispatcher().addCallback(
                this, // LifecycleOwner
                callback);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        if (action == KeyEvent.ACTION_DOWN) {
            if(keyCode == KEYCODE_BACK){
                if(Objects.requireNonNull(webView.getUrl()).contains("main_2.")){
                    webView.loadUrl("javascript:android.onData(window.backScenePoll.length)");
                }else {
                    webView.loadUrl("javascript:android.onData(window.backStack.length)");
                }
                return true;
            }
            int nkey = keyCode;
            String key = " ";
            switch (nkey){
                case 66:
                case 23:
                    nkey = 13;//enter
                    key = "Enter";
                    break;
                case 19:
                    nkey = 38;
                    key = "ArrowUp";
                    break;
                case 20:
                    nkey = 40;
                    key = "ArrowDown";

                    break;
                case 22:
                    nkey = 39;
                    key = "ArrowRight";
                    break;
                case 21:
                    nkey = 37;
                    key = "ArrowLeft";
                    break;
                case 62:
                    nkey = 32;
                    key = "Space";
                    break;
                case 67:
                    key = "Backspace";
                    nkey = 8;
                    break;
                case KEYCODE_VOLUME_UP:
                case KEYCODE_VOLUME_DOWN:
                case KEYCODE_VOLUME_MUTE:
                case KEYCODE_MUTE:
                    return super.dispatchKeyEvent(event);
                default:
                    if(keyCode <=16 && keyCode >=7){
                        nkey = keyCode + 89;
                        key = "KEY" +  ((char) nkey);
                    }else if(keyCode <=54 && keyCode >=29){
                        nkey = keyCode + 36;
                        key = "KEY" +  ((char) nkey);
                    }

            }
           // webView.loadUrl("javascript:document.onkeydown({keyCode: "+nkey+", key:'"+ key +", preventDefault:pd'})");
           webView.loadUrl("javascript:document.onkeydown(new KeyboardEvent('keydown', { key:'"+ key + "', keyCode: " + nkey + " }))");
        }
        return true;//capture
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        webView.saveState(outState);
    }

    @Override
    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onRestoreInstanceState(savedInstanceState, persistentState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    public void finish() {
        Server.getInstance().stop();
        super.finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean isAndroidTv(){
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        return false;
    }


    public void start() {
        //Intent intent = new Intent(this, HttpService.class);
        //startService(intent);
        try {
            Server.getInstance().startServer(MainActivity.this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String page = "_na";
        if(isAndroidTv()){
            page = "_2";
        }
        if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        //page = "_2";
        webView.loadUrl("http://127.0.0.1:8080/main" + page + ".html");
        //webView.loadUrl("http://192.168.0.210:8080/main" + page + ".html");
    }


    public class AsyncStart extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Updates.checkUpdates(MainActivity.this);
                Server.generateSourceList();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            start();
        }
    }

    private class MyChrome extends WebChromeClient {

        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        protected FrameLayout mFullscreenContainer;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        MyChrome() {
        }
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            android.util.Log.d("WebView", consoleMessage.message());
            return true;
        }

        public Bitmap getDefaultVideoPoster() {
            if (mCustomView == null) {
                return null;
            }
            return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
        }

        public void onHideCustomView() {
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
        }

        public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback) {
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = paramCustomViewCallback;
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
            getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}