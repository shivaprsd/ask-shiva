package org.shiva.askshiva;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class IntroActivity extends AppCompatActivity {
    public static final String USER_NAME = "org.shiva.askshiva.USER_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences pref = this.getSharedPreferences(USER_NAME, Context.MODE_PRIVATE);
        if (pref.contains(USER_NAME)) {     /* Not first time */
            openMain();
        }
        setContentView(R.layout.activity_intro);

        EditText name_input = findViewById(R.id.name_i);
        name_input.addTextChangedListener(new TextWatcher() {
            Button start_btn = findViewById(R.id.start_b);

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable et) {
                if (et.toString().matches("^\\w[\\w\\s]*")) {
                    start_btn.setEnabled(true);
                } else {
                    start_btn.setEnabled(false);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.name_i).clearFocus();
    }

    public void registerUser(View view) {
        String user_name = ((EditText) findViewById(R.id.name_i)).getText().toString();
        SharedPreferences pref = this.getSharedPreferences(USER_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putString(USER_NAME, user_name);
        editor.apply();
        openMain();
    }

    private void openMain() {
        Intent open_main = new Intent(this, MainActivity.class);
        startActivity(open_main);
        finish();
    }
}
