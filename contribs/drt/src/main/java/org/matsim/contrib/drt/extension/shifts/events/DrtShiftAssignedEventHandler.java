package org.matsim.contrib.drt.extension.shifts.events;

import org.matsim.core.events.handler.EventHandler;

/**
 * @author nkuehnel / MOIA
 */
public interface DrtShiftAssignedEventHandler extends EventHandler {
    public void handleEvent (DrtShiftAssignedEvent event);
}
