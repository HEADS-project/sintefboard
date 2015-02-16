package org.thingml.comm.rxtx.serial.protocol.channels;

import org.thingml.comm.rxtx.serial.protocol.SerialCommand;

/**
 * Created by leiko on 11/02/15.
 */
public class ChannelDelete implements SerialCommand {

    private String name0;
    private String id0;

    private String name1;
    private String id1;

    public ChannelDelete(String name0, String id0, String name1, String id1) {
        this.name0 = name0;
        this.id0 = id0;
        this.name1 = name1;
        this.id1 = id1;
        System.err.println("ChannelDelete("+name0+","+id0+","+name1+","+id1+")");
    }

    public String getName0() {
        return name0;
    }

    public void setName0(String name0) {
        this.name0 = name0;
    }

    public String getId0() {
        return id0;
    }

    public void setId0(String id0) {
        this.id0 = id0;
    }

    public String getName1() {
        return name1;
    }

    public void setName1(String name1) {
        this.name1 = name1;
    }

    public String getId1() {
        return id1;
    }

    public void setId1(String id1) {
        this.id1 = id1;
    }

    @Override
    public String toString() {
        return "channel delete " + this.name0 + " " + this.id0 + " " + this.name1 + " " + this.id1 + "\r\n";
    }

    public int priority() {
        return 7;
    }
}
