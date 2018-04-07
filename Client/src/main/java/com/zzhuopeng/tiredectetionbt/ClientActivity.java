package com.zzhuopeng.tiredectetionbt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.R.string;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;
import com.zzhuopeng.tiredectetionbt.Util.NetworkUtil;

public class ClientActivity extends Activity implements OnItemClickListener {
	private static final String TAG = "ClientActivity";
    private boolean isPermissionGranted = false;

	private Context mContext;
	private BluetoothAdapter mBluetoothAdapter; // Bluetooth适配器
	private BluetoothDevice device; // 蓝牙设备
	private ListView mListView;
	private ArrayList<ChatMessage> list;
	private com.zzhuopeng.tiredectetionbt.ClientAdapter clientAdapter; // ListView适配器
	private Button disconnect = null, sendButton = null;
	private EditText editText = null;

	private BluetoothSocket socket; // 客户端socket
	private ClientThread mClientThread; // 客户端运行线程
	private ReadThread mReadThread; // 读取流线程

	private String record = "0";

    private int errorCode = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        //Get Android Permissions
        if (isPermissionGranted == false) {
            CheckPermission();
        }

        //初始化即创建语音配置对象，只有初始化后才可以使用MSC的各项服务。SpeechUtility.createUtility(Context, APPID)
        SpeechUtility.createUtility(ClientActivity.this, SpeechConstant.APPID+"=5741698c");

        //1.创建SpeechSynthesizer对象, 第二个参数：本地合成时传InitListener
        SpeechSynthesizer.createSynthesizer(ClientActivity.this, new InitListener() {
            @Override
            public void onInit(int i) {
                errorCode = i;

                if (ErrorCode.SUCCESS == i) {
                    Toast.makeText(mContext, "语音合成器初始化成功", Toast.LENGTH_SHORT).show();
                }
            }
        });

