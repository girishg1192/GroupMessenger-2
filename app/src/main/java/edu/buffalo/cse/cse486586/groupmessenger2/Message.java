package edu.buffalo.cse.cse486586.groupmessenger2;

import android.util.Log;

import java.io.Serializable;

/**
 * Created by girish on 2/28/16.
 */
public class Message implements Serializable, Comparable {
    @Override
    public int compareTo(Object o) {
        Message cmp = (Message) o;
        int ret = (this.sequence < cmp.sequence) ? -1: 1;
        if(this.sequence == cmp.sequence && this!=cmp){
            ret = pid<cmp.pid ? -1: 1;
        }
        return ret;
    }
    @Override
    public boolean equals(Object o) {
        Message cmp = (Message)o;
        if(port == cmp.port && message.equals(cmp.message))
            return true;
        return false;
    }

    public enum MessageType{MESSAGE, PROPOSED_SEQ, AGREED_SEQ};
    public MessageType type;
    public String message;
    public int sequence;
    public int pid;
    public int port;
    public int consensus;
    boolean agreed;
    public int totalConsensus;

    public Message(){
        port = 2;
    }
    public Message(MessageType type_, String message_, String port_, int consensus, int sequence){
        type = type_;
        message = new String(message_);
        pid = portToPid(port_);
        port = Integer.parseInt(port_);
        this.consensus = consensus;
        agreed = false;
        totalConsensus = (1<<5)-1;
        this.sequence = sequence;
    }
    public Message(Message copy){
        this.type = copy.type;
        message = new String(copy.message);
        sequence = copy.sequence;
        pid = copy.pid;
        port = copy.port;
        consensus = copy.consensus;
        totalConsensus = copy.totalConsensus;
        this.agreed = copy.agreed;
    }
    public void print(){
        Log.e("Message", this.type + "<type" + "seq:" + sequence + "\nconsensus> " + this.consensus + "\nPort:" + this.port + ", " + this.message);
    }
    public int portToPid(String port){
        int pid = ((Integer.parseInt(port)) - 11108)/4;
        return pid;
    }
    public void setPort(String port_){
        int port = Integer.parseInt(port_);
        this.port = port;
//        pid = portToPid(port_);
    }
}
