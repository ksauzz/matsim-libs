/* *********************************************************************** *
 * project: org.matsim.*
 * PersonPrepareForSim.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.core.population.algorithms;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.routes.ExperimentalTransitRoute;

import java.util.HashSet;
import java.util.List;

/**
 * Performs several checks that persons are ready for a mobility simulation.
 * It is intended to run only once after the initial plans are read from file,
 * as we expect that no "damage" happens to the plans during the iterations.
 * <br>
 * Currently, this only checks that in all plans the act's have a link assigned
 * and that all plans have valid routes, calculating missing links and
 * routes if required. Additionally, it will output a warning to the
 * log if a person has no plans at all.
 *
 * @author mrieser
 */
public final class PersonPrepareForSim extends AbstractPersonAlgorithm {

	private final PlanAlgorithm router;
	private final XY2Links xy2links;
	private final Network carOnlyNetwork;
	private final ActivityFacilities activityFacilities;

	private static final Logger log = Logger.getLogger(PersonPrepareForSim.class);
	private final Scenario scenario;
	
	/*
	 * To be used by the controller which creates multiple instances of this class which would
	 * create multiple copies of a car-only-network. Instead, we can create that network once in
	 * the Controller and re-use it for each new instance. cdobler, sep'15
	 */
	public PersonPrepareForSim(final PlanAlgorithm router, final Scenario scenario, final Network carOnlyNetwork) {
		super();
		this.router = router;
		this.carOnlyNetwork = carOnlyNetwork ;
		if (NetworkUtils.isMultimodal(carOnlyNetwork)) {
			throw new RuntimeException("Expected carOnlyNetwork not to be multi-modal. Aborting!");
		}
		this.xy2links = new XY2Links(carOnlyNetwork, scenario.getActivityFacilities());
		this.activityFacilities = scenario.getActivityFacilities();
		this.scenario = scenario ;
	}
	
	public PersonPrepareForSim(final PlanAlgorithm router, final Scenario scenario) {
		super();
		this.router = router;
		this.carOnlyNetwork = scenario.getNetwork();
		Network net = this.carOnlyNetwork;
		if (NetworkUtils.isMultimodal( carOnlyNetwork )) {
			log.info("Network seems to be multimodal. XY2Links will only use car links.");
			TransportModeNetworkFilter filter = new TransportModeNetworkFilter( carOnlyNetwork );
			net = NetworkUtils.createNetwork(scenario.getConfig().network());
			HashSet<String> modes = new HashSet<String>();
			modes.add(TransportMode.car);
			filter.filter(net, modes);
		}
		
		this.xy2links = new XY2Links(net, scenario.getActivityFacilities());
		this.activityFacilities = scenario.getActivityFacilities();
		this.scenario = scenario ;
	}

