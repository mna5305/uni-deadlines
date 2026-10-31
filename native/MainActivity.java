package com.naeem.unideadlines;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SafBackupPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
