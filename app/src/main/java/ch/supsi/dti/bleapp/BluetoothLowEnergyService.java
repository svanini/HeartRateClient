package ch.supsi.dti.bleapp;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

public class BluetoothLowEnergyService extends Service {
    private static final String SERVER_NAME = "HeartRateServer"; //use this name for the Linux server, otherwise change it with the name of your Windows laptop
    private static final String TAG = BluetoothLowEnergyService.class.getSimpleName();

    private Messenger messenger;

    private Context context;
    private boolean gattConnected = false;

    private BluetoothLeScanner bleScanner;
    private BluetoothGatt mBluetoothGatt;

    private UUID HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private UUID HEART_RATE_MEASUREMENT_CHARACTERISTIC = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private UUID HR_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            BluetoothDevice device = result.getDevice();
            Log.i(TAG,"Device found! "+device.getAddress());
            bleScanner.stopScan(this);

            connectToDevice(device);
        }
    };

    public BluetoothLowEnergyService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        this.messenger = (Messenger) intent.getExtras().get("handler");
        this.context = getApplicationContext();
        bleScanner = MainActivity.bluetoothAdapter.getBluetoothLeScanner();

        ScanFilter filter = new ScanFilter.Builder().setDeviceName(SERVER_NAME).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        Bundle bundle = new Bundle();
        bundle.putString("type","status");
        bundle.putString("status","Discovery started...");
        sendMessage(bundle);

        bleScanner.startScan(Arrays.asList(filter),settings,mScanCallback);

        // uncomment if you have already paired your server and want to connect without discovery
        /*Set<BluetoothDevice> devices = MainActivity.bluetoothAdapter.getBondedDevices();
        if(!devices.isEmpty()){
            for(BluetoothDevice device : devices){
                if (device.getName().equals(SERVER_NAME)) {
                    Log.i(TAG, SENSOR_TAG_NAME + " found");
                    connectToDevice(device);
                    break;
                }
            }
        }*/
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Bundle bundle = new Bundle();
        bundle.putString("type","status");
        bundle.putString("status","Disconnected");
        sendMessage(bundle);
        Log.i("BT","Stopping service");
        super.onDestroy();
        bleScanner.stopScan(mScanCallback);

        if(mBluetoothGatt!=null && gattConnected) {
            BluetoothGattCharacteristic hrMeasurementCharacteristic = mBluetoothGatt.getService(HEART_RATE_SERVICE).getCharacteristic(HEART_RATE_MEASUREMENT_CHARACTERISTIC);
            mBluetoothGatt.setCharacteristicNotification(hrMeasurementCharacteristic, false);
            BluetoothGattDescriptor descriptor = hrMeasurementCharacteristic.getDescriptor(HR_CONFIG_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.disconnect();
            gattConnected = false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void connectToDevice(final BluetoothDevice device)
    {
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                bundle.putString("type","status");
                bundle.putString("status","Connecting to device...");
                sendMessage(bundle);
                mBluetoothGatt = device.connectGatt(context,true,btleGattCallback);
            }
        });
    }

    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED && !gattConnected){
                gattConnected = true;
                Bundle bundle = new Bundle();
                bundle.putString("type","status");
                bundle.putString("status","Discovering services...");
                Log.d(TAG,"Services discovery start...");
                sendMessage(bundle);
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            Log.d(TAG, "Services discover stop");

            //enable notifications
            BluetoothGattCharacteristic hrMeasurementCharacteristic = gatt.getService(HEART_RATE_SERVICE).getCharacteristic(HEART_RATE_MEASUREMENT_CHARACTERISTIC);
            gatt.setCharacteristicNotification(hrMeasurementCharacteristic, true);
            BluetoothGattDescriptor descriptor = hrMeasurementCharacteristic.getDescriptor(HR_CONFIG_DESCRIPTOR);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            Bundle bundle = new Bundle();
            bundle.putString("type","status");
            bundle.putString("status","Enabling notifications...");
            sendMessage(bundle);

            gatt.writeDescriptor(descriptor);

        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            int val1 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            int val2 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1); //for Widnows, this is the hear_rate
            int val3 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 2); //for Linux, this is the heart_rate
            Log.d(TAG, "Heart rate: " + val1 + ", " + val2 + " , " + val3);

            Bundle bundle = new Bundle();
            bundle.putString("type","data");
            bundle.putDouble("hr",SERVER_NAME.equals("HeartRateServer")? val3 : val2);

            sendMessage(bundle);
        }

    };

    private void sendMessage(Bundle bundle)
    {
        Message message = new Message();
        message.setData(bundle);
        try
        {
            messenger.send(message);
        }catch(RemoteException e)
        {
            e.printStackTrace();
        }
    }
}
