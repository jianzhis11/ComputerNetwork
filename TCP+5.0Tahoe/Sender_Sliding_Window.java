package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.Hashtable;
import java.util.TimerTask;

public class Sender_Sliding_Window {
    private Client client;
    public int cwnd = 1;	// 滑动窗口大小  慢开始
    private volatile int ssthresh = 16;		// 慢开始门限值
    private int count = 0;  // 拥塞避免： cwmd = cwmd + 1 / cwnd，每一个对新包的 ACK count++，所以 count == cwmd 时，cwnd = cwnd + 1
    private Hashtable<Integer, TCP_PACKET> packets = new Hashtable<Integer, TCP_PACKET>();	// 存储窗口内的包
    private Hashtable<Integer, UDT_Timer> timers = new Hashtable<Integer, UDT_Timer>();		// 存储计时器
    private int lastACKSequence = -1;
    private int lastACKSequenceCount = 0;	// 快重传，重复确认的序号和重复包数量

    public Sender_Sliding_Window(Client client) {
        this.client = client;
    }

    public boolean isFull() {
        return this.cwnd <= this.packets.size();
    }

    public void putPacket(TCP_PACKET packet) {
        int currentSequence = (packet.getTcpH().getTh_seq() - 1) / 100;
        this.packets.put(currentSequence, packet);
        this.timers.put(currentSequence, new UDT_Timer());
        this.timers.get(currentSequence).schedule(new RetransmitTask(this.client, packet, this), 3000, 3000);
    }

    public void receiveACK(int currentSequence) {
        if (currentSequence == this.lastACKSequence) {
            this.lastACKSequenceCount++;
            if (this.lastACKSequenceCount == 4) {
            	System.out.println("网络拥堵!");
                TCP_PACKET packet = this.packets.get(currentSequence + 1);
                if (packet != null) {
                    this.client.send(packet);
                    this.timers.get(currentSequence + 1).cancel();
                    this.timers.put(currentSequence + 1, new UDT_Timer());
                    this.timers.get(currentSequence + 1).schedule(new RetransmitTask(this.client, packet, this), 3000, 3000);
                }
                reStart();
            }
        } else {
            for (int i = this.lastACKSequence + 1; i <= currentSequence; i++) {
                this.packets.remove(i);
                if (this.timers.containsKey(i)) {
                    this.timers.get(i).cancel();
                    this.timers.remove(i);
                }
            }
            this.lastACKSequence = currentSequence;
            this.lastACKSequenceCount = 1;
            if (this.cwnd < this.ssthresh) {
            	System.out.println("慢开始");
                System.out.println("ssthresh: "+ssthresh);
                System.out.println("cwnd: "+cwnd+"-->"+(cwnd*2));
                this.cwnd=cwnd*2;
                System.out.println("########### window expand ############");
            } else {	
                this.count++;
                if (this.count >= this.cwnd) {
                	System.out.println("拥塞避免");
                	System.out.println("ssthresh: "+ssthresh);
                    System.out.println("cwnd: "+cwnd+"-->"+(cwnd+1));
                    this.cwnd++;
                    System.out.println("########### window expand ############");
                }
            }
        }
    }

    // 重传
    public void reStart() {
    	System.out.println("ssthresh: "+ssthresh+"-->"+(this.cwnd/2));
        this.ssthresh = this.cwnd / 2;
        System.out.println("cwnd: "+cwnd+"-->"+1);
        this.cwnd = 1;
    }
}

class RetransmitTask extends TimerTask {
    private Client client;
    private TCP_PACKET packet;
    private Sender_Sliding_Window window;

    public RetransmitTask(Client client, TCP_PACKET packet, Sender_Sliding_Window window) {
        this.client = client;
        this.packet = packet;
        this.window = window;
    }

    @Override
    public void run() {
        this.window.reStart();

        this.client.send(this.packet);
    }
}
