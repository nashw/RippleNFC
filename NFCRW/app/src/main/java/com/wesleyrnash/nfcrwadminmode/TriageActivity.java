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
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.msgpack.MessagePack;
import org.msgpack.packer.Packer;
import org.msgpack.template.Template;
import static org.msgpack.template.Templates.tMap;
import static org.msgpack.template.Templates.TString;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class TriageActivity extends Activity {

    //initialize values for read and write mode and set initial mode to WRITE_MODE
    final int READ_MODE = 0;
    final int WRITE_MODE = 1;
    int mode = WRITE_MODE;

    //set up NFC variables
    NfcAdapter adapter;
    PendingIntent pendingIntent;
    IntentFilter writeTagFilters[];
    Tag mytag;
    Context ctx;

    TextView lastName;
    TextView firstName;
    TextView tagId;

    //table 1 setup
    TableLayout table1;
    TableRow table1Row1;
    TextView table1Row1Time;
    TextView table1Row1BP;
    TextView table1Row1Pulse;
    TextView table1Row1Respiration;

    //table 2 setup
    TableLayout table2;
    TableRow table2Row1;
    TextView table2Row1Time;
    TextView table2Row1DS;
    TextView table2Row1Dose;

    //ArrayLists to contain all the TextViews and their corresponding headers
    ArrayList<TextView> textViews;
    ArrayList<String> headers;

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String TAG = "NFCRW";

    //set up encryption variables
    public static final String key = "TestTestTestTest";
    Key aesKey;
    Cipher cipher;

    //button to toggle between reading and writing
    Button toggleMode;

    JSONObject jsonMessage;

    MessagePack msgPack;
    Template<Map<String, String>> mapTemplate;
    Map<String, String> map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_triage);

        ctx=this;

        //set up encryption key and cipher
        try{
            aesKey = new SecretKeySpec(key.getBytes(), "AES");
            cipher = Cipher.getInstance("AES");
        } catch (Exception e){
            e.printStackTrace();
        }

        jsonMessage = new JSONObject();
        try {
            jsonMessage.put("s", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        msgPack = new MessagePack();
        mapTemplate = tMap(TString, TString);
        map = new HashMap<String, String>();

        //initialize UI elements
        lastName = (TextView)findViewById(R.id.et_lastName);
        firstName = (TextView)findViewById(R.id.et_firstName);
        tagId = (TextView) findViewById(R.id.tv_tagID);

        table1 = (TableLayout) findViewById(R.id.tl_table1);
        table2 = (TableLayout) findViewById(R.id.tl_table2);

        table1Row1 = (TableRow) findViewById(R.id.tr_table1_row1);
        table2Row1 = (TableRow) findViewById(R.id.tr_table2_row1);

        table1Row1Time = (TextView) findViewById(R.id.et_table1_row1_time);
        table1Row1BP = (TextView) findViewById(R.id.et_table1_row1_bp);
        table1Row1Pulse = (TextView) findViewById(R.id.et_table1_row1_pulse);
        table1Row1Respiration = (TextView) findViewById(R.id.et_table1_row1_respiration);

        table2Row1Time = (TextView) findViewById(R.id.et_table2_row1_time);
        table2Row1DS = (TextView) findViewById(R.id.et_table2_row1_ds);
        table2Row1Dose = (TextView) findViewById(R.id.et_table2_row1_dose);

        //add TextViews and headers to ArrayLists
        textViews = new ArrayList<TextView>();
        headers = new ArrayList<String>();
        textViews.add(lastName);
        headers.add("ln");
        textViews.add(firstName);
        headers.add("fn");

        textViews.add(table1Row1Time);
        headers.add("t1r1t");
        textViews.add(table1Row1BP);
        headers.add("t1r1b");
        textViews.add(table1Row1Pulse);
        headers.add("t1r1p");
        textViews.add(table1Row1Respiration);
        headers.add("t1r1r");

        textViews.add(table2Row1Time);
        headers.add("t2r1t");
        textViews.add(table2Row1DS);
        headers.add("t2r1s");
        textViews.add(table2Row1Dose);
        headers.add("t2r1d");

        toggleMode = (Button) findViewById(R.id.button_readwrite);

        //set click listener for toggle button
        toggleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //when clicked, the mode changes to whichever mode it is not currently
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

        //set up intent filter to handle intents when NFC is detected
        adapter = NfcAdapter.getDefaultAdapter(this);
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[] { tagDetected };
    }

    //gets the message and records from the tag
    private void read(Tag tag) throws NullPointerException{
        Ndef ndef = Ndef.get(tag);

        NdefMessage ndefMessage = ndef.getCachedNdefMessage();

        NdefRecord[] records = ndefMessage.getRecords();
        //check if the records are of acceptable format. If so, read the message
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

    //reads the message contained on the tag
    private void readText(NdefRecord record) throws UnsupportedEncodingException {
        //get the payload and id from the record
        byte[] payload = record.getPayload();
        byte[] idBytes = record.getId();
        String id = new String(idBytes);

        //check which type of encoding the payload is in
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

        // Get the Language Code
        int languageCodeLength = payload[0] & 0063;

        //extract the message from the payload (get rid of the language code)
        byte[] textBytes = Arrays.copyOfRange(payload, languageCodeLength + 1, payload.length);

        updateTextViews(id, textBytes);
    }

    //decrypts the message and sets the TextViews to the values specified by the message
    public void updateTextViews(String id, byte[] textBytes){
        //set the ID field
        tagId.setText("Tag ID: " + id);

        //check to make sure the message isn't empty
        if (textBytes != null) {
            Log.d(TAG, new String(textBytes));
            //create an ArrayList to store the bytes of the message
            ArrayList<Byte> resultBytes = new ArrayList<Byte>();
            try{
                //decrypt the message into a byte array
                cipher.init(Cipher.DECRYPT_MODE, aesKey);
                byte[] decoded = cipher.doFinal(textBytes);
                //store the byte array into the ArrayList
                for(int i = 0; i < decoded.length; i ++)
                    resultBytes.add(i, decoded[i]);
                Log.d(TAG, "decoded them bytes");
            } catch (Exception e){
                e.printStackTrace();
                Log.d(TAG, "Exception", e);
            }
            //convert the ArrayList back into a byte array
            byte[] decodedBytes = new byte[resultBytes.size()];
            for(int i = 0; i < resultBytes.size(); i++)
                decodedBytes[i] = resultBytes.get(i);
            //byte[] decodedBytes = Base64.decode(result.getBytes(), Base64.DEFAULT);
            //convert the message to a string
            //String result = new String(decodedBytes);

//            String readString = new String(decodedBytes);
//            JSONObject result = null;
//            try {
//                result = new JSONObject(readString);
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }

            Map<String, String> result = new HashMap<String, String>();
            try {
                result = msgPack.read(decodedBytes, mapTemplate);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.d(TAG, result.toString());

//            //parse the message
//            String delims = "[,]+";
//            String[] tokens = result.split(delims);

//            //create ArrayLists to hold the headers and texts in the message
//            ArrayList<String> readTextViews = new ArrayList<String>();
//            ArrayList<String> messages = new ArrayList<String>();

//            for(int i = 0; i < tokens.length; i++){
//                //add headers
//                if(i % 2 == 1)
//                    readTextViews.add(tokens[i]);
//                //add texts
//                if(i % 2 == 0 && i != 0)
//                    messages.add(tokens[i]);
//            }

//            //loop through all TextViews in the UI
//            for(int j = 0; j < textViews.size(); j++){
//                //clear any text in the TextView
//                textViews.get(j).setText("");
//                //loop through the number of text views that were read from the tag
//                for(int i = 0; i < readTextViews.size(); i++){
//                    //check if the current header is one that was read from the tag
//                    if(headers.get(j).equals(readTextViews.get(i))){
//                        //set the text of the corresponding TextView to the corresponding message
//                        textViews.get(j).setText(messages.get(i));
//                        //remove the read header and message since no other TextView will have the same header
//                        readTextViews.remove(i);
//                        messages.remove(i);
//                        break;
//                    }
//                }
//            }

            //loop through all TextViews in the UI
            for(int i = 0; i < textViews.size(); i++){
                textViews.get(i).setText(result.get(headers.get(i)));
            }
        }

    }

    //create the message to write to the tag from the EditText views.
    private String createString(){
        //initialize message with a starting character for easier parsing
        String message = "s";
        //loop through all EditText views
        for(int i = 0; i < textViews.size(); i++){
            //check if the text is not empty
            if(!textViews.get(i).getText().toString().equals(""))
                //add the header of the view and the text to the message separated by commas
                message += "," + headers.get(i) + "," + textViews.get(i).getText().toString();
        }


        return message;
    }

    //create the message to write to the tag from the EditText views.
    private void createJSON(){
        //initialize message with a starting character for easier parsing
        //String message = "s";
        for(String header : headers)
            jsonMessage.remove(header);

        //loop through all EditText views
        for(int i = 0; i < textViews.size(); i++){
            //check if the text is not empty
            if(!textViews.get(i).getText().toString().equals(""))
                //add the header of the view and the text to the message separated by commas
                //message += "," + headers.get(i) + "," + textViews.get(i).getText().toString();
                try {
                    jsonMessage.put(headers.get(i), textViews.get(i).getText().toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
        }


        //return message;
    }

    //create the message to write to the tag from the EditText views.
    private void createMap(){
        //initialize message with a starting character for easier parsing
        map.put("s", "");
        //loop through all EditText views
        for(int i = 0; i < textViews.size(); i++){
            //check if the text is not empty
            if(!textViews.get(i).getText().toString().equals(""))
                //add the header of the view and the text to the message separated by commas
                map.put(headers.get(i), textViews.get(i).getText().toString());
        }
    }

//    //writes the message to the tag
//    private void write(String text, Tag tag) throws IOException, FormatException, NullPointerException {
//        // Get an instance of Ndef for the tag.
//        Ndef ndef = Ndef.get(tag);
//
//        //store the ID on the tag
//        byte[] id = getId(ndef);
//
//        //create NDEF records
//        NdefRecord[] records = { createRecord(text, id) };
//        //put the records into an NDEF message
//        NdefMessage message = new NdefMessage(records);
//        // Enable I/O
//        ndef.connect();
//        // Write the message
//        ndef.writeNdefMessage(message);
//        // Close the connection
//        ndef.close();
//    }

    //writes the message to the tag
    private void write(Tag tag) throws IOException, FormatException, NullPointerException {
        // Get an instance of Ndef for the tag.
        Ndef ndef = Ndef.get(tag);

        //store the ID on the tag
        byte[] id = getId(ndef);

        //create NDEF records
        NdefRecord[] records = { createRecord(id) };
        //put the records into an NDEF message
        NdefMessage message = new NdefMessage(records);
        // Enable I/O
        ndef.connect();
        // Write the message
        ndef.writeNdefMessage(message);
        // Close the connection
        ndef.close();
    }

    //get the ID that is currently on the tag so it is not overwritten
    private byte[] getId(Ndef ndef){
        //get the message on the tag
        NdefMessage ndefMessage = ndef.getCachedNdefMessage();

        //get the records from the message
        NdefRecord[] records = ndefMessage.getRecords();
        //check if any records are of the proper type and format
        for (NdefRecord ndefRecord : records) {
            if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                //get the ID from the record
                return ndefRecord.getId();
            }
        }
        //if no ID found, return default
        return "default".getBytes();
    }

//    //turns a string of text into an NDEF formatted record
//    private NdefRecord createRecord(String text, byte[] id) throws UnsupportedEncodingException {
//        Log.d(TAG, "creating record: " + text);
//
//        //set the language
//        String lang       = "en";
//
//        //initialize byte array for the encoded message
//        byte[] encodedTextBytes = "".getBytes();
//        //encrypt the message
//        try{
//            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
//            encodedTextBytes = cipher.doFinal(text.getBytes());
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//        Log.d(TAG, new String(encodedTextBytes));
//        Log.d(TAG, "" + encodedTextBytes.length);
//        //byte[] encodedTextBytes = Base64.encode(text.getBytes(), Base64.DEFAULT);
//        //byte[] textBytes  = text.getBytes();
//
//        byte[] langBytes  = lang.getBytes("US-ASCII");
//        int    langLength = langBytes.length;
//        int    textLength = encodedTextBytes.length;
////        int    textLength = textBytes.length;
//
//        //initialize byte array for the payload
//        byte[] payload    = new byte[1 + langLength + textLength];
//
//        // set status byte (see NDEF spec for actual bits)
//        payload[0] = (byte) langLength;
//
//        // copy langbytes and textbytes into payload
//        System.arraycopy(langBytes,        0, payload, 1,              langLength);
//        System.arraycopy(encodedTextBytes, 0, payload, 1 + langLength, textLength);
////        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);
//
////        String id = "RippleNFC";
//
//        Log.d(TAG, new String(payload));
//
//        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,  NdefRecord.RTD_TEXT,  id, payload);
//
//        return recordNFC;
//    }

    //turns a string of text into an NDEF formatted record
    private NdefRecord createRecord(byte[] id) throws UnsupportedEncodingException {
        Log.d(TAG, "creating record: ");

        //set the language
        String lang       = "en";

        //initialize byte array for the encoded message
        byte[] encodedTextBytes = "".getBytes();
        //encrypt the message
        try{
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            encodedTextBytes = cipher.doFinal(jsonMessage.toString().getBytes());
        } catch (Exception e){
            e.printStackTrace();
        }

        String message = createString();
        byte[] stringBytes = "".getBytes();
        try{
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            stringBytes = cipher.doFinal(message.getBytes());
        } catch (Exception e){
            e.printStackTrace();
        }

        createMap();
        byte[] msgpackBytes = "".getBytes();
        try {
            msgpackBytes = msgPack.write(map);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            msgpackBytes = cipher.doFinal(msgpackBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d(TAG, new String(msgpackBytes));
        Log.d(TAG, "" + msgpackBytes.length);

        Log.d(TAG, new String(stringBytes));
        Log.d(TAG, "" + stringBytes.length);

        Log.d(TAG, new String(encodedTextBytes));
        Log.d(TAG, "" + encodedTextBytes.length);
        //byte[] encodedTextBytes = Base64.encode(text.getBytes(), Base64.DEFAULT);
        //byte[] textBytes  = text.getBytes();

        byte[] langBytes  = lang.getBytes("US-ASCII");
        int    langLength = langBytes.length;
        int    textLength = msgpackBytes.length;
//        int    textLength = textBytes.length;

        //initialize byte array for the payload
        byte[] payload    = new byte[1 + langLength + textLength];

        // set status byte (see NDEF spec for actual bits)
        payload[0] = (byte) langLength;

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes,        0, payload, 1,              langLength);
        System.arraycopy(msgpackBytes, 0, payload, 1 + langLength, textLength);
//        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

//        String id = "RippleNFC";

        Log.d(TAG, new String(payload));

        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,  NdefRecord.RTD_TEXT,  id, payload);

        return recordNFC;
    }


    //this method is called when a new intent is found. In this case, it is called whenever the tag is brought within range of the Android device
    @Override
    protected void onNewIntent(Intent intent){
        //check which mode is currently active
        if(mode == WRITE_MODE){
            //check that the intent that called this method was that an NFC tag was discovered
            if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
                //set NFC tag object to the data contained in the intent (i.e. the data on the tag)
                mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            }
            try {
                //check that the tag was properly read
                if(mytag==null){
                    Toast.makeText(ctx, ctx.getString(R.string.error_detected), Toast.LENGTH_SHORT).show();
                }else{
                    //create the message to write to the tag
                    //String message = createMessage();
                    createJSON();
                    //write(message,mytag);
                    write(mytag);
                    //notify the user of successful writing
                    Toast.makeText(ctx, ctx.getString(R.string.ok_writing), Toast.LENGTH_LONG ).show();
                    //set all the text fields to Test for testing purposes
                    for(int i = 0; i < textViews.size(); i++)
                        textViews.get(i).setText("Test");
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

    //handles the intent in read mode
    private void handleIntent(Intent intent) {
        //check that the intent is for discovering an NFC tag
        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Log.d(TAG, "NDEF DISCOVERED");

            //check that the tag is in plain text
            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {

                //store data from the tag into a tag object
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                try{
                    //read from the tag
                    read(tag);
                } catch (NullPointerException e){
                    e.printStackTrace();
                    Log.e(TAG, "Null Pointer", e);
                }
            }
        //even if the tag is not in NDEF format, check that it's still a tag intent
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) || NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Log.d(TAG, "TECH DISCOVERED");

            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = Ndef.class.getName();

            //check if the tag is of any of the types of acceptable tags
            for (String tech : techList) {
                if (searchedTech.equals(tech)) {
                    //if so, read from the tag
                    read(tag);
                    break;
                }
            }
        }


    }

    //I'm not to sure why the foreground dispatch needs to be started and stopped in onPause and onResume
    //but I do know that they are necessary and allow the program to have priority to handle intents
    //without the activity chooser popping up
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
        adapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }

    private void WriteModeOff(){
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

    //These two methods were generated with the program, so I don't know if they are necessary
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
}
