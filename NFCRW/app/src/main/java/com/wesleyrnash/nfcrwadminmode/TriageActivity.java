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
import android.os.AsyncTask;
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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

public class TriageActivity extends Activity {

    final int READ_MODE = 0;
    final int WRITE_MODE = 1;
    int mode = WRITE_MODE;

    NfcAdapter adapter;
    PendingIntent pendingIntent;
    IntentFilter writeTagFilters[];
    boolean writeMode;
    Tag mytag;
    Context ctx;
    //Button btnWrite;

    TextView lastName;
    TextView firstName;
    TextView tagId;
    ArrayList<TextView> textViews;
    ArrayList<String> headers;

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "NFCRW";

    private TextView readMessage;
    private NfcAdapter mNfcAdapter;

    Button toggleMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_triage);

        ctx=this;
        //btnWrite = (Button) findViewById(R.id.button_writeMessage);
        lastName = (TextView)findViewById(R.id.et_lastName);
        firstName = (TextView)findViewById(R.id.et_firstName);
        tagId = (TextView) findViewById(R.id.tv_tagID);

        textViews = new ArrayList<TextView>();
        headers = new ArrayList<String>();
        textViews.add(lastName);
        headers.add("ln");
        textViews.add(firstName);
        headers.add("fn");

        toggleMode = (Button) findViewById(R.id.button_readwrite);

        toggleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mode == WRITE_MODE){
                    mode = READ_MODE;
                    toggleMode.setText(R.string.string_readMode);
                    adapter = NfcAdapter.getDefaultAdapter(ctx);
                    handleIntent(getIntent());
                } else {
                    mode = WRITE_MODE;
                    toggleMode.setText(R.string.string_writeMode);
                }
            }
        });

        adapter = NfcAdapter.getDefaultAdapter(this);
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[] { tagDetected };
    }

    private void read(Tag tag){
        Ndef ndef = Ndef.get(tag);

        NdefMessage ndefMessage = ndef.getCachedNdefMessage();

        NdefRecord[] records = ndefMessage.getRecords();
        for (NdefRecord ndefRecord : records) {
            if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                try {
                    readText(ndefRecord);
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "Unsupported Encoding", e);
                }
            }
        }
    }

    private void readText(NdefRecord record) throws UnsupportedEncodingException {
        byte[] payload = record.getPayload();
        byte[] idBytes = record.getId();
        String id = new String(idBytes);

        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

        // Get the Language Code
        int languageCodeLength = payload[0] & 0063;

        // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
        // e.g. "en"

        // Get the Text
        String text =  new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        updateTextViews(id, text);
    }

    public void updateTextViews(String id, String result){
        tagId.setText("Tag ID: " + id);

        if (result != null) {
            Log.d(TAG, "in onPostExecute, not NULL");
            Log.d(TAG, result);
            byte[] decodedBytes = Base64.decode(result.getBytes(), Base64.DEFAULT);
            result = new String(decodedBytes);
            Log.d(TAG, result);
            //Toast.makeText(ctx, "onPostExecute", Toast.LENGTH_SHORT).show();
            //readMessage.setText("");
            String delims = "[,]+";
            String[] tokens = result.split(delims);
            ArrayList<String> readTextViews = new ArrayList<String>();
            ArrayList<String> messages = new ArrayList<String>();
            for(int i = 0; i < tokens.length; i++){
                if(i % 2 == 1)
                    readTextViews.add(tokens[i]);
                if(i % 2 == 0 && i != 0)
                    messages.add(tokens[i]);
            }
            for(int i = 0; i < readTextViews.size(); i++){
                if(messages.get(i).equals(" "))
                    messages.set(i, "");
                if(readTextViews.get(i).equals("ln"))
                    lastName.setText(messages.get(i));
                if(readTextViews.get(i).equals("fn"))
                    firstName.setText(messages.get(i));
            }
        }

    }

    private String createMessage(){
        String message = "s";
        for(int i = 0; i < textViews.size(); i++){
            if(!textViews.get(i).getText().toString().equals(""))
                message += "," + headers.get(i) + "," + textViews.get(i).getText().toString();
            else
                message += "," + headers.get(i) + "," + " ";
        }


        return message;
    }



    private void write(String text, Tag tag) throws IOException, FormatException, NullPointerException {


        // Get an instance of Ndef for the tag.
        Ndef ndef = Ndef.get(tag);

        String id = getId(ndef);

        NdefRecord[] records = { createRecord(text, id) };
        NdefMessage message = new NdefMessage(records);
        // Enable I/O
        ndef.connect();
        // Write the message
        ndef.writeNdefMessage(message);
        // Close the connection
        ndef.close();
    }

    private String getId(Ndef ndef){
        NdefMessage ndefMessage = ndef.getCachedNdefMessage();

        NdefRecord[] records = ndefMessage.getRecords();
        for (NdefRecord ndefRecord : records) {
            if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                byte[] idBytes = ndefRecord.getId();
                String id = new String(idBytes);
                return id;
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
        if(mode == WRITE_MODE){
            if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
                mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                //Toast.makeText(this, this.getString(R.string.ok_detection) + mytag.toString(), Toast.LENGTH_LONG ).show();
                //btnWrite.setText(R.string.ok_detection);
            }
            try {
                if(mytag==null){
                    Toast.makeText(ctx, ctx.getString(R.string.error_detected), Toast.LENGTH_SHORT).show();
                }else{
                    String message = createMessage();
                    write(message,mytag);
                    Toast.makeText(ctx, ctx.getString(R.string.ok_writing), Toast.LENGTH_LONG ).show();
                    //btnWrite.setText("Write successful!");
                    lastName.setText("Test");
                    firstName.setText("Test");
                }
            } catch (IOException e) {
                Toast.makeText(ctx, ctx.getString(R.string.error_writing), Toast.LENGTH_SHORT ).show();
                e.printStackTrace();
                //btnWrite.setText(R.string.string_writeMessage);
            } catch (FormatException e) {
                Toast.makeText(ctx, ctx.getString(R.string.error_writing) , Toast.LENGTH_SHORT ).show();
                e.printStackTrace();
                //btnWrite.setText(R.string.string_writeMessage);
            } catch (NullPointerException e){
                Toast.makeText(ctx, "NULL POINTER EXCEPTION", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                //btnWrite.setText("NULL POINTER EXCEPTION");
            }

        }
        if(mode == READ_MODE){
            //Toast.makeText(ctx, "Handling intent in Read Mode", Toast.LENGTH_SHORT).show();
            handleIntent(intent);
        }
    }

    private void handleIntent(Intent intent) {
        Toast.makeText(ctx, "in handleIntent", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "in handleIntent");
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Log.d(TAG, "NDEF DISCOVERED");

            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                Log.d(TAG, "MIME TEXT PLAIN");

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                read(tag);
                //new NdefReaderTask().execute(tag);

            } else {
                Log.d(TAG, "Wrong mime type: " + type);
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) || NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Log.d(TAG, "TECH DISCOVERED");


            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    read(tag);
                    //new NdefReaderTask().execute(tag);
                    break;
                }
            }
        } else {
            Log.d(TAG, "No NFC found??");
            //Log.d(TAG, action);
        }


    }

    @Override
    public void onPause(){
        if(mode == READ_MODE)
            stopForegroundDispatch(this, adapter);
        super.onPause();

        if(mode == WRITE_MODE)
            WriteModeOff();
    }

    @Override
    public void onResume(){
        super.onResume();
        if(mode == READ_MODE)
            setupForegroundDispatch(this, adapter);
        if(mode == WRITE_MODE)
            WriteModeOn();
    }

    private void WriteModeOn(){
        writeMode = true;
        adapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }

    private void WriteModeOff(){
        writeMode = false;
        adapter.disableForegroundDispatch(this);
    }

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.triage, menu);
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

