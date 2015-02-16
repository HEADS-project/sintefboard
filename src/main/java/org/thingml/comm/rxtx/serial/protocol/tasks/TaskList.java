package org.thingml.comm.rxtx.serial.protocol.tasks;

import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class TaskList implements SerialCommand {

    @Override
    public String toString() {
        return "task list\r\n";
    }

    public int priority() {
        return 1;
    }
}
