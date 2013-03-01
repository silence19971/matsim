/* *********************************************************************** *
 * project: org.matsim.*
 * MoneyThrowEventHandler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

/**
 * 
 */
package playground.ikaddoura.optimization.externalDelayEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.PersonEntersVehicleEvent;
import org.matsim.core.api.experimental.events.PersonLeavesVehicleEvent;
import org.matsim.core.api.experimental.events.TransitDriverStartsEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.core.events.handler.TransitDriverStartsEventHandler;
import org.matsim.core.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.scenario.ScenarioImpl;



/**
 * Throws InVehicleDelayEvents to indicate that an agent entering or leaving a public vehicle delayed passengers being in that public vehicle.
 * 
 * External effects to be considered in future:
 * TODO: Capacity constraints.
 * 
 * Assumptions for the current version:
 * 1) The scheduled dwell time at transit stops is 0sec. TODO: Get dwell time from schedule and account for dwell times >0sec.
 * 2) The door operation mode of public vehicles is serial. TODO: Adjust for parallel door operation mode.
 * 3) Public vehicles start with no delay. The slack time at the end of a transit route has to be larger than the max. delay of public vehicles.
 * 4) Transit stops belong to single transit routes. Transit routes do not intersect or overlay.
 * 5) Agents board the first arriving public vehicle. The vehicle capacity has to be larger than the max. number of passengers.
 * 
 * Note: Whenever a pt vehicle stops at a transit stop due to at least one agent boarding or alighting,
 * the pt vehicle will be delayed by additional 2 sec. That is, the delay of the first transfer is equal to
 * the transfer time per agent plus exactly these 2 sec. All following passengers only cause delays according to
 * their transfer times.
 * 
 * @author Ihab
 *
 */
public class InVehicleDelayHandler implements PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, TransitDriverStartsEventHandler, VehicleArrivesAtFacilityEventHandler, VehicleDepartsAtFacilityEventHandler {
	private final static Logger log = Logger.getLogger(InVehicleDelayHandler.class);

	private final ScenarioImpl scenario;
	private final EventsManager events;
	private final List<Id> ptDriverIDs = new ArrayList<Id>();
	private final List<Id> ptVehicleIDs = new ArrayList<Id>();
	private final Map<Id, Boolean> vehId2isFirstTransfer = new HashMap<Id, Boolean>();
	private final Map<Id, Integer> vehId2passengers = new HashMap<Id, Integer>();
	private final Map<Id, Id> vehId2lastLeavingAgent = new HashMap<Id, Id>();

	public InVehicleDelayHandler(EventsManager events, ScenarioImpl scenario) {
		this.events = events;
		this.scenario = scenario;
	}
	
	@Override
	public void reset(int iteration) {
		this.vehId2isFirstTransfer.clear();
		this.vehId2passengers.clear();
		this.ptDriverIDs.clear();
		this.ptVehicleIDs.clear();
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
				
		if (!this.ptDriverIDs.contains(event.getDriverId())){
			this.ptDriverIDs.add(event.getDriverId());
		}
		
		if (!this.ptVehicleIDs.contains(event.getVehicleId())){
			this.ptVehicleIDs.add(event.getVehicleId());
		}
	}
	
	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id personId = event.getPersonId();
		Id vehId = event.getVehicleId();
		double time = event.getTime();
				
