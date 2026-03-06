package com.example.partyplanner.data;

public class RsvpStats {
    public int going;
    public int maybe;
    public int declined;
    public int invited;

    public int total() {
        return going + maybe + declined + invited;
    }
}