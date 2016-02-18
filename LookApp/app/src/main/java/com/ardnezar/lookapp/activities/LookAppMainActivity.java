package com.ardnezar.lookapp.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.ardnezar.lookapp.CallActivity;
import com.ardnezar.lookapp.PeerConnectionClient;
import com.ardnezar.lookapp.R;

import java.util.ArrayList;
import java.util.HashMap;

public class LookAppMainActivity extends AppCompatActivity {

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link FragmentPagerAdapter} derivative, which will keep every
	 * loaded fragment in memory. If this becomes too memory intensive, it
	 * may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	private SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	private ViewPager mViewPager;
	private SharedPreferences sharedPref;
	private String keyprefVideoCallEnabled;
	private String keyprefResolution;
	private String keyprefFps;
	private String keyprefCaptureQualitySlider;
	private String keyprefVideoBitrateType;
	private String keyprefVideoBitrateValue;
	private String keyprefVideoCodec;
	private String keyprefAudioBitrateType;
	private String keyprefAudioBitrateValue;
	private String keyprefAudioCodec;
	private String keyprefHwCodecAcceleration;
	private String keyprefCaptureToTexture;
	private String keyprefNoAudioProcessingPipeline;
	private String keyprefAecDump;
	private String keyprefOpenSLES;
	private String keyprefDisplayHud;
	private String keyprefTracing;
	private String keyprefRoomServerUrl;
	private String keyprefRoom;
	private String keyprefRoomList;


	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(PEER_ADD_ACTION)) {
				String id = intent.getStringExtra(PEER_ID);
				if(!mPeerList.contains(id)) mPeerList.add(id);
//				mSectionsPagerAdapter.getItem(mViewPager.getCurrentItem()).
			} else if(intent.getAction().equals(PEER_REMOVE_ACTION)) {
				String id = intent.getStringExtra(PEER_ID);
				mPeerList.remove(id);
			} else if(intent.getAction().equals(MESSAGE_RECEIVED_ACTION)) {
				String id = intent.getStringExtra(PEER_ID);
				String message = intent.getStringExtra(MESSAGE);
				if(!mChatList.containsKey(id)) {
					ArrayList<String> list = new ArrayList();
					list.add(message);
					mChatList.put(id, list);
				} else {
					ArrayList<String> list = mChatList.get(id);
					list.add(message);
					mChatList.put(id, list);
				}
			}
		}
	};

	private ArrayList<String> mPeerList;
	private ArrayList<String> mMessageList;
	public HashMap<String, ArrayList<String>> mChatList;

	public static final String PEER_ADD_ACTION = "com.ardnezar.lookapp.PEER_ADDED";
	public static final String PEER_REMOVE_ACTION = "com.ardnezar.lookapp.PEER_REMOVED";

	public static final String PEER_ID = "Peer_Id";
	public static final String PEER_IDS = "Peers";
	public static final String MESSAGE = "message";


	public static final String MESSAGE_RECEIVED_ACTION = "com.ardnezar.lookapp.MESSAGE_RECEIVED";

	private static final String TAG = "LookRTCMain";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		setContentView(R.layout.activity_look_app_main);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		// Create the adapter that will return a fragment for each of the three
		// primary sections of the activity.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.container);
		mViewPager.setAdapter(mSectionsPagerAdapter);

