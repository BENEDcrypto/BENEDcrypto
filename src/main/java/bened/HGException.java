/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bened;

/**
 *
 * @author zoi
 */
public class HGException extends Exception {

    private int height = -1;
    
    public HGException(String message) {
        super(message);
    }

    public HGException(int height) {
        this.height = height;
    }
    
    public HGException(String message, int height) {
        super(message);
        this.height = height;
    }
    
    public HGException() {
    }

    public int getHeight() {
        return height;
    }
    
    public boolean hasHeight() {
        return height != -1;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
