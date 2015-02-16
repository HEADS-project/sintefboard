package org.thingml.comm.rxtx.serial.protocol.channels;

import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class ChannelList implements SerialCommand {

    @Override
    public String toString() {
        return "channel list\r\n";
    }

    public int priority() {
        return -1;
    }
}
