package com.wesleyrnash.nfcrwadminmode;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.Button;

import java.io.IOException;

public class MainActivity extends Activity {

    //create button objects
    Button triageButton;
    Button adminButton;

    Context ctx;

    //define a tag for debugging
    public static final String TAG = "NFCRW";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = this;

        //initialize buttons
        triageButton = (Button) findViewById(R.id.button_triageMode);
        adminButton = (Button) findViewById(R.id.button_adminMode);

        //set up click listeners
        triageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //start the TriageActivity
                Intent intent = new Intent(ctx, TriageActivity.class);
                startActivity(intent);
            }
        });

        adminButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //start the AdminActivity
                Intent intent = new Intent(ctx, AdminActivity.class);
                startActivity(intent);
            }
        });

        DataBaseHelper helper = new DataBaseHelper(this);
        try {
            helper.createDataBase();
        } catch (IOException e) {
            e.printStackTrace();
        }
        helper.openDataBase();

        Cursor cursor;
        cursor = helper.getRow();
        Log.d(TAG, cursor.getString(0) + "," + cursor.getString(1) + "," + cursor.getString(2));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
