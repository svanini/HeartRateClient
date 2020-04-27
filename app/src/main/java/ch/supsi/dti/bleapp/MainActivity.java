package ch.supsi.dti.bleapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;

    public static BluetoothAdapter bluetoothAdapter;

    private TextView heartRateText;
    private TextView statusText;

    private boolean permissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        else permissionGranted = true;

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent,REQUEST_ENABLE_BT);
        }

        bluetoothAdapter = mBluetoothAdapter;

        heartRateText = findViewById(R.id.heart_rate);
        statusText = findViewById(R.id.lStatus);

        final Handler myHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {

                if(msg.getData().getString("type").equalsIgnoreCase("data"))
                {
                    heartRateText.setText("HR: "+msg.getData().getDouble("hr"));
                    statusText.setText("Data received");
                }
                else if(msg.getData().getString("type").equalsIgnoreCase("status"))
                {
                    statusText.setText(msg.getData().getString("status"));
                }

            }
        };

        final Context _self = this;

        Button bConnect = findViewById(R.id.bConnect);
        bConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startIntent = new Intent(_self,BluetoothLowEnergyService.class);
                startIntent.putExtra("handler",new Messenger(myHandler));
                startService(startIntent);
            }
        });

        Button bDisconnect = findViewById(R.id.bDisconnect);
        bDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(_self,BluetoothLowEnergyService.class);
                stopService(stopIntent);
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this,BluetoothLowEnergyService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 1){
            if(grantResults.length  > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionGranted = true;
            }
            else
            {
                permissionGranted = false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == REQUEST_ENABLE_BT)
        {
            if(resultCode == RESULT_OK)
                Toast.makeText(this,"Bluetooth enabled",Toast.LENGTH_LONG);
            else
                Toast.makeText(this,"Bluetooth not enabled",Toast.LENGTH_LONG);
        }
    }


}
