/*
 * Copyright 2015 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.comcast.cdn.traffic_control.traffic_monitor.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.wicket.ajax.json.JSONArray;
import org.apache.wicket.ajax.json.JSONException;
import org.apache.wicket.ajax.json.JSONObject;

import com.comcast.cdn.traffic_control.traffic_monitor.config.Cache;
import com.comcast.cdn.traffic_control.traffic_monitor.data.DataPoint;

public class DsState extends AbstractState {
	private static final Logger LOGGER = Logger.getLogger(DsState.class);
	private static final long serialVersionUID = 1L;
	private static Map<String, DsState> states = new HashMap<String, DsState>();

	private DsStati currentDsStati;
	private int cachesConfigured = 0;
	private int cachesAvailable = 0;
	private int cachesReporting = 0;
	final private Map<String, EmbeddedStati> locs = new HashMap<String, EmbeddedStati>();
	final private Map<String, EmbeddedStati> cacheStatiMap = new HashMap<String, EmbeddedStati>();

	public DsState(final String id) {
		super(id);
	}

	public static DsState getOrCreate(final String id) {
		synchronized(states) {
			DsState as = states.get(id);
			if(as == null) {
				as = new DsState(id);
				states.put(id, as);
			}
			return as;
		}
	}

	public static DsState getState(final String id) {
		synchronized(states) {
			return states.get(id);
		}
	}

	public void accumulate(final DsStati stati, final String location, final CacheState state) {
		if (stati == null) {
			return;
		}

		if (currentDsStati == null) {
			currentDsStati = stati;
		} else {
			currentDsStati.accumulate(stati);
		}

		EmbeddedStati loc = locs.get(location);

		if (loc == null) {
			loc = new EmbeddedStati("location", location);
			locs.put(location,loc);
		}

		loc.accumulate(stati);

		EmbeddedStati cacheStati = cacheStatiMap.get(state.id);

		if (cacheStati == null) {
			cacheStati = new EmbeddedStati("cache", state.id);
			cacheStatiMap.put(state.id, cacheStati);
		}

		cacheStati.accumulate(stati);
	}

	public boolean completeRound(final JSONObject dsControls) {
		if (currentDsStati != null && currentDsStati.out_bytes != 0) {
			putDataPoints(currentDsStati.getStati("total"));
			currentDsStati = null;
		}

		putDataPoint("caches-configured", String.valueOf(cachesConfigured));
		putDataPoint("caches-available", String.valueOf(cachesAvailable));
		putDataPoint("caches-reporting", String.valueOf(cachesReporting));

		cachesConfigured = 0;
		cachesAvailable = 0;
		cachesReporting = 0;

		HealthDeterminer.setIsAvailable(this, dsControls);

		final StringBuilder sb = new StringBuilder();

		for (String locId : locs.keySet()) {
			final EmbeddedStati loc = locs.get(locId);
			final Map<String, String> stati = loc.completeRound();

			if (stati == null) {
				continue;
			}

			putDataPoints(stati);

			if (!HealthDeterminer.setIsAvailable(this, loc, dsControls)) {
				sb.append("\"").append(locId).append("\", ");
			}
		}

		putDataPoint("disabledLocations", sb.toString());

		for (String cacheId : cacheStatiMap.keySet()) {
			final EmbeddedStati cacheStat = cacheStatiMap.get(cacheId);
			final Map<String, String> stati = cacheStat.completeRound();

			if (stati == null) {
				continue;
			}

			addHiddenStats(stati.keySet());

			putDataPoints(stati);
		}

		return true;
	}
	public static void completeAll(final List<CacheState> crStates, final HealthDeterminer myHealthDeterminer, 
			final JSONObject dsList, final long lenientTime) {
		// loop all states
		for(CacheState crstate : crStates) {
			final Cache c = crstate.getCache();
			final JSONObject dsMap = c.getDeliveryServices();
			if(dsMap != null) {
				final String location = c.getLocation();
				for(String dsId : JSONObject.getNames(dsMap)) {
					try {
						final List<String> fqdns = getFqdns(dsId, dsMap);
						final DsState dss = DsState.getOrCreate(dsId);

						// Don't count the cache as reporting unless there were no errors and stats were read
						boolean error = false;
						boolean foundStats = false;

						for(String fqdn : fqdns) {
							final String propBase = "ats.plugin.remap_stats."+fqdn;
							final DsStati stati = DsState.createStati(propBase, crstate, lenientTime, dsId);

							dss.accumulate(stati, location, crstate);

							if (stati != null) {
								foundStats = true;

								if (stati.error) {
									error = true;
								}
							}
						}

						// Update cache counters
						dss.addCacheConfigured();

						if (crstate.isAvailable()) {
							dss.addCacheAvailable();
						}

						if (foundStats && !error) {
							dss.addCacheReporting();
						}
					} catch(Exception e) {
						LOGGER.warn(e,e);
					}
				}
			}
		}

		final Collection<String> toRemove = new ArrayList<String>();
		toRemove.addAll(states.keySet());
		for(String dsId : JSONObject.getNames(dsList)) {
			toRemove.remove(dsId);
			try {
				final DsState dss = getOrCreate(dsId);
				dss.completeRound(myHealthDeterminer.getDsControls(dss.getId()));
			} catch(Exception e) {
				LOGGER.warn(e,e);
			}
		}
		for(String id : toRemove) {
			states.remove(id);
		}
	}
	private static List<String> getFqdns(final String dsId, final JSONObject dsMap) throws JSONException {
		final org.apache.wicket.ajax.json.JSONArray ja = dsMap.optJSONArray(dsId);
		final ArrayList<String> fqdns = new ArrayList<String>();
		if(ja == null) {
			fqdns.add(dsMap.getString(dsId));
		} else {
			for (int i = 0; i < ja.length(); i++) {
				fqdns.add(ja.getString(i));
			}
		}
		return fqdns;
	}

	public static DsStati createStati(final String propBase, final CacheState cs, final long leniency, final String dsId) {
		DsStati dsStati;
		synchronized (cs) {
			final Deque<DataPoint> dataPoints = cs.getDataPoints(propBase+".out_bytes");
			if(dataPoints == null) {
				return null;
			}
			long lastIndex = dataPoints.getLast().getIndex();
			lastIndex = getLastGoodIndex(dataPoints, lastIndex);
			if(lastIndex < 0) { return null; }
			final long time = cs.getTime(lastIndex);
			if(time < leniency) {
				return null;
			}
			dsStati  = new DsStati(propBase, cs, lastIndex, dsId);
			final long prevIndex = getLastGoodIndex(dataPoints, lastIndex-1);
			if(prevIndex >= 0) {
				final DsStati priorDs = new DsStati(propBase, cs, prevIndex, dsId);
				if(!dsStati.calculateKbps(priorDs)) {
					if(LOGGER.isInfoEnabled()) {
						printDps(dataPoints, propBase);
					}
				}
			}
		}
		return dsStati;
	}

	public static boolean printDps(final Deque<DataPoint> dataPoints, final String id) {
		LOGGER.warn(id+":");

		Iterator<DataPoint> dataPointsIterator = dataPoints.descendingIterator();

		while (dataPointsIterator.hasNext()) {
			DataPoint dataPoint = dataPointsIterator.next();
			LOGGER.warn(String.format("\tindex: %d, span: %d, value: %s", dataPoint.getIndex(), dataPoint.getSpan(), dataPoint.getValue()));
		}

		return false;
	}

	private static long getLastGoodIndex(final Deque<DataPoint> dataPoints, final long targetIndex) {
		if(targetIndex < 0) {
			return -1;
		}

		Iterator<DataPoint> dataPointsIterator = dataPoints.descendingIterator();

		while (dataPointsIterator.hasNext()) {
			DataPoint dataPoint = dataPointsIterator.next();

			if (dataPoint.getValue() == null) {
				continue;
			}

			long index = dataPoint.getIndex();
			long span = dataPoint.getSpan();

			if (targetIndex <= (index - span)) {
				continue;
			}

			if (targetIndex < index) {
				return targetIndex;
			}

			return index;
		}

		return -1;
	}

	public static Collection<DsState> getDsStates() {
		return states.values();
	}

	public static String get(final String stateId, final String key) {
		return get(stateId).getLastValue(key);
	}
	public static DsState get(final String host) {
		synchronized(states) {
			return states.get(host);
		}
	}
	public JSONArray getDisabledLocations() throws JSONException {
		return new JSONArray("["+this.getLastValue("disabledLocations")+"]");
	}

	public static boolean has(final String host) {
		if(states.get(host)==null) { return false; }
		return true;
	}
	public static void startUpdateAll() {
		synchronized(states) {
			for(DsState ds :states.values()) {
				ds.prepareStatisticsForUpdate();
			}
		}
	}

	public void addCacheConfigured() {
		this.cachesConfigured++;
	}

	public void addCacheAvailable() {
		this.cachesAvailable++;
	}

	public void addCacheReporting() {
		this.cachesReporting++;
	}

}
