package org.thingml.comm.rxtx.serial.protocol.tasks;

import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class TaskResume implements SerialCommand {

    private String name;

    public TaskResume(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "task resume " + this.name + "\r\n";
    }

    public int priority() {
        return 11;
    }
}
