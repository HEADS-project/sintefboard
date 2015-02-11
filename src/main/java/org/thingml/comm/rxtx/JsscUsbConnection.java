/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.comm.rxtx;

import jssc.SerialPort;
import jssc.SerialPortException;
import org.thingml.comm.rxtx.serial.SerialObserver;
import org.thingml.comm.rxtx.serial.SerialPortReader;

/**
 *
 * @author Heshan
 */
public class JsscUsbConnection {

    /**
     * @param args the command line arguments
     */
    static SerialPort serialPort;

    public static void main(String[] args) throws SerialPortException {
        serialPort = new SerialPort("/dev/ttyACM0");
        try {
            System.out.println("port open :" + serialPort.openPort());//Open port
            serialPort.setParams(SerialPort.BAUDRATE_115200,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS + SerialPort.MASK_DSR;//Prepare mask
            serialPort.setEventsMask(mask);//Set mask
            
            serialPort.addEventListener(new SerialPortReader(serialPort, new SerialObserver() {
				
				public void receive(String data) {
					if (!data.startsWith("HEADS->norm>"))
						System.err.println(data);
				}
			}));//Add SerialPortEventListener
        } catch (SerialPortException ex) {
            System.out.println(ex);
        }
        serialPort.writeBytes("\r\n".getBytes());
        serialPort.writeBytes("task list \r\n".getBytes());
        serialPort.writeBytes("channel list \r\n".getBytes());	
        serialPort.writeBytes("help\r\n".getBytes());	
    }

    
}