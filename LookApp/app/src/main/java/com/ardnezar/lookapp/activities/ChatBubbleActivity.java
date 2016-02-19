package com.ardnezar.lookapp.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.ardnezar.lookapp.PeerConnectionClient;
import com.ardnezar.lookapp.R;

public class ChatBubbleActivity extends Activity {
	private static final String TAG = "ChatActivity";

	private ChatArrayAdapter chatArrayAdapter;
	private ListView listView;
	private EditText chatText;
	private Button buttonSend;

	Intent intent;
	private boolean side = true;

	private String mPeerId;

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "mReceiver");
			if(intent.getAction().equals(LookAppMainActivity.MESSAGE_RECEIVED_ACTION)) {
				if(intent.hasExtra(LookAppMainActivity.MESSAGE)) {
					String msg = intent.getStringExtra(LookAppMainActivity.MESSAGE);
					chatArrayAdapter.add(new ChatMessage(side, msg));
					side = !side;
				}
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_chat);
		mPeerId = getIntent().getStringExtra(LookAppMainActivity.PEER_ID);

		buttonSend = (Button) findViewById(R.id.buttonSend);

		listView = (ListView) findViewById(R.id.listView1);

		chatArrayAdapter = new ChatArrayAdapter(getApplicationContext(), R.layout.activity_chat_singlemessage);
		listView.setAdapter(chatArrayAdapter);

		chatText = (EditText) findViewById(R.id.chatText);
		chatText.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
					return sendChatMessage();
				}
				return false;
			}
		});
		buttonSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				sendChatMessage();
			}
		});

		listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		listView.setAdapter(chatArrayAdapter);

		//to scroll the list view to bottom on data change
		chatArrayAdapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				listView.setSelection(chatArrayAdapter.getCount() - 1);
			}
		});

		IntentFilter filter = new IntentFilter();
		filter.addAction(LookAppMainActivity.MESSAGE_RECEIVED_ACTION);
		registerReceiver(mReceiver, filter);
	}

	private boolean sendChatMessage(){
		String text = chatText.getText().toString();
		PeerConnectionClient.getInstance().sendTextMessage(mPeerId, "text", text);
		chatArrayAdapter.add(new ChatMessage(side, text));
		chatText.setText("");
		side = !side;
		return true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mReceiver);
	}
}