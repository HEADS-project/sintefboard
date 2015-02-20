/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.comm.rxtx;

/**
 *
 * @author steffend
 */
public class ChannelEntry {
    String  key;
    String  channelName;
    boolean removeBindingFlag;
    boolean addBindingFlag;
    
    public ChannelEntry(String key, String channelName) {
        this.key = key;
        this.channelName = channelName;
        removeBindingFlag = false;
        addBindingFlag = false;
    }
    
    public String GetChannelName() {
        return channelName;
    }
    
    public void SetChannelName(String channelName) {
        this.channelName = channelName;
    }
    
    public String GetKey() {
        return key;
    }
    
    public void SetBindingFlags() {
        removeBindingFlag = true;
        addBindingFlag = true;
    }
    
    public boolean GetAndClearRemoveBindingFlag() {
        boolean ret = removeBindingFlag;
        removeBindingFlag = false;
        return ret;
    }

    public boolean GetAndClearAddBindingFlag() {
        boolean ret = addBindingFlag;
        addBindingFlag = false;
        return ret;
    }

}
