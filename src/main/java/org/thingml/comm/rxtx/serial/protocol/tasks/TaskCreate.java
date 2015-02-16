package org.thingml.comm.rxtx.serial.protocol.tasks;

import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class TaskCreate implements SerialCommand {

    @Override
    public String toString() {
        return "task create\r\n";
    }

    public int priority() {
        return 20;
    }
}
