package org.thingml.comm.rxtx.serial.protocol.tasks;

import org.kevoree.log.Log;
import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class TaskRun implements SerialCommand {

    private String name;

    public TaskRun(String name) {
        this.name = name;
        Log.info("TaskRun("+name+")");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "task run " + this.name + "\r\n";
    }

    public int priority() {
        return 20;
    }
}