//    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {
//
//        @Override
//        protected String doInBackground(Tag... params) {
//
//            Log.d(TAG, "in doInBackground");
//
//            Tag tag = params[0];
//
//            Ndef ndef = Ndef.get(tag);
//            if (ndef == null) {
//                // NDEF is not supported by this Tag.
//                return null;
//            }
//
//            NdefMessage ndefMessage = ndef.getCachedNdefMessage();
//
//            NdefRecord[] records = ndefMessage.getRecords();
//            for (NdefRecord ndefRecord : records) {
//                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
//                    try {
//                        return readText(ndefRecord);
//                    } catch (UnsupportedEncodingException e) {
//                        Log.e(TAG, "Unsupported Encoding", e);
//                    }
//                }
//            }
//
//            return null;
//        }
//
//        private String readText(NdefRecord record) throws UnsupportedEncodingException {
//
//            Log.d(TAG, "in readText");
//
//			/*
//			 * See NFC forum specification for "Text Record Type Definition" at 3.2.1
//			 *
//			 * http://www.nfc-forum.org/specs/
//			 *
//			 * bit_7 defines encoding
//			 * bit_6 reserved for future use, must be 0
//			 * bit_5..0 length of IANA language code
//			 */
//
//            byte[] payload = record.getPayload();
//            byte[] id = record.getId();
//            Log.d(TAG, new String(id));
//
//            // Get the Text Encoding
//            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
//
//            // Get the Language Code
//            int languageCodeLength = payload[0] & 0063;
//
//            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
//            // e.g. "en"
//
//            // Get the Text
//            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
//            Log.d(TAG, "in onPostExecute");
//            if (result != null) {
//                Log.d(TAG, "in onPostExecute, not NULL");
//                Log.d(TAG, result);
//                byte[] decodedBytes = Base64.decode(result.getBytes(), Base64.DEFAULT);
//                result = new String(decodedBytes);
//                Log.d(TAG, result);
//                //Toast.makeText(ctx, "onPostExecute", Toast.LENGTH_SHORT).show();
//                //readMessage.setText("");
//                String delims = "[,]+";
//                String[] tokens = result.split(delims);
//                ArrayList<String> readTextViews = new ArrayList<String>();
//                ArrayList<String> messages = new ArrayList<String>();
//                for(int i = 0; i < tokens.length; i++){
//                    if(i % 2 == 1)
//                        readTextViews.add(tokens[i]);
//                    if(i % 2 == 0 && i != 0)
//                        messages.add(tokens[i]);
//                }
//                for(int i = 0; i < readTextViews.size(); i++){
//                    Log.d(TAG, "setting texts");
//                    if(readTextViews.get(i).equals("ln"))
//                        lastName.setText(messages.get(i));
//                    if(readTextViews.get(i).equals("fn"))
//                        firstName.setText(messages.get(i));
//                }
//            }
//        }
//    }
}
