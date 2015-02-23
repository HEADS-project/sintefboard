package org.thingml.comm.rxtx.serial.protocol.tasks;

import org.kevoree.log.Log;
import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class TaskStop implements SerialCommand {

    private String name;

    public TaskStop(String name) {
        this.name = name;
        Log.info("TaskStop("+name+")");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "task stop " + this.name + "\r\n";
    }

    public int priority() {
        return 10;
    }
}
