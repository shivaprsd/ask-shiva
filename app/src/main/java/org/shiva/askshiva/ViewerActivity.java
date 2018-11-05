package org.shiva.askshiva;

import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);

        final WebView webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        final String answer_text = getIntent().getStringExtra(MainActivity.PlaceholderFragment.EXTRA_ANSWER_TEXT);

        settings.setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/mathdown.html");
        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                webView.evaluateJavascript("updatePreview(\"" + answer_text + "\");", null);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.print:
                createWebPrintJob((WebView) findViewById(R.id.webView));
            default:
                return false;
        }
    }

    private void createWebPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(getString(R.string.app_name));

        if (printManager != null)
        printManager.print(getString(R.string.app_name), printAdapter,
                new PrintAttributes.Builder().build());
    }
}
