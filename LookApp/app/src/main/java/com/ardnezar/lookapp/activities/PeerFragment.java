package com.ardnezar.lookapp.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.ardnezar.lookapp.PeerConnectionClient;
import com.ardnezar.lookapp.R;

import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
public class PeerFragment extends Fragment implements AbsListView.OnItemClickListener {


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

	private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;

	private static final String TAG  = "RTCPeerFragment";

	// TODO: Rename parameter arguments, choose names that match
	// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
	private static final String ARG_PARAM1 = "param1";
	private static final String ARG_PARAM2 = "param2";

	// TODO: Rename and change types of parameters
	private String mParam1;
	private String mParam2;

	private OnFragmentInteractionListener mListener;

	private ArrayList<String> mPeerList;

	/**
	 * The fragment's ListView/GridView.
	 */
	private AbsListView mListView;

	/**
	 * The Adapter which will be used to populate the ListView/GridView with
	 * Views.
	 */
	private ArrayAdapter<String> mAdapter;




	// TODO: Rename and change types of parameters
	public static PeerFragment newInstance(String param1, String param2) {
		PeerFragment fragment = new PeerFragment();
		Bundle args = new Bundle();
		args.putString(ARG_PARAM1, param1);
		args.putString(ARG_PARAM2, param2);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onStart() {
		super.onStart();
		IntentFilter filter = new IntentFilter();
		filter.addAction(LookAppMainActivity.PEER_ADD_ACTION);
		filter.addAction(LookAppMainActivity.PEER_REMOVE_ACTION);
		getActivity().registerReceiver(mReceiver, filter);
	}

	@Override
	public void onStop() {
		super.onStop();
		getActivity().unregisterReceiver(mReceiver);
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "mReceiver");
			if(intent.getAction().equals(LookAppMainActivity.PEER_ADD_ACTION)) {
				if(intent.hasExtra(LookAppMainActivity.PEER_ID)) {
					String id = intent.getStringExtra(LookAppMainActivity.PEER_ID);
					if (!mPeerList.contains(id)) mPeerList.add(id);
					Log.d(TAG, "mReceiver..PEER_ADD_ACTION:" + id);
				} else if(intent.hasExtra(LookAppMainActivity.PEER_IDS)) {
					String[] ids = intent.getStringArrayExtra(LookAppMainActivity.PEER_IDS);
					if(ids != null) {
						for (String user : ids) {
							if (!mPeerList.contains(user)) mPeerList.add(user);
							Log.d(TAG, "mReceiver..PEER_ADD_ACTION:" + user);
						}
					}
				}
				mAdapter.notifyDataSetChanged();
			} else if(intent.getAction().equals(LookAppMainActivity.PEER_REMOVE_ACTION)) {
				Log.d(TAG, "mReceiver");
				String id = intent.getStringExtra(LookAppMainActivity.PEER_ID);
				mPeerList.remove(id);
				Log.d(TAG, "mReceiver..PEER_REMOVE_ACTION:" + id);
				mAdapter.notifyDataSetChanged();
			}
		}
	};

	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public PeerFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		if (getArguments() != null) {
			mParam1 = getArguments().getString(ARG_PARAM1);
			mParam2 = getArguments().getString(ARG_PARAM2);
		}

		// TODO: Change Adapter to display your content
//		mAdapter = new ArrayAdapter<DummyContent.DummyItem>(getActivity(),
//				android.R.layout.simple_list_item_1, android.R.id.text1, DummyContent.ITEMS);


		mPeerList = new ArrayList<String>();

//		mPeerList.add("Hello");

		mAdapter = new ArrayAdapter(getActivity(),
				android.R.layout.simple_list_item_1, android.R.id.text1, mPeerList);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_peer, container, false);



		// Set the adapter
		mListView = (AbsListView) view.findViewById(android.R.id.list);
		setEmptyText("No Peer");
		mListView.setAdapter(mAdapter);

		// Set OnItemClickListener so we can be notified on item clicks
		mListView.setOnItemClickListener(this);

		return view;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Log.d(TAG, "Clicked on:" + mPeerList.get(position));
		Intent intent = new Intent(getContext(), ChatBubbleActivity.class);
		intent.putExtra(LookAppMainActivity.PEER_ID, mPeerList.get(position));
		getActivity().startActivity(intent);
	}

	/**
	 * The default content for this Fragment has a TextView that is shown when
	 * the list is empty. If you would like to change the text, call this method
	 * to supply the text it should use.
	 */
	public void setEmptyText(CharSequence emptyText) {
		View emptyView = mListView.getEmptyView();

		if (emptyView instanceof TextView) {
			((TextView) emptyView).setText(emptyText);
		}
	}

	/**
	 * This interface must be implemented by activities that contain this
	 * fragment to allow an interaction in this fragment to be communicated
	 * to the activity and potentially other fragments contained in that
	 * activity.
	 * <p/>
	 * See the Android Training lesson <a href=
	 * "http://developer.android.com/training/basics/fragments/communicating.html"
	 * >Communicating with Other Fragments</a> for more information.
	 */
	public interface OnFragmentInteractionListener {
		// TODO: Update argument type and name
		public void onFragmentInteraction(String id);
	}

}
