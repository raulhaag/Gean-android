package ar.rulosoft.gean;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WebViewExtended extends WebView {

        public WebViewExtended(@NonNull Context context) {
            super(context);
        }

        public WebViewExtended(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public WebViewExtended(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        public WebViewExtended(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            // This line fixes some issues but introduces others, YMMV.
            // super.onCreateInputConnection(outAttrs);

            return new BaseInputConnection(this, false);
        }
    }