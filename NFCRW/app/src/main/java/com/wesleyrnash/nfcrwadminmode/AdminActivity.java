package com.wesleyrnash.nfcrwadminmode;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class AdminActivity extends Activity {

    NfcAdapter adapter;
    PendingIntent pendingIntent;
    IntentFilter writeTagFilters[];
    Tag mytag;
    Context ctx;

    TextView adminInstructions;
    TextView tagId;

    public static final String TAG = "NFCRW";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        ctx = this;

        adminInstructions = (TextView) findViewById(R.id.tv_adminInstructions);
        tagId = (TextView) findViewById(R.id.et_adminID);

        adapter = NfcAdapter.getDefaultAdapter(this);
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[] { tagDetected };
    }

    private void write(String id, Tag tag) throws IOException, FormatException, NullPointerException {


        // Get an instance of Ndef for the tag.
        Ndef ndef = Ndef.get(tag);

        String text = getText(ndef);

        NdefRecord[] records = { createRecord(text, id) };
        NdefMessage message = new NdefMessage(records);
        // Enable I/O
        ndef.connect();
        // Write the message
        ndef.writeNdefMessage(message);
        // Close the connection
        ndef.close();
    }

    private String getText(Ndef ndef){
        NdefMessage ndefMessage = ndef.getCachedNdefMessage();

        NdefRecord[] records = ndefMessage.getRecords();
        for (NdefRecord ndefRecord : records) {
            if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                byte[] payload = ndefRecord.getPayload();
                String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
                int languageCodeLength = payload[0] & 0063;
                try{
                    String result = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                    byte[] decodedBytes = Base64.decode(result.getBytes(), Base64.DEFAULT);
                    result = new String(decodedBytes);
                    return result;
                } catch (UnsupportedEncodingException e){
                    e.printStackTrace();
                    Log.e(TAG, "Unsupported Encoding", e);
                }
            }
        }
        return "default";
    }

    private NdefRecord createRecord(String text, String id) throws UnsupportedEncodingException {
        String lang       = "en";
        byte[] encodedTextBytes = Base64.encode(text.getBytes(), Base64.DEFAULT);
        //byte[] textBytes  = text.getBytes();
        byte[] langBytes  = lang.getBytes("US-ASCII");
        int    langLength = langBytes.length;
        int    textLength = encodedTextBytes.length;
//        int    textLength = textBytes.length;
        byte[] payload    = new byte[1 + langLength + textLength];

        // set status byte (see NDEF spec for actual bits)
        payload[0] = (byte) langLength;

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes,        0, payload, 1,              langLength);
        System.arraycopy(encodedTextBytes, 0, payload, 1 + langLength, textLength);
//        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

//        String id = "RippleNFC";

        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,  NdefRecord.RTD_TEXT,  id.getBytes(), payload);

        return recordNFC;
    }

    @Override
    protected void onNewIntent(Intent intent){
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
            mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
        try {
            if(mytag==null){
                Toast.makeText(ctx, ctx.getString(R.string.error_detected), Toast.LENGTH_SHORT).show();
            }else{
                String message = tagId.getText().toString();
                write(message,mytag);
                adminInstructions.setText(R.string.ok_writing);
            }
        } catch (IOException e) {
            Toast.makeText(ctx, ctx.getString(R.string.error_writing), Toast.LENGTH_SHORT ).show();
            e.printStackTrace();
        } catch (FormatException e) {
            Toast.makeText(ctx, ctx.getString(R.string.error_writing) , Toast.LENGTH_SHORT ).show();
            e.printStackTrace();
        } catch (NullPointerException e){
            Toast.makeText(ctx, "NULL POINTER EXCEPTION", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        adapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume(){
        super.onResume();
        adapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.admin, menu);
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
