package dev.astralclash.shop;

import dev.astralclash.champion.Champion;

import java.util.Arrays;
import java.util.List;

/**
 * Holds the 5 champion offerings for a single player's shop.
 * Slots can be individually locked (champion stays across rerolls).
 */
public class Shop {

    private final Champion[] slots;
    private final boolean[]  locked;

    public Shop(int size) {
        this.slots  = new Champion[size];
        this.locked = new boolean[size];
    }

    public Champion getSlot(int index)          { return slots[index]; }
    public void     setSlot(int index, Champion c) { slots[index] = c; }
    public boolean  isLocked(int index)         { return locked[index]; }
    public void     setLocked(int index, boolean v) { locked[index] = v; }

    public int      size()                      { return slots.length; }

    public void     clearSlot(int index) {
        if (!locked[index]) slots[index] = null;
    }

    public void     clearAll() {
        for (int i = 0; i < slots.length; i++) {
            if (!locked[i]) slots[i] = null;
        }
    }

    public List<Champion> getSlotsList() { return Arrays.asList(slots); }

    /** Returns true if ALL non-locked slots are empty (e.g., player bought everything). */
    public boolean isEmpty() {
        for (int i = 0; i < slots.length; i++) {
            if (!locked[i] && slots[i] != null) return false;
        }
        return true;
    }
}
