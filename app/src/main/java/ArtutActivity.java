package com.pjevic.damjan.artest3;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.FrameLayout;

import com.pjevic.damjan.artest3.R;

public class ArtutActivity extends Activity {
    private com.pjevic.damjan.artest3.OverlayView arContent;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Intent intent = getIntent();
        Uri data = intent.getData();


        FrameLayout arViewPane = (FrameLayout) findViewById(R.id.ar_view_pane);

        com.pjevic.damjan.artest3.ArDisplayView arDisplay = new com.pjevic.damjan.artest3.ArDisplayView(getApplicationContext(), this);
        arViewPane.addView(arDisplay);

        arContent = new com.pjevic.damjan.artest3.OverlayView(getApplicationContext());
        arViewPane.addView(arContent);
    }

    @Override
    protected void onPause() {
        arContent.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        arContent.onResume();
    }



}