package org.thingml.comm.rxtx.serial.protocol.tasks;

import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class TaskInstantiate implements SerialCommand {

    private String type;
    private String name;

    public TaskInstantiate(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "task instantiate " + this.type + " " + this.name + "\r\n";
    }

    public int priority() {
        return 5;
    }
}
