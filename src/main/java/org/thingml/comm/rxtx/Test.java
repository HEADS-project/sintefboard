package org.thingml.comm.rxtx;

public class Test {

	public static void main(String[] args) {
		String s = "Task type=rxlcd instance=rcv state=STARTED";
		String[] s1 = s.replace("Task type=", "").replace("instance=", "").replace("state=", "").split(" ");
		System.err.println(s1[0]);
		System.err.println(s1[1]);
		System.err.println(s1[2]);
	}

}
