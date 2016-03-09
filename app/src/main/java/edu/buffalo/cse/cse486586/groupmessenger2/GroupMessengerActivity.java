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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {
    static final int SERVER_PORT = 10000;
    static final int[] PORTS_ALL = {11108, 11112, 11116, 11120, 11124};
    static ArrayList<Integer> diededPorts = new ArrayList<Integer>();
    static AtomicInteger mActiveNodes = new AtomicInteger(5);
    private final String TAG = "GroupMsgr";
    private String myPort;
    private ContentResolver mContentResolver;
    private final Uri mUri;
    static int messageCount;
    static AtomicInteger maxSequence = new AtomicInteger();
    ConcurrentHashMap<String, Message> messageHash = new ConcurrentHashMap<String, Message>(50);
    PriorityBlockingQueue<Message> deliverableQueue = new PriorityBlockingQueue<Message>();
    Semaphore access = new Semaphore(2);
    AtomicBoolean deliver = new AtomicBoolean(false);
    private final ScheduledExecutorService pingAck = Executors.newScheduledThreadPool(2);
    ScheduledFuture<?> pingAckHandle = null;


    public GroupMessengerActivity() {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        mUri = uriBuilder.build();
        messageCount = 0;
        maxSequence.set(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
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
        pingAckHandle = pingAck.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sendHeartBeat();
            }
        }, 8, 2, TimeUnit.SECONDS);
