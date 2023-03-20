package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.message.TCP_PACKET;

import java.util.Timer;

public class Sender_Sliding_Window {
    private Client client;
    private int size = 16;	// 窗口大小
    private int base = 0;	// 窗口左沿
    private int nextIndex = 0;	// 下一个发送
    private TCP_PACKET[] packets = new TCP_PACKET[this.size];	// 存储窗口内的包

    private Timer timer;
    private TaskPacket_Retransmit task;

    public Sender_Sliding_Window(Client client) {
        this.client = client;
    }

    // 判断窗口是否是满的
    public boolean isFull() {
        return this.size <= this.nextIndex;
    }

    // 向窗口中加入包
    public void putPacket(TCP_PACKET packet) {
        this.packets[this.nextIndex] = packet;	// 在窗口的指定插入位置插入包
        // 如果在窗口左沿，则开启计时器，每隔3秒进行重传
        if (this.base == this.nextIndex) {
            this.timer = new Timer();
            this.task = new TaskPacket_Retransmit(this.client, this.packets);
            this.timer.schedule(this.task, 3000, 3000);
        }
        // 更新插入位置
        this.nextIndex++;
    }

    public void receiveACK(int currentSequence) {
    	// 收到的ACK在窗口范围内
        if (this.base <= currentSequence && currentSequence < this.base + this.size) {
        	// 将位于ACK后的包整体移动到窗口左沿
            for (int i = 0; currentSequence - this.base + 1 + i < this.size; i++) {
                this.packets[i] = this.packets[currentSequence - this.base + 1 + i];
                this.packets[currentSequence - this.base + 1 + i] = null;
            }
            // 更新插入位置
            this.nextIndex -=currentSequence - this.base + 1;
            // 更新窗口左沿指向的seq
            this.base = currentSequence + 1;
            // 停止计时
            this.timer.cancel();
            // 窗口中仍有包，重启计时器
            if (this.base != this.nextIndex) {
                this.timer = new Timer();
                this.task = new TaskPacket_Retransmit(this.client, this.packets);
                this.timer.schedule(this.task, 3000, 3000);
            }
        }
    }
}
