/* *********************************************************************** *
 * project: org.matsim.*
 * QSimEngine.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.ptproject.qsim.netsimengine;

import org.matsim.core.mobsim.framework.Steppable;
import org.matsim.ptproject.qsim.helpers.AgentSnapshotInfoBuilder;
import org.matsim.ptproject.qsim.interfaces.SimEngine;


/**
 * Coordinates the movement of vehicles on the links and the nodes.
 *
 * @author cdobler
 * @author dgrether
 */

public abstract class QSimEngine extends LinkActivator implements SimEngine, Steppable {

	protected abstract AgentSnapshotInfoBuilder getAgentSnapshotInfoBuilder();
	
}
