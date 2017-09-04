/* *********************************************************************** *
 * project: org.matsim.*
 * AStarLandmarksFactory
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

package org.matsim.core.router.util;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.AStarLandmarks;

/**
 * @author dgrether
 */
@Singleton
public class AStarLandmarksFactory implements LeastCostPathCalculatorFactory {

	private final Map<Network, PreProcessLandmarks> preProcessData = new HashMap<>();

	@Inject
	public AStarLandmarksFactory() {
	}

	@Override
	public LeastCostPathCalculator createPathCalculator(final Network network, final TravelDisutility travelCosts, final TravelTime travelTimes) {
		PreProcessLandmarks preProcessLandmarks = this.preProcessData.get(network);
		if (preProcessLandmarks == null) {
			preProcessLandmarks = new PreProcessLandmarks(travelCosts);
			preProcessLandmarks.setNumberOfThreads(8);
			preProcessLandmarks.run(network);
			this.preProcessData.put(network, preProcessLandmarks);
		}
		
		final double overdoFactor = 1.0;
		return new AStarLandmarks(network, preProcessLandmarks, travelCosts, travelTimes, overdoFactor);
	}

}
