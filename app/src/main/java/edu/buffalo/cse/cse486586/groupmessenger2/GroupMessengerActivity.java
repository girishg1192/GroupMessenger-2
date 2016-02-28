package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final int SERVER_PORT = 10000;
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final int[] PORTS_ALL = {11108, 11112, 11116, 11120, 11124};
    private final String TAG = "GroupMsgr";
    private String myPort;
    private ContentResolver mContentResolver;
    private final Uri mUri;
    static int messageCount;
    public enum MessageType{MESSAGE, PROPOSED_SEQ, AGREED_SEQ};

    public GroupMessengerActivity(){
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        mUri = uriBuilder.build();
        messageCount = 0;
    }
    private class Message implements Serializable{
        public MessageType type;
        public String message;
        public int sequence;
        public int pid;
        public Message(MessageType type_, String message_, String port){
            type = type_;
            message = new String(message_);
            pid = portToPid(port);
        }
        private int portToPid(String port){
            int pid = ((Integer.parseInt(port)) - 11108)/4;
            return pid;
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        Log.v(TAG, tel.getLine1Number());
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        EditText editBox = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button4).setOnClickListener(new SendClickListener(editBox));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
    public class SendClickListener implements View.OnClickListener {
        private final EditText editText;
        private final String TAG = "SendClickListener";
        SendClickListener(EditText ed_){
            editText = ed_;
        }

        @Override
        public void onClick(View v) {
            String msg = editText.getText().toString() + "\n";
            editText.setText("");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            Log.v(TAG, msg);
        }
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
//            Log.e(TAG, "In here! waiting for a client");

            try {
                do {
                    Socket clientHook = serverSocket.accept();
                    Log.e(TAG, clientHook.getInetAddress().getHostName());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientHook.getInputStream()));
                    String message = reader.readLine();
                    publishProgress(message);
                    clientHook.close();
                    //serverSocket.close();
                } while (true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            String strReceived = strings[0].trim();
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append(strReceived + "\n");

            mContentResolver = getContentResolver();

            ContentValues mContentValues = new ContentValues();
            mContentValues.put("key", Integer.toString(messageCount));
            mContentValues.put("value", strReceived);
            mContentResolver.insert(mUri, mContentValues);
            messageCount++;

        }
    }
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
                for(int i=0; i<5; i++) {
                    sendMessage(i, msgs);
                }

            return null;
        }
        private void sendMessage(int i, String... msgs){
            Socket socket = null;
            try {
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        PORTS_ALL[i]);
                String msgToSend = msgs[0];
                OutputStream out = socket.getOutputStream();
                ObjectOutputStream objStream = new ObjectOutputStream(out);
                Message msg = new Message(MessageType.MESSAGE, msgToSend, msgs[1]);

                Log.e(TAG, "Sending =" + msgToSend + " " + msgs[1]);
                byte[] byteStream = msgToSend.getBytes("UTF-8");
                out.write(byteStream);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }
}
