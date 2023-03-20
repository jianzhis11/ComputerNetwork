package com.ouc.tcp.test;


import java.util.TimerTask;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;

public class TCP_Sender extends TCP_Sender_ADT {
	
	UDT_Timer timer;
	private TCP_PACKET tcpPack;	//待发送的TCP数据报
	private volatile int flag = 0;
		
	/*构造函数*/
	public TCP_Sender() {
		super();	//调用超类构造函数
		super.initTCP_Sender(this);		//初始化TCP发送端
	}
	
	@Override
	//可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {
		//生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(dataIndex * appData.length + 1);//包序号设置为字节流号：
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);		
				
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		
		//发送TCP数据报
		udt_send(tcpPack);
		
		//设置计时器和超时重传任务
		timer = new UDT_Timer();
		UDT_RetransTask reTrans = new UDT_RetransTask(client, tcpPack);
		//每隔3秒执行重传，直到收到ACK
		timer.schedule(reTrans, 3000, 3000);
		
		flag = 0;
		//等待ACK报文
		while(flag==0) {
			waitACK();
		}
	}
	
	@Override
	//不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		/*
		 *  设置错误控制标志：
	     *  0.信道无差错
		 *	1.只出错
		 *	2.只丢包
		 *	3.只延迟
		 *	4.出错 / 丢包
		 *	5.出错 / 延迟
		 *	6.丢包 / 延迟
		 *	7.出错 / 丢包 / 延迟
		 */
		tcpH.setTh_eflag((byte)3);
		//发送数据报
		client.send(stcpPack);
	}
	
	@Override
	//需要修改
	public void waitACK() {
		//循环检查ackQueue
		//循环检查确认号对列中是否有新收到的ACK
		if(!ackQueue.isEmpty()){
			int currentAck=ackQueue.poll();
			System.out.println("CurrentAck: "+currentAck);
			if  (currentAck == tcpPack.getTcpH().getTh_seq()){
				System.out.println("Clear: "+tcpPack.getTcpH().getTh_seq());
				timer.cancel();
				flag = 1;
			}else{
				System.out.println("Retransmit: "+tcpPack.getTcpH().getTh_seq());
				udt_send(tcpPack);
				flag = 0;
			}
		}
	}

	@Override
	//接收到ACK报文：检查校验和，将确认号插入ack队列;NACK的确认号为－1；3.0版本不需要修改
	public void recv(TCP_PACKET recvPack) {
		if(CheckSum.computeChkSum(recvPack)==recvPack.getTcpH().getTh_sum()){
			System.out.println("Receive ACK Number： "+ recvPack.getTcpH().getTh_ack());
			ackQueue.add(recvPack.getTcpH().getTh_ack());
			System.out.println();
		}else{
			System.out.println("Receive Wrong ACK Number： ");
			ackQueue.add(-1);
			System.out.println();
		}  	 	   
	}

	
}