		if (!ptDriverIDs.contains(personId) && ptVehicleIDs.contains(vehId)){
										
			calculateExternalEffects(vehId, personId, this.scenario.getVehicles().getVehicles().get(vehId).getType().getAccessTime(), time);
			
			// update number of passengers in vehicle after calculating the external effect
			if (this.vehId2passengers.containsKey(vehId)){
				int passengersInVeh = this.vehId2passengers.get(vehId);
				this.vehId2passengers.put(vehId, passengersInVeh + 1);
			} else {
				this.vehId2passengers.put(vehId, 1);
			}
		}
	}
	
	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		Id personId = event.getPersonId();
		Id vehId = event.getVehicleId();
		double time = event.getTime();
		
		if (!ptDriverIDs.contains(personId) && ptVehicleIDs.contains(vehId)){							
			
			// update number of passengers in vehicle before calculating the external effect
			int passengersInVeh = this.vehId2passengers.get(vehId);
			this.vehId2passengers.put(vehId, passengersInVeh - 1);
			
			// der erste verzögert nur um 1+alighting time
			// der zweite dritte vierte ... um exakt die alighting time
			// der letzte verzögert nochmal um 1+alighting time
			// the last alighting agent will be charged extra, therefore remember last personId
			this.vehId2lastLeavingAgent.put(vehId, personId);
			
			calculateExternalEffects2(vehId, personId, this.scenario.getVehicles().getVehicles().get(vehId).getType().getEgressTime(), time);
		}
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		// Vehicle has arrived at transit stop. The following agent will be the first transfer of this vehicle.
		this.vehId2isFirstTransfer.put(event.getVehicleId(), true);
	}

	private void calculateExternalEffects(Id vehId, Id personId, double transferTime, double time) {
		
		//	Each time a public vehicle stops at a transit stop the public vehicle is delayed by 2 seconds.
		//	Assuming this time to belong to the marginal user costs of the first person entering or leaving a public vehicle.
		double delay = getActualDelay(vehId, transferTime);
		
		int delayedPassengers_inVeh = calcDelayedPassengersInVeh(vehId);
					
		InVehicleDelayEvent delayInVehicleEvent = new InVehicleDelayEvent(personId, vehId, time, delayedPassengers_inVeh, delay);
		this.events.processEvent(delayInVehicleEvent);			
	}
	
	private void calculateExternalEffects2(Id vehId, Id personId, double transferTime, double time) {
		
		//	Each time a public vehicle stops at a transit stop the public vehicle is delayed by 2 seconds.
		//	Assuming this time to belong to the marginal user costs of the first person entering or leaving a public vehicle.
		double delay = getActualDelay2(vehId, transferTime, personId);
		
		int delayedPassengers_inVeh = calcDelayedPassengersInVeh(vehId);
					
		InVehicleDelayEvent delayInVehicleEvent = new InVehicleDelayEvent(personId, vehId, time, delayedPassengers_inVeh, delay);
		this.events.processEvent(delayInVehicleEvent);			
	}
	
	private int calcDelayedPassengersInVeh(Id vehId) {
		int delayedPassengersInVeh = 0;
		if (this.vehId2passengers.containsKey(vehId)) {
			delayedPassengersInVeh = this.vehId2passengers.get(vehId);
		}
		return delayedPassengersInVeh;
	}
	
	private double getActualDelay(Id vehId, double transferTime) {
		
		boolean isFirstTransfer = this.vehId2isFirstTransfer.get(vehId);
		if (isFirstTransfer){
			this.vehId2isFirstTransfer.put(vehId, false);
		}
		
		double delay = transferTime;
		if (isFirstTransfer){
			double extraDelay = 2.;
			delay = transferTime + extraDelay;
		}
		
		return delay;
	}

	private double getActualDelay2(Id vehId, double transferTime, Id personId) {
		
		boolean isFirstTransfer = this.vehId2isFirstTransfer.get(vehId);
		if (isFirstTransfer){
			this.vehId2isFirstTransfer.put(vehId, false);
		}
				
		double delay = transferTime;
		if (isFirstTransfer){
			double extraDelay = 1.;
			delay = transferTime + extraDelay;
		}
		
		return delay;
	}

	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		// vehicle departs at facility. throw delay event for last agent that was leaving the public vehicle.
		
		if (this.vehId2lastLeavingAgent.get(event.getVehicleId()) == null){
			// no agent left the vehicle at the stop where the bus is departing from.
		} else {
			int delayedPassengers_inVeh = calcDelayedPassengersInVeh(event.getVehicleId());
			Id personId = this.vehId2lastLeavingAgent.get(event.getVehicleId());
			double delay = 1;
			InVehicleDelayEvent delayInVehicleEvent = new InVehicleDelayEvent(personId, event.getVehicleId(), event.getTime(), delayedPassengers_inVeh, delay);
			this.events.processEvent(delayInVehicleEvent);
		}
		
		this.vehId2lastLeavingAgent.remove(event.getVehicleId());
		
	}
	
}