	@Override
	public void run(final Person person) {
		// first make sure we have a selected plan
		Plan selectedPlan = person.getSelectedPlan();
		if (selectedPlan == null) {
			// the only way no plan can be selected should be when the person has no plans at all
			log.warn("Person " + person.getId() + " has no plans!");
			return;
		}
		
		// yyyyyy need to find out somewhere here if the access/egress legs of the incoming plans
		// are consistent with the config setting.  Otherwise need to re-route all of them. kai, jul'18

		// make sure all the plans have valid act-locations and valid routes
		for (Plan plan : person.getPlans()) {
			boolean needsXY2Links = false;
			boolean needsReRoute = false;
			
			// for backward compatibility: add routingMode to legs if not present		
			for (Trip trip : TripStructureUtils.getTrips(plan.getPlanElements())) {
				List<Leg> legs = trip.getLegsOnly();
				if (legs.size() >= 1) {
					String routingMode = TripStructureUtils.getRoutingMode(legs.get(0));

					for (Leg leg : legs) {
						// check all legs either have the same routing mode or all have routingMode==null
						String existingRoutingMode = TripStructureUtils.getRoutingMode(leg);
						if (existingRoutingMode == null) {
							if (routingMode == null) {
								// outdated initial plan without routingMode
							} else {
								String errorMessage = "Found a mixed trip, some legs with routingMode and others without. "
										+ "This is inconsistent. Agent id: " + person.getId().toString();
								log.error(errorMessage);
								throw new RuntimeException(errorMessage);
							}
						} else {
							if (!routingMode.equals(existingRoutingMode)) {
								String errorMessage = "Found a trip whose legs have different routingModes. "
										+ "This is inconsistent. Agent id: " + person.getId().toString();
								log.error(errorMessage);
								throw new RuntimeException(errorMessage);
							}
						}
					}

					// add routing mode
					if (routingMode == null) {
						if (legs.size() == 1) {
							// there is only a single leg (e.g. after Trips2Legs and a mode choice replanning module)
							routingMode = legs.get(0).getMode();
							if (routingMode.equals(TransportMode.transit_walk)) {
								String errorMessage = "Found a trip of only one leg of mode transit_walk. "
										+ "This should not happen during simulation since transit_walk was replaced by walk and "
										+ "routingMode. Agent id: " + person.getId().toString();
								log.error(errorMessage);
								throw new RuntimeException(errorMessage);
							}
							TripStructureUtils.setRoutingMode(legs.get(0), routingMode);
						} else {
							String errorMessage = "Found a trip whose legs have no routingMode. "
									+ "This is only allowed for (outdated) input plans, not during simulation (after PrepareForSim). Agent id: "
									+ person.getId().toString();
							log.error(errorMessage);
							throw new RuntimeException(errorMessage);
						}
					}
				}
			}
			
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof Activity) {
					Activity act = (Activity) pe;
					if ( act.getLinkId() == null // neither activity nor facility has a link
							&&
							//this check is necessary here, else, XY2Links will put the link/coord back to activity which is clear violation of facilitiesConfigGroup.removingLinksAndCoordinates =true. Amit July'18
							( act.getFacilityId() == null
									|| this.activityFacilities.getFacilities().isEmpty()
									|| this.activityFacilities.getFacilities().get(act.getFacilityId()) == null
									|| this.activityFacilities.getFacilities().get(act.getFacilityId()).getLinkId() == null)
							) {
					    	needsXY2Links = true;
							needsReRoute = true;
							break;
					}
				} else if (pe instanceof Leg) {
					Leg leg = (Leg) pe;
					
					if (TripStructureUtils.getRoutingMode(leg) == null) {
						String errorMessage = "Routing mode not set for leg :" + leg.toString() + " of agent id " + person.getId().toString();
						log.error( errorMessage );
						throw new RuntimeException( errorMessage );
					}
					
					if (leg.getRoute() == null) {
						needsReRoute = true;
					} else if (Double.isNaN(leg.getRoute().getDistance())){
						Double dist = null;
						if (leg.getRoute() instanceof NetworkRoute){
							/* So far, 1.0 is always used as relative position on start and end link. 
							 * This means that the end link is considered in route distance and the start link not.
							 * tt feb'16
							 */
							double relativePositionStartLink = scenario.getConfig().global().getRelativePositionOfEntryExitOnLink() ;
							double relativePositionEndLink  = scenario.getConfig().global().getRelativePositionOfEntryExitOnLink() ;
//							dist = RouteUtils.calcDistance((NetworkRoute) leg.getRoute(), relativePositionStartLink, relativePositionEndLink, this.network);
							dist = RouteUtils.calcDistance((NetworkRoute) leg.getRoute(), relativePositionStartLink, relativePositionEndLink, scenario.getNetwork() );
							// using the full network for the distance calculation.  kai, jul'18
						} else if (leg.getRoute() instanceof ExperimentalTransitRoute) {
							// replace deprecated ExperimentalTransitRoute with DefaultTransitPassengerRoute
							ExperimentalTransitRoute oldRoute = (ExperimentalTransitRoute) leg.getRoute();
							DefaultTransitPassengerRoute newRoute = new DefaultTransitPassengerRoute(
									oldRoute.getStartLinkId(),
									oldRoute.getEndLinkId(),
									oldRoute.getAccessStopId(),
									oldRoute.getEgressStopId(),
									oldRoute.getLineId(),
									oldRoute.getRouteId());
							leg.setRoute(newRoute);
						}
						if (dist != null){
							leg.getRoute().setDistance(dist);
						}
					}
				}
			}
			if (needsXY2Links) {
				this.xy2links.run(plan);
			}
			if (needsReRoute) {
				this.router.run(plan);
			}
		}

	}

}
