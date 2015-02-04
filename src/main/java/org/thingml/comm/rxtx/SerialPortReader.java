package org.thingml.comm.rxtx;

import java.util.StringTokenizer;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

class SerialPortReader implements SerialPortEventListener {

	SerialPort serialPort = null;
	SerialObserver obs;
	
	public SerialPortReader(SerialPort serialPort,SerialObserver obs) {
		super();
		this.serialPort = serialPort;
		this.obs = obs;
	}

	StringBuilder builder  =new StringBuilder();
	
	public void serialEvent(SerialPortEvent event) {
		if (event.isRXCHAR()) {// If data is available
			// System.out.println(event.getEventValue());
			if (event.getEventValue() > 1) {// Check bytes count in the input
											// buffer
				try {
					String s =serialPort.readString();
					boolean endline = s.endsWith("\r\n");
					StringTokenizer st = new StringTokenizer(builder.toString()+ s,"\r\n");
					int size = st.countTokens();
					   for (int i = 0; st.hasMoreTokens(); i++)
					    {
						   String s3 = st.nextToken();
						   if (size-1 ==i && !endline){
								builder.setLength(0);
								builder.append(s3);							   
						   }else
								obs.receive(s3);
					    }					
				
				
				} catch (SerialPortException ex) {
					System.out.println(ex);
				}
			}
		} else if (event.isCTS()) {// If CTS line has changed state
			if (event.getEventValue() == 1) {// If line is ON
				System.out.println("CTS - ON");
			} else {
				System.out.println("CTS - OFF");
			}
		} else if (event.isDSR()) {// /If DSR line has changed state
			if (event.getEventValue() == 1) {// If line is ON
				System.out.println("DSR - ON");
			} else {
				System.out.println("DSR - OFF");
			}
		}
	}
}