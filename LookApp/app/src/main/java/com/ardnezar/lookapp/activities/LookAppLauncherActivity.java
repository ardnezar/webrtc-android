package com.ardnezar.lookapp.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.ardnezar.lookapp.R;

public class LookAppLauncherActivity extends Activity {

	public static final String LOOK_APP_ID = "id";
	public static final String LOOK_SESSION_ID = "id";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		if(sharedPref.contains(LOOK_APP_ID) && sharedPref.getString(LOOK_APP_ID, null) != null) {
			Intent intent = new Intent(getApplicationContext(), LookAppMainActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(intent);
		} else {
			PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
			Intent intent = new Intent(getApplicationContext(), LookAppInitActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(intent);
		}
	}
}
