package ar.rulosoft.gean;

import static android.view.KeyEvent.KEYCODE_BACK;

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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import ar.rulosoft.gean.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.IOException;
import java.net.SocketException;

import ar.rulosoft.gean.Server;

public class MainActivity extends AppCompatActivity implements KeyEvent.Callback {
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    String ipaddres = "", lip;
    WebView webView;
    FrameLayout loadScreen;

    @JavascriptInterface
    public void onData(String value) {
        Log.i("onData", value);
        if (!"0".equals(value)) {
            new Handler(Looper.getMainLooper()).post(() -> webView.loadUrl("javascript:backClick()"));
        } else finish();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Updates.path = getFilesDir();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        webView = binding.webview;
        loadScreen = binding.loadScreen;
        webView.setWebViewClient(new WebViewClient(){
            public void onPageFinished(WebView view, String url) {
                loadScreen.setVisibility(View.GONE);
            }
        });
        webView.setWebChromeClient(new MainActivity.MyChrome());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setSaveFormData(true);

        webView.addJavascriptInterface(this, "android");
        new MainActivity.AsyncStart().execute();
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
        // Captura y procesa las pulsaciones de teclas aquí
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        if (action == KeyEvent.ACTION_DOWN) {
            if(keyCode == KEYCODE_BACK){
                webView.loadUrl("javascript:android.onData(window.backStack.length)");
                return true;
            }
            Log.e("mmu", " "+keyCode);
            int nkey = keyCode;
            char key = ' ';
            switch (nkey){
                case 66:
                case 23:
                    nkey = 13;//enter
                    break;
                case 19:
                    nkey = 38;
                    break;
                case 20:
                    nkey = 40;
                    break;
                case 22:
                    nkey = 39;
                    break;
                case 21:
                    nkey = 37;
                    break;
                case 62:
                    nkey = 32;
                    break;
                case 67:
                    nkey = 8;
                    break;
                default:
                    if(keyCode <=16 && keyCode >=7){
                        nkey = keyCode + 89;
                        key = (char) nkey;
                    }else if(keyCode <=54 && keyCode >=29){
                        nkey = keyCode + 36;
                        key = (char) nkey;
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
        try {
            Server server = Server.getInstance();
            server.start(MainActivity.this);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            String page = "";
            if(isAndroidTv()){
                page = "_2";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE))
                { WebView.setWebContentsDebuggingEnabled(true); }
                page = "_2";
            }
            webView.loadUrl("http://127.0.0.1:8080/main" + page + ".html");
        } catch (IOException e) {
            e.printStackTrace();
            Server.getInstance().stop();
        }
    }


    public class AsyncStart extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Updates.checkUpdates();
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