//        pingAck.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                deliverQueued();
//            }
//        }, 2, 1, TimeUnit.SECONDS );
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
    public void sendHeartBeat(){
        Socket socket = null;
        Message check = new Message();
        int i=0;
        if (diededPorts.isEmpty()) {
            try {
                for (i = 0; i < 5; i++) {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            PORTS_ALL[i]);
                    ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
                    outStream.writeObject(check);
                }
            } catch (IOException e) {
                Log.e(TAG, PORTS_ALL[i] + " failed");
                diededPorts.add(Integer.valueOf(PORTS_ALL[i]));
                mActiveNodes.decrementAndGet();
                handleFailure(PORTS_ALL[i]);
                e.printStackTrace();
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            do {
                try {
/*                    access.acquire();
                    if (deliver.getAndSet(false))
                        while (deliverableQueue.peek() != null && deliverableQueue.peek().agreed) {
                            Log.e(TAG, "Deliver preempt " + deliverableQueue.peek().message);
                            publishProgress(deliverableQueue.poll());
                        }
                    access.release();*/
                    Socket clientHook = serverSocket.accept();
                    /*
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientHook.getInputStream()));
                    String message = reader.readLine();
                    */
                    ObjectInputStream objStream = new ObjectInputStream(clientHook.getInputStream());
                    Message receivedMessage = (Message) objStream.readObject();
                    if (receivedMessage != null && receivedMessage.port!=2) {
                        if (receivedMessage.type == Message.MessageType.MESSAGE) {
                            Log.e(TAG, "Received message port " + receivedMessage.port);
                            if (!diededPorts.contains(new Integer(receivedMessage.port))) {
                                String key = receivedMessage.pid + receivedMessage.message;
//                                receivedMessage.sequence = Math.max(maxSequence.get(), receivedMessage.sequence);
                                if (!messageHash.containsKey(key)) {
                                    messageHash.put(key, receivedMessage);
                                } else
                                    Log.e(TAG, "Already there");

                                access.acquire();
                                if (!deliverableQueue.contains(receivedMessage)) {
                                    //TODO Changed ordering
                                    deliverableQueue.add(receivedMessage);
                                }
                                access.release();
                                final Message sendProposed = new Message(receivedMessage);

                                sendProposed.type = Message.MessageType.PROPOSED_SEQ;
                                sendProposed.sequence = maxSequence.getAndIncrement();
                                receivedMessage.print();
                                // Add to priority queue
                                Thread sendProposedSeq = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        sendMessage(sendProposed, sendProposed.port);
                                    }
                                });
                                sendProposedSeq.start();
                            }
                        } else if (receivedMessage.type == Message.MessageType.PROPOSED_SEQ) {
                            proposedSequence(receivedMessage);
                        } else if (receivedMessage.type == Message.MessageType.AGREED_SEQ) {
                            String key = receivedMessage.pid + receivedMessage.message;
                            if (messageHash.containsKey(key)) {
                                Message retrieve = messageHash.remove(key);
                                Log.v(TAG, "Dispatch " + receivedMessage.sequence + " " + receivedMessage.message);
                                maxSequence.set(Math.max(maxSequence.get(), receivedMessage.sequence));
                                access.acquire();
                                if (deliverableQueue.peek() != null)
                                    Log.e(TAG, "Waiting for :" + deliverableQueue.peek().message + " " + deliverableQueue.peek().sequence);
                                if (deliverableQueue.contains(retrieve)) {
                                    deliverableQueue.remove(retrieve);
                                    retrieve.sequence = receivedMessage.sequence;
                                    retrieve.agreed = true;
                                    deliverableQueue.add(retrieve);
                                    while (deliverableQueue.peek() != null && deliverableQueue.peek().agreed) {
                                        Log.e(TAG, "Deliver " + deliverableQueue.peek().message);
                                        publishProgress(deliverableQueue.poll());
                                    }
                                }
                                while (deliverableQueue.peek() != null && deliverableQueue.peek().agreed) {
                                    Log.e(TAG, "Deliver 1" + deliverableQueue.peek().message);
                                    publishProgress(deliverableQueue.poll());
                                }
                                access.release();
                            }
                        }
                    }
                    clientHook.close();

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (true);
        }


        private void proposedSequence(Message receivedMessage) {
            String key = receivedMessage.pid + receivedMessage.message;
            int sequence = receivedMessage.sequence;
            if (messageHash.containsKey(key) && messageHash.get(key).consensus < mActiveNodes.get()) {
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
                retrieve.totalConsensus &= ~(1 << receivedMessage.pid);
                deliverableQueue.remove(retrieve);
                deliverableQueue.add(retrieve);
                Log.e(TAG, "_--------------Proposed----------");
                retrieve.print();
                Log.e(TAG, "----------------Proposed End-----------");
                if (retrieve.consensus >= mActiveNodes.get() && retrieve.type!= Message.MessageType.AGREED_SEQ) {
                    retrieve.type = Message.MessageType.AGREED_SEQ;
                    Log.e(TAG, "---- Agreed sequence ----");
                    retrieve.print();
                    Log.e(TAG, "--------end--------");
                    sendSeqAck(retrieve);
                }
                messageHash.put(key, retrieve);
                access.release();
            }

        }

        protected void onProgressUpdate(Message... receivedMessage) {
            deliverMessage(receivedMessage[0].message);
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

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            int seq = maxSequence.getAndIncrement();
            for (int i = 0; i < 5; i++) {
                sendMessage(i, seq, msgs);
            }
            return null;
        }

        private void sendMessage(int i, int seq, String... msgs) {
            Socket socket;
            if (!diededPorts.contains(Integer.valueOf(PORTS_ALL[i]))) {
                try {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            PORTS_ALL[i]);
                    String msgToSend = msgs[0];
                    OutputStream out = socket.getOutputStream();
                    ObjectOutputStream objStream = new ObjectOutputStream(out);
                    Message msg = new Message(Message.MessageType.MESSAGE, msgToSend, msgs[1], 0, seq);
                    String key = msg.pid + msg.message;
                    access.acquire();
                    if (!messageHash.containsKey(key))
                        messageHash.put(key, msg);
                    if(!deliverableQueue.contains(msg))
                        deliverableQueue.add(msg);
                    access.release();

                    objStream.writeObject(msg);
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, PORTS_ALL[i] + " failed");
                    diededPorts.add(PORTS_ALL[i]);
                    mActiveNodes.decrementAndGet();
                    handleFailure(PORTS_ALL[i]);
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleFailure(int port) {
        try {
            access.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        pingAckHandle.cancel(true);
        Log.e(TAG, "______FAILURE_______");
        for (Map.Entry<String, Message> iter : messageHash.entrySet()) {
            Message msg = iter.getValue();
            int pid = (port - 11108) / 4;
            if (msg.port == port) {
                msg.agreed = true;
                deliverableQueue.remove(msg);
                deliverableQueue.add(msg);
                messageHash.put(iter.getKey(), msg);
//                deliverableQueue.remove(msg);
//                messageHash.remove(iter.getKey());
            } else if ((msg.totalConsensus & (1 << pid)) != 0 &&msg.port==Integer.parseInt(myPort)) {
                Log.e(TAG, "waiting for dead process");
                msg.print();
                if (msg.consensus == mActiveNodes.get()) {
                    msg.type = Message.MessageType.AGREED_SEQ;
                    final Message send = new Message(msg);
                    Thread sendProposedSeq = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            sendSeqAck(send);
                        }
                    });
                    sendProposedSeq.start();
                } else {
                    msg.consensus++;
                    msg.totalConsensus &= ~(1 << pid);
                }
                messageHash.put(iter.getKey(), msg);
            }
            if (msg.consensus == mActiveNodes.get() && msg.type!= Message.MessageType.AGREED_SEQ) {
                msg.type = Message.MessageType.AGREED_SEQ;
                final Message send = new Message(msg);
                Thread sendProposedSeq = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        sendSeqAck(send);
                    }
                });
                sendProposedSeq.start();
            }
        }

        if (deliverableQueue.peek() != null && deliverableQueue.peek().agreed)
            deliver.set(true);

        Log.e(TAG, "______________END___________");
        access.release();
    }

    private void sendSeqAck(Message receivedMessage) {
        for (int i = 0; i < 5; i++) {
            sendMessage(receivedMessage, PORTS_ALL[i]);
        }
    }

    private void sendMessage(Message receivedMessage, int port) {
        Socket socket;
        if (!diededPorts.contains(Integer.valueOf(port))) {
            try {
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        port);
                ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
                receivedMessage.setPort(myPort);
                outStream.writeObject(receivedMessage);
            } catch (IOException e) {
                Log.e(TAG, port + " failed");
                diededPorts.add(port);
                mActiveNodes.decrementAndGet();
                handleFailure(port);
                e.printStackTrace();
            }
        }
    }
    private void deliverQueued(){
        try {
            access.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.e("TIME", "TimedTask");
        while (deliverableQueue.peek() != null && deliverableQueue.peek().agreed) {
                Log.e(TAG, "Deliver preempt " + deliverableQueue.peek().message);
                deliverMessage(deliverableQueue.poll().message);
            }
        access.release();
    }
}

