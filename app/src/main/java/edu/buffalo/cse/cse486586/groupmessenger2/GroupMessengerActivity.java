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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {
    static final int SERVER_PORT = 10000;
    static final int[] PORTS_ALL = {11108, 11112, 11116, 11120, 11124};
    private final String TAG = "GroupMsgr";
    private String myPort;
    private ContentResolver mContentResolver;
    private final Uri mUri;
    static int messageCount;
    static int maxSequence;
    static int debug = 0;
    ConcurrentHashMap<String, Message> messageHash = new ConcurrentHashMap<String, Message>(50);
    PriorityQueue<Message> deliverableQueue = new PriorityQueue<Message>();
    Semaphore access = new Semaphore(2);

    private class MessagePriority {
        Message message;
        ArrayList proposedSequence;

        MessagePriority(Message msg) {
            message = new Message(msg);
            proposedSequence = (ArrayList) Collections.synchronizedList(new ArrayList<Integer>());
        }
    }

    public GroupMessengerActivity() {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        mUri = uriBuilder.build();
        messageCount = 0;
        maxSequence = 0;
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

        SendClickListener(EditText ed_) {
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

    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {
                do {
                    Socket clientHook = serverSocket.accept();
                    Log.e(TAG, clientHook.getInetAddress().getHostName());
                    /*
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientHook.getInputStream()));
                    String message = reader.readLine();
                    */
                    ObjectInputStream objStream = new ObjectInputStream(clientHook.getInputStream());
                    final Message receivedMessage = (Message) objStream.readObject();

                    if (receivedMessage.type == Message.MessageType.MESSAGE) {
                        String key = receivedMessage.pid + receivedMessage.message;
                        if (!messageHash.containsKey(key)) {
                            messageHash.put(key, receivedMessage);
                        } else
                            Log.e(TAG, "Already there");

                        receivedMessage.type = Message.MessageType.PROPOSED_SEQ;
                        receivedMessage.sequence = maxSequence++;
                        // Add to priority queue
                        deliverableQueue.add(receivedMessage);

                        Thread sendProposedSeq = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                sendMessage(receivedMessage, receivedMessage.port);
                            }
                        });
                        sendProposedSeq.start();
                    } else if (receivedMessage.type == Message.MessageType.PROPOSED_SEQ) {
                        proposedSequence(receivedMessage);
                    } else if (receivedMessage.type == Message.MessageType.AGREED_SEQ) {
                        String key = new String(receivedMessage.pid + receivedMessage.message);
                        if (messageHash.containsKey(key)) {
                            Message retrieve = messageHash.remove(key);
                            Log.v(TAG, "Dispatch " + retrieve.message);
                            maxSequence = Math.max(maxSequence, retrieve.sequence);
                            if (deliverableQueue.contains(retrieve)) {
                                deliverableQueue.remove(retrieve);
                                retrieve.agreed = true;
                                deliverableQueue.add(retrieve);
                            }
                            publishProgress(receivedMessage);
                        }
                    }
                    clientHook.close();
                } while (true);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        private void sendMessage(Message receivedMessage, int port) {
            Socket socket = null;
            try {
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        port);
                ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
                receivedMessage.setPort(myPort);
                outStream.writeObject(receivedMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendSeqAck(Message receivedMessage) {
            for (int i = 0; i < 5; i++) {
                sendMessage(receivedMessage, PORTS_ALL[i]);
            }
        }

        private void proposedSequence(Message receivedMessage) {

            String key = new String(receivedMessage.pid + receivedMessage.message);
            int sequence = receivedMessage.sequence;
            if (messageHash.containsKey(key)) {
                try {
                    access.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Message retrieve = messageHash.get(key);
                if (retrieve.sequence < sequence) {
                    retrieve.sequence = sequence;
                }
                retrieve.consensus++;
                if (retrieve.consensus == 5) {
                    retrieve.type = Message.MessageType.AGREED_SEQ;
                    /*new Thread(new Runnable() {
                        @Override
                        public void run() {*/
                    sendSeqAck(retrieve);
          /*              }
                    }).start();*/
                }
                messageHash.put(key, retrieve);

                access.release();
            }
/*            else{
                receivedMessage.consensus++;
                messageHash.put(key, receivedMessage);
            }*/
        }

        protected void onProgressUpdate(Message... receivedMessage) {
            if (receivedMessage[0].type == Message.MessageType.PROPOSED_SEQ) {
                proposedSequence(receivedMessage[0]);
            }
            if (receivedMessage[0].type == Message.MessageType.AGREED_SEQ) {
                if (deliverableQueue.peek() != null && deliverableQueue.peek().agreed) {
                    deliverMessage(deliverableQueue.poll().message);
                }
            }
        }


        private void deliverMessage(String... strings) {
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
            for (int i = 0; i < 5; i++) {
                sendMessage(i, msgs);
            }
            return null;
        }

        private void sendMessage(int i, String... msgs) {
            Socket socket = null;
            try {
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        PORTS_ALL[i]);
                String msgToSend = msgs[0];
                OutputStream out = socket.getOutputStream();
                ObjectOutputStream objStream = new ObjectOutputStream(out);
                Message msg = new Message(Message.MessageType.MESSAGE, msgToSend, msgs[1], 0);
                String key = new String(msg.pid + msg.message);
                if (!messageHash.containsKey(key))
                    messageHash.put(key, msg);

                objStream.writeObject(msg);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
