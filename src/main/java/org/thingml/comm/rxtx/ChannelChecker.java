/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.comm.rxtx;

import java.util.ArrayList;

/**
 *
 * @author steffend
 */
public class ChannelChecker {
    
    ArrayList<ChannelEntry> chList;

    public ChannelChecker() {
        this.chList = new ArrayList<ChannelEntry>();
    }

    private String createKey( String comp1, String port1,  String comp2, String port2) {
        String ret = "";

        if (comp1.compareTo(comp2) > 0) {
            ret = comp1 + "_" + port1 + "_" + comp2 + "_" + port2;
        } else {
            ret = comp2 + "_" + port2 + "_" + comp1 + "_" + port1;
        }
        
        return ret;
    }
    public String getChannelName( String comp1, String port1,  String comp2, String port2) {
        String ret = "";
        String keyString = createKey(comp1, port1, comp2, port2);
        boolean found = false;
        
        for (ChannelEntry che : chList) {
            if (che.GetKey().contentEquals(keyString) == true) {
                ret = che.GetChannelName();
                found = true;
                break;
            }
        }
        
        if (found == false) {
            ret = keyString;
        }  
        //System.err.println("getChannelName(" + comp1 + ", " + port1 + ", " + comp2 + ", " + port2 + ") returns <" + ret + ")");
        
        return ret;
    }
    
    public boolean registerCommandAddBinding( String comp1, String port1,  String comp2, String port2, String channelName) {
        boolean ret = true;
        String keyString = createKey(comp1, port1, comp2, port2);
        boolean found = false;

        for (ChannelEntry che : chList) {
            if (che.GetKey().contentEquals(keyString) == true) {
                che.SetChannelName(channelName);
                found = true;
                
                ret = che.GetAndClearAddBindingFlag();
                break;
            }
        }
        
        if (found == false) {
            chList.add(new ChannelEntry(keyString, channelName));
        }
        //System.err.println("registerCommandAddBinding(" + comp1 + ", " + port1 + ", " + comp2 + ", " + port2 + ", " + channelName +")");
        
        return ret;
    }
    
    public boolean registerCommandRemoveBinding( String comp1, String port1,  String comp2, String port2, String channelName) {
        boolean ret = true;
        String keyString = createKey(comp1, port1, comp2, port2);
        boolean found = false;

        for (ChannelEntry che : chList) {
            if (che.GetKey().contentEquals(keyString) == true) {
                che.SetChannelName(channelName);
                found = true;
                
                ret = che.GetAndClearRemoveBindingFlag();
                break;
            }
        }
        
        if (found == false) {
            chList.add(new ChannelEntry(keyString, channelName));
        }
        //System.err.println("registerCommandRemoveBinding(" + comp1 + ", " + port1 + ", " + comp2 + ", " + port2 + ", " + channelName +")");
        
        return ret;
    }
    
    public void prepareNewInterpretion() {
        
    }
    
    public void prepareNewCommands() {
        for (ChannelEntry che : chList) {
            che.SetBindingFlags();
        }
    }
    
}
