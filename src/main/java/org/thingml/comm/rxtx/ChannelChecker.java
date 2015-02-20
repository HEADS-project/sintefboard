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
public class ChannelChecker {
    
    public String getChannelName( String comp1, String port1,  String comp2, String port2) {
        String ret = "";
        boolean found = false;
        
        if (found == false) {
            if (comp1.compareTo(comp2) > 0) {
                ret = comp1 + "_" + port1 + "_" + comp2 + "_" + port2;
            } else {
                ret = comp2 + "_" + port2 + "_" + comp1 + "_" + port1;
            }
        }  
        System.err.println("getChannelName(" + comp1 + ", " + port1 + ", " + comp2 + ", " + port2 + ") returns <" + ret + ")");
        
        return ret;
    }
    
    public boolean registerCommandAddBinding( String comp1, String port1,  String comp2, String port2, String channelName) {
        boolean ret = true;
        System.err.println("registerCommandAddBinding(" + comp1 + ", " + port1 + ", " + comp2 + ", " + port2 + ", " + channelName +")");
        
        return ret;
    }
    
    public boolean registerCommandRemoveBinding( String comp1, String port1,  String comp2, String port2, String channelName) {
        boolean ret = true;
        System.err.println("registerCommandRemoveBinding(" + comp1 + ", " + port1 + ", " + comp2 + ", " + port2 + ", " + channelName +")");
        
        return ret;
    }
    
    public void prepareNewInterpretion() {
        
    }
    
    public void prepareNewCommands() {
        
    }
    
}
