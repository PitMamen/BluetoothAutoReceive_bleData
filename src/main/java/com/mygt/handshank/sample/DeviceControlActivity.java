/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mygt.handshank.sample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 对于一个BLE设备，该activity向用户提供设备连接，显示数据，显示GATT服务和设备的字符串支持等界面，
 * 另外这个activity还与BluetoothLeService通讯，反过来与Bluetooth LE API进行通讯
 */
public class DeviceControlActivity extends Activity implements BluetoothLeService.Callback {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    private final static byte[] hex = "0123456789ABCDEF".getBytes();
    public static String ACTION_GET_DATA = "Receive_the_data";
    private String dataStr;

    // 连接状态
    private EditText mDataField;
    private String mDeviceName;

    private TextView addressView;
//	private String mDeviceAddress;

    private List<String> addressList = new ArrayList<String>();

    private Set<String> rigAddressList = new HashSet<String>();  //存储蓝牙设备

    private BluetoothLeService mBluetoothLeService;

    private boolean mConnected = false;

    private BluetoothAdapter mBluetoothAdapter;


    // 管理服务的生命周期
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            connectService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            String address = bundle.getString(Constants.EXTRA_ADDRESS);
            switch (msg.what) {
                case BluetoothLeService.ACTION_GATT_CONNECTED:
                    mConnected = true;
                    addressView.setText(address);
                    rigAddressList.add(address);
                case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
                    rigAddressList.add(address);
                    break;
                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                    mConnected = false;
                    rigAddressList.remove(address);
                    // updateConnectionState(R.string.disconnected);
                    clearUI();
                    break;
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    // 将数据显示在mDataField上

                    byte[] data = bundle.getByteArray(Constants.EXTRA_DATA);
                    dataStr = Bytes2HexString(data);
                    mBluetoothLeService.getCharacteristic(address);



                    Log.d("haha", "data----" + dataStr + ",address---" + address);
                    displayData(dataStr);
                    break;
            }
        }
    };

    // 从字节数组到十六进制字符串转换
    public static String Bytes2HexString(byte[] b) {
        byte[] buff = new byte[2 * b.length];
        for (int i = 0; i < b.length; i++) {
            buff[2 * i] = hex[(b[i] >> 4) & 0x0f];
            buff[2 * i + 1] = hex[b[i] & 0x0f];
        }
        return new String(buff);
    }

    @Override
    public void onDispatchData(int type, byte[] data, String address) {
        Log.d("测试次数DeviceControl", "onDispatchData.........");
        Message message = handler.obtainMessage(type);       //从已经得到数据的回调中  再通过handler发送
        Bundle bundle = new Bundle();
        bundle.putByteArray(Constants.EXTRA_DATA, data);
        bundle.putString(Constants.EXTRA_ADDRESS, address);
        message.setData(bundle);
        handler.sendMessage(message);
    }


     //广播接收器
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "action:" + action);
            String address;
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                address = device.getAddress();
                rigAddressList.add(address);
                Log.d(TAG, "devices:" + device.getName() + "address:" + address);
            }
        }
    };

    private void clearUI() {
        // mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        // mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        //获取已经保存过的设备信息  
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bluetoothDevice : devices) {
            Log.d(TAG, "bond:" + bluetoothDevice.getBondState());

			  /*if(mac.equalsIgnoreCase(bluetoothDevice.getAddress())) {
                  mDeviceName = bluetoothDevice.getName();
				  mDeviceAddress = bluetoothDevice.getAddress();
			  }*/
            addressList.add(bluetoothDevice.getAddress());
            Log.d(TAG, "devices：" + bluetoothDevice.getName() + ", " + bluetoothDevice.getAddress());
        }

        // Sets up UI references.
        addressView = (TextView) findViewById(R.id.device_address);
        mDataField = (EditText) findViewById(R.id.data_value);


        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        super.onResume();
        register();
        connectService();
    }

    // 连接服务
    private void connectService() {
        if (mBluetoothLeService != null) {
            mBluetoothLeService.registerCallback(this);
            for (String address : addressList) {
                final boolean result = mBluetoothLeService.connect(address);
                //这里如果蓝牙连接上了 则自动接收数据
                byte[] writeBytes = new byte[20];
                writeBytes[0] = (byte) 0x00;
                mBluetoothLeService.writeCharacteristic(writeBytes);
                if (dataStr==null){
                    Log.d("haha", "消息为空！！！！！: ");
                }
                else if (dataStr != null) {
                    Intent intent = new Intent(ACTION_GET_DATA);
                    intent.putExtra("name", dataStr);
                    sendBroadcast(intent);
                }
                Log.d(TAG, "Connect request result=" + result + ",address:" + address);
                if (result) {
                    rigAddressList.add(address);
//					addressView.setText(address);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBluetoothLeService.unRegisterCallback(this);
        unRegister();
    }

    boolean isRegister = false;

    private void register() {
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        isRegister = true;
    }

    private void unRegister() {
        if (isRegister) {
            unregisterReceiver(mGattUpdateReceiver);
            isRegister = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unRegister();
        if (mBluetoothLeService != null) {
            mBluetoothLeService.unRegisterCallback(this);
        }
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

     //广播动作
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        //system
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return intentFilter;
    }
}