//		mSectionsPagerAdapter.getItem(mViewPager.getCurrentItem()).

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		keyprefVideoCallEnabled = getString(R.string.pref_videocall_key);
		keyprefResolution = getString(R.string.pref_resolution_key);
		keyprefFps = getString(R.string.pref_fps_key);
		keyprefCaptureQualitySlider = getString(R.string.pref_capturequalityslider_key);
		keyprefVideoBitrateType = getString(R.string.pref_startvideobitrate_key);
		keyprefVideoBitrateValue = getString(R.string.pref_startvideobitratevalue_key);
		keyprefVideoCodec = getString(R.string.pref_videocodec_key);
		keyprefHwCodecAcceleration = getString(R.string.pref_hwcodec_key);
		keyprefCaptureToTexture = getString(R.string.pref_capturetotexture_key);
		keyprefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key);
		keyprefAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key);
		keyprefAudioCodec = getString(R.string.pref_audiocodec_key);
		keyprefNoAudioProcessingPipeline = getString(R.string.pref_noaudioprocessing_key);
		keyprefAecDump = getString(R.string.pref_aecdump_key);
		keyprefOpenSLES = getString(R.string.pref_opensles_key);
		keyprefDisplayHud = getString(R.string.pref_displayhud_key);
		keyprefTracing = getString(R.string.pref_tracing_key);
		keyprefRoomServerUrl = getString(R.string.pref_room_server_url_key);
		keyprefRoom = getString(R.string.pref_room_key);
		keyprefRoomList = getString(R.string.pref_room_list_key);
		String address = "http://" + getResources().getString(R.string.host);
		address += (":" + getResources().getString(R.string.port) + "/");

//		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
//		PeerConnectionClient.getInstance().createPeerConnectionFactory(
//				this, address, pref.getString(LookAppLauncherActivity.LOOK_APP_ID, "1111111111"));

		IntentFilter filter = new IntentFilter();
		filter.addAction(PEER_ADD_ACTION);
		filter.addAction(PEER_REMOVE_ACTION);
		filter.addAction(MESSAGE_RECEIVED_ACTION);
		registerReceiver(mReceiver, filter);

		mPeerList = new ArrayList<>();
		mMessageList = new ArrayList<>();

		mChatList = new HashMap<>();

		startActivity(new Intent(this, CallActivity.class));
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_look_app_main, menu);
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


	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public int getItemPosition(Object object) {
			return super.getItemPosition(object);
		}

		@Override
		public Fragment getItem(int position) {
			Log.d(TAG, "getItem..position:"+position);
			// getItem is called to instantiate the fragment for the given page.
			// Return a PlaceholderFragment (defined as a static inner class below).
//			if(position == 0) {
//
//			}
			return new PeerFragment();
//			else {
//				return new ChatItemFragment();
//			}
		}

		@Override
		public int getCount() {
			// Show 3 total pages.
			return 1;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
				case 0:
					return "Contacts";
				case 1:
					return "Chats";
				case 2:
					return "SECTION 3";
			}
			return null;
		}
	}

//	/**
//	 * A placeholder fragment containing a simple view.
//	 */
//	public static class PlaceholderFragment extends Fragment {
//		/**
//		 * The fragment argument representing the section number for this
//		 * fragment.
//		 */
//		private static final String ARG_SECTION_NUMBER = "section_number";
//
//		/**
//		 * Returns a new instance of this fragment for the given section
//		 * number.
//		 */
//		public static PlaceholderFragment newInstance(int sectionNumber) {
//			PlaceholderFragment fragment = new PlaceholderFragment();
//			Bundle args = new Bundle();
//			args.putInt(ARG_SECTION_NUMBER, sectionNumber);
//			fragment.setArguments(args);
//			return fragment;
//		}
//
//		public PlaceholderFragment() {
//		}
//
//		@Override
//		public View onCreateView(LayoutInflater inflater, ViewGroup container,
//								 Bundle savedInstanceState) {
//			View rootView = inflater.inflate(R.layout.fragment_look_app_main, container, false);
//			TextView textView = (TextView) rootView.findViewById(R.id.section_label);
//			textView.setText(getString(R.string.section_format, getArguments().getInt(ARG_SECTION_NUMBER)));
//			return rootView;
//		}
//	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(this).edit().
				putString(LookAppLauncherActivity.LOOK_SESSION_ID, "").apply();
		PeerConnectionClient.getInstance().close();
		unregisterReceiver(mReceiver);
		if(mPeerList != null) {
			mPeerList.clear();
		}

		if(mMessageList != null) {
			mMessageList.clear();
		}
	}
}