		init();
	}

	// 变量初始化
	private void init() {
		// TODO Auto-generated method stub
		mContext = this;
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		list = new ArrayList<ChatMessage>();
		clientAdapter = new com.zzhuopeng.tiredectetionbt.ClientAdapter(mContext, list);
		mListView = (ListView) findViewById(R.id.list);
		mListView.setFastScrollEnabled(true);
		mListView.setAdapter(clientAdapter);
		mListView.setOnItemClickListener(this);

		// 注册receiver监听
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter);

		// 获取已经配对过的蓝牙设备
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				list.add(new ChatMessage(device.getName() + "\n" + device.getAddress(), true));
				clientAdapter.notifyDataSetChanged();
				mListView.setSelection(list.size() - 1);
			}
		} else {
			list.add(new ChatMessage("没有已经配对过的设备", true));
			clientAdapter.notifyDataSetChanged();
			mListView.setSelection(list.size() - 1);
		}

		editText = (EditText) findViewById(R.id.edit);
		editText.setEnabled(false);
		editText.clearFocus();

		sendButton = (Button) findViewById(R.id.btn_send);
		sendButton.setEnabled(false);
		sendButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				String msg = editText.getText().toString();
				if (msg.length() > 0) {
					sendMessageHandler(msg);
					editText.setText("");
					editText.clearFocus();
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
				} else {
					Toast.makeText(mContext, "发送内容不能为空", Toast.LENGTH_SHORT).show();
				}
			}
		});

		disconnect = (Button) findViewById(R.id.disconnect);
		disconnect.setEnabled(false);
		disconnect.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				// 关闭相关服务
				closeClient();
				BluetoothMsg.isOpen = false;
				BluetoothMsg.serviceOrCilent = BluetoothMsg.ServerOrCilent.NONE;
				Toast.makeText(mContext, "连接已断开", Toast.LENGTH_SHORT).show();
			}
		});
	}

    /**
     * If Android SDK Version >= 6.0; need to request runtime permissions.
     */
    private void CheckPermission() {

        String[] needPermissions = {
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
        };

        //permissionList contains ungranted permissions
        List<String> permissionList = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 23) {
            for (String permission : needPermissions) {
                //Determine if the permission is GRANTED. If it is not, manually turns it on.
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionList.add(permission);
                }
            }
            //Apply for permission manually
            if (!permissionList.isEmpty()) {
                String [] permissions = permissionList.toArray(new String[permissionList.size()]);
                ActivityCompat.requestPermissions(this, permissions, 1);
            } else {
                isPermissionGranted = true;
            }
        }
    }

	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		if (mBluetoothAdapter != null) {
			if (!mBluetoothAdapter.isEnabled()) {
				// 发送打开蓝牙的意图，系统会弹出一个提示对话框
				Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, RESULT_FIRST_USER);

				// 设置蓝牙的可见性，最大值3600秒，默认120秒，0表示永远可见(作为客户端，可见性可以不设置，服务端必须要设置)
				Intent displayIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				displayIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
				startActivity(displayIntent);

				// 直接打开蓝牙
				mBluetoothAdapter.enable();
			}
		}
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		// 扫描
		scanDevice();
	}

	/**
	 * 蓝牙设备扫描过程中(mBluetoothAdapter.startDiscovery())会发出的消息 ACTION_FOUND 扫描到远程设备
	 * ACTION_DISCOVERY_FINISHED 扫描结束
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				// 通过EXTRA_DEVICE附加域来得到一个BluetoothDevice设备
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				// If it's already paired, skip it, because it's been listed
				// already
				// 如果这个设备是不曾配对过的，添加到list列表
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					list.add(new ChatMessage(device.getName() + "\n" + device.getAddress(), false));
					clientAdapter.notifyDataSetChanged();
					mListView.setSelection(list.size() - 1);
				}
				// When discovery is finished, change the Activity title
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				setProgressBarIndeterminateVisibility(false);
				if (mListView.getCount() == 0) {
					list.add(new ChatMessage("没有发现蓝牙设备", false));
					clientAdapter.notifyDataSetChanged();
					mListView.setSelection(list.size() - 1);
				}
			}
		}
	};

	// Handler更新UI
	private Handler LinkDetectedHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// Toast.makeText(mContext, (String)msg.obj,
			// Toast.LENGTH_SHORT).show();
			if (msg.what == 1) {
				list.add(new ChatMessage((String) msg.obj, true));

                String s = (String) msg.obj;
                if (!s.equals(record)) {

                    if (NetworkUtil.isNetworkAvailable(mContext)) {
                        if (s.equals("0")) {
                            playVoice("驾驶员当前处于疲劳状态，请减速慢行");// 当收到"0"时，播报语音。
                        }
                        if (s.equals("1")) {
                            playVoice("驾驶员当前处于清醒状态。注意安全行驶");// 当收到"1"时，播报语音。
                        }
                    } else {
                        Toast.makeText(mContext, "当前网络不可用，请打开网络", Toast.LENGTH_SHORT).show();
                    }
                }
                record = s;

			} else {
				list.add(new ChatMessage((String) msg.obj, false));
			}
			clientAdapter.notifyDataSetChanged();
			mListView.setSelection(list.size() - 1);
		}
	};

	// 当连接上服务器的时候才可以选择发送数据和断开连接
	private Handler refreshUI = new Handler() {
		public void handleMessage(Message msg) {
			if (msg.what == 0) {
				disconnect.setEnabled(true);
				sendButton.setEnabled(true);
				editText.setEnabled(true);
			}
		}
	};

	// 开启客户端连接服务端
	private class ClientThread extends Thread {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			if (device != null) {
				try {
					socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
					// 连接
					Message msg = new Message();
					msg.obj = "请稍候，正在连接服务器: " + BluetoothMsg.BlueToothAddress;
					msg.what = 0;
					LinkDetectedHandler.sendMessage(msg);

					// 通过socket连接服务器，这是一个阻塞过程，直到连接建立或者连接失效
					socket.connect();

					Message msg2 = new Message();
					msg2.obj = "已经连接上服务端！可以发送信息";
					msg2.what = 0;
					LinkDetectedHandler.sendMessage(msg2);

					// 更新UI界面
					Message uiMessage = new Message();
					uiMessage.what = 0;
					refreshUI.sendMessage(uiMessage);

					// 可以开启读数据线程
					mReadThread = new ReadThread();
					mReadThread.start();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					// socket.connect()连接失效
					Message msg = new Message();
					msg.obj = "连接服务端异常！断开连接重新试一试。";
					msg.what = 0;
					LinkDetectedHandler.sendMessage(msg);
				}
			}
		}
	}

	// 通过socket获取InputStream流
	private class ReadThread extends Thread {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			byte[] buffer = new byte[1024];
			int bytes;
			InputStream is = null;
			try {
				is = socket.getInputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			while (true) {
				try {
					if ((bytes = is.read(buffer)) > 0) {
						byte[] data = new byte[bytes];
						for (int i = 0; i < data.length; i++) {
							data[i] = buffer[i];
						}
						String s = new String(data);
						Message msg = new Message();
						msg.obj = s;
						msg.what = 1;
						LinkDetectedHandler.sendMessage(msg);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					try {
						is.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					break;
				}
			}
		}
	}

	// 发送数据
	private void sendMessageHandler(String msg) {
		if (socket == null) {
			Toast.makeText(mContext, "没有可用的连接", Toast.LENGTH_SHORT).show();
			return;
		}
		try {
			OutputStream os = socket.getOutputStream();
			os.write(msg.getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		list.add(new ChatMessage(msg, false));
		clientAdapter.notifyDataSetChanged();
		mListView.setSelection(list.size() - 1);
	}

	// 停止服务
	private void closeClient() {
		new Thread() {
			public void run() {
				if (mClientThread != null) {
					mClientThread.interrupt();
					mClientThread = null;
				}
				if (mReadThread != null) {
					mReadThread.interrupt();
					mReadThread = null;
				}
				try {
					if (socket != null) {
						socket.close();
						socket = null;
					}
				} catch (IOException e) {
					// TODO: handle exception
				}
			}
		}.start();
	}

    /**
     * 在线语音合成-科大讯飞（离线的话，需要付钱）
     * 注：尽量在主线程调用，避免发生不能播报的情况
     */

	public void playVoice(String voiceStr) {

        //初始化成功
        if (errorCode == ErrorCode.SUCCESS) {
            SpeechSynthesizer mTts = SpeechSynthesizer.getSynthesizer();
            //2.合成参数设置，详见《科大讯飞MSC API手册(Android)》SpeechSynthesizer 类
//            mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan");//设置发音人,在此设置语音播报的人选例如：vivixiaoyan、vivixiaomei、vivixiaoqi
//            mTts.setParameter(SpeechConstant.SPEED, "50");//设置语速
//            mTts.setParameter(SpeechConstant.VOLUME, "80");//设置音量，范围0~100

            //3.开始合成(String, 合成监听器)
            mTts.startSpeaking(voiceStr, new SynthesizerListener() {
                //会话结束回调接口，没有错误时，error为null
                public void onCompleted(SpeechError error) {
                    Log.i(TAG, "onCompleted: "+error.toString());
                    Toast.makeText(mContext, "播放语音结束", Toast.LENGTH_SHORT).show();
                }

                //缓冲进度回调
                //percent为缓冲进度0~100，beginPos为缓冲音频在文本中开始位置，endPos表示缓冲音频在文本中结束位置，info为附加信息。
                public void onBufferProgress(int percent, int beginPos, int endPos, String info) {
                }

                //开始播放
                public void onSpeakBegin() {
                    Toast.makeText(mContext, "开始播放语音", Toast.LENGTH_SHORT).show();
                }

                //暂停播放
                public void onSpeakPaused() {
                }

                //播放进度回调
                //percent为播放进度0~100,beginPos为播放音频在文本中开始位置，endPos表示播放音频在文本中结束位置.
                public void onSpeakProgress(int percent, int beginPos, int endPos) {

                }

                //恢复播放回调接口
                public void onSpeakResumed() {
                }

                //会话事件回调接口
                public void onEvent(int arg0, int arg1, int arg2, Bundle arg3) {
                }
            });
        } else {
            Toast.makeText(mContext, "语音合成引擎未初始化成功。ErrorCode:" + errorCode, Toast.LENGTH_SHORT).show();
        }
    }

	// 扫描设备
	private void scanDevice() {
		// TODO Auto-generated method stub
		if (mBluetoothAdapter.isDiscovering()) {
			mBluetoothAdapter.cancelDiscovery();
		} else {
			list.clear();
			clientAdapter.notifyDataSetChanged();

			// 每次扫描前都先判断一下是否存在已经配对过的设备
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					list.add(new ChatMessage(device.getName() + "\n" + device.getAddress(), true));
					clientAdapter.notifyDataSetChanged();
					mListView.setSelection(list.size() - 1);
				}
			} else {
				list.add(new ChatMessage("没有配对好的设备。", true));
				clientAdapter.notifyDataSetChanged();
				mListView.setSelection(list.size() - 1);
			}
			/* 开始搜索 */
			mBluetoothAdapter.startDiscovery();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// TODO Auto-generated method stub
		ChatMessage item = list.get(arg2);
		String info = item.getMessage();
		if (info.length() >= 17) {
			String address = info.substring(info.length() - 17);
			BluetoothMsg.BlueToothAddress = address;

			Log.i(TAG, "onItemClick: "+address);

			// 停止扫描
			// BluetoothAdapter.startDiscovery()很耗资源，在尝试配对前必须中止它
			mBluetoothAdapter.cancelDiscovery();

			// 通过Mac地址去尝试连接一个设备
			device = mBluetoothAdapter.getRemoteDevice(BluetoothMsg.BlueToothAddress);
			mClientThread = new ClientThread();
			mClientThread.start();
			BluetoothMsg.isOpen = true;
		}
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter.cancelDiscovery();
			// 关闭蓝牙
			mBluetoothAdapter.disable();
		}
		unregisterReceiver(mReceiver);
		closeClient();
	}
}
