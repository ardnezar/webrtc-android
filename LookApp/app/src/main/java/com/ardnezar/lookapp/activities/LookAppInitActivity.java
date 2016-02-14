package com.ardnezar.lookapp.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.ardnezar.lookapp.R;

public class LookAppInitActivity extends Activity {

	private EditText mText;
	private Button mSubmitButton;

	private static final String TAG = "InitRTC";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_look_app_init);
		mText = (EditText)findViewById(R.id.number);
		mSubmitButton = (Button) findViewById(R.id.submit);
		mSubmitButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if(mText != null && mText.length() > 0) {
					Log.d(TAG, "Id:"+mText.getText().toString());
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
					prefs.edit().putString(LookAppLauncherActivity.LOOK_APP_ID, mText.getText().toString()).apply();
					Intent intent = new Intent(getApplicationContext(), LookAppMainActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
					startActivity(intent);
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

	}
}
