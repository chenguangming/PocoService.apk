package com.netease.open.pocoservice;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("PocoService", "onCreate");
//        setContentView(R.layout.activity_test);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("PocoService", "onResume");
        super.moveTaskToBack(true);
        Toast.makeText(getApplicationContext(), "poco service is running", Toast.LENGTH_SHORT).show();
    }
}
